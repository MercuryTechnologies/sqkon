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

    /**
     * Fires both `ALL_ENTITIES_KEY` and `entityKey(name)`, matching the helper of the
     * same name in `EntityQueries`. Used by the purge family — they delete rows, so
     * any active `selectAll`/`select`/`count` Flow subscriber needs to be woken.
     */
    private fun notifyEntityChanged(identifier: Int, entityName: String) {
        notifyQueries(identifier) { emit ->
            emit(ALL_ENTITIES_KEY)
            emit(entityKey(entityName))
        }
    }

    /**
     * Fires `ALL_ENTITIES_KEY` only — deliberately omitting `entityKey(name)`. Used
     * exclusively by [updateReadForEntities]. The omission is load-bearing: that
     * method runs from `KeyValueStorage.updateReadAt(...)`, which is itself driven by
     * `entityQueries.select(...).onEach { updateReadAt(...) }`. Firing the per-entity
     * key would re-emit the same Flow, re-trigger `onEach`, and loop forever.
     * `read_at` is a bookkeeping column nothing currently observes, so suppressing
     * the wake is a no-op for present subscribers; if a future query subscribes to
     * `ALL_ENTITIES_KEY` it will still see the broadcast.
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
            return try {
                sqkonDriver.executeQuery(
                    identifier = identifier("metadataSelectByEntityName"),
                    sql = sql,
                    parameters = 1,
                    binders = { bindString(0, entityName) },
                    mapper = mapper,
                )
            } catch (ex: SqlException) {
                println("SQL Error: $sql")
                throw ex
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
        try {
            sqkonDriver.executeUpdate(
                identifier = id,
                sql = sql,
                parameters = 4,
            ) {
                bindString(0, entity_name)
                bindLong(1, ms)
                bindLong(2, ms)
                bindString(3, entity_name)
            }
        } catch (ex: SqlException) {
            println("SQL Error: $sql")
            throw ex
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
        try {
            sqkonDriver.executeUpdate(
                identifier = id,
                sql = sql,
                parameters = 4,
            ) {
                bindString(0, entity_name)
                bindLong(1, ms)
                bindLong(2, ms)
                bindString(3, entity_name)
            }
        } catch (ex: SqlException) {
            println("SQL Error: $sql")
            throw ex
        }
        notifyMetadataChanged(id, entity_name)
    }

    /**
     * Bulk-updates `entity.read_at` for the listed keys. An empty [entity_keys] is a
     * deliberate no-op: SQLite rejects `IN ()` as a syntax error, and the surrounding
     * caller (`KeyValueStorage.updateReadAt`) routinely passes an empty collection
     * when a `selectAll(...)` Flow emits an empty result set.
     */
    fun updateReadForEntities(readAt: Long, entity_name: String, entity_keys: Collection<String>) {
        if (entity_keys.isEmpty()) return
        val id = identifier("metadataUpdateReadForEntities", entity_keys.size.toString())
        val placeholders = entity_keys.joinToString(",") { "?" }
        val sql = "UPDATE entity SET read_at = ? WHERE entity_name = ? AND entity_key IN ($placeholders)"
        try {
            sqkonDriver.executeUpdate(
                identifier = id,
                sql = sql,
                parameters = 2 + entity_keys.size,
            ) {
                bindLong(0, readAt)
                bindString(1, entity_name)
                entity_keys.forEachIndexed { idx, key -> bindString(2 + idx, key) }
            }
        } catch (ex: SqlException) {
            println("SQL Error: $sql")
            throw ex
        }
        notifyEntityReadAtChanged(id)
    }

    fun purgeExpires(entity_name: String, expiresAfter: Long) {
        val id = identifier("metadataPurgeExpires")
        val sql = "DELETE FROM entity WHERE entity_name = ? AND expires_at IS NOT NULL AND expires_at < ?"
        try {
            sqkonDriver.executeUpdate(
                identifier = id,
                sql = sql,
                parameters = 2,
            ) {
                bindString(0, entity_name)
                bindLong(1, expiresAfter)
            }
        } catch (ex: SqlException) {
            println("SQL Error: $sql")
            throw ex
        }
        notifyEntityChanged(id, entity_name)
    }

    fun purgeStale(entity_name: String, writeInstant: Long, readInstant: Long) {
        val id = identifier("metadataPurgeStale")
        val sql = "DELETE FROM entity WHERE entity_name = ? AND write_at < ? AND (read_at IS NULL OR read_at < ?)"
        try {
            sqkonDriver.executeUpdate(
                identifier = id,
                sql = sql,
                parameters = 3,
            ) {
                bindString(0, entity_name)
                bindLong(1, writeInstant)
                bindLong(2, readInstant)
            }
        } catch (ex: SqlException) {
            println("SQL Error: $sql")
            throw ex
        }
        notifyEntityChanged(id, entity_name)
    }

    fun purgeStaleWrite(entity_name: String, writeInstant: Long) {
        val id = identifier("metadataPurgeStaleWrite")
        val sql = "DELETE FROM entity WHERE entity_name = ? AND write_at < ?"
        try {
            sqkonDriver.executeUpdate(
                identifier = id,
                sql = sql,
                parameters = 2,
            ) {
                bindString(0, entity_name)
                bindLong(1, writeInstant)
            }
        } catch (ex: SqlException) {
            println("SQL Error: $sql")
            throw ex
        }
        notifyEntityChanged(id, entity_name)
    }

    fun purgeStaleRead(entity_name: String, readInstant: Long) {
        val id = identifier("metadataPurgeStaleRead")
        val sql = "DELETE FROM entity WHERE entity_name = ? AND read_at < ?"
        try {
            sqkonDriver.executeUpdate(
                identifier = id,
                sql = sql,
                parameters = 2,
            ) {
                bindString(0, entity_name)
                bindLong(1, readInstant)
            }
        } catch (ex: SqlException) {
            println("SQL Error: $sql")
            throw ex
        }
        notifyEntityChanged(id, entity_name)
    }
}

internal const val ALL_METADATA_KEY = "metadata"
internal fun metadataKey(entityName: String): String = "metadata_$entityName"
