# Sqkon Phase 6 — Direct `androidx.sqlite` driver (drop SQLDelight `SqlDriver`)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended — this is a large, interconnected cutover) or superpowers:executing-plans. Steps use checkbox (`- [ ]`) syntax.

**Linear ticket:** [MOB-3293](https://linear.app/mercury/issue/MOB-3293/sqkon-migration-phase-6-direct-androidxsqlite-driver-skip-sqldelight)
**Branch:** new `worktree-phase-6` off `main` (Phase 5 merged as `0255188`).
**Final plan location:** on approval, move to `docs/superpowers/plans/2026-05-26-mob-3293-direct-androidx-sqlite-driver.md`.

**Goal:** Replace the SQLDelight-backed `SqlDelightSqkonDriver` with a hand-rolled `AndroidxSqkonDriver` that talks straight to `androidx.sqlite.SQLiteConnection`/`SQLiteStatement`, including a connection pool (1 writer + N readers), a per-connection prepared-statement cache, a transaction manager (afterCommit/afterRollback + nesting), and a listener registry. Flip `DriverFactory.createDriver()` from `SqlDriver` → `SqkonDriver`, take `SqkonTransacter`/`EntityQueries`/`MetadataQueries` off SQLDelight's `TransacterImpl`, delete the `internal/sqldelight/` bridge, and replace the eygraber `AndroidxSqliteDatabaseType` in the public factories with a Sqkon-owned `SqkonDatabaseType`. The eygraber + SQLDelight-runtime jars stay on the classpath but unreferenced — **Phase 7 (MOB-3294)** removes them.

**Architecture:** Port eygraber's `sqldelight-androidx-driver` (v0.0.17, Apache-2.0 — sources extracted at `/tmp/eygraber_extract/`) into a new `com.mercury.sqkon.db.internal.androidx` package, retargeted from SQLDelight's `SqlDriver`/`SqlCursor`/`SqlPreparedStatement`/`Query.Listener`/`Transacter.Transaction` onto Sqkon's existing internal interfaces (`SqkonDriver`/`SqkonCursor`/`SqkonStatement`/`SqkonDriver.Listener`/`SqkonTransaction`). The transaction lifecycle (BEGIN/COMMIT/ROLLBACK + `afterCommit`/`afterRollback` queues + nested-tx tracking + `RollbackException`) currently provided by SQLDelight's `TransacterImpl` is reimplemented inside the driver + a rewritten `SqkonTransacter`.

**Tech Stack:** Kotlin Multiplatform (commonMain + jvmMain/androidMain actuals; iosMain stub). `androidx.sqlite:sqlite` (KMP interfaces, commonMain) + `androidx.sqlite:sqlite-bundled` (`BundledSQLiteDriver`, jvm/android actuals). `kotlinx.coroutines` (`Mutex`, `Channel`, `runBlocking` via an expect bridge). `kotlin.test` + `runTest` + Turbine. **Decision (this session): breaking change, `feat!:`, rides the still-open `release: 3.0.0` PR #56 — the whole migration is one major.**

---

## Context

Phases 0–5 are merged. Phase 3 introduced the internal `SqkonDriver`/`SqkonStatement`/`SqkonCursor`/`SqkonTransaction` abstraction; Phase 5 moved the public transaction API to `SqkonTransactionScope`. Today every query still flows: `EntityQueries`/`MetadataQueries` → `sqkonDriver: SqlDelightSqkonDriver(sqlDriver)` → SQLDelight `SqlDriver` → eygraber `AndroidxSqliteDriver` → `androidx.sqlite`. `EntityQueries`/`MetadataQueries`/`SqkonTransacter` all extend SQLDelight's `TransacterImpl`, and `DriverFactory.createDriver()` returns a SQLDelight `SqlDriver`.

Phase 6 removes the two middle layers: a single `AndroidxSqkonDriver` implements `SqkonDriver` directly over `androidx.sqlite`. This is the implementation cutover that lets Phase 7 delete the SQLDelight + eygraber dependencies entirely. iOS stays a `TODO()` stub (it already is); only the `expect`/`actual` signatures must keep compiling.

---

## Current state (verified file:line)

**Stable internal interfaces (the contract the new driver must satisfy — do NOT change):**
- `internal/SqkonDriver.kt:8-37` — `executeUpdate(identifier:Int?, sql, parameters:Int, binders:(SqkonStatement.()->Unit)?): Long`; `<R> executeQuery(identifier, sql, parameters, binders, mapper:(SqkonCursor)->R): R`; `addListener(vararg queryKeys, listener)`, `removeListener(...)`, `notifyListeners(vararg queryKeys)`; `newTransaction(): SqkonTransaction`; `currentTransaction(): SqkonTransaction?`; `close()`; `fun interface Listener { fun queryResultsChanged() }`.
- `internal/SqkonStatement.kt:3-9` — `bindBytes/bindBoolean/bindDouble/bindLong/bindString(index, value?)`.
- `internal/SqkonCursor.kt:3-10` — `next():Boolean`, `getString/getLong/getBytes/getDouble/getBoolean(index)`.
- `internal/SqkonQuery.kt`, `internal/SqkonFlowQuery.kt` — `SqkonQuery` + `asFlow()/mapToList/mapToOne`. **Unchanged** — they only touch `SqkonDriver`/`SqkonCursor`.
- `internal/ListenerIdentityMap.kt` — bridges Sqkon listener ↔ delegate identity. Reused by `EntityQueries`/`MetadataQueries` (`DriverBackedSqkonQuery`).
- `QueryExt.kt` — `AutoIncrementSqlPreparedStatement` (wraps `SqkonStatement`, **keep**) + `toSqkonQuery()` (wraps a SQLDelight `Query`, **dead after cutover — remove**, verify with grep).

**Layers being replaced/removed:**
- `internal/sqldelight/` (whole dir): `SqlDelightSqkonDriver.kt:11-64`, `SqlDelightSqkonStatement.kt`, `SqlDelightSqkonCursor.kt`, `SqlDelightSqkonTransaction.kt`, `BridgeHelpers.kt`. **Delete in Task 12.**
- `internal/SqkonTransacter.kt:15` `open class SqkonTransacter(driver: SqlDriver) : TransacterImpl(driver)` — trxMap + `currentOutermostTransactionHash()` + `transaction`/`transactionWithResult` overrides delegating to `super` (SQLDelight). **Rewrite off `TransacterImpl` (Task 8).**
- `internal/SqkonTransaction.kt:10-13` `abstract class SqkonTransaction { afterCommit; afterRollback }`. **Extend with `enclosingTransaction` + internal `endTransaction` (Task 7).**
- `Transactions.kt` (Phase 5) — `SqlDelightTransactionScope`/`SqlDelightResultTransactionScope` wrap SQLDelight's `TransactionWithoutReturn`/`TransactionWithReturn`. **Rewrite to wrap `SqkonTransaction` (Task 8).**
- `EntityQueries.kt:16-22` / `MetadataQueries.kt:11-17` — `class …(sqlDriver: SqlDriver) : TransacterImpl(sqlDriver)` with `sqkonDriver = SqlDelightSqkonDriver(sqlDriver)`. **Take a `SqkonDriver`, extend the new `SqkonTransacter`, drop the `sqlDriver`/`TransacterImpl` (Task 9).** `EntityQueries.kt:563-565 identifier(vararg String?)` (hashCode join) is the statement-cache key fn — **keep**, the driver cache keys on this `Int`.
- `SqkonDatabaseDriver.kt:5-9` — `expect val connectionPoolSize`, `expect val defaultSqkonDispatchers`, `expect class DriverFactory { fun createDriver(): SqlDriver }`. **Flip return to `SqkonDriver` (Task 10).**
- JVM `SqkonDatabaseDriver.jvm.kt` / Android `SqkonDatabaseDriver.android.kt` — build eygraber `AndroidxSqliteDriver` (WAL, sync NORMAL, `MultipleReadersSingleWriter(walCount=connectionPoolSize)`); Android's `SqkonAndroidxSqliteConnectionFactory` opens with `SQLITE_OPEN_READWRITE or SQLITE_OPEN_CREATE or SQLITE_OPEN_FULLMUTEX`; JVM passes `BundledSQLiteDriver()` with **no explicit flags**. **Rewrite to build `AndroidxSqkonDriver`; set FULLMUTEX on BOTH platforms (Task 10).**
- `Sqkon.jvm.kt:8-24` `fun Sqkon(scope, json, type: AndroidxSqliteDatabaseType = Memory, config, dispatchers)` and `Sqkon.android.kt:28-45` `fun Sqkon(context, scope, json, dbFileName, config, dispatchers)`. **Swap `AndroidxSqliteDatabaseType` → `SqkonDatabaseType`; add `driverConfig: SqkonDriverConfig` (Task 11).**

**eygraber reference (extracted `/tmp/eygraber_extract/`, v0.0.17, Apache-2.0):**
- `commonMain/.../ConnectionPool.kt` — `AndroidxDriverConnectionPool`: writer `Mutex` (`runBlocking { lock() }` to bridge into the synchronous driver), readers via `Channel<ReaderSQLiteConnection>(UNLIMITED)` pre-populated with N lazy connections, journal-mode `ReentrantLock`. `SingleReaderWriter` when in-memory.
- `commonMain/.../AndroidxSqliteDriver.kt` — per-connection cache `HashMap<SQLiteConnection, LruCache<Int, AndroidxStatement>>` + lock; eviction = LRU on `put`; listener registry `linkedMapOf<String, MutableSet<Query.Listener>>` guarded by a `SynchronizedObject`, notify snapshots under lock then fires outside; `newTransaction()`/`currentTransaction()` via `TransactionsThreadLocal` + a `Transaction` inner class doing BEGIN/COMMIT/ROLLBACK.
- `commonMain/.../AndroidxStatement.kt` — `AndroidxPreparedStatement` (exec) + `AndroidxQuery` (results); `bind*`, `execute()`, `executeQuery(mapper)`, `reset()`, `close()`.
- `nativeMain/.../TransactionsThreadLocal.kt` — `expect class TransactionsThreadLocal`.
- `commonMain/.../LruCache.kt` — multiplatform LRU.

**Gate tests (must stay green — Phase 0 suite):**
- `StatementCacheEvictionTest` — 200 concurrent distinct query *shapes* return correct counts; 100 concurrent insert/update/delete complete cleanly, final count 0 (no FD/handle leak).
- `ConcurrencyTest.slowWriter_doesNotBlockFourParallelReaders` — 4 readers progress while a 100ms writer holds the writer connection (WAL + pool).
- `EntityQueriesNotifyTest` — write to entity A must NOT notify a listener on entity B; same-name cross-instance write DOES notify. (Keys: `ALL_ENTITIES_KEY="entity"`, `entityKey(name)="entity_$name"` at `EntityQueries.kt:567-570`.)
- `KeyValueStorageTransactionTest` (5 + Phase-5 additions) — silent rollback, nested rollback aborts enclosing, afterCommit fires only post-commit, not on rollback, nested afterCommit fires once at outermost; `transactionWithResult` returns / throws `SqkonRollbackException`.
- `KeyValueStorageMetadataPerRowTest` — `select` updates read_at async; `selectAll` flow does NOT re-emit from the read_at side-effect write (metadata listener key isolation); `count` doesn't touch read_at.
- `KeysetPagingTest.keysetPaging_emptyToPopulated_invalidates` — a listener attached during an empty load must fire on later insert (invalidation).
- `SqkonDriverRoundtripTest` — exercises `SqkonDriver` directly (create/insert/select, listener add/notify, transaction nullability). **Update its driver construction (Task 13).**
- `SchemaMigrationTest` (jvmTest) + `SqkonDatabaseDriverTest.jvm.kt`/`.android.kt` — construct the driver/`AndroidxSqliteDriver` directly; **update (Task 13).**

---

## Critical Invariants

1. **`SqkonDriver` contract is unchanged.** The new driver must match `executeUpdate`/`executeQuery`/`addListener`/`removeListener`/`notifyListeners`/`newTransaction`/`currentTransaction`/`close` exactly so `EntityQueries`/`MetadataQueries`/`SqkonQuery`/`SqkonFlowQuery` need no logic change beyond their constructor wiring.
2. **Statement-cache key = `EntityQueries.identifier(...)` `Int`.** The driver caches a prepared statement per `(connection, identifier)`. A `null` identifier ⇒ never cached (prepare-and-close each call). Eviction must `remove → execute → reset → put-back, closing the displaced statement` (eygraber pattern) — `StatementCacheEvictionTest` is the gate.
3. **One writer, N readers, WAL.** Writer guarded by a `Mutex` acquired blocking; readers drawn from a `Channel`. `BEGIN IMMEDIATE` on top-level write transactions. In-memory ⇒ single connection (reads+writes serialize). `ConcurrencyTest` is the gate.
4. **Listener keys isolate entities.** Registry is `Map<String queryKey, Set<Listener>>`; `notifyListeners(keys)` fires only listeners registered on those keys. `EntityQueriesNotifyTest` + `KeyValueStorageMetadataPerRowTest` are the gates.
5. **Transaction semantics = SQLDelight's `TransacterImpl`.** Reimplement: nested transactions share the outermost connection/transaction; `afterCommit`/`afterRollback` queue on the enclosing transaction and fire once at the outermost commit/rollback; `rollback()` throws an internal `RollbackException` caught at the outermost boundary; any other throwable rolls back and propagates. `KeyValueStorageTransactionTest` is the gate. Port the logic from SQLDelight `TransacterImpl`/`Transacter` (Apache-2.0).
6. **`currentTransaction()` is thread-confined.** Use `TransactionsThreadLocal` (expect/actual: `java.lang.ThreadLocal` on jvm/android; a simple holder on ios). Writes must run on the single-thread write dispatcher (already wired in `defaultSqkonDispatchers`).
7. **iOS stays `TODO()`.** Only the `expect`/`actual` signatures must compile. `connectionPoolSize`/dispatchers already have iOS actuals.
8. **Blocking bridge.** `runBlocking` is not in commonMain's API surface. Add `internal expect fun <T> sqkonRunBlocking(block: suspend CoroutineScope.() -> T): T` (actuals: jvm/android/ios → `kotlinx.coroutines.runBlocking`). The pool uses it to acquire the writer mutex / receive a reader from the synchronous `SqkonDriver` methods.
9. **`feat!:` riding 3.0.** Public `Sqkon(...)` signatures change (`SqkonDatabaseType`, `SqkonDriverConfig`). Merge before release PR #56 (`3.0.0`) is released so the whole migration is one major.

---

## File Structure

**New (commonMain, `com.mercury.sqkon.db`):**
- `SqkonDatabaseType.kt` — `sealed interface SqkonDatabaseType { data object Memory; data class FileBacked(val path: String) }`.
- `SqkonDriverConfig.kt` — `class SqkonDriverConfig(journalMode, sync, readerConnections=4, statementCacheSize=25)` + `enum SqkonJournalMode { WAL, DELETE, … }` + `enum SqkonSync { Off, Normal, Full }`.

**New (commonMain, `com.mercury.sqkon.db.internal.androidx`):**
- `AndroidxSqkonDriver.kt` — `internal class AndroidxSqkonDriver(connectionFactory, name, schema: SqkonSchema, config) : SqkonDriver`.
- `SqkonConnectionFactory.kt` — `internal fun interface SqkonConnectionFactory { fun open(name: String): SQLiteConnection }` (actuals supply flags).
- `SqkonConnectionPool.kt` — writer `Mutex` + reader `Channel`, journal-mode init (port `ConnectionPool.kt`).
- `AndroidxStatement.kt` — `AndroidxPreparedStatement` + `AndroidxQuery` implementing `SqkonStatement`; query exposes a `SqkonCursor` over `SQLiteStatement.step()` (port `AndroidxStatement.kt`).
- `AndroidxSqkonCursor.kt` — `SqkonCursor` over a stepped `SQLiteStatement`.
- `SqkonStatementCache.kt` — per-connection `LruCache<Int, AndroidxStatement>` + eviction.
- `LruCache.kt` — port eygraber's multiplatform LRU.
- `SqkonDriverTransaction.kt` — the driver's transaction handle (BEGIN/COMMIT/ROLLBACK + queues), implements `SqkonTransaction`.

**New (expect + actuals):**
- `internal/TransactionsThreadLocal.kt` (expect) + `…/jvmMain|androidMain|iosMain` actuals.
- `internal/SqkonRunBlocking.kt` (expect `sqkonRunBlocking`) + jvm/android/ios actuals.

**Modified:** `internal/SqkonTransaction.kt`, `internal/SqkonTransacter.kt`, `Transactions.kt`, `EntityQueries.kt`, `MetadataQueries.kt`, `SqkonDatabaseDriver.kt` (+ 4 actuals incl. new ios), `Sqkon.jvm.kt`, `Sqkon.android.kt`, `QueryExt.kt` (drop dead `toSqkonQuery`), test factories + `SchemaMigrationTest` + `SqkonDriverRoundtripTest`, `gradle/libs.versions.toml`/`library/build.gradle.kts` (move `androidx.sqlite:sqlite` to commonMain `api`/`implementation`).

**Deleted:** `internal/sqldelight/` (entire dir).

---

## Tasks

### Task 1: Public config + database-type value classes

**Files:** Create `library/src/commonMain/kotlin/com/mercury/sqkon/db/SqkonDatabaseType.kt`, `…/SqkonDriverConfig.kt`

- [ ] **Step 1: `SqkonDatabaseType.kt`**

```kotlin
package com.mercury.sqkon.db

/** Where the database lives. Replaces the eygraber `AndroidxSqliteDatabaseType` in the public API. */
sealed interface SqkonDatabaseType {
    /** In-memory database; dropped when the driver closes. Single connection (no reader pool). */
    data object Memory : SqkonDatabaseType
    /** File-backed database at [path]. */
    data class FileBacked(val path: String) : SqkonDatabaseType
}
```

- [ ] **Step 2: `SqkonDriverConfig.kt`**

```kotlin
package com.mercury.sqkon.db

/** SQLite journal mode. WAL is the default and required for the reader/writer pool. */
enum class SqkonJournalMode { WAL, DELETE, TRUNCATE, PERSIST, MEMORY, OFF }

/** SQLite `synchronous` setting. NORMAL is safe + fast under WAL. */
enum class SqkonSync { OFF, NORMAL, FULL, EXTRA }

/**
 * Tunables for [com.mercury.sqkon.db.internal.androidx.AndroidxSqkonDriver]. Defaults match the
 * pre-3.0 behavior (WAL, NORMAL, 4 reader connections), except every connection now opens with
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
```

- [ ] **Step 3:** `./gradlew :library:compileCommonMainKotlinMetadata` → BUILD SUCCESSFUL.
- [ ] **Step 4:** Commit `feat(arch): public SqkonDatabaseType + SqkonDriverConfig (MOB-3293)`.

---

### Task 2: `sqkonRunBlocking` + `TransactionsThreadLocal` expect/actuals

**Files:** Create `internal/SqkonRunBlocking.kt` (commonMain expect) + jvm/android/ios actuals; `internal/TransactionsThreadLocal.kt` (commonMain expect) + jvm/android/ios actuals.

- [ ] **Step 1: commonMain expects**

`internal/SqkonRunBlocking.kt`:
```kotlin
package com.mercury.sqkon.db.internal

import kotlinx.coroutines.CoroutineScope

/** Bridges the suspending pool primitives into the synchronous SqkonDriver. Not available on js/wasm. */
internal expect fun <T> sqkonRunBlocking(block: suspend CoroutineScope.() -> T): T
```

`internal/TransactionsThreadLocal.kt`:
```kotlin
package com.mercury.sqkon.db.internal

/** Thread-confined holder for the active driver transaction. */
internal expect class TransactionsThreadLocal() {
    fun get(): SqkonDriverTransaction?
    fun set(value: SqkonDriverTransaction?)
}
```
(`SqkonDriverTransaction` is created in Task 7; this file compiles after Task 7 — keep them in the same commit, or stub the type now.)

- [ ] **Step 2: jvm + android actuals** (identical bodies; one file each)

`jvmMain/.../internal/SqkonRunBlocking.jvm.kt` and `androidMain/.../internal/SqkonRunBlocking.android.kt`:
```kotlin
package com.mercury.sqkon.db.internal

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking

internal actual fun <T> sqkonRunBlocking(block: suspend CoroutineScope.() -> T): T =
    runBlocking { block() }
```

`jvmMain`/`androidMain` `TransactionsThreadLocal.*.kt`:
```kotlin
package com.mercury.sqkon.db.internal

internal actual class TransactionsThreadLocal actual constructor() {
    private val threadLocal = ThreadLocal<SqkonDriverTransaction?>()
    actual fun get(): SqkonDriverTransaction? = threadLocal.get()
    actual fun set(value: SqkonDriverTransaction?) = threadLocal.set(value)
}
```

- [ ] **Step 3: ios actuals**

`iosMain/.../internal/SqkonRunBlocking.ios.kt`:
```kotlin
package com.mercury.sqkon.db.internal

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking

internal actual fun <T> sqkonRunBlocking(block: suspend CoroutineScope.() -> T): T =
    runBlocking { block() }
```

`iosMain` `TransactionsThreadLocal.ios.kt` — iOS write dispatcher is single-threaded, but keep correctness with a thread-local:
```kotlin
package com.mercury.sqkon.db.internal

import kotlin.native.concurrent.ThreadLocal

internal actual class TransactionsThreadLocal actual constructor() {
    private companion object Holder { @ThreadLocal var current: SqkonDriverTransaction? = null }
    actual fun get(): SqkonDriverTransaction? = Holder.current
    actual fun set(value: SqkonDriverTransaction?) { Holder.current = value }
}
```
(If `@ThreadLocal` on a companion var proves awkward in compile, fall back to a single non-thread-local `var` — iOS serializes writes on one dispatcher thread, and iOS `createDriver()` is `TODO()` so this is never exercised at runtime in Phase 6.)

- [ ] **Step 4:** Defer compile to Task 7 (depends on `SqkonDriverTransaction`). Commit with Task 7.

---

### Task 3: Port `LruCache`

**Files:** Create `internal/androidx/LruCache.kt`

- [ ] **Step 1:** Copy `/tmp/eygraber_extract/commonMain/.../LruCache.kt` to `library/src/commonMain/kotlin/com/mercury/sqkon/db/internal/androidx/LruCache.kt`. Change `package` to `com.mercury.sqkon.db.internal.androidx`, mark the class `internal`, and **preserve the Apache-2.0 license header + attribution comment** (`// Adapted from com.eygraber:sqldelight-androidx-driver 0.0.17 (Apache-2.0)`). It is a generic `LruCache<K, V>` with `get`, `put` (returns displaced value), `remove`, `evictAll`, and an `onEntryEvicted`/size hook — keep its API intact.
- [ ] **Step 2:** `./gradlew :library:compileCommonMainKotlinMetadata` → BUILD SUCCESSFUL.
- [ ] **Step 3:** Commit `chore: vendor LruCache from eygraber driver (MOB-3293)`.

---

### Task 4: `AndroidxStatement` (prepared + query) + `AndroidxSqkonCursor`

**Files:** Create `internal/androidx/AndroidxStatement.kt`, `internal/androidx/AndroidxSqkonCursor.kt`

- [ ] **Step 1: Cursor** — `AndroidxSqkonCursor` wraps a stepped `androidx.sqlite.SQLiteStatement`, implementing `SqkonCursor`:

```kotlin
package com.mercury.sqkon.db.internal.androidx

import androidx.sqlite.SQLiteStatement
import com.mercury.sqkon.db.internal.SqkonCursor

internal class AndroidxSqkonCursor(private val statement: SQLiteStatement) : SqkonCursor {
    override fun next(): Boolean = statement.step()
    override fun getString(index: Int): String? =
        if (statement.isNull(index)) null else statement.getText(index)
    override fun getLong(index: Int): Long? =
        if (statement.isNull(index)) null else statement.getLong(index)
    override fun getBytes(index: Int): ByteArray? =
        if (statement.isNull(index)) null else statement.getBlob(index)
    override fun getDouble(index: Int): Double? =
        if (statement.isNull(index)) null else statement.getDouble(index)
    override fun getBoolean(index: Int): Boolean? =
        if (statement.isNull(index)) null else statement.getLong(index) == 1L
}
```

- [ ] **Step 2: Statements** — port `/tmp/eygraber_extract/commonMain/.../AndroidxStatement.kt`, retargeting `SqlPreparedStatement`→`SqkonStatement` and the cursor/mapper to `SqkonCursor`. Two classes implementing `SqkonStatement`:

```kotlin
package com.mercury.sqkon.db.internal.androidx

import androidx.sqlite.SQLiteStatement
import com.mercury.sqkon.db.internal.SqkonCursor
import com.mercury.sqkon.db.internal.SqkonStatement

internal sealed interface AndroidxStatement : SqkonStatement {
    fun reset()
    fun close()
}

/** DML/DDL — no rows returned; [execute] returns affected-row count via changes(). */
internal class AndroidxPreparedStatement(
    private val statement: SQLiteStatement,
) : AndroidxStatement {
    override fun bindBytes(index: Int, value: ByteArray?) =
        if (value == null) statement.bindNull(index + 1) else statement.bindBlob(index + 1, value)
    override fun bindLong(index: Int, value: Long?) =
        if (value == null) statement.bindNull(index + 1) else statement.bindLong(index + 1, value)
    override fun bindDouble(index: Int, value: Double?) =
        if (value == null) statement.bindNull(index + 1) else statement.bindDouble(index + 1, value)
    override fun bindString(index: Int, value: String?) =
        if (value == null) statement.bindNull(index + 1) else statement.bindText(index + 1, value)
    override fun bindBoolean(index: Int, value: Boolean?) =
        if (value == null) statement.bindNull(index + 1) else statement.bindLong(index + 1, if (value) 1L else 0L)

    /** Step to completion; return rows changed (caller reads `changes()` on the connection). */
    fun execute(): Long { while (statement.step()) { /* drain */ }; return 0L }
    override fun reset() = statement.reset()
    override fun close() = statement.close()
}

/** SELECT — [executeQuery] maps the stepped cursor. */
internal class AndroidxQuery(
    private val statement: SQLiteStatement,
) : AndroidxStatement {
    override fun bindBytes(index: Int, value: ByteArray?) =
        if (value == null) statement.bindNull(index + 1) else statement.bindBlob(index + 1, value)
    override fun bindLong(index: Int, value: Long?) =
        if (value == null) statement.bindNull(index + 1) else statement.bindLong(index + 1, value)
    override fun bindDouble(index: Int, value: Double?) =
        if (value == null) statement.bindNull(index + 1) else statement.bindDouble(index + 1, value)
    override fun bindString(index: Int, value: String?) =
        if (value == null) statement.bindNull(index + 1) else statement.bindText(index + 1, value)
    override fun bindBoolean(index: Int, value: Boolean?) =
        if (value == null) statement.bindNull(index + 1) else statement.bindLong(index + 1, if (value) 1L else 0L)

    fun <R> executeQuery(mapper: (SqkonCursor) -> R): R = mapper(AndroidxSqkonCursor(statement))
    override fun reset() = statement.reset()
    override fun close() = statement.close()
}
```

**Note:** `androidx.sqlite` bind indices are **1-based**; Sqkon/SQLDelight binders are **0-based** (see `AutoIncrementSqlPreparedStatement`). Hence `index + 1` everywhere. Affected-row count: the driver reads it via `SELECT changes()` after `execute()` (Task 7), since `SQLiteStatement` has no direct count.

- [ ] **Step 3:** `./gradlew :library:compileCommonMainKotlinMetadata` → BUILD SUCCESSFUL (note: `androidx.sqlite:sqlite` must be a commonMain dependency — wired in Task 10 Step 0; if compile fails on `androidx.sqlite.*` imports, do that gradle step first).
- [ ] **Step 4:** Commit `feat(arch): androidx.sqlite statement + cursor adapters (MOB-3293)`.

---

### Task 5: Connection pool

**Files:** Create `internal/androidx/SqkonConnectionFactory.kt`, `internal/androidx/SqkonConnectionPool.kt`

- [ ] **Step 1: factory interface**

```kotlin
package com.mercury.sqkon.db.internal.androidx

import androidx.sqlite.SQLiteConnection

/** Opens a connection by file name. Actuals supply the open flags (incl. SQLITE_OPEN_FULLMUTEX). */
internal fun interface SqkonConnectionFactory {
    fun open(name: String): SQLiteConnection
}
```

- [ ] **Step 2: pool** — port `/tmp/eygraber_extract/commonMain/.../ConnectionPool.kt`, retargeting to `SqkonConnectionFactory`/`SqkonDriverConfig` and using `sqkonRunBlocking` instead of `runBlocking`. Single writer `Mutex`, readers in a `Channel<SQLiteConnection>(UNLIMITED)` pre-populated with `config.readerConnections` lazily-opened connections; `Memory`/single-connection mode reuses the writer connection for reads. Preserve attribution. Shape:

```kotlin
package com.mercury.sqkon.db.internal.androidx

import androidx.sqlite.SQLiteConnection
import com.mercury.sqkon.db.SqkonDriverConfig
import com.mercury.sqkon.db.internal.sqkonRunBlocking
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex

// Adapted from com.eygraber:sqldelight-androidx-driver 0.0.17 (Apache-2.0)
internal class SqkonConnectionPool(
    private val factory: SqkonConnectionFactory,
    private val name: String,
    private val isMemory: Boolean,
    private val config: SqkonDriverConfig,
) : AutoCloseable {
    private val writerMutex = Mutex()
    private val writer: SQLiteConnection by lazy { factory.open(name).also(::applyPragmas) }
    private val readerChannel = Channel<SQLiteConnection>(Channel.UNLIMITED)
    private val readerCount = if (isMemory) 0 else config.readerConnections
    private var readersPopulated = false

    private fun applyPragmas(c: SQLiteConnection) {
        c.execSql("PRAGMA journal_mode = ${config.journalMode.name}")
        c.execSql("PRAGMA synchronous = ${config.sync.name}")
    }

    fun acquireWriter(): SQLiteConnection { sqkonRunBlocking { writerMutex.lock() }; return writer }
    fun releaseWriter() { writerMutex.unlock() }

    fun acquireReader(): SQLiteConnection {
        if (readerCount == 0) { sqkonRunBlocking { writerMutex.lock() }; return writer }
        ensureReaders()
        return sqkonRunBlocking { readerChannel.receive() }
    }
    fun releaseReader(c: SQLiteConnection) {
        if (readerCount == 0) writerMutex.unlock() else sqkonRunBlocking { readerChannel.send(c) }
    }

    private fun ensureReaders() {
        if (readersPopulated) return
        // open the writer first so PRAGMA journal_mode=WAL is set before readers attach
        applyPragmas(writer)
        repeat(readerCount) { readerChannel.trySend(factory.open(name).also(::applyPragmas)) }
        readersPopulated = true
    }

    override fun close() {
        if (readersPopulated) repeat(readerCount) { readerChannel.tryReceive().getOrNull()?.close() }
        writer.close()
    }
}
```
(`SQLiteConnection.execSql(...)` is the androidx one-shot exec extension; if not present, use `prepare(sql).use { it.step() }`. Match eygraber's exact reader (re)population + journal-mode-lock logic when porting — the sketch above is the shape, not a substitute for the source.)

- [ ] **Step 3:** `./gradlew :library:compileCommonMainKotlinMetadata` → BUILD SUCCESSFUL.
- [ ] **Step 4:** Commit `feat(arch): SqkonConnectionPool (writer mutex + reader channel) (MOB-3293)`.

---

### Task 6: Per-connection statement cache

**Files:** Create `internal/androidx/SqkonStatementCache.kt`

- [ ] **Step 1:** A cache holding one `LruCache<Int, AndroidxStatement>` per `SQLiteConnection`. Key = `EntityQueries.identifier(...)` `Int`. Replicate eygraber's `getOrPrepare`: on hit, `reset()` then return; on miss, prepare + `put` and **close the displaced entry**. `null` identifier ⇒ prepare a fresh statement the caller closes (never cached).

```kotlin
package com.mercury.sqkon.db.internal.androidx

import androidx.sqlite.SQLiteConnection
import com.mercury.sqkon.db.SqkonDriverConfig

internal class SqkonStatementCache(private val config: SqkonDriverConfig) {
    private val perConnection = HashMap<SQLiteConnection, LruCache<Int, AndroidxStatement>>()

    /** Returns a ready (reset) statement. If [identifier] is null the statement is uncached. */
    fun <R> withStatement(
        connection: SQLiteConnection,
        identifier: Int?,
        sql: String,
        isQuery: Boolean,
        block: (AndroidxStatement) -> R,
    ): R {
        if (identifier == null) {
            val stmt = prepare(connection, sql, isQuery)
            try { return block(stmt) } finally { stmt.close() }
        }
        val cache = perConnection.getOrPut(connection) {
            LruCache(config.statementCacheSize) { _, evicted -> evicted.close() }
        }
        val stmt = cache.remove(identifier) ?: prepare(connection, sql, isQuery)
        val result = block(stmt)
        stmt.reset()
        cache.put(identifier, stmt)?.close() // close any entry displaced by the put
        return result
    }

    private fun prepare(c: SQLiteConnection, sql: String, isQuery: Boolean): AndroidxStatement =
        if (isQuery) AndroidxQuery(c.prepare(sql)) else AndroidxPreparedStatement(c.prepare(sql))

    fun evictAll() { perConnection.values.forEach { it.evictAll() }; perConnection.clear() }
}
```
(Match eygraber's exact eviction ordering — remove → use → reset → put-back, closing displaced — which `StatementCacheEvictionTest` pins. Adjust the `LruCache` ctor/`onEvicted` shape to the vendored class from Task 3.)

- [ ] **Step 2:** `./gradlew :library:compileCommonMainKotlinMetadata` → BUILD SUCCESSFUL.
- [ ] **Step 3:** Commit `feat(arch): per-connection prepared-statement cache (MOB-3293)`.

---

### Task 7: Transaction handle + extend `SqkonTransaction`

**Files:** Modify `internal/SqkonTransaction.kt`; create `internal/androidx/SqkonDriverTransaction.kt`

- [ ] **Step 1:** Extend `SqkonTransaction` with the structure the manager needs (port from SQLDelight `Transacter.Transaction`):

```kotlin
package com.mercury.sqkon.db.internal

/** Sqkon-owned transaction handle. Models SQLDelight's Transacter.Transaction lifecycle. */
internal abstract class SqkonTransaction {
    abstract val enclosingTransaction: SqkonTransaction?
    private val postCommit = mutableListOf<() -> Unit>()
    private val postRollback = mutableListOf<() -> Unit>()
    var successful: Boolean = false
    var childrenSuccessful: Boolean = true

    fun afterCommit(block: () -> Unit) { postCommit.add(block) }
    fun afterRollback(block: () -> Unit) { postRollback.add(block) }

    /** Called by the manager. Runs queued hooks. */
    internal fun runAfterCommit() = postCommit.forEach { it() }
    internal fun runAfterRollback() = postRollback.forEach { it() }
    internal fun takeChildHooks(child: SqkonTransaction) {
        postCommit.addAll(child.drainCommit()); postRollback.addAll(child.drainRollback())
    }
    private fun drainCommit() = postCommit.toList().also { postCommit.clear() }
    private fun drainRollback() = postRollback.toList().also { postRollback.clear() }
}
```

- [ ] **Step 2:** `SqkonDriverTransaction` — the concrete handle bound to the writer connection; issues `BEGIN IMMEDIATE`/`COMMIT`/`ROLLBACK` via the driver:

```kotlin
package com.mercury.sqkon.db.internal.androidx

import com.mercury.sqkon.db.internal.SqkonTransaction

internal class SqkonDriverTransaction(
    override val enclosingTransaction: SqkonTransaction?,
    private val onTopLevelEnd: () -> Unit, // releases the writer connection
) : SqkonTransaction() {
    var beganOnConnection = false
    fun markTopLevelEnded() = onTopLevelEnd()
}
```

(The actual BEGIN/COMMIT/ROLLBACK SQL is issued by `AndroidxSqkonDriver.newTransaction()` / the transacter — see Tasks 8 & 9. Keep this handle minimal; the manager owns SQL.)

- [ ] **Step 3:** Now Task 2's `TransactionsThreadLocal` (typed on `SqkonDriverTransaction`) compiles. `./gradlew :library:compileCommonMainKotlinMetadata :library:compileKotlinJvm` → BUILD SUCCESSFUL.
- [ ] **Step 4:** Commit `feat(arch): SqkonTransaction lifecycle + driver transaction handle (MOB-3293)` (include Task 2 files).

---

### Task 8: Assemble `AndroidxSqkonDriver` + rewrite `SqkonTransacter` + `Transactions.kt`

**Files:** Create `internal/androidx/AndroidxSqkonDriver.kt`; rewrite `internal/SqkonTransacter.kt`, `Transactions.kt`

- [ ] **Step 1: `AndroidxSqkonDriver`** — implements `SqkonDriver` using the pool + cache + listener registry + thread-local transaction. Port the listener registry + notify from eygraber (`linkedMapOf<String, MutableSet<SqkonDriver.Listener>>` + a `kotlinx.atomicfu.locks.SynchronizedObject`/`reentrant lock`; snapshot under lock, fire outside). Transaction SQL lives here:

```kotlin
package com.mercury.sqkon.db.internal.androidx

import com.mercury.sqkon.db.SqkonDatabaseType
import com.mercury.sqkon.db.SqkonDriverConfig
import com.mercury.sqkon.db.internal.*
import com.mercury.sqkon.db.internal.schema.SqkonSchema

internal class AndroidxSqkonDriver(
    factory: SqkonConnectionFactory,
    name: String,
    isMemory: Boolean,
    schema: SqkonSchema,
    config: SqkonDriverConfig,
) : SqkonDriver {
    private val pool = SqkonConnectionPool(factory, name, isMemory, config)
    private val cache = SqkonStatementCache(config)
    private val transactions = TransactionsThreadLocal()
    private val listenersLock = /* kotlinx.atomicfu.locks.SynchronizedObject() */ Any()
    private val listeners = linkedMapOf<String, MutableSet<SqkonDriver.Listener>>()

    init {
        // Run schema create/migrate on the writer connection before first use.
        // (Port eygraber's version-check + migrate gate; calls schema.create(this) for v0.)
        ensureSchema(schema, config)
        sqliteVersionGuard()
    }

    override fun executeUpdate(identifier: Int?, sql: String, parameters: Int,
                              binders: (SqkonStatement.() -> Unit)?): Long {
        val tx = transactions.get()
        val connection = tx?.let { writerConn } ?: pool.acquireWriter()
        try {
            return cache.withStatement(connection, identifier, sql, isQuery = false) { stmt ->
                binders?.invoke(stmt)
                (stmt as AndroidxPreparedStatement).execute()
            }
        } finally { if (tx == null) pool.releaseWriter() }
    }

    override fun <R> executeQuery(identifier: Int?, sql: String, parameters: Int,
                                 binders: (SqkonStatement.() -> Unit)?, mapper: (SqkonCursor) -> R): R {
        val tx = transactions.get()
        val connection = if (tx != null) writerConn else pool.acquireReader()
        try {
            return cache.withStatement(connection, identifier, sql, isQuery = true) { stmt ->
                binders?.invoke(stmt)
                (stmt as AndroidxQuery).executeQuery(mapper)
            }
        } finally { if (tx == null) pool.releaseReader(connection) }
    }

    override fun addListener(vararg queryKeys: String, listener: SqkonDriver.Listener) =
        synchronizedListeners { queryKeys.forEach { listeners.getOrPut(it) { linkedSetOf() }.add(listener) } }
    override fun removeListener(vararg queryKeys: String, listener: SqkonDriver.Listener) =
        synchronizedListeners { queryKeys.forEach { listeners[it]?.remove(listener) } }
    override fun notifyListeners(vararg queryKeys: String) {
        val snapshot = synchronizedListeners { queryKeys.flatMap { listeners[it].orEmpty() }.toSet() }
        snapshot.forEach { it.queryResultsChanged() }
    }

    override fun newTransaction(): SqkonTransaction { /* see Step 2 — driven by SqkonTransacter */ }
    override fun currentTransaction(): SqkonTransaction? = transactions.get()
    override fun close() { cache.evictAll(); pool.close() }
}
```

(The driver owns: acquire-writer-for-the-whole-transaction, `BEGIN IMMEDIATE` on top-level `newTransaction()`, `COMMIT`/`ROLLBACK` on end, releasing the writer when the outermost transaction ends, and `transactions.set(...)`. Port this precisely from eygraber's `AndroidxSqliteDriver.newTransaction()`/`Transaction.endTransaction()`, substituting `SqkonDriverTransaction`. Affected-row count for `executeUpdate` = read `changes()` once after `execute()` if a caller needs it; current callers ignore the return except inserts — verify against `EntityQueries`.)

- [ ] **Step 2: rewrite `SqkonTransacter`** off `TransacterImpl` — drive `driver.newTransaction()`/`currentTransaction()` and reproduce `TransacterImpl.transaction`/`transactionWithResult` (nested, RollbackException, hook propagation). Port from SQLDelight's `TransacterImpl` (Apache-2.0). **Reuse the existing `SqkonRollbackException`** from Phase 5 (`SqkonTransactionScope.kt`, public with internal ctor) — do NOT redeclare it; relax its ctor to `internal` visibility callable here if needed. The commit/rollback + hook firing live on `SqkonTransaction` (Task 7: `successful`, `runAfterCommit`, `runAfterRollback`, `takeChildHooks`) driven by the driver's `newTransaction()`/transaction-end logic (Task 8 Step 1) — the manager just runs the body, marks `successful`, and lets the driver `end` the transaction in `finally`.

```kotlin
package com.mercury.sqkon.db.internal

open class SqkonTransacter(private val driver: SqkonDriver) {

    fun transaction(noEnclosing: Boolean = false, body: SqkonTransactionScopeReceiver.() -> Unit) {
        transactionWithWrapper<Unit>(noEnclosing) { body() }
    }
    fun <R> transactionWithResult(noEnclosing: Boolean = false, body: SqkonTransactionScopeReceiver.() -> R): R =
        transactionWithWrapper(noEnclosing, body)

    private fun <R> transactionWithWrapper(noEnclosing: Boolean, body: SqkonTransactionScopeReceiver.() -> R): R {
        val enclosing = driver.currentTransaction()
        check(enclosing == null || !noEnclosing) { "Already in a transaction" }
        val transaction = driver.newTransaction() // BEGIN IMMEDIATE if top-level; sets thread-local
        val scope = SqkonTransactionScopeReceiver(transaction, this)
        var thrownException: Throwable? = null
        var returnValue: R? = null
        try {
            returnValue = scope.body()
            transaction.successful = true            // SqkonTransaction.successful (Task 7)
        } catch (e: SqkonRollbackException) {
            // explicit rollback: leave successful=false
        } catch (e: Throwable) {
            thrownException = e
        } finally {
            // transaction.endTransaction(): if outermost, COMMIT when successful && childrenSuccessful
            // else ROLLBACK, then fire runAfterCommit()/runAfterRollback(); if nested, propagate hooks
            // to the enclosing via takeChildHooks(). endTransaction() is an internal method on the
            // driver's SqkonDriverTransaction (Task 7) that issues the COMMIT/ROLLBACK SQL + releases
            // the writer connection. Port end-logic from eygraber/TransacterImpl.
            transaction.endTransaction()
        }
        if (thrownException != null) throw thrownException
        @Suppress("UNCHECKED_CAST")
        return if (enclosing == null) (returnValue as R) else (returnValue as R)
    }

    /** Outermost transaction identity hash, for the updateWriteAt dedup. */
    internal fun currentOutermostTransactionHash(): Int {
        var t = driver.currentTransaction() ?: error("not in a transaction")
        while (t.enclosingTransaction != null) t = t.enclosingTransaction!!
        return t.hashCode()
    }
}
```
(This is the **shape**; port the exact commit/rollback/hook-propagation + `RollbackException` handling from SQLDelight `TransacterImpl.transactionWithWrapper`. `SqkonTransactionScopeReceiver` is the public `SqkonTransactionScope` impl — Step 3. `currentOutermostTransactionHash` now walks `enclosingTransaction` instead of `trxMap`, so the old `trxMap` is deleted.)

- [ ] **Step 3: rewrite `Transactions.kt`** — the public extensions are unchanged in signature; the internal scope impl now wraps `SqkonTransaction` (not SQLDelight). Make `SqkonTransactionScope`'s impl a single class (mirrors the Phase-5 review outcome: `rollback()` throws `SqkonRollbackException`, the Unit `transaction` swallows it):

```kotlin
package com.mercury.sqkon.db

import com.mercury.sqkon.db.internal.SqkonTransaction
import com.mercury.sqkon.db.internal.SqkonTransacter

fun <T : Any> KeyValueStorage<T>.transaction(body: SqkonTransactionScope.() -> Unit): Unit = runTransaction(body)
fun <T : Any, R> KeyValueStorage<T>.transactionWithResult(body: SqkonTransactionScope.() -> R): R = runTransactionWithResult(body)

internal class SqkonTransactionScopeReceiver(
    private val transaction: SqkonTransaction,
    private val transacter: SqkonTransacter,
) : SqkonTransactionScope {
    override fun afterCommit(action: () -> Unit) = transaction.afterCommit(action)
    override fun afterRollback(action: () -> Unit) = transaction.afterRollback(action)
    override fun rollback(): Nothing = throw SqkonRollbackException()
    override fun transaction(body: SqkonTransactionScope.() -> Unit) =
        transacter.transaction(noEnclosing = false) { body() }
}
```
(`KeyValueStorage.runTransaction`/`runTransactionWithResult` from Phase 5 already call `transacter.transaction(...)`; only the receiver type the wrapper builds changes. Keep the Unit-path `try/catch (SqkonRollbackException)` swallow in `KeyValueStorage.runTransaction`.)

- [ ] **Step 4:** Compile commonMain + jvm. Defer full test to Task 10. Commit `feat(arch): AndroidxSqkonDriver + native transaction manager (MOB-3293)`.

---

### Task 9: Rewire `EntityQueries` / `MetadataQueries` off `TransacterImpl`

**Files:** Modify `EntityQueries.kt`, `MetadataQueries.kt`

- [ ] **Step 1:** Change both class headers from `class X(sqlDriver: SqlDriver) : TransacterImpl(sqlDriver)` with `sqkonDriver = SqlDelightSqkonDriver(sqlDriver)` to take a `SqkonDriver` directly and extend the new `SqkonTransacter`:

```kotlin
// EntityQueries.kt
class EntityQueries(
    @PublishedApi internal val driver: SqkonDriver,
) : SqkonTransacter(driver) {
    // replace every `sqkonDriver.` with `driver.`; delete the `sqlDriver` field + SqlDelightSqkonDriver import
    …
}
```
Same for `MetadataQueries`. Their `executeUpdate`/`executeQuery`/`notifyQueries`/listener wiring already target `SqkonDriver` — only the field name (`sqkonDriver` → `driver`) and the supertype change. `metadataQueries.transaction { … }` (called from `KeyValueStorage.updateReadAt`/`updateWriteAt`) now resolves to `SqkonTransacter.transaction` — body uses no receiver methods, so it compiles.

- [ ] **Step 2:** `KeyValueStorage.kt` — `private val transacter: SqkonTransacter` stays; the `keyValueStorage(...)` factory default `transactor: SqkonTransacter = SqkonTransacter(entityQueries.sqlDriver)` changes to `SqkonTransacter(entityQueries.driver)` (or pass `entityQueries` itself, since it *is* a `SqkonTransacter`). Update `KeyValueStorage.updateWriteAt()` hash call — already `transacter.currentOutermostTransactionHash()` (Phase-5 rename / Task 8 keeps the name).
- [ ] **Step 3:** Compile. Commit `refactor: EntityQueries/MetadataQueries on SqkonDriver, drop TransacterImpl (MOB-3293)`.

---

### Task 10: Flip `DriverFactory` to `SqkonDriver` + platform actuals

**Files:** `gradle/libs.versions.toml` + `library/build.gradle.kts`; `SqkonDatabaseDriver.kt` (expect) + `.jvm.kt`/`.android.kt`/`.ios.kt` actuals

- [ ] **Step 0: gradle** — ensure `androidx.sqlite:sqlite` (the KMP interfaces) is a `commonMain` dependency (currently `implementation`; the new commonMain driver references `androidx.sqlite.*`). Keep `androidx.sqlite:sqlite-bundled` on jvm/android. Leave eygraber + sqldelight runtime in place (Phase 7 removes). `./gradlew :library:compileCommonMainKotlinMetadata`.
- [ ] **Step 1: expect** — `SqkonDatabaseDriver.kt`:
```kotlin
internal expect val connectionPoolSize: Int
internal expect val defaultSqkonDispatchers: SqkonDispatchers
internal expect class DriverFactory {
    fun createDriver(): SqkonDriver
}
```
- [ ] **Step 2: JVM actual** — build `AndroidxSqkonDriver`, FULLMUTEX flags, `SqkonDatabaseType`:
```kotlin
internal actual class DriverFactory(
    private val type: SqkonDatabaseType = SqkonDatabaseType.Memory,
    private val config: SqkonDriverConfig = SqkonDriverConfig(),
) {
    actual fun createDriver(): SqkonDriver {
        val bundled = BundledSQLiteDriver()
        val factory = SqkonConnectionFactory { name ->
            bundled.open(name, SQLITE_OPEN_READWRITE or SQLITE_OPEN_CREATE or SQLITE_OPEN_FULLMUTEX)
        }
        val (name, isMemory) = when (type) {
            SqkonDatabaseType.Memory -> ":memory:" to true
            is SqkonDatabaseType.FileBacked -> type.path to false
        }
        return AndroidxSqkonDriver(factory, name, isMemory, SqkonDatabaseSchema, config)
    }
}
```
- [ ] **Step 3: Android actual** — same, with `FileProvider`-style path from `context.getDatabasePath(name)` (preserve current file location semantics) and `connectionPoolSize` from system resource feeding `SqkonDriverConfig(readerConnections = …)`.
- [ ] **Step 4: iOS actual** — `actual fun createDriver(): SqkonDriver = TODO("iOS driver lands later")` (keep the stub; signature now returns `SqkonDriver`).
- [ ] **Step 5: full compile** — `./gradlew :library:compileKotlinJvm :library:compileDebugKotlinAndroid :library:compileKotlinIosArm64` (or the configured ios target) → BUILD SUCCESSFUL.
- [ ] **Step 6:** Commit `feat: DriverFactory returns SqkonDriver via AndroidxSqkonDriver (MOB-3293)`.

---

### Task 11: Public `Sqkon(...)` factories — `SqkonDatabaseType` + `SqkonDriverConfig`

**Files:** `Sqkon.jvm.kt`, `Sqkon.android.kt`

- [ ] **Step 1: JVM** — swap the param type (breaking, `feat!:`):
```kotlin
@JvmOverloads
fun Sqkon(
    scope: CoroutineScope,
    json: Json = SqkonJson { },
    type: SqkonDatabaseType = SqkonDatabaseType.Memory,
    config: KeyValueStorage.Config = KeyValueStorage.Config(),
    driverConfig: SqkonDriverConfig = SqkonDriverConfig(),
    dispatchers: SqkonDispatchers = defaultSqkonDispatchers,
): Sqkon {
    val driver = DriverFactory(type, driverConfig).createDriver()
    val metadataQueries = MetadataQueries(driver)
    val entityQueries = EntityQueries(driver)
    return Sqkon(entityQueries, metadataQueries, scope, json, config, dispatchers = dispatchers)
}
```
- [ ] **Step 2: Android** — keep `context` + `dbFileName`; add `driverConfig`; `DriverFactory(context, dbFileName, driverConfig)`; pass `driver` to `MetadataQueries`/`EntityQueries`. Keep the `@Deprecated(inMemory)` overload mapping `inMemory=true → dbFileName=null`.
- [ ] **Step 3:** `./gradlew :library:compileKotlinJvm :library:compileDebugKotlinAndroid` → BUILD SUCCESSFUL.
- [ ] **Step 4:** Commit `feat!: public Sqkon factories take SqkonDatabaseType + SqkonDriverConfig (MOB-3293)`.

---

### Task 12: Delete the SQLDelight bridge + dead code

**Files:** delete `internal/sqldelight/`; prune `QueryExt.kt`, `SqkonTransacter` leftovers

- [ ] **Step 1:** `grep -rn 'internal.sqldelight\|SqlDelightSqkon\|toSqkonQuery\|app.cash.sqldelight' library/src/commonMain library/src/jvmMain library/src/androidMain library/src/iosMain` — every hit must be gone or dead. Remove `QueryExt.kt`'s `toSqkonQuery` (and its `app.cash.sqldelight.Query` import) if unused.
- [ ] **Step 2:** `git rm -r library/src/commonMain/kotlin/com/mercury/sqkon/db/internal/sqldelight`.
- [ ] **Step 3:** `./gradlew :library:compileKotlinJvm :library:compileDebugKotlinAndroid` → BUILD SUCCESSFUL (proves nothing in main referenced the bridge).
- [ ] **Step 4:** Commit `chore: delete SQLDelight SqkonDriver bridge (MOB-3293)`.

---

### Task 13: Update tests that construct the driver directly

**Files:** `SqkonDatabaseDriverTest.jvm.kt`/`.android.kt`, `SchemaMigrationTest.kt`, `SqkonDriverRoundtripTest.kt`, any test importing eygraber/`SqkonDatabase.Schema`

- [ ] **Step 1:** `driverFactory()` test actuals → `DriverFactory(SqkonDatabaseType.Memory)` (JVM) / `DriverFactory(context, "sqkon-test-$uuid.db")` (Android).
- [ ] **Step 2:** `SqkonDriverRoundtripTest` — construct `AndroidxSqkonDriver` (or go through `driverFactory().createDriver()`), not `SqlDelightSqkonDriver`.
- [ ] **Step 3:** `SchemaMigrationTest` — it builds an `AndroidxSqliteDriver` + v1 schema fixture directly to test `SqkonDatabaseSchema.migrate(1→2)`. Rework to drive the migration through `AndroidxSqkonDriver` (or a raw `BundledSQLiteDriver` connection running the v1 DDL then `SqkonDatabaseSchema.migrate(SqkonDriverWrapper, 1, 2)`). Keep the assertions (PRAGMA sections + `write_at` epoch-millis backfill).
- [ ] **Step 4:** `./gradlew :library:jvmTest` → all green.
- [ ] **Step 5:** Commit `test: drive tests through AndroidxSqkonDriver (MOB-3293)`.

---

### Task 14: `sqlite_version()` guard + final verification + PR

- [ ] **Step 1:** In `AndroidxSqkonDriver.init` (or `ensureSchema`) run `SELECT sqlite_version()` and `error("Sqkon needs SQLite >= 3.45 for jsonb(); found $v")` if below 3.45. `androidx.sqlite:sqlite-bundled 2.6.2` ships 3.46+, so this only fires on misconfiguration.
- [ ] **Step 2: full clean run**
```bash
./gradlew clean
./gradlew :library:jvmTest
```
Expected: BUILD SUCCESSFUL — special attention to `StatementCacheEvictionTest`, `ConcurrencyTest`, `EntityQueriesNotifyTest`, `KeyValueStorageTransactionTest`, `KeyValueStorageMetadataPerRowTest`, `KeysetPagingTest`, `SqkonDriverRoundtripTest`, `SchemaParityTest`, `SchemaMigrationTest`.
- [ ] **Step 3:** `./gradlew :library:compileDebugKotlinAndroid` + (if configured) the iOS compile target → BUILD SUCCESSFUL. Android instrumented tests run in CI.
- [ ] **Step 4:** Confirm eygraber/sqldelight are unreferenced by main:
```bash
grep -rn 'com.eygraber\|app.cash.sqldelight' library/src/commonMain library/src/jvmMain library/src/androidMain library/src/iosMain
```
Expected: zero (gate tests in jvmTest may still import eygraber config for fixtures — acceptable; Phase 7 removes deps).
- [ ] **Step 5: push + PR** (`feat!:`, references #56 release wave):
```bash
git push -u origin worktree-phase-6
gh pr create --title "feat!: direct androidx.sqlite driver, drop SQLDelight SqlDriver (MOB-3293)" --body "<summary: AndroidxSqkonDriver + pool + statement cache + native transaction manager replace SqlDelightSqkonDriver; DriverFactory now returns SqkonDriver; EntityQueries/MetadataQueries/SqkonTransacter off TransacterImpl; public Sqkon factories take SqkonDatabaseType + SqkonDriverConfig (breaking); JVM now opens with SQLITE_OPEN_FULLMUTEX; sqldelight bridge deleted. eygraber + sqldelight runtime still on classpath, removed in Phase 7. Rides the open 3.0.0 release.>"
```
- [ ] **Step 6:** Finish via **superpowers:finishing-a-development-branch**. Do not cut a release manually — release-please folds this into the open `3.0.0` (#56).

---

## Verification (end-to-end)

1. **Functional parity** — every Phase-0 gate test passes through the new driver: cache eviction, reader/writer concurrency, cross-entity notify isolation, transaction commit/rollback/nesting + afterCommit ordering, per-row read/write metadata (incl. the no-re-emit invariant), keyset paging empty→populated invalidation, driver roundtrip, schema parity + migration.
2. **No SQLDelight in the runtime path** — `internal/sqldelight/` gone; `grep` finds no `app.cash.sqldelight`/`com.eygraber` in main source sets.
3. **`SqkonDriver` contract intact** — `EntityQueries`/`MetadataQueries`/`SqkonQuery`/`SqkonFlowQuery` unchanged except constructor wiring.
4. **Multiplatform compiles** — jvm + android + ios (stubbed `TODO()`); `DriverFactory.createDriver(): SqkonDriver` actualized on all targets.
5. **JVM FULLMUTEX** — both platforms open connections with `SQLITE_OPEN_FULLMUTEX` (called out in the PR + commit).
6. **Public API** — `Sqkon(...)` takes `SqkonDatabaseType` + `SqkonDriverConfig`; `feat!:` rides the open 3.0.0.

## Risks & Mitigations

| Risk | Mitigation |
|------|------------|
| Statement-cache races / leaks | Port eygraber's exact remove→use→reset→put-back-closing-displaced eviction. `StatementCacheEvictionTest` (200 shapes + 100 concurrent CRUD) is the gate. |
| Transaction semantics drift from SQLDelight | Port `TransacterImpl.transactionWithWrapper` logic verbatim (hooks propagate to enclosing, fire once at outermost; `RollbackException` caught at boundary). `KeyValueStorageTransactionTest` is the gate. |
| Cross-entity over-notification | Driver listener registry keyed by query string; `notifyListeners` fires only matching keys. `EntityQueriesNotifyTest` + metadata no-re-emit test are gates. |
| `runBlocking` not in commonMain | `internal expect fun sqkonRunBlocking` (jvm/android/ios → `runBlocking`); no js/wasm target today. |
| Writer-mutex deadlock on main thread | Writes run on the single-thread write dispatcher (`defaultSqkonDispatchers.write`), already wired; `sqkonRunBlocking` only blocks that worker. `ConcurrencyTest` is the gate. |
| 1-based vs 0-based bind indices | androidx.sqlite binds are 1-based; Sqkon/`AutoIncrementSqlPreparedStatement` are 0-based → `index + 1` in every `AndroidxStatement.bind*`. |
| iOS `androidx.sqlite` availability | iOS `createDriver()` stays `TODO()`; only signatures compile. No iOS runtime path in Phase 6. |
| Large single PR | Matches the ticket's "implementation cutover" framing; can't be half-shipped (bridge either exists or not). Execute subagent-driven, task-by-task, gate tests between tasks. |
| `androidx.sqlite` exec/`changes()` API specifics | Verify `SQLiteConnection.prepare`/`execSql` + `SELECT changes()` against `androidx.sqlite 2.6.2` while porting; adjust `AndroidxPreparedStatement.execute()` to read affected rows if any caller needs it (today only inserts care). |
