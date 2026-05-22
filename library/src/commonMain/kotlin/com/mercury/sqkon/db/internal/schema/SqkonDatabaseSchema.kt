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
        if (oldVersion <= 1 && newVersion > 1) {
            driver.exec(CREATE_METADATA_TABLE)
            driver.exec("ALTER TABLE entity ADD COLUMN read_at INTEGER")
            driver.exec("ALTER TABLE entity ADD COLUMN write_at INTEGER")
            driver.exec("UPDATE entity SET write_at = CURRENT_TIMESTAMP")
            CREATE_ENTITY_INDEXES.forEach(driver::exec)
        }
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
