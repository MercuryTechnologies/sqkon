package com.mercury.sqkon.db

import com.mercury.sqkon.LargeTestObject
import com.mercury.sqkon.TestObject
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class KeyValueStorageEdgeCasesTest {

    private val mainScope = MainScope()
    private val driver = driverFactory().createDriver()
    private val entityQueries = EntityQueries(driver)
    private val metadataQueries = MetadataQueries(driver)
    private val store = keyValueStorage<TestObject>("edge", entityQueries, metadataQueries, mainScope)
    private val largeStore = keyValueStorage<LargeTestObject>("large", entityQueries, metadataQueries, mainScope)

    @After fun tearDown() { mainScope.cancel() }

    @Test
    fun insert_ignoreIfExistsTrue_isNoOp() = runTest {
        val first = TestObject(id = "k", name = "first")
        val second = TestObject(id = "k", name = "second")
        store.insert("k", first)
        store.insert("k", second, ignoreIfExists = true)
        val actual = store.selectByKey("k").first()!!
        assertEquals("first", actual.name)
    }

    @Test
    fun insert_ignoreIfExistsFalse_throwsOnDuplicate() = runTest {
        val first = TestObject(id = "k", name = "first")
        val second = TestObject(id = "k", name = "second")
        store.insert("k", first)
        // The duplicate PRIMARY KEY now surfaces as `SqlException` — the real driver exception type
        // (androidx.sqlite.SQLiteException) the breadcrumb catch blocks rely on. Before #82,
        // SqlException aliased java.sql.SQLException, so this would not have matched.
        assertFailsWith<SqlException> {
            store.insert("k", second, ignoreIfExists = false)
        }
    }

    @Test
    fun specialCharacterKeys_roundTrip() = runTest {
        val keys = listOf(
            "unicode-emoji-🚀-key",
            "single'quote",
            "back\\slash",
            "newline\nkey",
            "nullbyte-ish",
            "中文-key",
        )
        keys.forEachIndexed { i, k -> store.insert(k, TestObject(id = "id$i")) }
        keys.forEachIndexed { i, k ->
            val v = store.selectByKey(k).first()
            assertEquals("id$i", v?.id, "key '$k' did not round-trip")
        }
    }

    @Test
    fun oneMegabyteBlob_roundTrips() = runTest {
        val obj = LargeTestObject(id = "big", payload = "x".repeat(1 shl 20))
        largeStore.insert(obj.id, obj)
        val actual = largeStore.selectByKey(obj.id).first()!!
        assertEquals(obj.payload.length, actual.payload.length)
        assertEquals(obj, actual)
    }

    // EntityQueries.kt:148-149: when (entityKeys?.size) { null, 0 -> {} } — no key filter added,
    // so an empty list returns ALL rows for the entity, same as selectAll().
    @Test
    fun selectByKeys_emptyList_returnsAllRowsForEntity() = runTest {
        val items = (1..3).map { TestObject(id = "id$it") }
        store.insertAll(items.associateBy { it.id })
        val result = store.selectByKeys(emptyList()).first()
        assertEquals(3, result.size)
    }
}
