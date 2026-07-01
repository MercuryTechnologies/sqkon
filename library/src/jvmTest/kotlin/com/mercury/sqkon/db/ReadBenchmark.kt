package com.mercury.sqkon.db

import com.mercury.sqkon.TestObject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Read suite: full-table hydrate, indexed key lookups, and json-query where-scans at varying
 * selectivity and path shape. Note: every top-level `select(where = …)` predicate lowers to the same
 * engine strategy — a `json_tree` tree-walk of each row plus a `fullkey LIKE` (the scalar
 * `json_extract` form is only used inside CASE expressions, never from `select`). So the where cases
 * below vary the matched path and match count, not the query strategy (issue #68). Dataset is seeded
 * once; all cases are read-only so no reset is needed. Reads are `Flow`-based, collected with
 * `.first()`. See [Benchmark] for how the suite runs.
 */
class ReadBenchmark : BenchmarkSuite("read") {

    @Test
    fun benchmark() = runTest {
        assumeBenchmarkEnabled()
        val rows = benchmarkRows()
        val seed = seedObjects(rows)
        storage.insertAll(seed)
        val hotKeys = seed.keys.take(100).toList()

        runner.bench("selectAll", opsPerIter = rows) {
            storage.selectAll().first()
        }
        runner.bench("selectByKey_hot", opsPerIter = hotKeys.size) {
            hotKeys.forEach { key -> storage.selectByKey(key).first() }
        }
        runner.bench("selectByKeys_batch", opsPerIter = hotKeys.size) {
            storage.selectByKeys(hotKeys).first()
        }
        runner.bench("where_int_eq_1match", opsPerIter = rows) {
            // top-level Int field, matches ~1 row — highly selective json_tree where-scan
            storage.select(where = TestObject::value eq (rows / 2)).first()
        }
        runner.bench("where_string_eq_halfmatch", opsPerIter = rows) {
            // top-level String field, matches ~half the rows
            storage.select(where = TestObject::name eq "even").first()
        }
        runner.bench("where_list_contains_allmatch", opsPerIter = rows) {
            // attributes is a List<String> (defaults to "1".."10"); a collection-element where that
            // matches every row — the worst-case json_tree fan-out shape (issue #68).
            storage.select(where = TestObject::attributes eq "5").first()
        }
        runner.bench("select_ordered_scan", opsPerIter = rows) {
            storage.select(orderBy = listOf(OrderBy(TestObject::value, OrderDirection.DESC))).first()
        }
        runner.report(mapOf("rows" to rows))
    }
}
