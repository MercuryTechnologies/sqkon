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

    private fun notifyEntityChanged(identifier: Int, @Suppress("UNUSED_PARAMETER") entityName: String) {
        // Matches SQLDelight-generated semantics: notify on the broad entity-table key only.
        // EntityQueries' selectAll Flow subscribes to entityKey(name); not firing it here is
        // load-bearing because KeyValueStorage.select(...).onEach { updateReadAt(...) } chains
        // back into updateReadForEntities, which would otherwise re-trigger the same Flow.
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
        override fun <R> execute(mapper: (SqkonCursor) -> R): R = sqkonDriver.executeQuery(
            identifier = identifier("metadataSelectByEntityName"),
            sql = "SELECT entity_name, lastReadAt, lastWriteAt FROM metadata WHERE entity_name = ?",
            parameters = 1,
            binders = { bindString(0, entityName) },
            mapper = mapper,
        )

        override fun toString(): String = "metadataSelectByEntityName"
    }

    fun upsertRead(entity_name: String, lastReadAt: Instant) {
        val id = identifier("metadataUpsertRead")
        val ms = lastReadAt.toEpochMilliseconds()
        sqkonDriver.executeUpdate(
            identifier = id,
            sql = """
                INSERT INTO metadata (entity_name, lastReadAt) VALUES (?, ?)
                ON CONFLICT(entity_name) DO UPDATE SET lastReadAt = ? WHERE entity_name = ?
            """.trimIndent().replace('\n', ' '),
            parameters = 4,
        ) {
            bindString(0, entity_name)
            bindLong(1, ms)
            bindLong(2, ms)
            bindString(3, entity_name)
        }
        notifyMetadataChanged(id, entity_name)
    }

    fun upsertWrite(entity_name: String, lastWriteAt: Instant) {
        val id = identifier("metadataUpsertWrite")
        val ms = lastWriteAt.toEpochMilliseconds()
        sqkonDriver.executeUpdate(
            identifier = id,
            sql = """
                INSERT INTO metadata (entity_name, lastWriteAt) VALUES (?, ?)
                ON CONFLICT(entity_name) DO UPDATE SET lastWriteAt = ? WHERE entity_name = ?
            """.trimIndent().replace('\n', ' '),
            parameters = 4,
        ) {
            bindString(0, entity_name)
            bindLong(1, ms)
            bindLong(2, ms)
            bindString(3, entity_name)
        }
        notifyMetadataChanged(id, entity_name)
    }

    fun updateReadForEntities(readAt: Long, entity_name: String, entity_keys: Collection<String>) {
        if (entity_keys.isEmpty()) return
        val id = identifier("metadataUpdateReadForEntities", entity_keys.size.toString())
        val placeholders = entity_keys.joinToString(",") { "?" }
        sqkonDriver.executeUpdate(
            identifier = id,
            sql = "UPDATE entity SET read_at = ? WHERE entity_name = ? AND entity_key IN ($placeholders)",
            parameters = 2 + entity_keys.size,
        ) {
            bindLong(0, readAt)
            bindString(1, entity_name)
            entity_keys.forEachIndexed { idx, key -> bindString(2 + idx, key) }
        }
        notifyEntityChanged(id, entity_name)
    }

    fun purgeExpires(entity_name: String, expiresAfter: Long) {
        val id = identifier("metadataPurgeExpires")
        sqkonDriver.executeUpdate(
            identifier = id,
            sql = "DELETE FROM entity WHERE entity_name = ? AND expires_at IS NOT NULL AND expires_at < ?",
            parameters = 2,
        ) {
            bindString(0, entity_name)
            bindLong(1, expiresAfter)
        }
        notifyEntityChanged(id, entity_name)
    }

    fun purgeStale(entity_name: String, writeInstant: Long, readInstant: Long) {
        val id = identifier("metadataPurgeStale")
        sqkonDriver.executeUpdate(
            identifier = id,
            sql = "DELETE FROM entity WHERE entity_name = ? AND write_at < ? AND (read_at IS NULL OR read_at < ?)",
            parameters = 3,
        ) {
            bindString(0, entity_name)
            bindLong(1, writeInstant)
            bindLong(2, readInstant)
        }
        notifyEntityChanged(id, entity_name)
    }

    fun purgeStaleWrite(entity_name: String, writeInstant: Long) {
        val id = identifier("metadataPurgeStaleWrite")
        sqkonDriver.executeUpdate(
            identifier = id,
            sql = "DELETE FROM entity WHERE entity_name = ? AND write_at < ?",
            parameters = 2,
        ) {
            bindString(0, entity_name)
            bindLong(1, writeInstant)
        }
        notifyEntityChanged(id, entity_name)
    }

    fun purgeStaleRead(entity_name: String, readInstant: Long) {
        val id = identifier("metadataPurgeStaleRead")
        sqkonDriver.executeUpdate(
            identifier = id,
            sql = "DELETE FROM entity WHERE entity_name = ? AND read_at < ?",
            parameters = 2,
        ) {
            bindString(0, entity_name)
            bindLong(1, readInstant)
        }
        notifyEntityChanged(id, entity_name)
    }
}

internal const val ALL_METADATA_KEY = "metadata"
internal fun metadataKey(entityName: String): String = "metadata_$entityName"
