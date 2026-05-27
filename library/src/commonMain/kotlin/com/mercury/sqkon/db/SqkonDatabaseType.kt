package com.mercury.sqkon.db

/**
 * Where the database lives. Replaces the eygraber `AndroidxSqliteDatabaseType` in the public API.
 */
sealed interface SqkonDatabaseType {
    /** In-memory database; dropped when the driver closes. Single connection (no reader pool). */
    data object Memory : SqkonDatabaseType
    /** File-backed database at [path]. */
    data class FileBacked(val path: String) : SqkonDatabaseType
}
