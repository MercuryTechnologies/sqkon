package com.mercury.sqkon.db.internal.schema

import com.mercury.sqkon.db.internal.SqkonDriver

internal object SqkonDatabaseSchema : SqkonSchema {

    override val version: Long = SQKON_SCHEMA_VERSION

    override fun create(driver: SqkonDriver) {
        driver.exec(CREATE_ENTITY_TABLE)
        driver.exec(CREATE_METADATA_TABLE)
        CREATE_ENTITY_INDEXES.forEach(driver::exec)
    }

    override fun migrate(driver: SqkonDriver, oldVersion: Long, newVersion: Long) {
        // Apply each version step in turn. Any version with no registered migration fails loudly,
        // so an unhandled delta can never silently bump user_version with no DDL applied (the caller
        // writes user_version only after this returns). Downgrades are rejected by ensureSchema. #77
        for (target in (oldVersion + 1)..newVersion) {
            when (target) {
                2L -> migrateToV2(driver)
                else -> error(
                    "No migration registered for schema version $target " +
                        "(migrating $oldVersion -> $newVersion)"
                )
            }
        }
    }

    private fun migrateToV2(driver: SqkonDriver) {
        driver.exec(CREATE_METADATA_TABLE)
        driver.exec("ALTER TABLE entity ADD COLUMN read_at INTEGER")
        driver.exec("ALTER TABLE entity ADD COLUMN write_at INTEGER")
        // write_at is read as epoch-millis (ResultRow uses Instant.fromEpochMilliseconds).
        // 1.sqm used CURRENT_TIMESTAMP, which writes a text datetime that coerces to a bogus
        // small integer (~the year) on an INTEGER column and breaks purge comparisons. Backfill
        // real epoch-millis instead. strftime('%s') is whole seconds, so multiply to millis.
        driver.exec("UPDATE entity SET write_at = CAST(strftime('%s', 'now') AS INTEGER) * 1000")
        CREATE_ENTITY_INDEXES.forEach(driver::exec)
    }

    // All INTEGER timestamp columns hold UTC milliseconds. `value` holds JSONB; use jsonb_ operators.
    private val CREATE_ENTITY_TABLE = """
        CREATE TABLE entity (
            entity_name TEXT NOT NULL,
            entity_key TEXT NOT NULL,
            added_at INTEGER NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_at INTEGER NOT NULL DEFAULT CURRENT_TIMESTAMP,
            expires_at INTEGER,
            value BLOB NOT NULL,
            read_at INTEGER,
            write_at INTEGER,
            PRIMARY KEY (entity_name, entity_key)
        )
    """.trimIndent()

    private val CREATE_METADATA_TABLE = """
        CREATE TABLE metadata (
            entity_name TEXT NOT NULL PRIMARY KEY,
            lastReadAt INTEGER,
            lastWriteAt INTEGER
        )
    """.trimIndent()

    private val CREATE_ENTITY_INDEXES = listOf(
        "CREATE INDEX idx_entity_read_at ON entity (read_at)",
        "CREATE INDEX idx_entity_write_at ON entity (write_at)",
        "CREATE INDEX idx_entity_expires_at ON entity (expires_at)",
    )
}

private fun SqkonDriver.exec(sql: String) {
    executeUpdate(identifier = null, sql = sql, parameters = 0)
}
