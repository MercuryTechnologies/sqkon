package com.mercury.sqkon.db

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.driver.bundled.SQLITE_OPEN_CREATE
import androidx.sqlite.driver.bundled.SQLITE_OPEN_FULLMUTEX
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READWRITE
import androidx.sqlite.execSQL
import com.mercury.sqkon.db.SchemaSnapshotUtil.dumpSchemaSnapshot
import com.mercury.sqkon.db.internal.androidx.AndroidxSqkonDriver
import com.mercury.sqkon.db.internal.androidx.SqkonConnectionFactory
import com.mercury.sqkon.db.internal.schema.SqkonDatabaseSchema
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Seeds a v1 database (entity-only schema, no metadata table, no indices, with one row +
 * `PRAGMA user_version = 1`), then opens it via [AndroidxSqkonDriver] which auto-runs the
 * v1→v2 migration on construction. Asserts:
 *   1. Resulting schema matches the v2 snapshot (structural sections).
 *   2. The seeded row survives intact.
 *   3. write_at is backfilled with real epoch-millis (not a CURRENT_TIMESTAMP text datetime,
 *      which would coerce to a bogus small integer on the INTEGER column).
 */
class SchemaMigrationTest {

    private val v1Ddl = """
        CREATE TABLE entity (
            entity_name TEXT NOT NULL,
            entity_key TEXT NOT NULL,
            added_at INTEGER NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_at INTEGER NOT NULL DEFAULT CURRENT_TIMESTAMP,
            expires_at INTEGER,
            value BLOB NOT NULL,
            PRIMARY KEY (entity_name, entity_key)
        )
    """.trimIndent()

    @Test
    fun migrate_v1_to_v2_matchesV2Snapshot_andPreservesData() {
        val dbFile = File.createTempFile("sqkon-mig-", ".db").apply { deleteOnExit() }
        seedV1Database(dbFile.absolutePath)

        // Open through the production driver: ensureSchema() sees user_version=1 < schema=2,
        // so it runs SqkonDatabaseSchema.migrate(driver, 1, 2) + bumps user_version to 2.
        val bundled = BundledSQLiteDriver()
        val factory = SqkonConnectionFactory { name ->
            bundled.open(name, SQLITE_OPEN_READWRITE or SQLITE_OPEN_CREATE or SQLITE_OPEN_FULLMUTEX)
        }
        val driver = AndroidxSqkonDriver(
            factory = factory,
            name = dbFile.absolutePath,
            isMemory = false,
            schema = SqkonDatabaseSchema,
            config = SqkonDriverConfig(readerConnections = 1),
        )

        try {
            val actual = dumpSchemaSnapshot(driver)
            val expected = File("src/jvmTest/resources/sqkon-schema-v2.snapshot").readText()

            // Compare structural sections rather than full snapshot byte-for-byte: sqlite_master.sql
            // for the entity table differs between a fresh create() and a migration path (ALTER
            // TABLE adds columns without rewriting the stored DDL), so the sqlite_master section
            // is intentionally excluded here.
            val sections = listOf(
                "PRAGMA table_info(entity)",
                "PRAGMA index_list(entity)",
                "PRAGMA table_info(metadata)",
                "PRAGMA user_version",
            )
            for (header in sections) {
                assertEquals(
                    SchemaSnapshotUtil.section(expected, header),
                    SchemaSnapshotUtil.section(actual, header),
                    "Section '$header' does not match v2 snapshot after migration",
                )
            }

            // Seeded row survived + write_at backfilled.
            var rowEntityKey: String? = null
            var rowWriteAt: Long? = null
            driver.executeQuery(
                identifier = null,
                sql = "SELECT entity_key, write_at FROM entity WHERE entity_name = 'mig'",
                parameters = 0,
                mapper = { c ->
                    c.next()
                    rowEntityKey = c.getString(0)
                    rowWriteAt = c.getLong(1)
                },
            )
            assertEquals("row1", rowEntityKey)
            // Must be real epoch-millis, not a CURRENT_TIMESTAMP year-coercion (~2026) or whole
            // seconds (~1.7e9). Anything past this threshold (2001-09-09 in ms) proves millis.
            assertTrue(
                rowWriteAt != null && rowWriteAt!! > 1_000_000_000_000L,
                "write_at must be backfilled with epoch-millis, was $rowWriteAt",
            )
        } finally {
            driver.close()
        }
    }

    /** Build a v1 database file by hand: v1 schema + one row + `user_version = 1`. */
    private fun seedV1Database(path: String) {
        val bundled = BundledSQLiteDriver()
        val c = bundled.open(path, SQLITE_OPEN_READWRITE or SQLITE_OPEN_CREATE or SQLITE_OPEN_FULLMUTEX)
        try {
            c.execSQL(v1Ddl)
            val insert = c.prepare(
                "INSERT INTO entity (entity_name, entity_key, expires_at, value) VALUES (?, ?, NULL, ?)"
            )
            try {
                insert.bindText(1, "mig")
                insert.bindText(2, "row1")
                insert.bindText(3, """{"hello":"world"}""")
                insert.step()
            } finally { insert.close() }
            c.execSQL("PRAGMA user_version = 1")
        } finally {
            c.close()
        }
    }
}
