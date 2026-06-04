package com.mercury.sqkon.db

import com.mercury.sqkon.TestObject
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Regression test for the count over-count bug (#68).
 *
 * `CountQuery` emitted `SELECT COUNT(*)` while `SelectQuery` uses `SELECT DISTINCT`. When the
 * WHERE clause contains a `json_tree`-joined predicate that matches multiple nodes per entity
 * (list paths / `inList` / nested `.then`), the join yields one row per matching node, so
 * `COUNT(*)` counted each match. `count(where=p)` then disagreed with `select(where=p).size`.
 */
class KeyValueStorageCountDistinctTest {

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
    fun count_withListPredicateMatchingMultipleElements_matchesSelectSize() = runTest {
        // A single entity whose list field has three elements, all matching the predicate.
        // The json_tree join therefore produces three rows for this one entity.
        val obj = TestObject(attributes = listOf("a", "b", "c"))
        storage.insert(obj.id, obj)

        val predicate = TestObject::attributes inList listOf("a", "b", "c")

        val selectedSize = storage.select(where = predicate).first().size
        val counted = storage.count(where = predicate).first()

        // select() de-duplicates via DISTINCT — one matching entity.
        assertEquals(1, selectedSize)
        // count() must agree with select().size for the same predicate.
        assertEquals(selectedSize, counted)
    }
}
