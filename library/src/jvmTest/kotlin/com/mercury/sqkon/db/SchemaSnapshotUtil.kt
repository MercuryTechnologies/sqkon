package com.mercury.sqkon.db

import com.mercury.sqkon.db.internal.SqkonDriver

internal object SchemaSnapshotUtil {

    /**
     * Deterministic, sorted text dump of the current SQLite schema. Used as a byte-for-byte
     * fixture in SchemaParityTest and as the post-migration assertion in SchemaMigrationTest.
     */
    fun dumpSchemaSnapshot(driver: SqkonDriver): String = buildString {
        appendLine("=== sqlite_master (excluding internal) ===")
        sqliteMaster(driver).forEach { row ->
            appendLine("[${row.type}] name=${row.name} tbl=${row.tblName}")
            appendLine("  sql: ${normalise(row.sql)}")
        }
        appendLine()
        appendLine("=== PRAGMA table_info(entity) ===")
        pragmaTableInfo(driver, "entity").forEach { col -> appendLine(formatTableInfo(col)) }
        appendLine()
        appendLine("=== PRAGMA index_list(entity) ===")
        pragmaIndexList(driver, "entity").forEach { idx -> appendLine(formatIndexList(idx)) }
        appendLine()
        appendLine("=== PRAGMA table_info(metadata) ===")
        pragmaTableInfo(driver, "metadata").forEach { col -> appendLine(formatTableInfo(col)) }
        appendLine()
        appendLine("=== PRAGMA user_version ===")
        appendLine(pragmaUserVersion(driver))
    }

    /** Extract a single `=== header ===` section from a [dumpSchemaSnapshot] string. */
    fun section(snapshot: String, header: String): String {
        val start = snapshot.indexOf("=== $header ===")
        if (start == -1) return ""
        val end = snapshot.indexOf("\n===", start + 1).let { if (it == -1) snapshot.length else it }
        return snapshot.substring(start, end).trim()
    }

    private fun normalise(s: String?): String =
        (s ?: "")
            .replace(Regex("--[^\n]*"), "")
            .replace(Regex("\\s+"), " ")
            .trim()

    private data class MasterRow(val type: String, val name: String, val tblName: String, val sql: String?)
    private data class TableInfoRow(val cid: Long, val name: String, val type: String, val notnull: Long, val dflt: String?, val pk: Long)
    private data class IndexListRow(val seq: Long, val name: String, val unique: Long, val origin: String, val partial: Long)

    private fun sqliteMaster(driver: SqkonDriver): List<MasterRow> {
        val rows = mutableListOf<MasterRow>()
        driver.executeQuery(
            identifier = null,
            sql = "SELECT type, name, tbl_name, sql FROM sqlite_master " +
                  "WHERE name NOT LIKE 'sqlite_%' ORDER BY type, name",
            parameters = 0,
            mapper = { c ->
                while (c.next()) {
                    rows += MasterRow(
                        type = c.getString(0)!!,
                        name = c.getString(1)!!,
                        tblName = c.getString(2)!!,
                        sql = c.getString(3),
                    )
                }
            },
        )
        return rows
    }

    private fun pragmaTableInfo(driver: SqkonDriver, table: String): List<TableInfoRow> {
        val rows = mutableListOf<TableInfoRow>()
        driver.executeQuery(
            identifier = null,
            sql = "PRAGMA table_info($table)",
            parameters = 0,
            mapper = { c ->
                while (c.next()) {
                    rows += TableInfoRow(
                        cid = c.getLong(0)!!,
                        name = c.getString(1)!!,
                        type = c.getString(2)!!,
                        notnull = c.getLong(3)!!,
                        dflt = c.getString(4),
                        pk = c.getLong(5)!!,
                    )
                }
            },
        )
        return rows.sortedBy { it.name }
    }

    private fun pragmaIndexList(driver: SqkonDriver, table: String): List<IndexListRow> {
        val rows = mutableListOf<IndexListRow>()
        driver.executeQuery(
            identifier = null,
            sql = "PRAGMA index_list($table)",
            parameters = 0,
            mapper = { c ->
                while (c.next()) {
                    rows += IndexListRow(
                        seq = c.getLong(0)!!,
                        name = c.getString(1)!!,
                        unique = c.getLong(2)!!,
                        origin = c.getString(3) ?: "",
                        partial = c.getLong(4) ?: 0L,
                    )
                }
            },
        )
        return rows.sortedBy { it.name }
    }

    private fun pragmaUserVersion(driver: SqkonDriver): Long {
        var v = -1L
        driver.executeQuery(
            identifier = null,
            sql = "PRAGMA user_version",
            parameters = 0,
            mapper = { c -> c.next(); v = c.getLong(0)!! },
        )
        return v
    }

    private fun formatTableInfo(c: TableInfoRow): String =
        "cid=${c.cid} name=${c.name} type=${c.type} notnull=${c.notnull} default=${c.dflt ?: "null"} pk=${c.pk}"

    private fun formatIndexList(i: IndexListRow): String =
        "seq=${i.seq} name=${i.name} unique=${i.unique} origin=${i.origin} partial=${i.partial}"
}
