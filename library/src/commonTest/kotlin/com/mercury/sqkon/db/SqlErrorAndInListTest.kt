package com.mercury.sqkon.db

import com.mercury.sqkon.TestObject
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Coverage for the IN-list bound-variable limit and the SQL-error breadcrumb path (#84).
 *
 * `selectByKeys` / `deleteByKeys` / `updateReadForEntities` build `IN (?, ?, …)` dynamically, so
 * the key-set size becomes the SQLite bound-variable count (limit `SQLITE_MAX_VARIABLE_NUMBER`,
 * 32766 on modern SQLite). These tests assert a large-but-valid set works, and an over-limit set
 * fails loudly with the original `SqlException` (the EntityQueries breadcrumb rethrows it, never
 * swallows it).
 */
class SqlErrorAndInListTest {

    private val mainScope = MainScope()
    private val driver = driverFactory().createDriver()
    private val entityQueries = EntityQueries(driver)
    private val metadataQueries = MetadataQueries(driver)
    private val store = keyValueStorage<TestObject>("inlist", entityQueries, metadataQueries, mainScope)

    @After
    fun tearDown() {
        mainScope.cancel()
    }

    @Test
    fun selectByKeys_largeKeySet_returnsAllRows() = runTest {
        // 2000 keys: well under the 32766 bound-variable limit. The read also drives
        // updateReadForEntities over the same 2000 keys (another IN(?,…) site).
        val objects = (1..2000).map { TestObject() }.associateBy { it.id }
        store.insertAll(objects)

        val result = store.selectByKeys(objects.keys.toList()).first()
        assertEquals(objects.size, result.size)
    }

    @Test
    fun deleteByKeys_largeKeySet_deletesAll() = runTest {
        val objects = (1..2000).map { TestObject() }.associateBy { it.id }
        store.insertAll(objects)

        store.deleteByKeys(*objects.keys.toTypedArray())

        assertEquals(0, store.selectAll().first().size)
    }

    @Test
    fun selectByKeys_overVariableLimit_failsWithSqlException_notSilently() = runTest {
        // 40000 > SQLITE_MAX_VARIABLE_NUMBER (32766): the IN(?,…) list exceeds the bound-variable
        // limit. The driver must raise a SqlException, and the EntityQueries select breadcrumb must
        // rethrow it with the original type — not swallow it or return a silently wrong result.
        val tooManyKeys = (1..40_000).map { "k$it" }
        assertFailsWith<SqlException> {
            store.selectByKeys(tooManyKeys).first()
        }
    }
}
