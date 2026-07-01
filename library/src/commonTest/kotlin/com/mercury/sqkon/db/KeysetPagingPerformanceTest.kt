package com.mercury.sqkon.db

import androidx.paging.PagingConfig
import androidx.paging.PagingSource.LoadResult
import androidx.paging.testing.TestPager
import com.mercury.sqkon.TestObject
import com.mercury.sqkon.db.internal.SqkonCursor
import com.mercury.sqkon.db.internal.SqkonDriver
import com.mercury.sqkon.db.internal.SqkonStatement
import com.mercury.sqkon.db.internal.SqkonTransaction
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Performance regression guards for keyset paging: the per-source queries (total count and page
 * boundaries) must be computed ONCE per PagingSource, not re-run on every page load. Uses a
 * [CountingDriver] to count actual SQL executions — deterministic, no timing.
 */
class KeysetPagingPerformanceTest {

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
    fun countQuery_runsOncePerSource_notPerPageLoad() = runTest {
        storage.insertAll((1..100).map { TestObject() }.associateBy { it.id })
        val config = PagingConfig(pageSize = 10, prefetchDistance = 0, initialLoadSize = 10, enablePlaceholders = true)
        val pager = TestPager(config, storage.selectKeysetPagingSource(pageSize = 10))

        driver.queries.clear() // ignore insert/setup SQL
        with(pager) { refresh(); append(); append() } // 3 page loads on ONE source

        assertEquals(
            1, driver.countMatching("COUNT(DISTINCT"),
            "total-count query must run once per source, not once per page load",
        )
    }

    @Test
    fun countQuery_skipped_whenPlaceholdersDisabled() = runTest {
        storage.insertAll((1..100).map { TestObject() }.associateBy { it.id })
        // enablePlaceholders=false requires prefetchDistance>0 (PagingConfig invariant).
        val config = PagingConfig(pageSize = 10, prefetchDistance = 1, initialLoadSize = 10, enablePlaceholders = false)
        val pager = TestPager(config, storage.selectKeysetPagingSource(pageSize = 10))

        driver.queries.clear()
        with(pager) { refresh(); append(); append() }

        assertEquals(
            0, driver.countMatching("COUNT(DISTINCT"),
            "with placeholders disabled Paging ignores item counts — the COUNT query must not run",
        )
    }

    @Test
    fun pageBoundaries_computedOncePerSource_notPerPageLoad() = runTest {
        storage.insertAll((1..100).map { TestObject() }.associateBy { it.id })
        val config = PagingConfig(pageSize = 10, prefetchDistance = 0, initialLoadSize = 10)
        val pager = TestPager(config, storage.selectKeysetPagingSource(pageSize = 10))

        driver.queries.clear()
        val last = with(pager) { refresh(); append(); append() } as LoadResult.Page

        assertEquals(3, pager.getPages().size, "fixture: three pages loaded on one source")
        // selectPageBoundaries is the only paged query using a modulo predicate ("(rn - 1) % ?").
        assertEquals(
            1, driver.countMatching("% ?"),
            "page-boundaries query must be computed once per source, not once per page load",
        )
        assertEquals(10, last.data.size, "fixture: full final page")
    }
}

/** Delegating [SqkonDriver] that records the SQL of every executeQuery for assertions. */
private class CountingDriver(private val delegate: SqkonDriver) : SqkonDriver {
    val queries = mutableListOf<String>()

    override fun <R> executeQuery(
        identifier: Int?,
        sql: String,
        parameters: Int,
        binders: (SqkonStatement.() -> Unit)?,
        mapper: (SqkonCursor) -> R,
    ): R {
        queries += sql
        return delegate.executeQuery(identifier, sql, parameters, binders, mapper)
    }

    override fun executeUpdate(
        identifier: Int?,
        sql: String,
        parameters: Int,
        binders: (SqkonStatement.() -> Unit)?,
    ): Long = delegate.executeUpdate(identifier, sql, parameters, binders)

    override fun addListener(vararg queryKeys: String, listener: SqkonDriver.Listener) =
        delegate.addListener(queryKeys = queryKeys, listener = listener)

    override fun removeListener(vararg queryKeys: String, listener: SqkonDriver.Listener) =
        delegate.removeListener(queryKeys = queryKeys, listener = listener)

    override fun notifyListeners(vararg queryKeys: String) =
        delegate.notifyListeners(queryKeys = queryKeys)

    override fun newTransaction(): SqkonTransaction = delegate.newTransaction()
    override fun currentTransaction(): SqkonTransaction? = delegate.currentTransaction()
    override fun close() = delegate.close()

    fun countMatching(fragment: String): Int = queries.count { it.contains(fragment) }
}
