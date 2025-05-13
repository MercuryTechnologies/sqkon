package com.mercury.sqkon.db.paging

import app.cash.paging.PagingSourceLoadParams
import app.cash.paging.PagingSourceLoadParamsAppend
import app.cash.paging.PagingSourceLoadParamsPrepend
import app.cash.paging.PagingSourceLoadParamsRefresh
import app.cash.paging.PagingSourceLoadResult
import app.cash.paging.PagingSourceLoadResultInvalid
import app.cash.paging.PagingSourceLoadResultPage
import app.cash.paging.PagingState
import app.cash.sqldelight.Query
import app.cash.sqldelight.Transacter
import app.cash.sqldelight.TransactionCallbacks
import com.mercury.sqkon.db.Entity
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

internal class OffsetQueryPagingSource<T : Any>(
    private val queryProvider: (limit: Int, offset: Int) -> Query<Entity>,
    private val countQuery: Query<Int>,
    private val transacter: Transacter,
    private val context: CoroutineContext,
    private val deserialize: (Entity) -> T?,
    private val initialOffset: Int,
) : QueryPagingSource<Int, T>() {

    override val jumpingSupported get() = true

    override suspend fun load(
        params: PagingSourceLoadParams<Int>,
    ): PagingSourceLoadResult<Int, T> = withContext(context) {
        val key = params.key ?: initialOffset
        val limit = when (params) {
            is PagingSourceLoadParamsPrepend<*> -> minOf(key, params.loadSize)
            else -> params.loadSize
        }
        val getPagingSourceLoadResult: TransactionCallbacks.() -> PagingSourceLoadResultPage<Int, T> =
            {
                val count = countQuery.executeAsOne()
                val offset = when (params) {
                    is PagingSourceLoadParamsPrepend<*> -> maxOf(0, key - params.loadSize)
                    is PagingSourceLoadParamsAppend<*> -> key
                    is PagingSourceLoadParamsRefresh<*> -> {
                        if (key >= count) maxOf(0, count - params.loadSize) else key
                    }

                    else -> error("Unknown PagingSourceLoadParams ${params::class}")
                }
                val data = queryProvider(limit, offset)
                    .also { currentQuery = it }
                    .executeAsList()
                    .mapNotNull { deserialize(it) }
                val nextPosToLoad = offset + data.size
                PagingSourceLoadResultPage(
                    data = data,
                    prevKey = offset.takeIf { it > 0 && data.isNotEmpty() },
                    nextKey = nextPosToLoad.takeIf { data.isNotEmpty() && data.size >= limit && it < count },
                    itemsBefore = offset,
                    itemsAfter = maxOf(0, count - nextPosToLoad),
                )
            }
        val loadResult = transacter
            .transactionWithResult(bodyWithReturn = getPagingSourceLoadResult)
        (if (invalid) PagingSourceLoadResultInvalid() else loadResult)
    }

    override fun getRefreshKey(state: PagingState<Int, T>): Int? {
        return state.anchorPosition?.let { maxOf(0, it - (state.config.initialLoadSize / 2)) }
    }
}
