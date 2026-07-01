package com.mercury.sqkon.db

import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingSource.LoadResult
import androidx.paging.testing.TestPager
import com.mercury.sqkon.TestObject
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Paging suite: pages fully through the dataset with each PagingSource. Exercises the O(n)
 * `ROW_NUMBER()` window keyset recomputes per page vs. SQL `OFFSET`'s per-page scan — the headline
 * paging cost this whole benchmark effort exists to track. See [Benchmark] for how the suite runs.
 */
class PagingBenchmark : BenchmarkSuite("paging") {

    @Test
    fun benchmark() = runTest {
        assumeBenchmarkEnabled()
        val rows = benchmarkRows()
        val pageSize = 50
        val pages = (rows + pageSize - 1) / pageSize // ceil(rows / pageSize): page-loads per drain
        storage.insertAll(seedObjects(rows))

        runner.bench("keyset_page_through", opsPerIter = pages) {
            pageThrough(pageSize) { storage.selectKeysetPagingSource(pageSize = pageSize) }
        }
        runner.bench("offset_page_through", opsPerIter = pages) {
            pageThrough(pageSize) { storage.selectPagingSource() }
        }
        runner.report(mapOf("rows" to rows, "pageSize" to pageSize))
    }

    // Pages from the first page to the end on a single source produced by [source]. The loop stops
    // on the first non-Page / empty append result, so it is robust whether TestPager.append()
    // signals end-of-list by returning null or by returning an empty final Page.
    private suspend fun <K : Any> pageThrough(pageSize: Int, source: () -> PagingSource<K, TestObject>) {
        val config = PagingConfig(pageSize = pageSize, prefetchDistance = 0, initialLoadSize = pageSize)
        val pager = TestPager(config, source())
        pager.refresh()
        var next = pager.append()
        while (next is LoadResult.Page && next.data.isNotEmpty()) {
            next = pager.append()
        }
    }
}
