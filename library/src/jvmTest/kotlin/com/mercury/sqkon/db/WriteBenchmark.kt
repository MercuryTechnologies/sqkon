package com.mercury.sqkon.db

import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Write suite: bulk vs. single-row insert throughput, plus the update and upsert merge paths.
 * `reset` re-establishes the starting state before each iteration, untimed, so only the write path
 * under test is measured. ops/sec reads as rows/sec. See [Benchmark] for how the suite runs.
 */
class WriteBenchmark : BenchmarkSuite("write") {

    @Test
    fun benchmark() = runTest {
        assumeBenchmarkEnabled()
        val rows = benchmarkRows()
        val seed = seedObjects(rows)

        runner.bench("insertAll_bulk", opsPerIter = rows, reset = { storage.deleteAll() }) {
            storage.insertAll(seed)
        }
        runner.bench("insert_loop_single", opsPerIter = rows, reset = { storage.deleteAll() }) {
            seed.forEach { (key, value) -> storage.insert(key, value) }
        }
        runner.bench(
            "updateAll_bulk", opsPerIter = rows,
            reset = { storage.deleteAll(); storage.insertAll(seed) },
        ) {
            storage.updateAll(seed)
        }
        runner.bench(
            "upsertAll_bulk", opsPerIter = rows,
            reset = { storage.deleteAll(); storage.insertAll(seed) },
        ) {
            storage.upsertAll(seed)
        }
        runner.report(mapOf("rows" to rows))
    }
}
