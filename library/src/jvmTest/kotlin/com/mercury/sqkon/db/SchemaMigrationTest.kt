package com.mercury.sqkon.db

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteConcurrencyModel
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteConfiguration
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDatabaseType
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDriver
import com.eygraber.sqldelight.androidx.driver.SqliteJournalMode
import com.eygraber.sqldelight.androidx.driver.SqliteSync
import com.mercury.sqkon.db.SchemaSnapshotUtil.dumpSchemaSnapshot
import com.mercury.sqkon.db.internal.schema.SqkonDatabaseSchema
import com.mercury.sqkon.db.internal.sqldelight.SqlDelightSqkonDriver
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Manually creates the v1 schema (entity table without read_at/write_at,
 * no metadata table, no indices) seeded with a row, then runs
 * SqkonDatabaseSchema.migrate(driver, 1L, 2L) and asserts:
 *   1. Resulting schema matches the v2 snapshot.
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
        );
    """.trimIndent()

    @Test
    fun migrate_v1_to_v2_matchesV2Snapshot_andPreservesData() {
        val driver = createV1Driver()

        // Seed a row in v1 shape (no read_at/write_at columns).
        driver.execute(
            identifier = null,
            sql = "INSERT INTO entity (entity_name, entity_key, expires_at, value) VALUES (?, ?, NULL, ?)",
            parameters = 3,
        ) {
            bindString(0, "mig")
            bindString(1, "row1")
            bindString(2, """{"hello":"world"}""")
        }

        // Pin user_version = 1 so SQLDelight migration starts at the right version.
        driver.execute(null, "PRAGMA user_version = 1", 0) {}

        // Run real migration through hand-rolled schema.
        SqkonDatabaseSchema.migrate(SqlDelightSqkonDriver(driver), 1L, 2L)

        // Hand-rolled migrate() does not touch PRAGMA user_version itself.
        // Bump defensively so the snapshot fixture matches.
        driver.execute(null, "PRAGMA user_version = 2", 0) {}

        // Verify structural schema parity with v2 snapshot.
        // Note: sqlite_master.sql for the entity table differs between a fresh Schema.create()
        // and a migration path (ALTER TABLE adds columns without updating the stored DDL),
        // so we compare PRAGMA table_info and PRAGMA index_list sections rather than the full
        // snapshot byte-for-byte.
        val actual = dumpSchemaSnapshot(driver)
        val expected = File("src/jvmTest/resources/sqkon-schema-v2.snapshot").readText()

        // Compare the structural sections (table_info + index_list + user_version) rather than the
        // full snapshot byte-for-byte: sqlite_master.sql for the entity table differs between a
        // fresh create() and a migration path (ALTER TABLE adds columns without rewriting the
        // stored DDL), so the sqlite_master section is intentionally excluded here.
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
            mapper = { c ->
                c.next().value
                rowEntityKey = c.getString(0)
                rowWriteAt = c.getLong(1)
                QueryResult.Unit
            },
            parameters = 0,
        ) {}
        assertEquals("row1", rowEntityKey)
        // Must be real epoch-millis, not a CURRENT_TIMESTAMP year-coercion (~2026) or whole
        // seconds (~1.7e9). Anything past this threshold (2001-09-09 in ms) proves millis.
        assertTrue(
            rowWriteAt != null && rowWriteAt!! > 1_000_000_000_000L,
            "write_at must be backfilled with epoch-millis, was $rowWriteAt",
        )
    }

    /**
     * Build a fresh in-memory driver that runs only the v1 DDL — no SQLDelight Schema.create.
     */
    private fun createV1Driver(): SqlDriver {
        val noopSchema = object : SqlSchema<QueryResult.Value<Unit>> {
            override val version: Long = 1L
            override fun create(driver: SqlDriver): QueryResult.Value<Unit> {
                v1Ddl.split(";").map { it.trim() }.filter { it.isNotEmpty() }.forEach { stmt ->
                    driver.execute(null, stmt, 0) {}
                }
                return QueryResult.Value(Unit)
            }
            override fun migrate(
                driver: SqlDriver,
                oldVersion: Long,
                newVersion: Long,
                vararg callbacks: AfterVersion,
            ): QueryResult.Value<Unit> = QueryResult.Value(Unit)
        }
        return AndroidxSqliteDriver(
            driver = BundledSQLiteDriver(),
            databaseType = AndroidxSqliteDatabaseType.Memory,
            schema = noopSchema,
            configuration = AndroidxSqliteConfiguration(
                journalMode = SqliteJournalMode.WAL,
                sync = SqliteSync.Normal,
                concurrencyModel = AndroidxSqliteConcurrencyModel.MultipleReadersSingleWriter(
                    isWal = true,
                    walCount = 1,
                ),
            ),
        )
    }
}
