// Adapted from com.eygraber:sqldelight-androidx-driver 0.0.17 (Apache-2.0).
package com.mercury.sqkon.db.internal.androidx

import androidx.collection.LruCache
import androidx.sqlite.SQLiteConnection
import com.mercury.sqkon.db.SqkonDriverConfig
import com.mercury.sqkon.db.internal.SqkonCursor
import com.mercury.sqkon.db.internal.SqkonStatement
import com.mercury.sqkon.db.internal.sqkonRunBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Per-connection prepared-statement cache. Each `SQLiteConnection` gets an `LruCache<Int, AndroidxStatement>`
 * keyed by the SQLDelight-style statement identifier (`EntityQueries.identifier(...)`).
 *
 * Eviction protocol (per call):
 *   1. `cache.remove(identifier)` — claim it (statement is now "in use", not in cache).
 *   2. Caller binds + executes; we then `reset()` it.
 *   3. `cache.put(identifier, statement)` — store back. If `put` returns a previous entry
 *      (overwrite race) or an LRU eviction fires, the displaced statement is closed.
 *
 * Concurrent calls on the same identifier each get their own prepared statement (the loser of the
 * `remove` race prepares fresh), and the loser's statement is closed when its `put` displaces the
 * winner's — so we never share a `SQLiteStatement` across threads (it isn't safe).
 *
 * `null` identifier ⇒ never cached: prepare-and-close around the call.
 */
internal class SqkonStatementCache(private val config: SqkonDriverConfig) {

    private val perConnection = HashMap<SQLiteConnection, LruCache<Int, AndroidxStatement>>()
    private val mapMutex = Mutex()

    /** Execute a non-query statement (DDL/DML). Returns nothing — caller can read `changes()` itself if needed. */
    fun executeUpdate(
        connection: SQLiteConnection,
        identifier: Int?,
        sql: String,
        binders: (SqkonStatement.() -> Unit)?,
    ) {
        if (identifier == null) {
            val stmt = AndroidxPreparedStatement(connection.prepare(sql))
            try { binders?.invoke(stmt); stmt.execute() } finally { stmt.close() }
            return
        }
        val cache = lookupCache(connection)
        val claimed = sqkonRunBlocking { mapMutex.withLock { cache.remove(identifier) } }
        val stmt: AndroidxPreparedStatement = (claimed as? AndroidxPreparedStatement)
            ?: AndroidxPreparedStatement(connection.prepare(sql))
        try {
            binders?.invoke(stmt)
            stmt.execute()
        } finally {
            stmt.reset()
            val displaced = sqkonRunBlocking { mapMutex.withLock { cache.put(identifier, stmt) } }
            displaced?.close()
        }
    }

    /** Execute a query statement and map the resulting cursor. */
    fun <R> executeQuery(
        connection: SQLiteConnection,
        identifier: Int?,
        sql: String,
        binders: (SqkonStatement.() -> Unit)?,
        mapper: (SqkonCursor) -> R,
    ): R {
        if (identifier == null) {
            val stmt = AndroidxQuery(connection.prepare(sql))
            try {
                binders?.invoke(stmt)
                return stmt.executeQuery(mapper)
            } finally { stmt.close() }
        }
        val cache = lookupCache(connection)
        val claimed = sqkonRunBlocking { mapMutex.withLock { cache.remove(identifier) } }
        val stmt: AndroidxQuery = (claimed as? AndroidxQuery)
            ?: AndroidxQuery(connection.prepare(sql))
        try {
            binders?.invoke(stmt)
            return stmt.executeQuery(mapper)
        } finally {
            stmt.reset()
            val displaced = sqkonRunBlocking { mapMutex.withLock { cache.put(identifier, stmt) } }
            displaced?.close()
        }
    }

    fun evictAll() {
        sqkonRunBlocking {
            mapMutex.withLock {
                perConnection.values.forEach { it.evictAll() }
                perConnection.clear()
            }
        }
    }

    private fun lookupCache(connection: SQLiteConnection): LruCache<Int, AndroidxStatement> =
        sqkonRunBlocking {
            mapMutex.withLock {
                perConnection.getOrPut(connection) {
                    object : LruCache<Int, AndroidxStatement>(config.statementCacheSize) {
                        override fun entryRemoved(
                            evicted: Boolean,
                            key: Int,
                            oldValue: AndroidxStatement,
                            newValue: AndroidxStatement?,
                        ) {
                            if (evicted) oldValue.close()
                        }
                    }
                }
            }
        }
}
