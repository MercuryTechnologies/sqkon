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
 * (WAL, NORMAL, 4 reader connections), with two intentional differences: every connection now
 * opens with `SQLITE_OPEN_FULLMUTEX` on JVM too (previously implicit only on Android), and a
 * non-zero `busy_timeout` (3000 ms) is applied for WAL-contention hardening (was SQLite's
 * fail-immediately default of 0).
 */
class SqkonDriverConfig(
    val journalMode: SqkonJournalMode = SqkonJournalMode.WAL,
    val sync: SqkonSync = SqkonSync.NORMAL,
    /** Reader connections in the pool (file-backed only; Memory uses a single connection). */
    val readerConnections: Int = 4,
    /** Per-connection prepared-statement LRU size. */
    val statementCacheSize: Int = 25,
    /**
     * `PRAGMA busy_timeout` in milliseconds, applied to every connection. Standard WAL hardening:
     * under WAL a writer can transiently hit `SQLITE_BUSY` during checkpoint, and a second
     * driver/process on the same file fails `BEGIN IMMEDIATE` immediately without it. Set to `0`
     * to use SQLite's default (fail immediately, no wait).
     */
    val busyTimeoutMillis: Long = 3_000,
)
