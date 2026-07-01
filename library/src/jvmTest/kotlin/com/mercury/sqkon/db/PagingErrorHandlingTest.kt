package com.mercury.sqkon.db

import androidx.paging.PagingConfig
import androidx.paging.PagingSource.LoadResult
import androidx.paging.testing.TestPager
import com.mercury.sqkon.TestObject
import com.mercury.sqkon.db.paging.KeysetQueryPagingSource
import com.mercury.sqkon.db.paging.OffsetQueryPagingSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import kotlin.test.assertTrue

/**
 * Error-handling contract for the paging sources (#81):
 *  - a query/deserialization failure must surface as `LoadResult.Error`, not propagate out of
 *    `load()` (which can crash the collecting coroutine) — the offset source had no try/catch;
 *  - `CancellationException` must be rethrown, never converted into `LoadResult.Error` — the keyset
 *    source caught it via a blanket `catch (Exception)`.
 */
class PagingErrorHandlingTest {

    private val mainScope = MainScope()
    private val driver = driverFactory().createDriver()
    private val entityQueries = EntityQueries(driver)
    private val metadataQueries = MetadataQueries(driver)
    private val storage = keyValueStorage<TestObject>(
        "test-object", entityQueries, metadataQueries, mainScope,
    )

    @After
    fun tearDown() {
        mainScope.cancel()
    }

    @Test
    fun offsetPagingSource_deserializeFailure_returnsLoadResultError_doesNotThrow() = runTest {
        storage.insertAll((1..5).map { TestObject() }.associateBy { it.id })

        val source = OffsetQueryPagingSource<TestObject>(
            queryProvider = { limit, offset ->
                entityQueries.select("test-object", limit = limit.toLong(), offset = offset.toLong())
            },
            countQuery = entityQueries.count("test-object"),
            transacter = entityQueries,
            context = Dispatchers.IO,
            deserialize = { throw RuntimeException("boom") },
            initialOffset = 0,
        )

        // Before the fix this throws out of load(); now it must come back as Error.
        val result = TestPager(PagingConfig(pageSize = 10), source).refresh()
        assertTrue(result is LoadResult.Error, "expected LoadResult.Error, got $result")
    }

    @Test
    fun keysetPagingSource_cancellation_isRethrown_notReportedAsError() = runTest {
        storage.insertAll((1..5).map { TestObject() }.associateBy { it.id })

        val source = KeysetQueryPagingSource<TestObject>(
            queryProvider = entityQueries.selectKeyed("test-object"),
            pageBoundariesProvider = entityQueries.selectPageBoundaries("test-object"),
            boundaryForKeyProvider = entityQueries.selectBoundaryForKey("test-object"),
            countQuery = entityQueries.count("test-object"),
            transacter = entityQueries,
            context = Dispatchers.IO,
            deserialize = { throw CancellationException("cancelled") },
            pageSize = 10,
        )

        // CancellationException must propagate (structured concurrency), not be swallowed into Error.
        val pager = TestPager(PagingConfig(pageSize = 10), source)
        val thrown: Throwable? = try {
            pager.refresh()
            null
        } catch (e: Throwable) {
            e
        }
        assertTrue(
            thrown is CancellationException,
            "expected CancellationException to propagate, got $thrown",
        )
    }
}
