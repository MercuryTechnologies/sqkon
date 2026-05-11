package com.mercury.sqkon.db

import com.mercury.sqkon.TestObject
import com.mercury.sqkon.TestObjectChild
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KeyValueStorageNotPredicateTest {

    private val mainScope = MainScope()
    private val driver = driverFactory().createDriver()
    private val entityQueries = EntityQueries(driver)
    private val metadataQueries = MetadataQueries(driver)
    private val store = keyValueStorage<TestObject>("not-pred", entityQueries, metadataQueries, mainScope)

    @After fun tearDown() { mainScope.cancel() }

    private suspend fun seed(): List<TestObject> {
        val items = (1..5).map { i -> TestObject(id = "id$i", name = "Name$i", value = i * 10) }
        store.insertAll(items.associateBy { it.id })
        return items
    }

    // Regression: Not.toSqlQuery() previously wrapped the json_tree-joined WHERE with
    // NOT (...). A single entity produces many json_tree rows (one per JSON field), so
    // the NOT was satisfied by any non-target row and every entity matched.
    @Test
    fun not_invertsPredicate() = runTest {
        seed()
        val result = store.select(where = not(TestObject::value eq 30)).first()
        assertEquals(4, result.size)
        assertTrue(result.none { it.value == 30 })
    }

    @Test
    fun not_eqNull_isEquivalentToIsNotNull() = runTest {
        seed()
        // every TestObject has a non-null `name`, so NOT(name == null) matches all 5
        val result = store.select(where = not(TestObject::name eq null)).first()
        assertEquals(5, result.size)
    }

    // List-path regression: `attributes` builds `$.attributes[%]`, which is a
    // `json_tree.fullkey LIKE` pattern (not a valid `json_extract` path). The pure
    // scalar lowering of NOT would silently produce wrong results for this case;
    // the correlated `NOT EXISTS` lowering keeps it correct.
    @Test
    fun not_overListPath_excludesMatchingEntities() = runTest {
        val items = listOf(
            TestObject(id = "a", attributes = listOf("11", "12")),
            TestObject(id = "b", attributes = listOf("21", "22")),
            TestObject(id = "c", attributes = listOf("31", "32")),
        )
        store.insertAll(items.associateBy { it.id })

        val result = store.select(where = not(TestObject::attributes eq "22")).first()
        assertEquals(2, result.size)
        assertTrue(result.none { it.attributes?.contains("22") == true })
        assertEquals(setOf("a", "c"), result.map { it.id }.toSet())
    }

    @Test
    fun not_overListPath_inList_excludesMatchingEntities() = runTest {
        val items = listOf(
            TestObject(id = "a", attributes = listOf("11", "12")),
            TestObject(id = "b", attributes = listOf("21", "22")),
            TestObject(id = "c", attributes = listOf("31", "32")),
            TestObject(id = "d", attributes = listOf("41", "42")),
        )
        store.insertAll(items.associateBy { it.id })

        val result = store.select(
            where = not(TestObject::attributes inList listOf("22", "32")),
        ).first()
        assertEquals(setOf("a", "d"), result.map { it.id }.toSet())
    }

    // Nested list-path via `.then(...)` — path becomes `$.list[%].createdAt`.
    // Ensures NOT EXISTS correctly inverts predicates that drill into list element fields.
    @Test
    fun not_overNestedListPath_excludesMatchingEntities() = runTest {
        val target = kotlin.time.Instant.fromEpochSeconds(1_700_000_000)
        val other = kotlin.time.Instant.fromEpochSeconds(1_800_000_000)
        val matching = TestObject(
            id = "match",
            list = listOf(TestObjectChild(createdAt = target), TestObjectChild(createdAt = other)),
        )
        val unrelated = TestObject(
            id = "other",
            list = listOf(TestObjectChild(createdAt = other), TestObjectChild(createdAt = other)),
        )
        store.insertAll(mapOf(matching.id to matching, unrelated.id to unrelated))

        val result = store.select(
            where = not(
                TestObject::list.then(TestObjectChild::createdAt) eq target.toString(),
            ),
        ).first()
        assertEquals(listOf("other"), result.map { it.id })
    }
}
