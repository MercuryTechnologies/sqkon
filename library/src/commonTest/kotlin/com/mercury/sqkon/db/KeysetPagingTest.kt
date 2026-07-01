package com.mercury.sqkon.db

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingSource.LoadResult
import androidx.paging.testing.TestPager
import androidx.paging.testing.asSnapshot
import com.mercury.sqkon.TestObject
import com.mercury.sqkon.until
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KeysetPagingTest {

    private val mainScope = MainScope()
    private val driver = driverFactory().createDriver()
    private val entityQueries = EntityQueries(driver)
    private val metadataQueries = MetadataQueries(driver)
    private val testObjectStorage = keyValueStorage<TestObject>(
        "test-object", entityQueries, metadataQueries, mainScope
    )

    @After
    fun tearDown() {
        mainScope.cancel()
    }

    @Test
    fun keysetPageByTen() = runTest {
        val expected = (1..100).map { TestObject() }.associateBy { it.id }
        testObjectStorage.insertAll(expected)

        val config = PagingConfig(pageSize = 10, prefetchDistance = 0, initialLoadSize = 10)
        val pager = TestPager(config, testObjectStorage.selectKeysetPagingSource(pageSize = 10))

        val result = pager.refresh() as LoadResult.Page<String, TestObject>
        assertEquals(10, result.data.size, "Page result should contain 10 items")
        assertNull(result.prevKey, "Prev key should be null for first page")
        assertNotNull(result.nextKey, "Next key should not be null when more pages exist")
    }

    @Test
    fun keysetPageByTenAppending() = runTest {
        val expected = (1..100).map { TestObject() }.associateBy { it.id }
        testObjectStorage.insertAll(expected)

        val config = PagingConfig(pageSize = 10, prefetchDistance = 0, initialLoadSize = 10)
        val pager = TestPager(config, testObjectStorage.selectKeysetPagingSource(pageSize = 10))

        val result = with(pager) {
            refresh()
            append()
            append()
        } as LoadResult.Page<String, TestObject>
        assertEquals(10, result.data.size, "Page result should contain 10 items")
        assertNotNull(result.prevKey, "Prev key should not be null after first page")
        assertNotNull(result.nextKey, "Next key should not be null when more pages exist")

        assertEquals(3, pager.getPages().size, "Should have 3 pages")
    }

    @Test
    fun keysetPageToEnd() = runTest {
        val expected = (1..25).map { TestObject() }.associateBy { it.id }
        testObjectStorage.insertAll(expected)

        val config = PagingConfig(pageSize = 10, prefetchDistance = 0, initialLoadSize = 10)
        val pager = TestPager(config, testObjectStorage.selectKeysetPagingSource(pageSize = 10))

        with(pager) {
            refresh()
            append()
            val lastPage = append() as LoadResult.Page<String, TestObject>
            assertEquals(5, lastPage.data.size, "Last page should have remaining 5 items")
            assertNull(lastPage.nextKey, "Next key should be null on last page")
        }
    }

    @Test
    fun keysetPagingNoDuplicatesAcrossPages() = runTest {
        val expected = (1..50).map { TestObject() }.associateBy { it.id }
        testObjectStorage.insertAll(expected)

        val config = PagingConfig(pageSize = 10, prefetchDistance = 0, initialLoadSize = 10)
        val pager = TestPager(config, testObjectStorage.selectKeysetPagingSource(pageSize = 10))

        val allItems = with(pager) {
            refresh()
            append()
            append()
            append()
            append()
            getPages().flatMap { (it as LoadResult.Page<String, TestObject>).data }
        }
        assertEquals(50, allItems.size, "Should have all 50 items across pages")
        assertEquals(
            allItems.map { it.id }.toSet().size, allItems.size,
            "All items should be unique across pages"
        )
    }

    @Test
    fun keysetPageWithOrderBy() = runTest {
        val objects = (1..30).map { i -> TestObject(value = 1000 + i) }
            .associateBy { it.id }
        testObjectStorage.insertAll(objects)

        val config = PagingConfig(pageSize = 10, prefetchDistance = 0, initialLoadSize = 10)
        val pager = TestPager(
            config,
            testObjectStorage.selectKeysetPagingSource(
                pageSize = 10,
                orderBy = listOf(OrderBy(TestObject::value, OrderDirection.ASC))
            )
        )

        val result = pager.refresh() as LoadResult.Page<String, TestObject>
        assertEquals(10, result.data.size)
        // Verify ordering is maintained
        val values = result.data.map { it.value }
        assertEquals(values.sorted(), values, "Results should be sorted by value ASC")
    }

    @Test
    fun keysetPaging_Invalidation() = runTest {
        val expected = (1..100).map { TestObject() }.associateBy { it.id }
        testObjectStorage.insertAll(expected)

        val config = PagingConfig(pageSize = 10, prefetchDistance = 0, initialLoadSize = 10)
        val results = mutableListOf<PagingData<TestObject>>()
        val pagerFlow = Pager(
            config,
            pagingSourceFactory = { testObjectStorage.selectKeysetPagingSource(pageSize = 10) }
        ).flow.shareIn(scope = backgroundScope, replay = 1, started = SharingStarted.Eagerly)

        backgroundScope.launch {
            pagerFlow.collect { results.add(it) }
        }
        val initialSet = pagerFlow.asSnapshot { this.refresh() }
        assertEquals(10, initialSet.size, "Should have 10 items")

        until { results.size >= 2 }
        assertEquals(2, results.size, "Should have 2 results")

        // Insert new value to invalidate the query
        TestObject().also { testObjectStorage.insert(it.id, it) }

        // Should refresh with new boundaries
        until { results.size >= 3 }
        assertEquals(3, results.size, "Should have 3 results after invalidation")
    }

    @Test
    fun keysetPaging_InsertedRowAppearsInCorrectPage() = runTest {
        // Insert 20 items with known sequential values for deterministic ordering
        val initial = (1..20).map { i -> TestObject(value = i * 10) }.associateBy { it.id }
        testObjectStorage.insertAll(initial)

        val orderBy = listOf(OrderBy(TestObject::value, OrderDirection.ASC))
        val config = PagingConfig(pageSize = 10, prefetchDistance = 0, initialLoadSize = 10)

        // Page through initial data — first page should be values 10..100
        val pager1 = TestPager(config, testObjectStorage.selectKeysetPagingSource(pageSize = 10, orderBy = orderBy))
        val page1 = pager1.refresh() as LoadResult.Page<String, TestObject>
        assertEquals(10, page1.data.size)
        assertEquals(
            (1..10).map { it * 10 }, page1.data.map { it.value },
            "Initial first page should have values 10-100"
        )

        // Insert a row with value 5 — should appear at the START of page 1 after invalidation
        val earlyItem = TestObject(value = 5)
        testObjectStorage.insert(earlyItem.id, earlyItem)

        // New PagingSource (simulates what Pager does on invalidation) — boundaries recomputed
        val pager2 = TestPager(config, testObjectStorage.selectKeysetPagingSource(pageSize = 10, orderBy = orderBy))
        val page2 = pager2.refresh() as LoadResult.Page<String, TestObject>
        assertEquals(10, page2.data.size)
        assertTrue(
            page2.data.any { it.id == earlyItem.id },
            "Inserted item with value=5 should appear in first page (sorted ASC)"
        )
        assertEquals(
            5, page2.data.first().value,
            "Inserted item should be first in the sorted page"
        )

        // Insert a row with value 150 — should appear in page 2, NOT page 1
        val lateItem = TestObject(value = 150)
        testObjectStorage.insert(lateItem.id, lateItem)

        // New PagingSource with fresh boundaries for 22 items
        val pager3 = TestPager(config, testObjectStorage.selectKeysetPagingSource(pageSize = 10, orderBy = orderBy))
        val firstPage = pager3.refresh() as LoadResult.Page<String, TestObject>
        assertTrue(
            firstPage.data.none { it.id == lateItem.id },
            "Late-inserted item (value=150) should NOT be in first page"
        )

        // Walk all pages and verify complete sorted set
        val allItems = with(pager3) {
            // Already refreshed above, append remaining pages
            val pages = mutableListOf(firstPage)
            while (pages.last().nextKey != null) {
                pages.add(append() as LoadResult.Page<String, TestObject>)
            }
            pages.flatMap { it.data }
        }
        // 20 original + 2 inserted = 22 total
        assertEquals(22, allItems.size, "Should have all 22 items across all pages")
        assertTrue(
            allItems.any { it.id == lateItem.id },
            "Late-inserted item should appear in the full paged set"
        )
        val allValues = allItems.map { it.value }
        assertEquals(allValues.sorted(), allValues, "All items should remain sorted across pages")
    }

    @Test
    fun keysetPaging_getRefreshKey_onFreshSource_returnsAnchorPageLoadKey() = runTest {
        // Regression: Paging3 calls getRefreshKey on a new PagingSource before
        // its first load(), so refresh key must derive from state. Asserts the
        // EXACT load key for the anchor page — a naive prevKey/nextKey would
        // return an adjacent key and silently shift position by a page.
        val items = (1..50).map { TestObject() }.associateBy { it.id }
        testObjectStorage.insertAll(items)

        val config = PagingConfig(pageSize = 10, prefetchDistance = 0, initialLoadSize = 10)
        val sourceA = testObjectStorage.selectKeysetPagingSource(pageSize = 10)
        val pagerA = TestPager(config, sourceA)
        with(pagerA) { refresh(); append(); append() }
        // Anchor at index 25 → 3rd loaded page (items 20-29).
        val state = pagerA.getPagingState(anchorPosition = 25)
        // The anchor page's load key equals the prior page's nextKey.
        val expectedKey = state.pages[1].nextKey
        assertNotNull(expectedKey, "Fixture invariant: page[1].nextKey is non-null")

        val sourceB = testObjectStorage.selectKeysetPagingSource(pageSize = 10)
        assertEquals(
            expectedKey, sourceB.getRefreshKey(state),
            "Refresh key must equal the anchor page's load key, not an adjacent one"
        )
    }

    @Test
    fun keysetPaging_getRefreshKey_anchorInFirstLoadedPage() = runTest {
        // When the anchor falls in the first loaded page, the anchor's load key
        // equals pages[1].prevKey — still recoverable from state without shift.
        val items = (1..50).map { TestObject() }.associateBy { it.id }
        testObjectStorage.insertAll(items)

        val config = PagingConfig(pageSize = 10, prefetchDistance = 0, initialLoadSize = 10)
        val sourceA = testObjectStorage.selectKeysetPagingSource(pageSize = 10)
        val pagerA = TestPager(config, sourceA)
        with(pagerA) { refresh(); append() }
        val state = pagerA.getPagingState(anchorPosition = 3)
        val expectedKey = state.pages[1].prevKey

        val sourceB = testObjectStorage.selectKeysetPagingSource(pageSize = 10)
        assertEquals(expectedKey, sourceB.getRefreshKey(state))
    }

    @Test
    fun keysetPaging_refresh_withStaleBoundaryKey_snapsToContainingPage() = runTest {
        // Simulates the RemoteMediator-writes-mid-load scenario:
        //   1. Old source had boundaries at "key-001", "key-011", "key-021".
        //   2. getRefreshKey on a fresh source yields "key-011" (anchor page's load key).
        //   3. Mediator-style insert adds keys "key-005-injected-1..5" — sorts lex
        //      between "key-005" and "key-006", shifting "key-011" from rn=11 to rn=16.
        //      New boundaries (pageSize=10 against 35 rows): "key-001", "key-006",
        //      "key-016", "key-026". "key-011" is no longer a boundary.
        //   4. load(params.key = "key-011") MUST snap to "key-006" (page containing it),
        //      NOT throw `Key key-011 not found in page boundaries`.
        val initial = (1..30).associate { i ->
            val id = "key-${i.toString().padStart(3, '0')}"
            id to TestObject(id = id, value = i)
        }
        testObjectStorage.insertAll(initial)

        val config = PagingConfig(pageSize = 10, prefetchDistance = 0, initialLoadSize = 10)
        val sourceA = testObjectStorage.selectKeysetPagingSource(pageSize = 10)
        val pagerA = TestPager(config, sourceA)
        with(pagerA) { refresh(); append() } // 2 pages loaded
        val state = pagerA.getPagingState(anchorPosition = 15)

        val injected = (1..5).associate { i ->
            val id = "key-005-injected-$i"
            id to TestObject(id = id, value = 100 + i)
        }
        testObjectStorage.insertAll(injected)

        val sourceB = testObjectStorage.selectKeysetPagingSource(pageSize = 10)
        val staleRefreshKey = sourceB.getRefreshKey(state)
        assertEquals("key-011", staleRefreshKey, "Fixture invariant: refresh key is the old page-2 load key")

        val result = sourceB.load(
            PagingSource.LoadParams.Refresh(
                key = staleRefreshKey,
                loadSize = 10,
                placeholdersEnabled = false,
            )
        )
        val page = result as? LoadResult.Page<String, TestObject>
            ?: error("Expected a Page (snap must not crash); got $result")
        assertEquals("key-006", page.data.first().id, "Snap returns the containing page's start")
        assertTrue(
            page.data.any { it.id == "key-011" },
            "Snapped page must contain the originally-requested stale boundary key"
        )
        assertEquals(10, page.data.size, "Snapped page is a full page")
    }

    @Test
    fun keysetPaging_mediatorWriteDuringLoad_preservesAnchorPage() = runTest {
        // End-to-end regression: user scrolls to the 3rd page, mediator-style write
        // shifts boundaries, fresh source resumes near the anchor (not page 0).
        val initial = (1..50).associate { i ->
            val id = "key-${i.toString().padStart(3, '0')}"
            id to TestObject(id = id, value = i)
        }
        testObjectStorage.insertAll(initial)

        val config = PagingConfig(pageSize = 10, prefetchDistance = 0, initialLoadSize = 10)
        val sourceA = testObjectStorage.selectKeysetPagingSource(pageSize = 10)
        val pagerA = TestPager(config, sourceA)
        with(pagerA) { refresh(); append(); append() } // 3 pages: key-001..key-030
        val state = pagerA.getPagingState(anchorPosition = 25)

        val injected = (1..5).associate { i ->
            val id = "key-005-injected-$i"
            id to TestObject(id = id, value = 100 + i)
        }
        testObjectStorage.insertAll(injected)

        val sourceB = testObjectStorage.selectKeysetPagingSource(pageSize = 10)
        val refreshKey = sourceB.getRefreshKey(state)
        assertNotNull(refreshKey, "getRefreshKey must produce a non-null key from non-empty state")
        // Anchor at position 25 maps to pagerA's 3rd page (load key "key-021"):
        assertEquals("key-021", refreshKey, "Fixture invariant: anchor maps to page-3 load key")

        val refreshLoad = sourceB.load(
            PagingSource.LoadParams.Refresh(
                key = refreshKey, loadSize = 10, placeholdersEnabled = false,
            )
        )
        val page = refreshLoad as? LoadResult.Page<String, TestObject>
            ?: error("Refresh must succeed; got $refreshLoad")

        // 55 items after injection. Boundaries at rn=1,11,21,31,41,51 = "key-001",
        // "key-006", "key-016", "key-026", "key-036", "key-046". "key-021" is now
        // at rn=26 -> snaps back to boundary at rn=21 = "key-016".
        assertEquals(
            "key-016", page.data.first().id,
            "Refreshed page starts at the snapped page-start, NOT reset to page 0 (key-001)"
        )
        assertTrue(
            page.data.any { it.id == "key-021" },
            "Refreshed page must contain the original anchor key"
        )
    }

    @Test
    fun keysetPageEmptyDataset() = runTest {
        val config = PagingConfig(pageSize = 10, prefetchDistance = 0, initialLoadSize = 10)
        val pager = TestPager(config, testObjectStorage.selectKeysetPagingSource(pageSize = 10))

        val result = pager.refresh() as LoadResult.Page<String, TestObject>
        assertTrue(result.data.isEmpty(), "Empty dataset should return empty page")
        assertNull(result.prevKey, "Prev key should be null for empty dataset")
        assertNull(result.nextKey, "Next key should be null for empty dataset")
    }

    @Test
    fun keysetPaging_emptyToPopulated_invalidates() = runTest {
        // Storage starts empty — simulates the Paging3 RemoteMediator initial-write
        // scenario where the local DB is empty until the network call completes.
        val config = PagingConfig(pageSize = 10, prefetchDistance = 0, initialLoadSize = 10)
        val results = mutableListOf<PagingData<TestObject>>()
        val pagerFlow = Pager(
            config,
            pagingSourceFactory = { testObjectStorage.selectKeysetPagingSource(pageSize = 10) }
        ).flow.shareIn(scope = backgroundScope, replay = 1, started = SharingStarted.Eagerly)

        backgroundScope.launch { pagerFlow.collect { results.add(it) } }

        // Initial refresh against an empty store yields an empty page.
        val initialSet = pagerFlow.asSnapshot { this.refresh() }
        assertEquals(0, initialSet.size, "Initial snapshot should be empty")

        until { results.size >= 2 }

        // Insert data after the empty load — the listener registered during the
        // empty load must fire and invalidate the PagingSource.
        val items = (1..15).map { TestObject() }.associateBy { it.id }
        testObjectStorage.insertAll(items)

        // Without the fix this until() times out (5s) because no listener was ever
        // attached to observe the table. With the fix, results.size advances.
        until { results.size >= 3 }
        val populated = pagerFlow.asSnapshot()
        assertEquals(10, populated.size, "Should load first page after invalidation")
    }

    @Test
    fun keysetPageWithWhere() = runTest {
        val matching = (1..50).map { TestObject(value = 1) }.associateBy { it.id }
        val nonMatching = (1..50).map { TestObject(value = 2) }.associateBy { it.id }
        testObjectStorage.insertAll(matching)
        testObjectStorage.insertAll(nonMatching)

        val config = PagingConfig(pageSize = 10, prefetchDistance = 0, initialLoadSize = 10)
        val pager = TestPager(
            config,
            testObjectStorage.selectKeysetPagingSource(
                pageSize = 10,
                where = TestObject::value eq 1
            )
        )

        val result = pager.refresh() as LoadResult.Page<String, TestObject>
        assertEquals(10, result.data.size)
        assertTrue(
            result.data.all { it.value == 1 },
            "All results should match the where clause"
        )
    }

    @Test
    fun keysetPaging_reportsAbsolutePositions_forPlaceholderSupport() = runTest {
        // Regression for scroll-jump-on-append: keyset pages must report their
        // absolute position (itemsBefore/itemsAfter) so a Pager with
        // enablePlaceholders keeps ABSOLUTE list indices. Without it every page
        // reports COUNT_UNDEFINED, Paging3 force-disables placeholders, LazyColumn
        // indices become window-relative, and a RemoteMediator-write invalidation
        // re-anchors the loaded window and jumps the user's scroll position.
        val expected = (1..30).map { TestObject() }.associateBy { it.id }
        testObjectStorage.insertAll(expected)

        val config = PagingConfig(pageSize = 10, prefetchDistance = 0, initialLoadSize = 10)
        val pager = TestPager(config, testObjectStorage.selectKeysetPagingSource(pageSize = 10))

        val page1 = pager.refresh() as LoadResult.Page<String, TestObject>
        assertEquals(0, page1.itemsBefore, "First page starts at absolute index 0")
        assertEquals(20, page1.itemsAfter, "20 items remain after the first page of 30")

        val page2 = pager.append() as LoadResult.Page<String, TestObject>
        assertEquals(10, page2.itemsBefore, "Second page starts after the first 10 items")
        assertEquals(10, page2.itemsAfter, "10 items remain after the second page")

        val page3 = pager.append() as LoadResult.Page<String, TestObject>
        assertEquals(20, page3.itemsBefore, "Third page starts after the first 20 items")
        assertEquals(0, page3.itemsAfter, "No items remain after the final page")
    }

    @Test
    fun keysetPaging_mediatorWrite_refreshedPageKnowsAbsolutePosition() = runTest {
        // The scroll-jump root cause, end to end: user scrolls to page 3, a
        // mediator-style write invalidates + shifts boundaries, and the fresh
        // source's refresh must report the snapped page's ABSOLUTE offset so
        // Paging3 can keep the anchor at its true index instead of jumping.
        // Same fixture as keysetPaging_mediatorWriteDuringLoad_preservesAnchorPage:
        // 50 rows + 5 injected = 55; anchor 25 snaps to the boundary at rn=21
        // ("key-016") = the 3rd page = absolute offset 20.
        val initial = (1..50).associate { i ->
            val id = "key-${i.toString().padStart(3, '0')}"
            id to TestObject(id = id, value = i)
        }
        testObjectStorage.insertAll(initial)

        val config = PagingConfig(pageSize = 10, prefetchDistance = 0, initialLoadSize = 10)
        val sourceA = testObjectStorage.selectKeysetPagingSource(pageSize = 10)
        val pagerA = TestPager(config, sourceA)
        with(pagerA) { refresh(); append(); append() }
        val state = pagerA.getPagingState(anchorPosition = 25)

        val injected = (1..5).associate { i ->
            val id = "key-005-injected-$i"
            id to TestObject(id = id, value = 100 + i)
        }
        testObjectStorage.insertAll(injected)

        val sourceB = testObjectStorage.selectKeysetPagingSource(pageSize = 10)
        val refreshKey = sourceB.getRefreshKey(state)
        val page = sourceB.load(
            PagingSource.LoadParams.Refresh(
                key = refreshKey, loadSize = 10, placeholdersEnabled = true,
            )
        ) as LoadResult.Page<String, TestObject>

        assertEquals("key-016", page.data.first().id, "Fixture invariant: snaps to page-3 boundary")
        assertEquals(20, page.itemsBefore, "Refreshed page must report its absolute offset (20)")
        assertEquals(
            55, page.itemsBefore + page.data.size + page.itemsAfter,
            "itemsBefore + loaded + itemsAfter must equal the full dataset count",
        )
    }
}
