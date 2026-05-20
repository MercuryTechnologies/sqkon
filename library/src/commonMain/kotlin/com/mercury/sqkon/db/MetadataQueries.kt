package com.mercury.sqkon.db

import app.cash.sqldelight.TransacterImpl
import app.cash.sqldelight.db.SqlDriver
import com.mercury.sqkon.db.internal.SqkonCursor
import com.mercury.sqkon.db.internal.SqkonDriver
import com.mercury.sqkon.db.internal.SqkonQuery
import com.mercury.sqkon.db.internal.sqldelight.SqlDelightSqkonDriver
import kotlinx.datetime.Instant

class MetadataQueries(
    @PublishedApi
    internal val sqlDriver: SqlDriver,
) : TransacterImpl(sqlDriver) {

    @PublishedApi
    internal val sqkonDriver: SqkonDriver = SqlDelightSqkonDriver(sqlDriver)

    private fun notifyMetadataChanged(identifier: Int, entityName: String) {
        notifyQueries(identifier) { emit ->
            emit(ALL_METADATA_KEY)
            emit(metadataKey(entityName))
        }
    }

    /** Mirrors [EntityQueries.notifyEntityChanged] — used by the purge family. */
    private fun notifyEntityChanged(identifier: Int, entityName: String) {
        notifyQueries(identifier) { emit ->
            emit(ALL_ENTITIES_KEY)
            emit(entityKey(entityName))
        }
    }

    /**
     * Fires `ALL_ENTITIES_KEY` only — emitting `entityKey(name)` would re-wake the
     * `selectAll(...).onEach { updateReadAt(...) }` chain that called this method,
     * looping forever. `read_at` is bookkeeping nothing currently observes.
     */
    private fun notifyEntityReadAtChanged(identifier: Int) {
        notifyQueries(identifier) { emit ->
            emit(ALL_ENTITIES_KEY)
        }
    }

    internal fun selectByEntityName(entityName: String): SqkonQuery<Metadata> =
        SelectByEntityNameQuery(entityName) { cursor ->
            Metadata(
                entity_name = cursor.getString(0)!!,
                lastReadAt = cursor.getLong(1)?.let(Instant::fromEpochMilliseconds),
                lastWriteAt = cursor.getLong(2)?.let(Instant::fromEpochMilliseconds),
            )
        }

    private inner class SelectByEntityNameQuery(
        private val entityName: String,
        mapper: (SqkonCursor) -> Metadata,
    ) : DriverBackedSqkonQuery<Metadata>(
        sqkonDriver, arrayOf(metadataKey(entityName)), mapper,
    ) {
        override fun <R> execute(mapper: (SqkonCursor) -> R): R {
            val sql = "SELECT entity_name, lastReadAt, lastWriteAt FROM metadata WHERE entity_name = ?"
            return withSqlBreadcrumb(sql) {
                sqkonDriver.executeQuery(
                    identifier = identifier("metadataSelectByEntityName"),
                    sql = sql,
                    parameters = 1,
                    binders = { bindString(0, entityName) },
                    mapper = mapper,
                )
            }
        }

        override fun toString(): String = "metadataSelectByEntityName"
    }

    fun upsertRead(entity_name: String, lastReadAt: Instant) {
        val id = identifier("metadataUpsertRead")
        val ms = lastReadAt.toEpochMilliseconds()
        val sql = """
            INSERT INTO metadata (entity_name, lastReadAt) VALUES (?, ?)
            ON CONFLICT(entity_name) DO UPDATE SET lastReadAt = ? WHERE entity_name = ?
        """.trimIndent().replace('\n', ' ')
        withSqlBreadcrumb(sql) {
            sqkonDriver.executeUpdate(id, sql, parameters = 4) {
                bindString(0, entity_name)
                bindLong(1, ms)
                bindLong(2, ms)
                bindString(3, entity_name)
            }
        }
        notifyMetadataChanged(id, entity_name)
    }

    fun upsertWrite(entity_name: String, lastWriteAt: Instant) {
        val id = identifier("metadataUpsertWrite")
        val ms = lastWriteAt.toEpochMilliseconds()
        val sql = """
            INSERT INTO metadata (entity_name, lastWriteAt) VALUES (?, ?)
            ON CONFLICT(entity_name) DO UPDATE SET lastWriteAt = ? WHERE entity_name = ?
        """.trimIndent().replace('\n', ' ')
        withSqlBreadcrumb(sql) {
            sqkonDriver.executeUpdate(id, sql, parameters = 4) {
                bindString(0, entity_name)
                bindLong(1, ms)
                bindLong(2, ms)
                bindString(3, entity_name)
            }
        }
        notifyMetadataChanged(id, entity_name)
    }

    /**
     * Bulk-updates `entity.read_at` for [entity_keys]. An empty collection is a
     * deliberate no-op: SQLite rejects `IN ()` as a syntax error, and
     * `KeyValueStorage.updateReadAt` routinely passes an empty list when a
     * `selectAll(...)` Flow emits an empty result set.
     */
    fun updateReadForEntities(readAt: Long, entity_name: String, entity_keys: Collection<String>) {
        if (entity_keys.isEmpty()) return
        val id = identifier("metadataUpdateReadForEntities", entity_keys.size.toString())
        val sql = "UPDATE entity SET read_at = ? WHERE entity_name = ? AND entity_key IN (${entity_keys.sqlPlaceholders()})"
        withSqlBreadcrumb(sql) {
            sqkonDriver.executeUpdate(id, sql, parameters = 2 + entity_keys.size) {
                bindLong(0, readAt)
                bindString(1, entity_name)
                entity_keys.forEachIndexed { idx, key -> bindString(2 + idx, key) }
            }
        }
        notifyEntityReadAtChanged(id)
    }

    fun purgeExpires(entity_name: String, expiresAfter: Long) {
        val id = identifier("metadataPurgeExpires")
        val sql = "DELETE FROM entity WHERE entity_name = ? AND expires_at IS NOT NULL AND expires_at < ?"
        withSqlBreadcrumb(sql) {
            sqkonDriver.executeUpdate(id, sql, parameters = 2) {
                bindString(0, entity_name)
                bindLong(1, expiresAfter)
            }
        }
        notifyEntityChanged(id, entity_name)
    }

    fun purgeStale(entity_name: String, writeInstant: Long, readInstant: Long) {
        val id = identifier("metadataPurgeStale")
        val sql = "DELETE FROM entity WHERE entity_name = ? AND write_at < ? AND (read_at IS NULL OR read_at < ?)"
        withSqlBreadcrumb(sql) {
            sqkonDriver.executeUpdate(id, sql, parameters = 3) {
                bindString(0, entity_name)
                bindLong(1, writeInstant)
                bindLong(2, readInstant)
            }
        }
        notifyEntityChanged(id, entity_name)
    }

    fun purgeStaleWrite(entity_name: String, writeInstant: Long) {
        val id = identifier("metadataPurgeStaleWrite")
        val sql = "DELETE FROM entity WHERE entity_name = ? AND write_at < ?"
        withSqlBreadcrumb(sql) {
            sqkonDriver.executeUpdate(id, sql, parameters = 2) {
                bindString(0, entity_name)
                bindLong(1, writeInstant)
            }
        }
        notifyEntityChanged(id, entity_name)
    }

    fun purgeStaleRead(entity_name: String, readInstant: Long) {
        val id = identifier("metadataPurgeStaleRead")
        val sql = "DELETE FROM entity WHERE entity_name = ? AND read_at < ?"
        withSqlBreadcrumb(sql) {
            sqkonDriver.executeUpdate(id, sql, parameters = 2) {
                bindString(0, entity_name)
                bindLong(1, readInstant)
            }
        }
        notifyEntityChanged(id, entity_name)
    }
}

internal const val ALL_METADATA_KEY = "metadata"
internal fun metadataKey(entityName: String): String = "metadata_$entityName"

/** Logs the executing SQL when a [SqlException] escapes, then rethrows untouched. */
internal inline fun <R> withSqlBreadcrumb(sql: String, block: () -> R): R = try {
    block()
} catch (ex: SqlException) {
    println("SQL Error: $sql")
    throw ex
}

/** Returns `?,?,?` for a 3-element collection — used to build `IN (...)` lists. */
internal fun Collection<*>.sqlPlaceholders(separator: String = ","): String =
    joinToString(separator) { "?" }
