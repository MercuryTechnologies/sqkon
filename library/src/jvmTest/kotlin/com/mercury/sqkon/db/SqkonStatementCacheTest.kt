package com.mercury.sqkon.db

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.driver.bundled.SQLITE_OPEN_CREATE
import androidx.sqlite.driver.bundled.SQLITE_OPEN_FULLMUTEX
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READWRITE
import com.mercury.sqkon.db.internal.androidx.SqkonStatementCache
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Regression test for the statement-cache identifier-collision bug (#74).
 *
 * The cache is keyed by a 32-bit `identifier` (a hashCode of the runtime-variable query shape).
 * On a cache hit it reused the cached statement when only the identifier + subtype matched, without
 * checking the SQL — so a collision between two structurally different same-subtype queries would
 * execute the wrong prepared statement. The fix compares the cached statement's SQL on a hit.
 */
class SqkonStatementCacheTest {

    private fun memoryConnection(): SQLiteConnection =
        BundledSQLiteDriver().open(
            ":memory:", SQLITE_OPEN_READWRITE or SQLITE_OPEN_CREATE or SQLITE_OPEN_FULLMUTEX,
        )

    @Test
    fun sameIdentifierDifferentSql_doesNotReuseStaleStatement() {
        val cache = SqkonStatementCache(SqkonDriverConfig())
        val conn = memoryConnection()
        try {
            // Cache a statement for "SELECT 1" under identifier 42.
            val r1 = cache.executeQuery(conn, identifier = 42, sql = "SELECT 1", binders = null) { c ->
                c.next(); c.getLong(0)
            }
            assertEquals(1L, r1)

            // Same identifier (simulating a hashCode collision) but DIFFERENT SQL. The cache must
            // NOT reuse the cached "SELECT 1" statement — it must prepare "SELECT 2".
            val r2 = cache.executeQuery(conn, identifier = 42, sql = "SELECT 2", binders = null) { c ->
                c.next(); c.getLong(0)
            }
            assertEquals(2L, r2)
        } finally {
            cache.evictAll() // close cached statements before the connection
            conn.close()
        }
    }

    @Test
    fun sameIdentifierSameSql_reusesStatementAndReturnsCorrectResults() {
        val cache = SqkonStatementCache(SqkonDriverConfig())
        val conn = memoryConnection()
        try {
            // Repeated identical (identifier, sql) must keep returning the right result while
            // exercising the reuse path (no collision).
            repeat(3) {
                val r = cache.executeQuery(conn, identifier = 7, sql = "SELECT 5", binders = null) { c ->
                    c.next(); c.getLong(0)
                }
                assertEquals(5L, r)
            }
        } finally {
            cache.evictAll() // close cached statements before the connection
            conn.close()
        }
    }
}
