package com.mercury.sqkon.db.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
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
        params: PagingSource.LoadParams<Int>,
    ): PagingSource.LoadResult<Int, T> = withContext(context) {
        val key = params.key ?: initialOffset
        val limit = when (params) {
            is PagingSource.LoadParams.Prepend<*> -> minOf(key, params.loadSize)
            else -> params.loadSize
        }
        val getPagingSourceLoadResult: TransactionCallbacks.() -> PagingSource.LoadResult.Page<Int, T> =
            {
                val count = countQuery.executeAsOne()
                val offset = when (params) {
                    is PagingSource.LoadParams.Prepend<*> -> maxOf(0, key - params.loadSize)
                    is PagingSource.LoadParams.Append<*> -> key
                    is PagingSource.LoadParams.Refresh<*> -> {
                        if (key >= count) maxOf(0, count - params.loadSize) else key
                    }

                    else -> error("Unknown PagingSourceLoadParams ${params::class}")
                }
                val data = queryProvider(limit, offset)
                    .also { currentQuery = it }
                    .executeAsList()
                    .mapNotNull { deserialize(it) }
                val nextPosToLoad = offset + data.size
                PagingSource.LoadResult.Page(
                    data = data,
                    prevKey = offset.takeIf { it > 0 && data.isNotEmpty() },
                    nextKey = nextPosToLoad.takeIf { data.isNotEmpty() && data.size >= limit && it < count },
                    itemsBefore = offset,
                    itemsAfter = maxOf(0, count - nextPosToLoad),
                )
            }
        val loadResult = transacter
            .transactionWithResult(bodyWithReturn = getPagingSourceLoadResult)
        (if (invalid) PagingSource.LoadResult.Invalid() else loadResult)
    }

    override fun getRefreshKey(state: PagingState<Int, T>): Int? {
        return state.anchorPosition?.let { maxOf(0, it - (state.config.initialLoadSize / 2)) }
    }
}
