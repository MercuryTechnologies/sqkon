package com.mercury.sqkon.db

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
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
        val pager = TestPager(config, testObjectStorage.selectKeysetPagingSource())

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
        val pager = TestPager(config, testObjectStorage.selectKeysetPagingSource())

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
        val pager = TestPager(config, testObjectStorage.selectKeysetPagingSource())

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
        val pager = TestPager(config, testObjectStorage.selectKeysetPagingSource())

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
            pagingSourceFactory = { testObjectStorage.selectKeysetPagingSource() }
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
        val pager1 = TestPager(config, testObjectStorage.selectKeysetPagingSource(orderBy = orderBy))
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
        val pager2 = TestPager(config, testObjectStorage.selectKeysetPagingSource(orderBy = orderBy))
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
        val pager3 = TestPager(config, testObjectStorage.selectKeysetPagingSource(orderBy = orderBy))
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
    fun keysetPageEmptyDataset() = runTest {
        val config = PagingConfig(pageSize = 10, prefetchDistance = 0, initialLoadSize = 10)
        val pager = TestPager(config, testObjectStorage.selectKeysetPagingSource())

        val result = pager.refresh() as LoadResult.Page<String, TestObject>
        assertTrue(result.data.isEmpty(), "Empty dataset should return empty page")
        assertNull(result.prevKey, "Prev key should be null for empty dataset")
        assertNull(result.nextKey, "Next key should be null for empty dataset")
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
}
