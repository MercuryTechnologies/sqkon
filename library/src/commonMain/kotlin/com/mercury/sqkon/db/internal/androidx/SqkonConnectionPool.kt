// Adapted from com.eygraber:sqldelight-androidx-driver 0.0.17 (Apache-2.0).
package com.mercury.sqkon.db.internal.androidx

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.mercury.sqkon.db.SqkonDriverConfig
import com.mercury.sqkon.db.internal.sqkonRunBlocking
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One writer connection (guarded by a [Mutex]) + N reader connections (drained from a [Channel]).
 * In-memory databases use a single writer connection for both reads and writes (SQLite memory
 * databases can't be shared across connections).
 *
 * `acquire*Connection()` blocks the calling thread via [sqkonRunBlocking] — callers must run on a
 * background dispatcher (the write dispatcher is single-threaded by design).
 */
internal class SqkonConnectionPool(
    private val factory: SqkonConnectionFactory,
    private val name: String,
    private val isMemory: Boolean,
    private val config: SqkonDriverConfig,
) : AutoCloseable {

    private val writerMutex = Mutex()
    private val readerChannel = Channel<SQLiteConnection>(Channel.UNLIMITED)
    private val readerCount: Int = if (isMemory) 0 else config.readerConnections

    /** Eagerly opened so journal_mode=WAL is set before any reader attaches to the file. */
    private val writerConnection: SQLiteConnection by lazy {
        factory.open(name).apply(::applyPragmas)
    }

    private var readersInitialized = false

    private fun applyPragmas(c: SQLiteConnection) {
        c.execSQL("PRAGMA journal_mode = ${config.journalMode.pragma};")
        c.execSQL("PRAGMA synchronous = ${config.sync.pragma};")
    }

    fun acquireWriter(): SQLiteConnection {
        sqkonRunBlocking { writerMutex.lock() }
        return writerConnection
    }

    fun releaseWriter() { writerMutex.unlock() }

    fun acquireReader(): SQLiteConnection {
        if (readerCount == 0) return acquireWriter()
        ensureReaders()
        return sqkonRunBlocking { readerChannel.receive() }
    }

    fun releaseReader(c: SQLiteConnection) {
        if (readerCount == 0) { releaseWriter(); return }
        sqkonRunBlocking { readerChannel.send(c) }
    }

    /** Force-opens writer + readers. Idempotent. */
    private fun ensureReaders() {
        if (readersInitialized) return
        // Touch writer first so WAL is enabled before readers attach.
        writerConnection
        repeat(readerCount) { readerChannel.trySend(factory.open(name).apply(::applyPragmas)) }
        readersInitialized = true
    }

    override fun close() {
        sqkonRunBlocking {
            writerMutex.withLock { writerConnection.close() }
            if (readersInitialized) {
                repeat(readerCount) { readerChannel.tryReceive().getOrNull()?.close() }
            }
            readerChannel.close()
        }
    }
}
