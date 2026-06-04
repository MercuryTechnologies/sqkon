package com.mercury.sqkon.db

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.driver.bundled.SQLITE_OPEN_CREATE
import androidx.sqlite.driver.bundled.SQLITE_OPEN_FULLMUTEX
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READWRITE
import com.mercury.sqkon.TestObject
import com.mercury.sqkon.db.internal.androidx.SqkonConnectionFactory
import com.mercury.sqkon.db.internal.androidx.SqkonConnectionPool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CyclicBarrier
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Coverage for the multi-reader connection pool (#71). The JVM `driverFactory()` returns an
 * in-memory database, where `readerCount == 0` and `acquireReader`/`releaseReader` short-circuit
 * to the single writer — so the reader `Channel`, `ensureReaders()` init, and the
 * "open N / drain N on close" invariant were exercised by no JVM test. These tests run a real
 * **file-backed** pool with `readerConnections = 4`.
 */
class SqkonConnectionPoolTest {

    private val readerCount = 4

    private fun tempDbPath(): String =
        File.createTempFile("sqkon-pool-", ".db").also { it.delete(); it.deleteOnExit() }.absolutePath

    /** Opens real bundled connections and records each one + whether it has been closed. */
    private class TrackingFactory(private val path: String) : SqkonConnectionFactory {
        val opened = CopyOnWriteArrayList<TrackingConnection>()
        private val bundled = BundledSQLiteDriver()
        override fun open(name: String): SQLiteConnection {
            val c = TrackingConnection(
                bundled.open(path, SQLITE_OPEN_READWRITE or SQLITE_OPEN_CREATE or SQLITE_OPEN_FULLMUTEX)
            )
            opened += c
            return c
        }
    }

    private class TrackingConnection(private val delegate: SQLiteConnection) : SQLiteConnection {
        @Volatile
        var closed = false
            private set

        override fun prepare(sql: String): SQLiteStatement = delegate.prepare(sql)
        override fun inTransaction(): Boolean = delegate.inTransaction()
        override fun close() {
            closed = true
            delegate.close()
        }
    }

    private fun pool(factory: SqkonConnectionFactory, path: String): SqkonConnectionPool =
        SqkonConnectionPool(
            factory = factory,
            name = path,
            isMemory = false,
            config = SqkonDriverConfig(readerConnections = readerCount),
        )

    @Test
    fun concurrentReadsProceedWhileWriterHeld() {
        val path = tempDbPath()
        val factory = TrackingFactory(path)
        val pool = pool(factory, path)
        try {
            // Hold the writer mutex for the whole block.
            val writer = pool.acquireWriter()

            // With the writer still held, all N readers must be acquirable — they are drawn from
            // the reader Channel, not the writer mutex. If readers were tied to the writer this
            // would block (and the test would hang). Proves true reader/writer parallelism.
            val readers = (1..readerCount).map { pool.acquireReader() }

            assertEquals(readerCount, readers.toSet().size, "expected $readerCount distinct reader connections")
            assertTrue(readers.none { it === writer }, "reader connections must be separate from the writer")

            readers.forEach { pool.releaseReader(it) }
            pool.releaseWriter()
        } finally {
            pool.close()
        }
    }

    @Test
    fun close_drainsWriterAndAllReaderConnections() {
        val path = tempDbPath()
        val factory = TrackingFactory(path)
        val pool = pool(factory, path)

        // Force the writer + all readers open, then return the readers so close() can drain them.
        pool.acquireWriter(); pool.releaseWriter()
        val readers = (1..readerCount).map { pool.acquireReader() }
        readers.forEach { pool.releaseReader(it) }

        assertEquals(readerCount + 1, factory.opened.size, "expected 1 writer + $readerCount readers opened")

        pool.close()

        assertTrue(
            factory.opened.all { it.closed },
            "close() must close the writer and every reader connection",
        )
    }

    @Test
    fun concurrentFirstReads_initializeReadersExactlyOnce() {
        val path = tempDbPath()
        val factory = TrackingFactory(path)
        val pool = pool(factory, path)
        try {
            // Fire readerCount simultaneous first-reads. ensureReaders() must initialize exactly
            // once (double-checked under initMutex); a broken guard would open 2×N connections,
            // which close() — draining only N — would then leak.
            val barrier = CyclicBarrier(readerCount)
            (1..readerCount).map {
                thread {
                    barrier.await()
                    val c = pool.acquireReader()
                    pool.releaseReader(c)
                }
            }.forEach { it.join() }

            assertEquals(
                readerCount + 1,
                factory.opened.size,
                "ensureReaders must open exactly 1 writer + $readerCount readers, never 2×N",
            )
        } finally {
            pool.close()
        }
    }

    @Test
    fun fileBackedStorage_concurrentReadLoadThroughReaderPool_returnsCorrectResults() =
        runTest(timeout = 30.seconds) {
            val mainScope = MainScope()
            val driver = DriverFactory(
                type = SqkonDatabaseType.FileBacked(tempDbPath()),
                config = SqkonDriverConfig(readerConnections = readerCount),
            ).createDriver()
            try {
                val entityQueries = EntityQueries(driver)
                val metadataQueries = MetadataQueries(driver)
                val store = keyValueStorage<TestObject>("pool-load", entityQueries, metadataQueries, mainScope)

                val items = (0..49).map { TestObject(value = it) }.associateBy { it.id }
                store.insertAll(items)

                // 200 concurrent reads of varying shapes, forced onto Dispatchers.IO so they run
                // through the actual reader pool + per-connection statement caches.
                coroutineScope {
                    val sizes = (1..200).map { shape ->
                        async(Dispatchers.IO) {
                            val limit = (shape % 50) + 1L
                            entityQueries.select(
                                entityName = "pool-load",
                                entityKeys = null,
                                where = null,
                                orderBy = emptyList(),
                                limit = limit,
                                offset = null,
                                expiresAt = null,
                            ).executeAsList().size
                        }
                    }.awaitAll()

                    sizes.forEachIndexed { i, n ->
                        val shape = i + 1
                        val expected = ((shape % 50) + 1).coerceAtMost(items.size)
                        assertEquals(expected, n, "shape $shape expected $expected rows, got $n")
                    }
                }
            } finally {
                mainScope.cancel()
                driver.close()
            }
        }
}
