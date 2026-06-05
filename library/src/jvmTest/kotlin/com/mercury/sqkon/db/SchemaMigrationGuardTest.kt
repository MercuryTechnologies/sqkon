package com.mercury.sqkon.db

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.driver.bundled.SQLITE_OPEN_CREATE
import androidx.sqlite.driver.bundled.SQLITE_OPEN_FULLMUTEX
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READWRITE
import androidx.sqlite.execSQL
import com.mercury.sqkon.db.internal.androidx.AndroidxSqkonDriver
import com.mercury.sqkon.db.internal.androidx.SqkonConnectionFactory
import com.mercury.sqkon.db.internal.schema.SqkonDatabaseSchema
import org.junit.Test
import java.io.File
import kotlin.test.assertFailsWith

/**
 * Regression tests for migration exhaustiveness / downgrade guard (#77). Previously `ensureSchema`
 * routed any `current != version` to `migrate(...)` then unconditionally bumped `user_version` —
 * so an unhandled delta (no DDL) or a downgrade silently corrupted the recorded version.
 */
class SchemaMigrationGuardTest {

    private val flags = SQLITE_OPEN_READWRITE or SQLITE_OPEN_CREATE or SQLITE_OPEN_FULLMUTEX
    private fun factory() = SqkonConnectionFactory { name -> BundledSQLiteDriver().open(name, flags) }

    @Test
    fun openingNewerSchemaVersion_failsLoudly_notSilentDowngrade() {
        val dbFile = File.createTempFile("sqkon-downgrade-", ".db").apply { deleteOnExit() }
        // Stamp the file with a future schema version.
        BundledSQLiteDriver().open(dbFile.absolutePath, flags).use { it.execSQL("PRAGMA user_version = 99") }

        // Opening with the current (older) schema must throw rather than rewrite user_version down.
        assertFailsWith<IllegalStateException> {
            AndroidxSqkonDriver(
                factory = factory(),
                name = dbFile.absolutePath,
                isMemory = false,
                schema = SqkonDatabaseSchema,
                config = SqkonDriverConfig(readerConnections = 1),
            )
        }
    }

    @Test
    fun migrate_toUnregisteredVersion_failsLoudly_notSilentBump() {
        val driver = AndroidxSqkonDriver(
            factory = factory(),
            name = ":memory:",
            isMemory = true,
            schema = SqkonDatabaseSchema,
            config = SqkonDriverConfig(),
        )
        try {
            // No v2->v3 migration is registered: migrate must throw rather than no-op (a no-op would
            // let ensureSchema bump user_version to 3 with no DDL applied).
            assertFailsWith<IllegalStateException> {
                SqkonDatabaseSchema.migrate(driver, oldVersion = 2, newVersion = 3)
            }
        } finally {
            driver.close()
        }
    }
}
