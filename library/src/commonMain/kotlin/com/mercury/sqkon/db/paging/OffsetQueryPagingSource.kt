package com.mercury.sqkon.db.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.mercury.sqkon.db.Entity
import com.mercury.sqkon.db.SqkonTransactionScope
import com.mercury.sqkon.db.internal.SqkonQuery
import com.mercury.sqkon.db.internal.SqkonTransacter
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

internal class OffsetQueryPagingSource<T : Any>(
    private val queryProvider: (limit: Int, offset: Int) -> SqkonQuery<Entity>,
    private val countQuery: SqkonQuery<Int>,
    private val transacter: SqkonTransacter,
    private val context: CoroutineContext,
    private val deserialize: (Entity) -> T?,
    private val initialOffset: Int,
    private val onRowsLoaded: (List<Entity>) -> Unit = {},
) : QueryPagingSource<Int, T>() {

    // Computed once per source. The DISTINCT-entity count only changes when the rowset changes
    // (insert/delete/upsert), and those writes notify entityKey(entityName), invalidating this
    // source so the Pager builds a fresh one that recomputes. read_at writes deliberately notify
    // only ALL_ENTITIES_KEY (not entityKey) to avoid re-waking paging, so they neither invalidate
    // this source nor change the count — the cached value stays valid for the source's lifetime.
    // Same assumption KeysetQueryPagingSource.totalCount relies on (#117, #118).
    private var totalCount: Int? = null

    override val jumpingSupported get() = true

    override suspend fun load(
        params: PagingSource.LoadParams<Int>,
    ): PagingSource.LoadResult<Int, T> = withContext(context) {
        loadResultCatching {
            val key = params.key ?: initialOffset
            val limit = when (params) {
                is PagingSource.LoadParams.Prepend<*> -> minOf(key, params.loadSize)
                else -> params.loadSize
            }
            // Rows fetched inside the transaction, hoisted so read-tracking runs *after* the
            // invalid check below — a load discarded as Invalid must not mark rows read (#119).
            var loadedRows: List<Entity>? = null
            val getPagingSourceLoadResult: SqkonTransactionScope.() -> PagingSource.LoadResult.Page<Int, T> =
                {
                    val count = totalCount ?: countQuery.executeAsOne().also { totalCount = it }
                    val offset = when (params) {
                        is PagingSource.LoadParams.Prepend<*> -> maxOf(0, key - params.loadSize)
                        is PagingSource.LoadParams.Append<*> -> key
                        is PagingSource.LoadParams.Refresh<*> -> {
                            if (key >= count) maxOf(0, count - params.loadSize) else key
                        }

                        else -> error("Unknown PagingSourceLoadParams ${params::class}")
                    }
                    val entities = queryProvider(limit, offset)
                        .also { currentQuery = it }
                        .executeAsList()
                    loadedRows = entities
                    val data = entities.mapNotNull { deserialize(it) }
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
                .transactionWithResult(body = getPagingSourceLoadResult)
            if (invalid) {
                PagingSource.LoadResult.Invalid()
            } else {
                // Mark rows read only for a page that is actually returned to Paging3.
                loadedRows?.let(onRowsLoaded)
                loadResult
            }
        }
    }

    override fun getRefreshKey(state: PagingState<Int, T>): Int? {
        return state.anchorPosition?.let { maxOf(0, it - (state.config.initialLoadSize / 2)) }
    }
}
