package com.mercury.sqkon.db.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.mercury.sqkon.db.Entity
import com.mercury.sqkon.db.SqkonTransactionScope
import com.mercury.sqkon.db.internal.SqkonQuery
import com.mercury.sqkon.db.internal.SqkonTransacter
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * Keyset-based [PagingSource] that uses pre-calculated page boundary keys for stable pagination.
 * Unlike [OffsetQueryPagingSource], boundaries don't drift under concurrent writes and a page
 * never skips OFFSET rows. Note: each page still runs a `ROW_NUMBER()` window over the filtered
 * set (O(n) per page), not an indexed range scan — see the Paging guide (#80).
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
 * @param countQuery Total (DISTINCT-entity) row count. Reported as [PagingSource.LoadResult.Page]
 *   itemsBefore/itemsAfter so Paging3 keeps ABSOLUTE list indices — a mediator-write invalidation
 *   then preserves scroll position instead of collapsing the loaded window. Executed at most once
 *   per source, and only when the consumer enables placeholders (skipped otherwise — Paging then
 *   uses COUNT_UNDEFINED with no count query). Positions are exact when each entity maps to one row
 *   (the common case); a json_tree-multiplying `where` inherits keyset's raw-row over-count (#68).
 * @param transacter Used to run queries within a transaction for consistency.
 * @param context Coroutine context for query execution.
 * @param deserialize Converts an [Entity] to the target type, returning null to skip.
 * @param onRowsLoaded Invoked once per load with the raw rows fetched for the page (before
 *   deserialize), so callers can mark them read without re-running the query.
 */
internal class KeysetQueryPagingSource<T : Any>(
    private val queryProvider: (beginInclusive: String, endExclusive: String?) -> SqkonQuery<Entity>,
    private val pageBoundariesProvider: (anchor: String?, limit: Long) -> SqkonQuery<String>,
    private val boundaryForKeyProvider: (lookupKey: String, limit: Long) -> SqkonQuery<String>,
    private val countQuery: SqkonQuery<Int>,
    private val transacter: SqkonTransacter,
    private val context: CoroutineContext,
    private val deserialize: (Entity) -> T?,
    private val pageSize: Int,
    private val onRowsLoaded: (List<Entity>) -> Unit = {},
) : QueryPagingSource<String, T>() {

    private var pageBoundaries: List<String>? = null
    private var totalCount: Int = 0

    override val jumpingSupported: Boolean get() = false

    override suspend fun load(
        params: PagingSource.LoadParams<String>,
    ): PagingSource.LoadResult<String, T> = withContext(context) {
        loadResultCatching {
            // Rows fetched inside the transaction, hoisted so read-tracking runs *after* the
            // invalid check below — a load discarded as Invalid must not mark rows read (#119).
            // Stays null when no page query ran (empty boundaries).
            var loadedRows: List<Entity>? = null
            val getPagingSourceLoadResult: SqkonTransactionScope.() -> PagingSource.LoadResult<String, T> =
                {
                    // Always use the stable pageSize for boundary computation, not
                    // params.loadSize which varies (initialLoadSize on first Refresh).
                    val boundaries = pageBoundaries ?: run {
                        val boundariesQuery = pageBoundariesProvider(params.key, pageSize.toLong())
                        val list = boundariesQuery.executeAsList()
                        pageBoundaries = list
                        // Only when the consumer uses placeholders: otherwise Paging ignores
                        // itemsBefore/itemsAfter, so skip the COUNT query entirely. Once per source,
                        // in the boundaries' transaction (keeps count and boundaries consistent).
                        if (params.placeholdersEnabled) totalCount = countQuery.executeAsOne()
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
                            itemsBefore = 0,
                            itemsAfter = 0,
                        )
                    } else {
                        // Snap params.key to a boundary in the freshly computed set. The key may
                        // be (a) null on first load, (b) already a boundary, or (c) a stale
                        // boundary key from the previous source that no longer aligns with the
                        // new boundaries because a mediator wrote between the two boundary
                        // computations. Case (c) is what would otherwise throw
                        // `Key X not found in page boundaries`.
                        val requestedKey = params.key
                        val keyIndex = when {
                            requestedKey == null -> 0
                            else -> {
                                val idx = boundaries.indexOf(requestedKey)
                                if (idx >= 0) idx else {
                                    val snapped = boundaryForKeyProvider(requestedKey, pageSize.toLong())
                                        .executeAsOneOrNull()
                                    snapped?.let { boundaries.indexOf(it) }?.takeIf { it >= 0 } ?: 0
                                }
                            }
                        }
                        val key = boundaries[keyIndex]
                        val previousKey = boundaries.getOrNull(keyIndex - 1)
                        // Load enough whole pages to satisfy params.loadSize. On REFRESH
                        // Paging requests initialLoadSize (typically 3 * pageSize) so the
                        // previously-visible window repopulates in one atomic page; loading a
                        // single page here would blink the off-anchor visible rows to
                        // placeholders until follow-up prefetch APPEND/PREPEND refills them
                        // (#128). APPEND/PREPEND use loadSize == pageSize, so this loads one
                        // page for them — unchanged behaviour. nextKey is the boundary past the
                        // loaded window so the next APPEND resumes exactly where this ends.
                        val pageCount = ((params.loadSize + pageSize - 1) / pageSize)
                            .coerceAtLeast(1)
                        val nextKey = boundaries.getOrNull(keyIndex + pageCount)

                        val entities = queryProvider(key, nextKey)
                            .also { currentQuery = it }
                            .executeAsList()
                        loadedRows = entities
                        val results = entities.mapNotNull { deserialize(it) }

                        if (params.placeholdersEnabled) {
                            // Boundaries sit every pageSize rows, so page keyIndex starts at row
                            // keyIndex * pageSize; coerce guards boundary/count skew.
                            val itemsBefore = (keyIndex * pageSize).coerceAtMost(totalCount)
                            PagingSource.LoadResult.Page(
                                data = results,
                                prevKey = previousKey,
                                nextKey = nextKey,
                                itemsBefore = itemsBefore,
                                itemsAfter = (totalCount - itemsBefore - results.size).coerceAtLeast(0),
                            )
                        } else {
                            // Placeholders off → Paging uses COUNT_UNDEFINED; no count needed.
                            PagingSource.LoadResult.Page(
                                data = results,
                                prevKey = previousKey,
                                nextKey = nextKey,
                            )
                        }
                    }
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
        // Single loaded page: prevKey is the boundary *before* this page (the closest
        // hint to the anchor's location); null means the anchor is in the first page,
        // and our load(null) resolves to boundaries.first() — preserving the anchor.
        // Do not fall back to nextKey: that points to the *next* page's start, which
        // would load past the anchor row and shift it off-screen.
        return anchorPage.prevKey
    }
}
