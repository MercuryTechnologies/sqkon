package com.mercury.sqkon.db

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver

internal object SchemaSnapshotUtil {

    /**
     * Deterministic, sorted text dump of the current SQLite schema. Used as a byte-for-byte
     * fixture in SchemaParityTest and as the post-migration assertion in SchemaMigrationTest.
     *
     * Sections (each labeled with === name ===):
     *   sqlite_master rows (excluding sqlite_% internals), sorted by (type, name)
     *   PRAGMA table_info(entity), sorted by name
     *   PRAGMA index_list(entity), sorted by name
     *   PRAGMA table_info(metadata), sorted by name
     *   PRAGMA user_version
     */
    fun dumpSchemaSnapshot(driver: SqlDriver): String = buildString {
        appendLine("=== sqlite_master (excluding internal) ===")
        sqliteMaster(driver).forEach { row ->
            appendLine("[${row.type}] name=${row.name} tbl=${row.tblName}")
            appendLine("  sql: ${normalise(row.sql)}")
        }
        appendLine()
        appendLine("=== PRAGMA table_info(entity) ===")
        pragmaTableInfo(driver, "entity").forEach { col ->
            appendLine(formatTableInfo(col))
        }
        appendLine()
        appendLine("=== PRAGMA index_list(entity) ===")
        pragmaIndexList(driver, "entity").forEach { idx ->
            appendLine(formatIndexList(idx))
        }
        appendLine()
        appendLine("=== PRAGMA table_info(metadata) ===")
        pragmaTableInfo(driver, "metadata").forEach { col ->
            appendLine(formatTableInfo(col))
        }
        appendLine()
        appendLine("=== PRAGMA user_version ===")
        appendLine(pragmaUserVersion(driver))
    }

    /**
     * Extract a single `=== header ===` section (header line through the next section or EOF)
     * from a [dumpSchemaSnapshot] string. Inverse of the section markers written above; lives
     * here so the snapshot format has one owner. Returns "" if the section is absent.
     */
    fun section(snapshot: String, header: String): String {
        val start = snapshot.indexOf("=== $header ===")
        if (start == -1) return ""
        val end = snapshot.indexOf("\n===", start + 1).let { if (it == -1) snapshot.length else it }
        return snapshot.substring(start, end).trim()
    }

    /**
     * Strip SQL `-- ...` line comments (which run to EOL) before collapsing whitespace,
     * otherwise newline removal would smash everything after `--` onto one comment line.
     */
    private fun normalise(s: String?): String =
        (s ?: "")
            .replace(Regex("--[^\n]*"), "")
            .replace(Regex("\\s+"), " ")
            .trim()

    private data class MasterRow(val type: String, val name: String, val tblName: String, val sql: String?)
    private data class TableInfoRow(val cid: Long, val name: String, val type: String, val notnull: Long, val dflt: String?, val pk: Long)
    private data class IndexListRow(val seq: Long, val name: String, val unique: Long, val origin: String, val partial: Long)

    private fun sqliteMaster(driver: SqlDriver): List<MasterRow> {
        val rows = mutableListOf<MasterRow>()
        driver.executeQuery(
            identifier = null,
            sql = "SELECT type, name, tbl_name, sql FROM sqlite_master " +
                  "WHERE name NOT LIKE 'sqlite_%' ORDER BY type, name",
            mapper = { c ->
                while (c.next().value) {
                    rows += MasterRow(
                        type = c.getString(0)!!,
                        name = c.getString(1)!!,
                        tblName = c.getString(2)!!,
                        sql = c.getString(3),
                    )
                }
                QueryResult.Unit
            },
            parameters = 0,
        ) {}
        return rows
    }

    private fun pragmaTableInfo(driver: SqlDriver, table: String): List<TableInfoRow> {
        val rows = mutableListOf<TableInfoRow>()
        driver.executeQuery(
            identifier = null,
            sql = "PRAGMA table_info($table)",
            mapper = { c ->
                while (c.next().value) {
                    rows += TableInfoRow(
                        cid = c.getLong(0)!!,
                        name = c.getString(1)!!,
                        type = c.getString(2)!!,
                        notnull = c.getLong(3)!!,
                        dflt = c.getString(4),
                        pk = c.getLong(5)!!,
                    )
                }
                QueryResult.Unit
            },
            parameters = 0,
        ) {}
        return rows.sortedBy { it.name }
    }

    private fun pragmaIndexList(driver: SqlDriver, table: String): List<IndexListRow> {
        val rows = mutableListOf<IndexListRow>()
        driver.executeQuery(
            identifier = null,
            sql = "PRAGMA index_list($table)",
            mapper = { c ->
                while (c.next().value) {
                    rows += IndexListRow(
                        seq = c.getLong(0)!!,
                        name = c.getString(1)!!,
                        unique = c.getLong(2)!!,
                        origin = c.getString(3) ?: "",
                        partial = c.getLong(4) ?: 0L,
                    )
                }
                QueryResult.Unit
            },
            parameters = 0,
        ) {}
        return rows.sortedBy { it.name }
    }

    private fun pragmaUserVersion(driver: SqlDriver): Long {
        var v = -1L
        driver.executeQuery(
            identifier = null,
            sql = "PRAGMA user_version",
            mapper = { c ->
                c.next().value
                v = c.getLong(0)!!
                QueryResult.Unit
            },
            parameters = 0,
        ) {}
        return v
    }

    private fun formatTableInfo(c: TableInfoRow): String =
        "cid=${c.cid} name=${c.name} type=${c.type} notnull=${c.notnull} default=${c.dflt ?: "null"} pk=${c.pk}"

    private fun formatIndexList(i: IndexListRow): String =
        "seq=${i.seq} name=${i.name} unique=${i.unique} origin=${i.origin} partial=${i.partial}"
}
