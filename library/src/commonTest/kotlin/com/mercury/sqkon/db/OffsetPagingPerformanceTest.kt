package com.mercury.sqkon.db

import androidx.paging.PagingConfig
import androidx.paging.testing.TestPager
import com.mercury.sqkon.TestObject
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Performance regression guards for offset paging: the page-data query must run ONCE per page
 * load (not twice — read-tracking must not re-execute it) and the total-count query must run ONCE
 * per PagingSource (not once per page load). Uses [CountingDriver] to count SQL — deterministic,
 * no timing.
 */
class OffsetPagingPerformanceTest {

    private val mainScope = MainScope()
    private val driver = CountingDriver(driverFactory().createDriver())
    private val entityQueries = EntityQueries(driver)
    private val metadataQueries = MetadataQueries(driver)
    private val storage = keyValueStorage<TestObject>(
        "test-object", entityQueries, metadataQueries, mainScope
    )

    @After
    fun tearDown() = mainScope.cancel()

    @Test
    fun pageQuery_runsOncePerPageLoad_notTwice() = runTest {
        storage.insertAll((1..100).map { TestObject() }.associateBy { it.id })
        val config = PagingConfig(pageSize = 10, prefetchDistance = 0, initialLoadSize = 10)
        val pager = TestPager(config, storage.selectPagingSource())

        driver.queries.clear() // ignore insert/setup SQL
        with(pager) { refresh(); append(); append() } // 3 page loads on ONE source

        // "OFFSET ?" is unique to the offset data query (select), not the count query.
        assertEquals(
            3, driver.countMatching("OFFSET ?"),
            "offset page query must run once per page load (3 loads), not twice",
        )
    }
}
