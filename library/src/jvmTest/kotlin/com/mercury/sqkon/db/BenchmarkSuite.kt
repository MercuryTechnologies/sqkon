package com.mercury.sqkon.db

import com.mercury.sqkon.TestObject
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import org.junit.After

/**
 * Base class for the benchmark suite. Owns the in-memory fixtures every case needs — a [MainScope],
 * an in-memory driver, [EntityQueries]/[MetadataQueries], a [KeyValueStorage] of [TestObject], and a
 * [BenchmarkRunner] — plus the `@After` teardown and a deterministic [seedObjects] helper, so each
 * concrete benchmark class is just its cases.
 *
 * The opt-in gate ([assumeBenchmarkEnabled]) is called at the top of each concrete `@Test`, not in
 * `@Before`, so it composes inside `runTest {}` and the classes still compile/skip cleanly under the
 * normal `jvmTest` gate.
 */
abstract class BenchmarkSuite(suiteName: String) {
    private val mainScope = MainScope()
    private val driver = driverFactory().createDriver()
    private val entityQueries = EntityQueries(driver)
    private val metadataQueries = MetadataQueries(driver)

    protected val storage: KeyValueStorage<TestObject> =
        keyValueStorage<TestObject>(suiteName, entityQueries, metadataQueries, mainScope)
    protected val runner = BenchmarkRunner(suiteName)

    @After
    fun tearDown() = mainScope.cancel()

    /**
     * Deterministic, zero-padded, sortable keys (`key-000001`..) with `value = i` and an even/odd
     * `name`, so where/orderBy cases have predictable selectivity across runs. Returns the map ready
     * for `insertAll`.
     */
    protected fun seedObjects(n: Int): Map<String, TestObject> =
        (1..n).associate { i ->
            val id = "key-" + i.toString().padStart(6, '0')
            id to TestObject(id = id, value = i, name = if (i % 2 == 0) "even" else "odd")
        }
}
