package com.mercury.sqkon.db

import com.mercury.sqkon.TestObject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Count + delete suite: `COUNT(DISTINCT)` with and without a WHERE join, and the delete paths
 * (by-where, by-key batch, truncate-all). Count cases are read-only (seed once); delete cases mutate,
 * so `reset` re-seeds untimed before each iteration. See [Benchmark] for how the suite runs.
 */
class CountDeleteBenchmark : BenchmarkSuite("count-delete") {

    @Test
    fun benchmark() = runTest {
        assumeBenchmarkEnabled()
        val rows = benchmarkRows()
        val seed = seedObjects(rows)
        val hotKeys = seed.keys.take(100).toTypedArray()

        // Count cases: read-only, seed once.
        storage.insertAll(seed)
        // Count cases report counts/sec (opsPerIter=1): one COUNT(DISTINCT) call over the full table.
        runner.bench("count_all", opsPerIter = 1) {
            storage.count().first()
        }
        runner.bench("count_where", opsPerIter = 1) {
            storage.count(where = TestObject::value gt (rows / 2)).first()
        }

        // Delete cases report rows/sec (opsPerIter = rows actually removed), so the three are
        // comparable. Mutating, so re-seed untimed before each iteration.
        val reseed: suspend () -> Unit = { storage.deleteAll(); storage.insertAll(seed) }
        runner.bench("delete_where", opsPerIter = rows / 2, reset = reseed) {
            // name eq "even" matches the even-indexed half of the seed
            storage.delete(where = TestObject::name eq "even")
        }
        runner.bench("deleteByKeys_batch", opsPerIter = hotKeys.size, reset = reseed) {
            storage.deleteByKeys(*hotKeys)
        }
        runner.bench("deleteAll", opsPerIter = rows, reset = reseed) {
            storage.deleteAll()
        }
        runner.report(mapOf("rows" to rows))
    }
}
