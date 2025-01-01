package com.mercury.sqkon.db

import androidx.paging.PagingSource.LoadResult
import app.cash.paging.Pager
import app.cash.paging.PagingConfig
import app.cash.paging.PagingData
import app.cash.paging.testing.TestPager
import app.cash.paging.testing.asSnapshot
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
import kotlin.test.assertNull

class OffsetPagingTest {

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
    fun offsetPageByTen() = runTest {
        val expected = (1..100).map { _ -> TestObject() }.associateBy { it.id }
        testObjectStorage.insertAll(expected)

        val config = PagingConfig(pageSize = 10, prefetchDistance = 0, initialLoadSize = 10)
        val pager = TestPager(config, testObjectStorage.selectPagingSource())

        val result = pager.refresh() as LoadResult.Page<Int, TestObject>
        assertEquals(10, result.data.size, "Page result should contain 10 items")
        assertNull(result.prevKey, "Prev key should be null")
        assertEquals(10, result.nextKey, "Next key should be offset 10")
        assertEquals(0, result.itemsBefore, "Items before should be 0")
        assertEquals(90, result.itemsAfter, "Items after should be 90")

        assertEquals(1, pager.getPages().size, "Should have 1 page")
    }

    @Test
    fun offsetPageByTenAppending() = runTest {
        val expected = (1..100).map { _ -> TestObject() }.associateBy { it.id }
        testObjectStorage.insertAll(expected)

        val config = PagingConfig(pageSize = 10, prefetchDistance = 0, initialLoadSize = 10)
        val pager = TestPager(config, testObjectStorage.selectPagingSource())

        val result = with(pager) {
            refresh()
            append()
            append()
        } as LoadResult.Page<Int, TestObject>
        assertEquals(10, result.data.size, "Page result should contain 10 items")
        assertEquals(20, result.prevKey, "Prev key should be 20")
        assertEquals(30, result.nextKey, "Next key should be offset 30")
        assertEquals(20, result.itemsBefore, "Items before should be 20")
        assertEquals(70, result.itemsAfter, "Items after should be 70")

        assertEquals(3, pager.getPages().size, "Should have 3 pages")
    }

    @Test
    fun offsetPageByTen_Invalidation() = runTest {
        val expected = (1..100).map { _ -> TestObject() }.associateBy { it.id }
        testObjectStorage.insertAll(expected)

        val config = PagingConfig(pageSize = 10, prefetchDistance = 0, initialLoadSize = 10)
        val results = mutableListOf<PagingData<TestObject>>()
        val pagerFlow = Pager(
            config, pagingSourceFactory = { testObjectStorage.selectPagingSource() }
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

        // Should refresh
        until { results.size >= 3 }
        assertEquals(3, results.size, "Should have 3 results after invalidation")
    }

}
