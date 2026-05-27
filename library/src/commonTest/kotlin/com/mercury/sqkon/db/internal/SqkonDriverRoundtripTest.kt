package com.mercury.sqkon.db.internal

import com.mercury.sqkon.db.driverFactory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SqkonDriverRoundtripTest {

    private val driver: SqkonDriver = driverFactory().createDriver()

    @AfterTest
    fun tearDown() { driver.close() }

    @Test
    fun create_insert_select_roundtrip() {
        driver.executeUpdate(null, "CREATE TABLE t(id INTEGER, name TEXT)", 0)
        driver.executeUpdate(null, "INSERT INTO t VALUES (?, ?)", 2) {
            bindLong(0, 1L); bindString(1, "foo")
        }
        val row = driver.executeQuery(
            identifier = null,
            sql = "SELECT id, name FROM t WHERE id = ?",
            parameters = 1,
            binders = { bindLong(0, 1L) },
            mapper = { cursor ->
                assertTrue(cursor.next())
                cursor.getLong(0)!! to cursor.getString(1)!!
            },
        )
        assertEquals(1L to "foo", row)
    }

    @Test
    fun addListener_notifyListeners_fires_and_removeListener_stops_it() {
        var fires = 0
        val listener = SqkonDriver.Listener { fires++ }
        driver.addListener("k", listener = listener)
        driver.notifyListeners("k")
        assertEquals(1, fires)
        driver.removeListener("k", listener = listener)
        driver.notifyListeners("k")
        assertEquals(1, fires)
    }

    @Test
    fun currentTransaction_null_when_none_active() {
        assertNull(driver.currentTransaction())
    }

    @Test
    fun statement_cache_key_passes_through() {
        // Same identifier + sql should reuse a prepared statement under the driver's LruCache.
        // We can't introspect the cache directly; assert that repeat calls don't throw and
        // produce consistent results.
        driver.executeUpdate(null, "CREATE TABLE c(x INTEGER)", 0)
        repeat(5) {
            driver.executeUpdate(identifier = 99, sql = "INSERT INTO c VALUES (?)", parameters = 1) {
                bindLong(0, it.toLong())
            }
        }
        val count = driver.executeQuery(
            identifier = 100,
            sql = "SELECT COUNT(*) FROM c",
            parameters = 0,
            binders = null,
            mapper = { it.next(); it.getLong(0)!! },
        )
        assertEquals(5L, count)
    }
}
