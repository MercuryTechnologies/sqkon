package com.mercury.sqkon.db

import org.junit.Test
import java.io.File
import kotlin.test.assertEquals

/**
 * Boots a fresh v2 SqkonDatabase and dumps a deterministic, sorted text
 * representation of the schema. Compared byte-for-byte against
 * `library/src/jvmTest/resources/sqkon-schema-v2.snapshot`.
 *
 * The schema canary (the hand-rolled-schema successor to the old SQLDelight
 * migration verifier).
 *
 * To regenerate after an INTENTIONAL schema change:
 *   1. Bump the version and add the migration SQL in `internal/schema/SqkonSchema.kt`.
 *   2. Delete the snapshot file.
 *   3. Re-run this test — it writes the new snapshot on first run.
 *   4. Inspect the diff before committing.
 */
class SchemaParityTest {

    private val snapshotPath = "src/jvmTest/resources/sqkon-schema-v2.snapshot"

    @Test
    fun freshSchema_matchesFixture() {
        val driver = driverFactory().createDriver()
        val actual = SchemaSnapshotUtil.dumpSchemaSnapshot(driver)

        val file = File(snapshotPath)
        if (!file.exists()) {
            file.parentFile.mkdirs()
            file.writeText(actual)
            error(
                "Snapshot did not exist; wrote new snapshot to $snapshotPath. " +
                "Review and re-run."
            )
        }
        val expected = file.readText()
        assertEquals(
            expected, actual,
            "Schema drift detected. Diff vs $snapshotPath",
        )
    }
}
