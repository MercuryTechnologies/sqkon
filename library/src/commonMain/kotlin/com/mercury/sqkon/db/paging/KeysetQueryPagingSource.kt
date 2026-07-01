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

    /** Whole pageSize pages needed to cover [rows] rows (ceil division). */
    private fun pagesFor(rows: Int): Int = (rows + pageSize - 1) / pageSize

    /**
     * Pages the load window extends *behind* the requested page — the centering shift.
     * [load] and [getRefreshKey] must agree on this geometry: getRefreshKey picks a key
     * assuming exactly this window placement will cover the anchor.
     */
    private fun windowBackShift(pageCount: Int): Int = (pageCount - 1) / 2

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
                        // Load enough whole pages for params.loadSize, centered on the
                        // requested page, so a REFRESH (loadSize = initialLoadSize)
                        // repopulates the visible window around the anchor in one atomic
                        // page instead of blinking off-anchor rows to placeholders (#128).
                        // APPEND/PREPEND pass loadSize == pageSize: one page, no shift.
                        val pageCount = pagesFor(params.loadSize)
                        val startIndex = (keyIndex - windowBackShift(pageCount))
                            .coerceAtLeast(0)
                        val startKey = boundaries[startIndex]
                        val previousKey = boundaries.getOrNull(startIndex - 1)
                        val nextKey = boundaries.getOrNull(startIndex + pageCount)

                        val entities = queryProvider(startKey, nextKey)
                            .also { currentQuery = it }
                            .executeAsList()
                        loadedRows = entities
                        val results = entities.mapNotNull { deserialize(it) }

                        if (params.placeholdersEnabled) {
                            // Boundaries sit every pageSize rows, so the window starting at
                            // startIndex begins at row startIndex * pageSize; coerce guards
                            // boundary/count skew.
                            val itemsBefore = (startIndex * pageSize).coerceAtMost(totalCount)
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
        // calls this on a fresh source before its first load(). Pages are
        // contiguous, so any page loaded after another has its exact load key in
        // the preceding page's nextKey — regardless of how many pageSize pages
        // either spans. (pages[i+1].prevKey is NOT equivalent: for a multi-page
        // refresh window it sits pageCount-1 pages past the window start, which
        // would drop the anchor and reintroduce the #128 blink.)
        val anchorPosition = state.anchorPosition ?: return null
        val anchorPage = state.closestPageToPosition(anchorPosition) ?: return null
        val anchorIndex = state.pages.indexOf(anchorPage)
        state.pages.getOrNull(anchorIndex - 1)?.let { return it.nextKey }

        // First loaded page: its own load key is not recoverable from state, and it
        // may span several pageSize pages (a refresh window honoring initialLoadSize).
        // The only keys state still holds sit at known sub-pages of this page's
        // window (pageSpan = 3 shown):
        //
        //   sub-page:   -1    |   0      1      2   |    3
        //   key:      prevKey | [--- this page ---] |  nextKey
        //                                       ^ pages[1].prevKey = sub-page 2
        //
        // A refresh from a key at sub-page k loads windowPages pages starting
        // backReach pages before k (load()'s centering, mirrored). Find the anchor's
        // sub-page and return the key whose window covers it.
        if (anchorPage.data.isEmpty()) return anchorPage.prevKey
        val pageSpan = pagesFor(anchorPage.data.size)
        val leadingPlaceholders = anchorPage.itemsBefore
            .takeIf { it != PagingSource.LoadResult.Page.COUNT_UNDEFINED } ?: 0
        val subPage = (anchorPosition - leadingPlaceholders)
            .coerceIn(0, anchorPage.data.size - 1) / pageSize
        val windowPages = pagesFor(state.config.initialLoadSize)
        val backReach = windowBackShift(windowPages)
        fun windowFromKeyAtCoversAnchor(keySubPage: Int): Boolean =
            subPage >= keySubPage - backReach &&
                subPage < keySubPage - backReach + windowPages

        // The late-side candidate: pages[1].prevKey when a next page exists (one page
        // before this page's end), else this page's own nextKey (its end).
        val nextPage = state.pages.getOrNull(anchorIndex + 1)
        val (lateKey, lateKeySubPage) = when {
            nextPage != null -> nextPage.prevKey to pageSpan - 1
            else -> anchorPage.nextKey to pageSpan
        }
        return when {
            windowFromKeyAtCoversAnchor(lateKeySubPage) -> lateKey
            // prevKey sits at sub-page -1 — covers early sub-pages, and is the closest
            // fallback when nothing covers (a lone multi-page window's middle). null
            // means the page starts at boundaries.first(), and load(null) preserves that.
            else -> anchorPage.prevKey
        }
    }
}
