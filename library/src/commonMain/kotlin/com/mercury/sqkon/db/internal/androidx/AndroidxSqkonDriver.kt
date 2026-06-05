// Adapted from com.eygraber:sqldelight-androidx-driver 0.0.17 (Apache-2.0) for listener registry
// + transaction lifecycle patterns; backed by Sqkon's own SqkonDriver/SqkonTransaction interfaces.
package com.mercury.sqkon.db.internal.androidx

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.mercury.sqkon.db.SqkonDriverConfig
import com.mercury.sqkon.db.internal.SqkonCursor
import com.mercury.sqkon.db.internal.SqkonDriver
import com.mercury.sqkon.db.internal.SqkonStatement
import com.mercury.sqkon.db.internal.SqkonTransaction
import com.mercury.sqkon.db.internal.TransactionsThreadLocal
import com.mercury.sqkon.db.internal.schema.SqkonSchema
import com.mercury.sqkon.db.internal.sqkonRunBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Sqkon's native [SqkonDriver] over `androidx.sqlite`. Owns the [SqkonConnectionPool] + per-connection
 * [SqkonStatementCache] + a string-keyed listener registry. Implements transactions via
 * [SqkonDriverTransaction] (BEGIN IMMEDIATE at top level; hook propagation on nesting). Schema
 * create + migrate run once on construction, inside a top-level transaction so they're atomic.
 */
internal class AndroidxSqkonDriver(
    factory: SqkonConnectionFactory,
    private val name: String,
    private val isMemory: Boolean,
    schema: SqkonSchema,
    config: SqkonDriverConfig = SqkonDriverConfig(),
) : SqkonDriver {

    private val pool = SqkonConnectionPool(factory, name, isMemory, config)
    private val cache = SqkonStatementCache(config)
    private val transactions = TransactionsThreadLocal()

    private val listenersMutex = Mutex()
    private val listeners = linkedMapOf<String, MutableSet<SqkonDriver.Listener>>()

    init {
        // If schema init fails (e.g. a rejected downgrade or an unregistered migration step),
        // close the pool so the lazily-opened writer connection isn't leaked — construction failed,
        // so the caller never gets an instance to close(). See #77.
        try {
            ensureSchema(schema)
        } catch (t: Throwable) {
            pool.close()
            throw t
        }
    }

    // ---------- SqkonDriver: execute ----------

    override fun executeUpdate(
        identifier: Int?,
        sql: String,
        parameters: Int,
        binders: (SqkonStatement.() -> Unit)?,
    ): Long {
        val tx = transactions.get() as? SqkonDriverTransaction
        val connection = tx?.connection ?: pool.acquireWriter()
        try {
            cache.executeUpdate(connection, identifier, sql, binders)
            return 0L // affected-row count not tracked; current callers ignore it
        } finally {
            if (tx == null) pool.releaseWriter()
        }
    }

    override fun <R> executeQuery(
        identifier: Int?,
        sql: String,
        parameters: Int,
        binders: (SqkonStatement.() -> Unit)?,
        mapper: (SqkonCursor) -> R,
    ): R {
        val tx = transactions.get() as? SqkonDriverTransaction
        val connection = tx?.connection ?: pool.acquireReader()
        try {
            return cache.executeQuery(connection, identifier, sql, binders, mapper)
        } finally {
            if (tx == null) pool.releaseReader(connection)
        }
    }

    // ---------- SqkonDriver: listeners ----------

    override fun addListener(vararg queryKeys: String, listener: SqkonDriver.Listener) {
        sqkonRunBlocking {
            listenersMutex.withLock {
                queryKeys.forEach { key ->
                    listeners.getOrPut(key) { linkedSetOf() }.add(listener)
                }
            }
        }
    }

    override fun removeListener(vararg queryKeys: String, listener: SqkonDriver.Listener) {
        sqkonRunBlocking {
            listenersMutex.withLock {
                queryKeys.forEach { key -> listeners[key]?.remove(listener) }
            }
        }
    }

    override fun notifyListeners(vararg queryKeys: String) {
        val snapshot: Set<SqkonDriver.Listener> = sqkonRunBlocking {
            listenersMutex.withLock {
                val out = linkedSetOf<SqkonDriver.Listener>()
                queryKeys.forEach { key -> listeners[key]?.let(out::addAll) }
                out
            }
        }
        // Fire outside the lock to avoid re-entrancy deadlocks.
        snapshot.forEach { it.queryResultsChanged() }
    }

    // ---------- SqkonDriver: transactions ----------

    override fun newTransaction(): SqkonTransaction {
        val enclosing = transactions.get() as? SqkonDriverTransaction
        val connection: SQLiteConnection = enclosing?.connection ?: pool.acquireWriter()
        val tx = SqkonDriverTransaction(
            enclosingTransaction = enclosing,
            connection = connection,
            transactions = transactions,
            onTopLevelRelease = { pool.releaseWriter() },
        )
        // Top-level: issue BEGIN IMMEDIATE so SQLite locks the database for writes.
        if (enclosing == null) connection.execSQL("BEGIN IMMEDIATE")
        transactions.set(tx)
        return tx
    }

    override fun currentTransaction(): SqkonTransaction? = transactions.get()

    override fun close() {
        cache.evictAll()
        pool.close()
    }

    // ---------- Schema init ----------

    private fun ensureSchema(schema: SqkonSchema) {
        val connection = pool.acquireWriter()
        try {
            val current = readUserVersion(connection)
            if (current == schema.version) return
            // Reject downgrades loudly instead of silently rewriting user_version down — an older
            // app must not "migrate" a database created by a newer schema. See #77.
            check(current <= schema.version) {
                "Database schema version ($current) is newer than this library's schema " +
                    "(${schema.version}); downgrades are not supported."
            }
            // Run schema work inside a transaction. Set thread-local so calls into this driver
            // (schema.create / schema.migrate -> driver.executeUpdate) reuse the writer connection
            // instead of re-acquiring (which would deadlock the mutex we already hold).
            val tx = SqkonDriverTransaction(
                enclosingTransaction = null,
                connection = connection,
                transactions = transactions,
                // Do NOT release the writer here — we hold it for the whole ensureSchema scope.
                onTopLevelRelease = { /* no-op; outer finally releases */ },
            )
            connection.execSQL("BEGIN IMMEDIATE")
            transactions.set(tx)
            try {
                if (current == 0L) schema.create(this) else schema.migrate(this, current, schema.version)
                writeUserVersion(connection, schema.version)
                tx.successful = true
                tx.endTransaction() // issues COMMIT, fires hooks (none here)
            } catch (t: Throwable) {
                tx.endTransaction() // successful=false → ROLLBACK
                throw t
            }
        } finally {
            pool.releaseWriter()
        }
    }

    private fun readUserVersion(connection: SQLiteConnection): Long {
        val stmt = connection.prepare("PRAGMA user_version")
        try {
            return if (stmt.step()) stmt.getLong(0) else 0L
        } finally { stmt.close() }
    }

    private fun writeUserVersion(connection: SQLiteConnection, v: Long) {
        connection.execSQL("PRAGMA user_version = $v")
    }
}
