package com.mercury.sqkon.db

import androidx.paging.PagingConfig
import androidx.paging.PagingSource.LoadResult
import androidx.paging.testing.TestPager
import com.mercury.sqkon.TestObject
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import kotlin.time.measureTime

/**
 * Informational paging timing benchmark for trend visibility — NOT a correctness test and NOT a
 * hard regression gate (the deterministic query-count guards in KeysetPagingPerformanceTest /
 * OffsetPagingPerformanceTest are the build-failing guardrails).
 *
 * Skipped by default so it never slows the blocking `jvmTest` gate. Run it explicitly:
 *   ./gradlew :library:jvmTest --tests "*PagingBenchmark" -Dsqkon.benchmark=true
 * Optionally set the dataset size with -Dsqkon.benchmark.rows=5000 (default 2000).
 * Results are written to library/build/benchmark-results/paging-benchmark.txt and echoed to stdout.
 *
 * Deliberately dependency-free (kotlin.time + nanoTime, no JMH). kotlinx-benchmark's Gradle plugin
 * bundles a Kotlin compiler plugin and does not state Kotlin 2.3 support (its latest release
 * targets Kotlin 2.2.0), so it is a build-breakage risk on this repo's Kotlin 2.3.21. Once
 * kotlinx-benchmark supports Kotlin 2.3, this can be promoted to a real JMH benchmark.
 */
class PagingBenchmark {

    private val mainScope = MainScope()
    private val driver = driverFactory().createDriver()
    private val entityQueries = EntityQueries(driver)
    private val metadataQueries = MetadataQueries(driver)
    private val storage = keyValueStorage<TestObject>(
        "test-object", entityQueries, metadataQueries, mainScope
    )

    @After
    fun tearDown() = mainScope.cancel()

    @Test
    fun pagingThroughput() = runTest {
        assumeTrue(
            "set -Dsqkon.benchmark=true to run the paging benchmark",
            System.getProperty("sqkon.benchmark") == "true",
        )
        val rows = System.getProperty("sqkon.benchmark.rows")?.toIntOrNull() ?: 2000
        val pageSize = 50
        storage.insertAll((1..rows).map { TestObject() }.associateBy { it.id })

        // Warmup (let the JIT settle) — page fully through once per source type; timing discarded.
        pageThroughKeyset(pageSize)
        pageThroughOffset(pageSize)

        val runs = 3
        val keysetTime = measureTime { repeat(runs) { pageThroughKeyset(pageSize) } }
        val offsetTime = measureTime { repeat(runs) { pageThroughOffset(pageSize) } }

        val report = buildString {
            appendLine("sqkon paging benchmark")
            appendLine("rows=$rows pageSize=$pageSize runs=$runs")
            appendLine("keyset_total_ms=${keysetTime.inWholeMilliseconds}")
            appendLine("offset_total_ms=${offsetTime.inWholeMilliseconds}")
        }
        println(report)
        File("build/benchmark-results").apply { mkdirs() }
            .resolve("paging-benchmark.txt").writeText(report)
    }

    // Pages from the first page to the end on a single source. The loop stops on the first
    // non-Page / empty append result, so it is robust whether TestPager.append() signals
    // end-of-list by returning null or by returning an empty final Page.
    private suspend fun pageThroughKeyset(pageSize: Int) {
        val config = PagingConfig(pageSize = pageSize, prefetchDistance = 0, initialLoadSize = pageSize)
        val pager = TestPager(config, storage.selectKeysetPagingSource(pageSize = pageSize))
        pager.refresh()
        var next = pager.append()
        while (next is LoadResult.Page && next.data.isNotEmpty()) {
            next = pager.append()
        }
    }

    private suspend fun pageThroughOffset(pageSize: Int) {
        val config = PagingConfig(pageSize = pageSize, prefetchDistance = 0, initialLoadSize = pageSize)
        val pager = TestPager(config, storage.selectPagingSource())
        pager.refresh()
        var next = pager.append()
        while (next is LoadResult.Page && next.data.isNotEmpty()) {
            next = pager.append()
        }
    }
}
