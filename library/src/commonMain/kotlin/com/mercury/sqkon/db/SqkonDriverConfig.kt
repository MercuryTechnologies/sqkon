package com.mercury.sqkon.db

/** SQLite journal mode. WAL is the default and required for the reader/writer pool. */
enum class SqkonJournalMode(internal val pragma: String) {
    WAL("WAL"),
    DELETE("DELETE"),
    TRUNCATE("TRUNCATE"),
    PERSIST("PERSIST"),
    MEMORY("MEMORY"),
    OFF("OFF"),
}

/** SQLite `synchronous` setting. NORMAL is safe + fast under WAL. */
enum class SqkonSync(internal val pragma: String) {
    OFF("OFF"),
    NORMAL("NORMAL"),
    FULL("FULL"),
    EXTRA("EXTRA"),
}

/**
 * Tunables for the native androidx.sqlite-backed driver. Defaults match the pre-3.0 behavior
 * (WAL, NORMAL, 4 reader connections), except every connection now opens with
 * `SQLITE_OPEN_FULLMUTEX` on JVM too (previously implicit only on Android).
 */
class SqkonDriverConfig(
    val journalMode: SqkonJournalMode = SqkonJournalMode.WAL,
    val sync: SqkonSync = SqkonSync.NORMAL,
    /** Reader connections in the pool (file-backed only; Memory uses a single connection). */
    val readerConnections: Int = 4,
    /** Per-connection prepared-statement LRU size. */
    val statementCacheSize: Int = 25,
)
