package com.mercury.sqkon.db

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.driver.bundled.SQLITE_OPEN_CREATE
import androidx.sqlite.driver.bundled.SQLITE_OPEN_FULLMUTEX
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READWRITE
import com.mercury.sqkon.db.internal.SqkonCursor
import com.mercury.sqkon.db.internal.androidx.AndroidxSqkonDriver
import com.mercury.sqkon.db.internal.androidx.SqkonConnectionFactory
import com.mercury.sqkon.db.internal.schema.SqkonDatabaseSchema
import org.junit.Test
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

/**
 * Resource-lifecycle coverage for the connection pool + statement cache (#83):
 *  - driver.close() closes the writer and every reader connection, is idempotent, and a query
 *    after close fails (defined outcome) rather than hanging;
 *  - exceeding statementCacheSize evicts and **closes** the displaced statement;
 *  - a same-identifier subtype collision (query then update) closes the displaced statement.
 */
class PoolAndCacheCloseTest {

    private val flags = SQLITE_OPEN_READWRITE or SQLITE_OPEN_CREATE or SQLITE_OPEN_FULLMUTEX
    private val scalarMapper: (SqkonCursor) -> Long? = { c -> c.next(); c.getLong(0) }

    private fun tempDbPath(): String =
        File.createTempFile("sqkon-close-", ".db").also { it.delete(); it.deleteOnExit() }.absolutePath

    /** A factory whose connections + prepared statements record whether they have been closed. */
    private class SpyFactory(private val path: String, private val flags: Int) : SqkonConnectionFactory {
        val connections = CopyOnWriteArrayList<SpyConnection>()
        private val bundled = BundledSQLiteDriver()
        override fun open(name: String): SQLiteConnection =
            SpyConnection(bundled.open(path, flags)).also { connections += it }

        fun totalStatementCloses(): Int = connections.sumOf { c -> c.statements.count { it.closed } }
    }

    private class SpyConnection(private val d: SQLiteConnection) : SQLiteConnection {
        val statements = CopyOnWriteArrayList<SpyStatement>()
        @Volatile
        var closed = false
            private set

        override fun prepare(sql: String): SQLiteStatement =
            SpyStatement(d.prepare(sql)).also { statements += it }

        override fun inTransaction(): Boolean = d.inTransaction()
        override fun close() {
            closed = true
            d.close()
        }
    }

    // Delegates every abstract SQLiteStatement method; the interface defaults (bindInt, etc.) route
    // through these. Only close() is observed.
    private class SpyStatement(private val d: SQLiteStatement) : SQLiteStatement {
        @Volatile
        var closed = false
            private set

        override fun bindBlob(index: Int, value: ByteArray) = d.bindBlob(index, value)
        override fun bindDouble(index: Int, value: Double) = d.bindDouble(index, value)
        override fun bindLong(index: Int, value: Long) = d.bindLong(index, value)
        override fun bindText(index: Int, value: String) = d.bindText(index, value)
        override fun bindNull(index: Int) = d.bindNull(index)
        override fun getBlob(index: Int): ByteArray = d.getBlob(index)
        override fun getDouble(index: Int): Double = d.getDouble(index)
        override fun getLong(index: Int): Long = d.getLong(index)
        override fun getText(index: Int): String = d.getText(index)
        override fun isNull(index: Int): Boolean = d.isNull(index)
        override fun getColumnCount(): Int = d.getColumnCount()
        override fun getColumnName(index: Int): String = d.getColumnName(index)
        override fun getColumnType(index: Int): Int = d.getColumnType(index)
        override fun step(): Boolean = d.step()
        override fun reset() = d.reset()
        override fun clearBindings() = d.clearBindings()
        override fun close() {
            closed = true
            d.close()
        }
    }

    @Test
    fun driverClose_closesAllConnections_isIdempotent_andQueryAfterCloseFails() {
        val path = tempDbPath()
        val factory = SpyFactory(path, flags)
        val driver = AndroidxSqkonDriver(
            factory, path, isMemory = false, schema = SqkonDatabaseSchema,
            config = SqkonDriverConfig(readerConnections = 2),
        )
        // A read forces ensureReaders() (writer + 2 readers opened).
        driver.executeQuery(identifier = null, sql = "SELECT 1", parameters = 0, mapper = scalarMapper)

        driver.close()
        assertTrue(factory.connections.size >= 3, "expected writer + 2 readers, got ${factory.connections.size}")
        assertTrue(factory.connections.all { it.closed }, "every connection must be closed after driver.close()")

        // Idempotent: a second close must not throw.
        driver.close()

        // Query after close: a defined failure (not a hang).
        assertFails {
            driver.executeQuery(identifier = null, sql = "SELECT 1", parameters = 0, mapper = scalarMapper)
        }
    }

    @Test
    fun statementCache_evictsAndClosesDisplacedStatement() {
        val factory = SpyFactory(":memory:", flags)
        val driver = AndroidxSqkonDriver(
            factory, ":memory:", isMemory = true, schema = SqkonDatabaseSchema,
            config = SqkonDriverConfig(statementCacheSize = 2),
        )
        try {
            val before = factory.totalStatementCloses()
            // Three distinct cached shapes through a size-2 cache: the third put evicts (and closes)
            // the least-recently-used statement.
            driver.executeQuery(identifier = 1, sql = "SELECT 1", parameters = 0, mapper = scalarMapper)
            driver.executeQuery(identifier = 2, sql = "SELECT 2", parameters = 0, mapper = scalarMapper)
            driver.executeQuery(identifier = 3, sql = "SELECT 3", parameters = 0, mapper = scalarMapper)
            assertEquals(
                1, factory.totalStatementCloses() - before,
                "exactly one displaced statement should be closed by LRU eviction",
            )
        } finally {
            driver.close()
        }
    }

    @Test
    fun statementCache_subtypeCollision_closesDisplacedStatement() {
        val factory = SpyFactory(":memory:", flags)
        val driver = AndroidxSqkonDriver(
            factory, ":memory:", isMemory = true, schema = SqkonDatabaseSchema,
            config = SqkonDriverConfig(),
        )
        try {
            // Cache an AndroidxQuery under identifier 5.
            driver.executeQuery(identifier = 5, sql = "SELECT 1", parameters = 0, mapper = scalarMapper)
            val before = factory.totalStatementCloses()
            // Reuse identifier 5 for an update: the cached entry is the wrong subtype, so the cache
            // must close it before preparing a fresh prepared statement.
            driver.executeUpdate(identifier = 5, sql = "CREATE TABLE t5 (x)", parameters = 0)
            assertEquals(
                1, factory.totalStatementCloses() - before,
                "subtype collision must close the displaced statement",
            )
        } finally {
            driver.close()
        }
    }
}
