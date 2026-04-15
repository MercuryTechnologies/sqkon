package com.mercury.sqkon.db.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import app.cash.sqldelight.Query
import app.cash.sqldelight.Transacter
import app.cash.sqldelight.TransactionCallbacks
import com.mercury.sqkon.db.Entity
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * Keyset-based [PagingSource] that uses pre-calculated page boundary keys for efficient
 * pagination. Unlike [OffsetQueryPagingSource], this avoids the O(n) cost of OFFSET on large
 * datasets by using indexed key lookups for each page.
 *
 * Page boundaries are computed once per PagingSource lifecycle. On invalidation (data change),
 * the Pager creates a new source which recomputes boundaries.
 *
 * @param queryProvider Returns entities within a key range [beginInclusive, endExclusive).
 *   When endExclusive is null, returns all remaining entities from beginInclusive.
 * @param pageBoundariesProvider Returns the entity_key at each page boundary, given an optional
 *   anchor key and the page size as limit.
 * @param transacter Used to run queries within a transaction for consistency.
 * @param context Coroutine context for query execution.
 * @param deserialize Converts an [Entity] to the target type, returning null to skip.
 */
internal class KeysetQueryPagingSource<T : Any>(
    private val queryProvider: (beginInclusive: String, endExclusive: String?) -> Query<Entity>,
    private val pageBoundariesProvider: (anchor: String?, limit: Long) -> Query<String>,
    private val transacter: Transacter,
    private val context: CoroutineContext,
    private val deserialize: (Entity) -> T?,
) : QueryPagingSource<String, T>() {

    private var pageBoundaries: List<String>? = null

    override val jumpingSupported: Boolean get() = false

    override suspend fun load(
        params: PagingSource.LoadParams<String>,
    ): PagingSource.LoadResult<String, T> = withContext(context) {
        try {
            val getPagingSourceLoadResult: TransactionCallbacks.() -> PagingSource.LoadResult<String, T> =
                {
                    val boundaries = pageBoundaries
                        ?: pageBoundariesProvider(params.key, params.loadSize.toLong())
                            .executeAsList()
                            .also { pageBoundaries = it }

                    if (boundaries.isEmpty()) {
                        PagingSource.LoadResult.Page(
                            data = emptyList(),
                            prevKey = null,
                            nextKey = null,
                        )
                    } else {
                        val key = params.key ?: boundaries.first()
                        val keyIndex = boundaries.indexOf(key)
                        require(keyIndex != -1) { "Key $key not found in page boundaries" }

                        val previousKey = boundaries.getOrNull(keyIndex - 1)
                        val nextKey = boundaries.getOrNull(keyIndex + 1)

                        val results = queryProvider(key, nextKey)
                            .also { currentQuery = it }
                            .executeAsList()
                            .mapNotNull { deserialize(it) }

                        PagingSource.LoadResult.Page(
                            data = results,
                            prevKey = previousKey,
                            nextKey = nextKey,
                        )
                    }
                }
            val loadResult = transacter
                .transactionWithResult(bodyWithReturn = getPagingSourceLoadResult)
            if (invalid) PagingSource.LoadResult.Invalid() else loadResult
        } catch (e: Exception) {
            if (invalid) PagingSource.LoadResult.Invalid()
            else PagingSource.LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<String, T>): String? {
        val boundaries = pageBoundaries ?: return null
        val last = state.pages.lastOrNull() ?: return null
        val keyIndexFromNext = last.nextKey?.let { boundaries.indexOf(it) - 1 }
        val keyIndexFromPrev = last.prevKey?.let { boundaries.indexOf(it) + 1 }
        val keyIndex = keyIndexFromNext ?: keyIndexFromPrev ?: return null
        return boundaries.getOrNull(keyIndex)
    }
}
