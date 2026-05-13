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
 * @param boundaryForKeyProvider Given an entity_key and the page size, returns the boundary key
 *   for the page that contains it. Used to snap a refresh key that came from stale boundaries
 *   (e.g. a RemoteMediator wrote rows between the previous source's load and the new source's
 *   boundary recomputation) back onto a real boundary in the new set.
 * @param transacter Used to run queries within a transaction for consistency.
 * @param context Coroutine context for query execution.
 * @param deserialize Converts an [Entity] to the target type, returning null to skip.
 */
internal class KeysetQueryPagingSource<T : Any>(
    private val queryProvider: (beginInclusive: String, endExclusive: String?) -> Query<Entity>,
    private val pageBoundariesProvider: (anchor: String?, limit: Long) -> Query<String>,
    private val boundaryForKeyProvider: (lookupKey: String, limit: Long) -> Query<String>,
    private val transacter: Transacter,
    private val context: CoroutineContext,
    private val deserialize: (Entity) -> T?,
    private val pageSize: Int,
) : QueryPagingSource<String, T>() {

    private var pageBoundaries: List<String>? = null

    override val jumpingSupported: Boolean get() = false

    override suspend fun load(
        params: PagingSource.LoadParams<String>,
    ): PagingSource.LoadResult<String, T> = withContext(context) {
        try {
            val getPagingSourceLoadResult: TransactionCallbacks.() -> PagingSource.LoadResult<String, T> =
                {
                    // Always use the stable pageSize for boundary computation, not
                    // params.loadSize which varies (initialLoadSize on first Refresh).
                    val boundaries = pageBoundaries ?: run {
                        val boundariesQuery = pageBoundariesProvider(params.key, pageSize.toLong())
                        val list = boundariesQuery.executeAsList()
                        pageBoundaries = list
                        if (list.isEmpty()) {
                            // Register a listener on the boundaries query so an empty→populated
                            // transition (e.g., RemoteMediator initial write) triggers invalidation.
                            currentQuery = boundariesQuery
                        }
                        list
                    }

                    if (boundaries.isEmpty()) {
                        PagingSource.LoadResult.Page(
                            data = emptyList(),
                            prevKey = null,
                            nextKey = null,
                        )
                    } else {
                        // Snap params.key to a boundary in the freshly computed set. The key may
                        // be (a) null on first load, (b) already a boundary, or (c) a stale
                        // boundary key from the previous source that no longer aligns with the
                        // new boundaries because a mediator wrote between the two boundary
                        // computations. Case (c) is what would otherwise throw
                        // `Key X not found in page boundaries`.
                        val requestedKey = params.key
                        val key = when {
                            requestedKey == null -> boundaries.first()
                            requestedKey in boundaries -> requestedKey
                            else -> {
                                val snapped = boundaryForKeyProvider(requestedKey, pageSize.toLong())
                                    .executeAsOneOrNull()
                                if (snapped != null && snapped in boundaries) snapped else boundaries.first()
                            }
                        }
                        val keyIndex = boundaries.indexOf(key)
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
        // Must derive from state, not the instance's pageBoundaries — Paging3
        // calls this on a fresh source before its first load(). Our keys are
        // page-start boundaries, so the anchor page's own load key equals
        // pages[i+1].prevKey (or pages[i-1].nextKey); using anchorPage.prevKey
        // /nextKey directly would shift the user by one full page.
        val anchorPosition = state.anchorPosition ?: return null
        val anchorPage = state.closestPageToPosition(anchorPosition) ?: return null
        val anchorIndex = state.pages.indexOf(anchorPage)
        state.pages.getOrNull(anchorIndex + 1)?.let { return it.prevKey }
        state.pages.getOrNull(anchorIndex - 1)?.let { return it.nextKey }
        // Single loaded page: prevKey is the boundary *before* this page, nextKey is
        // the boundary *after*. Either is a usable hint — load() will snap it to a
        // real boundary if the set has since shifted. Prefer prevKey so the user
        // lands on or before their current view; fall back to nextKey when prevKey
        // is null (anchor in the first loaded page).
        return anchorPage.prevKey ?: anchorPage.nextKey
    }
}
