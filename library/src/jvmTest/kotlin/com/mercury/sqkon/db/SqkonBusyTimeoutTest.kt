package com.mercury.sqkon.db

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.driver.bundled.SQLITE_OPEN_CREATE
import androidx.sqlite.driver.bundled.SQLITE_OPEN_FULLMUTEX
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READWRITE
import com.mercury.sqkon.db.internal.androidx.SqkonConnectionFactory
import com.mercury.sqkon.db.internal.androidx.SqkonConnectionPool
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals

/**
 * Regression test for the missing `busy_timeout` (#75). `applyPragmas` set only `journal_mode`
 * and `synchronous`, so every connection used SQLite's default `busy_timeout = 0` — any transient
 * WAL/checkpoint/multi-process contention failed `BEGIN IMMEDIATE` with `SQLITE_BUSY` immediately.
 */
class SqkonBusyTimeoutTest {

    private fun tempDbPath(): String =
        File.createTempFile("sqkon-busy-", ".db").also { it.delete(); it.deleteOnExit() }.absolutePath

    private fun factory(path: String) = SqkonConnectionFactory {
        BundledSQLiteDriver().open(path, SQLITE_OPEN_READWRITE or SQLITE_OPEN_CREATE or SQLITE_OPEN_FULLMUTEX)
    }

    private fun readBusyTimeout(conn: SQLiteConnection): Long =
        conn.prepare("PRAGMA busy_timeout").use { st -> st.step(); st.getLong(0) }

    /** Reads busy_timeout off the writer connection, always releasing the writer mutex. */
    private fun busyTimeoutOf(pool: SqkonConnectionPool): Long {
        val conn = pool.acquireWriter()
        try {
            return readBusyTimeout(conn)
        } finally {
            pool.releaseWriter()
        }
    }

    @Test
    fun busyTimeout_defaultsToConfiguredValue() {
        val path = tempDbPath()
        val pool = SqkonConnectionPool(factory(path), path, isMemory = false, config = SqkonDriverConfig())
        try {
            assertEquals(3_000L, busyTimeoutOf(pool))
        } finally {
            pool.close()
        }
    }

    @Test
    fun busyTimeout_honorsCustomConfig() {
        val path = tempDbPath()
        val pool = SqkonConnectionPool(
            factory(path), path, isMemory = false, config = SqkonDriverConfig(busyTimeoutMillis = 1234),
        )
        try {
            assertEquals(1234L, busyTimeoutOf(pool))
        } finally {
            pool.close()
        }
    }
}
