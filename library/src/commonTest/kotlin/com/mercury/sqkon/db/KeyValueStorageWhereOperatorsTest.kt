package com.mercury.sqkon.db

import com.mercury.sqkon.NullableTestObject
import com.mercury.sqkon.TestObject
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KeyValueStorageWhereOperatorsTest {

    private val mainScope = MainScope()
    private val driver = driverFactory().createDriver()
    private val entityQueries = EntityQueries(driver)
    private val metadataQueries = MetadataQueries(driver)
    private val store = keyValueStorage<TestObject>("ops", entityQueries, metadataQueries, mainScope)
    private val nullStore = keyValueStorage<NullableTestObject>("nulls", entityQueries, metadataQueries, mainScope)

    @After fun tearDown() { mainScope.cancel() }

    private suspend fun seedTestObjects(): List<TestObject> {
        val items = (1..5).map { i ->
            TestObject(
                id = "id$i",
                name = "Name$i",
                value = i * 10,
            )
        }
        store.insertAll(items.associateBy { it.id })
        return items
    }

    @Test
    fun notEq_excludesMatchingValue() = runTest {
        val items = seedTestObjects()
        val result = store.select(where = TestObject::value neq 30).first()
        assertEquals(4, result.size)
        assertTrue(result.none { it.value == 30 })
    }

    @Test
    fun like_matchesPattern() = runTest {
        seedTestObjects()
        val result = store.select(where = TestObject::name like "Name%").first()
        assertEquals(5, result.size)
    }

    @Test
    fun gt_lt_haveCorrectBoundaries() = runTest {
        seedTestObjects()
        // gt 30: values 40, 50 → 2
        assertEquals(2, store.select(where = TestObject::value gt 30).first().size)
        // lt 30: values 10, 20 → 2
        assertEquals(2, store.select(where = TestObject::value lt 30).first().size)
        // gt 20 AND lt 50: values 30, 40 → 2
        assertEquals(
            2,
            store.select(where = (TestObject::value gt 20) and (TestObject::value lt 50)).first().size,
        )
    }

    @Test
    fun not_select_isBrokenForJsonTreeJoin() = runTest {
        // KNOWN BUG (MOB-3287): Not.toSqlQuery() wraps the json_tree WHERE with NOT (...),
        // but a single entity has many json_tree rows (id, name, value, …). The NOT condition
        // is satisfied by any non-matching row, so every entity is returned regardless.
        // not(eq 30) should return 4 items but actually returns all 5.
        // neq 30 is the correct way to exclude a value (returns 4 as expected).
        seedTestObjects()
        val viaNeq = store.select(where = TestObject::value neq 30).first()
        assertEquals(4, viaNeq.size)
        assertTrue(viaNeq.none { it.value == 30 })

        // Document broken not() behaviour — returns ALL items rather than inverted set
        val viaNot = store.select(where = not(TestObject::value eq 30)).first()
        assertEquals(5, viaNot.size) // all 5 returned — bug
    }

    @Test
    fun andOrNesting_evaluatesLeftToRight() = runTest {
        seedTestObjects()
        // (value > 19 AND value < 41) OR value == 50  →  {20, 30, 40, 50}
        val where = ((TestObject::value gt 19) and (TestObject::value lt 41)) or (TestObject::value eq 50)
        val result = store.select(where = where).first()
        assertEquals(setOf(20, 30, 40, 50), result.map { it.value }.toSet())
    }

    @Test
    fun inList_emptyList_returnsNothing() = runTest {
        seedTestObjects()
        val result = store.select(where = TestObject::value inList emptyList<Int>()).first()
        assertEquals(0, result.size)
    }

    @Test
    fun eqNull_matchesIsNull() = runTest {
        nullStore.insert("a", NullableTestObject(id = "a", name = null))
        nullStore.insert("b", NullableTestObject(id = "b", name = "set"))
        val result = nullStore.select(where = NullableTestObject::name eq null).first()
        assertEquals(1, result.size)
        assertEquals("a", result.first().id)
    }

    @Test
    fun neqNull_matchesIsNotNull() = runTest {
        nullStore.insert("a", NullableTestObject(id = "a", name = null))
        nullStore.insert("b", NullableTestObject(id = "b", name = "set"))
        val result = nullStore.select(where = NullableTestObject::name neq null).first()
        assertEquals(1, result.size)
        assertEquals("b", result.first().id)
    }
}
