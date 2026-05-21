package com.mercury.sqkon.db.internal.schema

import com.mercury.sqkon.db.internal.SqkonDriver

internal object SqkonDatabaseSchema : SqkonSchema {

    override val version: Long = 2L

    override fun create(driver: SqkonDriver) {
        driver.executeUpdate(
            identifier = null,
            sql = """
                |CREATE TABLE entity (
                |    entity_name TEXT NOT NULL,
                |    entity_key TEXT NOT NULL,
                |    -- UTC timestamp in milliseconds
                |    added_at INTEGER NOT NULL DEFAULT CURRENT_TIMESTAMP,
                |    -- UTC timestamp in milliseconds
                |    updated_at INTEGER NOT NULL DEFAULT CURRENT_TIMESTAMP,
                |    -- UTC timestamp in milliseconds
                |    expires_at INTEGER,
                |    -- JSONB Blob use jsonb_ operators
                |    value BLOB NOT NULL,
                |    -- UTC timestamp in milliseconds
                |    read_at INTEGER,
                |    -- UTC timestamp in milliseconds
                |    write_at INTEGER,
                |    PRIMARY KEY (entity_name, entity_key)
                |)
            """.trimMargin(),
            parameters = 0,
        )
        driver.executeUpdate(
            identifier = null,
            sql = """
                |CREATE TABLE metadata (
                |    entity_name TEXT NOT NULL PRIMARY KEY,
                |    lastReadAt INTEGER,
                |    lastWriteAt INTEGER
                |)
            """.trimMargin(),
            parameters = 0,
        )
        driver.executeUpdate(null, "CREATE INDEX idx_entity_read_at ON entity (read_at)", 0)
        driver.executeUpdate(null, "CREATE INDEX idx_entity_write_at ON entity (write_at)", 0)
        driver.executeUpdate(null, "CREATE INDEX idx_entity_expires_at ON entity (expires_at)", 0)
    }

    override fun migrate(driver: SqkonDriver, oldVersion: Long, newVersion: Long) {
        if (oldVersion <= 1 && newVersion > 1) {
            driver.executeUpdate(
                identifier = null,
                sql = """
                    |CREATE TABLE metadata (
                    |    entity_name TEXT NOT NULL PRIMARY KEY,
                    |    lastReadAt INTEGER,
                    |    lastWriteAt INTEGER
                    |)
                """.trimMargin(),
                parameters = 0,
            )
            driver.executeUpdate(null, "ALTER TABLE entity ADD COLUMN read_at INTEGER", 0)
            driver.executeUpdate(null, "ALTER TABLE entity ADD COLUMN write_at INTEGER", 0)
            driver.executeUpdate(null, "UPDATE entity SET write_at = CURRENT_TIMESTAMP", 0)
            driver.executeUpdate(null, "CREATE INDEX idx_entity_read_at ON entity (read_at)", 0)
            driver.executeUpdate(null, "CREATE INDEX idx_entity_write_at ON entity (write_at)", 0)
            driver.executeUpdate(null, "CREATE INDEX idx_entity_expires_at ON entity (expires_at)", 0)
        }
    }
}
