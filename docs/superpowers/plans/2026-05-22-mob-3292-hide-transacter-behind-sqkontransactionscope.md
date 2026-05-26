# Sqkon Phase 5 — Hide SQLDelight `Transacter` behind `SqkonTransactionScope` (`feat!:` 2.0)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Linear ticket:** [MOB-3292](https://linear.app/mercury/issue/MOB-3292/sqkon-migration-phase-5-feat-hide-transacter-behind)
**Branch:** new worktree `worktree-phase-5` off `main` (Phase 4 merged as `a4142b7`).
**Final plan location:** On approval, move this file to `docs/superpowers/plans/2026-05-22-mob-3292-hide-transacter-behind-sqkontransactionscope.md` (matches the Phase 1/4 convention).

**Goal:** Remove `app.cash.sqldelight.Transacter` and `app.cash.sqldelight.TransactionCallbacks` from Sqkon's **public** API. Replace `KeyValueStorage : Transacter by transacter` with a public, Sqkon-owned `SqkonTransactionScope` sealed interface plus `transaction { }` / `transactionWithResult { }` extension functions. This is the migration's **only breaking change** — release-please cuts **2.0** from the `feat!:` prefix.

**Architecture:** Add a public `sealed interface SqkonTransactionScope` (afterCommit / afterRollback / rollback / nested transaction) in `com.mercury.sqkon.db`. Two internal sqldelight-backed wrapper classes (one for the `Unit` path, one for the result path) adapt SQLDelight's lambda receivers to the scope; they live in the **same package** as the sealed interface (Kotlin requires sealed implementations to share the interface's package + module). Public access is via top-level extension functions on `KeyValueStorage<T>`; `internal open fun transaction`/`transactionWithResult` **members** keep the existing in-class call sites (`insert`/`update`/`delete`/…) compiling and provide the open-subclassing seam the ticket asks for. `SqkonTransacter` stays `TransacterImpl(SqlDriver)`-backed — the driver swap to `SqkonDriver` is **Phase 6 (MOB-3293)**, not this phase.

**Tech Stack:** Kotlin Multiplatform (commonMain, jvmMain, androidMain, iosMain scaffold). `app.cash.sqldelight` (internal-only after this phase). `kotlin.test` + `kotlinx-coroutines-test` (`runTest`) + Turbine.

---

## Context

Sqkon is migrating off SQLDelight in seven phases. Phases 0–4 are merged. Phase 4 (MOB-3291) hand-rolled the schema; the **query** path was hand-rolled behind the internal `SqkonDriver` abstraction in Phase 3. The remaining public coupling to SQLDelight is the **transaction** surface:

`KeyValueStorage<T>` is declared `: Transacter by transacter`, so callers can use a store *as* a `app.cash.sqldelight.Transacter` and the `transaction { }` lambda receiver is SQLDelight's `TransactionCallbacks`/`TransactionWithoutReturn`. The write-batching helper is `internal fun TransactionCallbacks.updateWriteAt()`. Phase 5 severs this so the public API names only Sqkon types. This unblocks **Phase 6 (MOB-3293)**, which replaces `AndroidxSqliteDriver` with a native `SqkonDriver` and flips `SqkonTransacter` off `TransacterImpl` — at which point the sqldelight imports added here as `internal` are deleted.

**Why it's breaking:** removing `: Transacter by transacter` and `TransactionCallbacks` from a public-visible signature is source-incompatible for any consumer that holds `val s: Transacter = store` or imports `TransactionCallbacks`. Hence `feat!:` → 2.0.

**Pre-merge action item (out-of-repo, do NOT block plan execution):** inventory internal Mercury consumers of `Transacter` / `TransactionCallbacks` from sqkon. In-repo there are zero non-test external usages (see "Existing Code" below); coordinate any downstream `val x: Transacter = ...` before merging the PR.

---

## File Structure

**New files (all `com.mercury.sqkon.db`, commonMain):**
- `library/src/commonMain/kotlin/com/mercury/sqkon/db/SqkonTransactionScope.kt` — public `sealed interface SqkonTransactionScope` + public `class SqkonRollbackException internal constructor(...)`.
- `library/src/commonMain/kotlin/com/mercury/sqkon/db/Transactions.kt` — public `transaction` / `transactionWithResult` extension functions **and** the two `internal` sqldelight-backed scope implementations (same package as the sealed interface).

**Modified files:**
- `library/src/commonMain/kotlin/com/mercury/sqkon/db/internal/SqkonTransacter.kt` — replace the `Transacter.Transaction.parentTransactionHash()` extension with an `internal fun currentParentTransactionHash(): Int` that lives inside the transacter. Keep the `transaction`/`transactionWithResult` overrides + `trxMap`.
- `library/src/commonMain/kotlin/com/mercury/sqkon/db/KeyValueStorage.kt` — drop `: Transacter by transacter`; drop the two sqldelight imports; add `internal open fun transaction(...)` / `internal open fun <R> transactionWithResult(...)` members; change `internal fun TransactionCallbacks.updateWriteAt()` → `internal fun SqkonTransactionScope.updateWriteAt()` (compute the dedup hash via `transacter.currentParentTransactionHash()`).
- `library/src/commonTest/kotlin/com/mercury/sqkon/db/internal/SqkonTransacterParentTest.kt` — retarget the two assertions onto `transacter.currentParentTransactionHash()`.
- `library/src/commonTest/kotlin/com/mercury/sqkon/db/KeyValueStorageTransactionTest.kt` — **add** two `transactionWithResult` tests (commit-returns-value, rollback-throws-and-aborts). Existing 5 tests are unchanged and are the gate.
- `README.MD` — add a short "Transactions" usage section (none exists today).

**Untouched (verified):** `SqkonTransaction` (internal abstract, Phase-3 scaffold) and `SqlDelightSqkonTransaction` + `SqkonTransactionForwardingTest` — these are parallel internal scaffolding, **not** referenced by production transaction code, and still compile. `EntityQueries`/`MetadataQueries` keep `: TransacterImpl(...)` (internal query layer; reconciled in Phase 6/7). `KeysetQueryPagingSource`/`OffsetQueryPagingSource` keep their internal `transacter: Transacter` constructor params (not in any public-visible signature; the public methods return `PagingSource<…>`). The `keyValueStorage(...)` factory is unchanged.

---

## Existing Code to Reuse (verified file:line)

- **`KeyValueStorage.kt`** `library/src/commonMain/kotlin/com/mercury/sqkon/db/KeyValueStorage.kt`
  - `:48` class header `... ) : Transacter by transacter {` — drop the supertype.
  - `:47` `private val transacter: SqkonTransacter` — **keep private**; members below use it.
  - `:59,93,108,127,142,159,415,429,444,462` methods use `= transaction { … }` then `updateWriteAt()` — these call sites stay verbatim; only the lambda **receiver type** changes (sqldelight `TransactionWithoutReturn` → `SqkonTransactionScope`), which is source-compatible because the bodies only call `entityQueries.*`, sibling methods, `updateWriteAt()`, and `return@transaction`.
  - `:489` `else -> return@transaction` — label still resolves to the `transaction` member call.
  - `:542-551` `updateReadAt` uses `metadataQueries.transaction { … }` — **unchanged** (different object; sqldelight `TransacterImpl`).
  - `:553` `private val updateWriteHashes = mutableSetOf<Int>()` — reuse.
  - `:559-573` `internal fun TransactionCallbacks.updateWriteAt()` — convert receiver to `SqkonTransactionScope`; replace `with(transacter){ entityQueries.sqlDriver.currentTransaction()!!.parentTransactionHash() }` with `transacter.currentParentTransactionHash()`.
  - `:604-627` `inline fun <reified T> keyValueStorage(...)` — unchanged.
- **`SqkonTransacter.kt`** `library/src/commonMain/kotlin/com/mercury/sqkon/db/internal/SqkonTransacter.kt`
  - `:15` `open class SqkonTransacter(driver: SqlDriver) : TransacterImpl(driver)` — keep (driver swap is Phase 6).
  - `:18` `protected val trxMap = mutableMapOf<Transacter.Transaction, Transacter.Transaction?>()` — keep (key stays the sqldelight transaction; an internal detail until Phase 6).
  - `:20-23` `fun Transacter.Transaction.parentTransactionHash(): Int` — **replace** with a private helper + `currentParentTransactionHash()` (see Task 2).
  - `:25-61` `transaction` / `transactionWithResult` overrides — keep verbatim (they record `trxMap` and are what the scope wrappers call for nesting).
- **Gate test:** `library/src/commonTest/kotlin/com/mercury/sqkon/db/KeyValueStorageTransactionTest.kt` — 5 tests pin behavior: silent rollback (`:30`), nested rollback rolls back enclosing (`:40`), `afterCommit` visible after commit (`:55`), `afterCommit` skipped on rollback (`:71`), nested `afterCommit` fires once at outermost commit (`:87`). **Must stay green unchanged.**
- **Dedup test:** `KeyValueStorageTest.kt:679` `updateWriteAtOnlyRunsOncePerTransaction` — `transaction { with(testObjectStorage) { updateWriteAt(); updateWriteAt() } }` exercises `SqkonTransactionScope.updateWriteAt()`; must stay green.
- **External-tx test:** `KeyValueStorageTest.kt:663` `externalTransaction` — `store.transaction { deleteAll(); insertAll(...) }`; resolves to the new public extension.

---

## SQLDelight API facts (relied on)

- `interface TransactionCallbacks { fun afterCommit(fn: () -> Unit); fun afterRollback(fn: () -> Unit) }`
- `interface TransactionWithoutReturn : TransactionCallbacks { fun rollback(): Nothing; fun transaction(noEnclosing: Boolean = false, body: TransactionWithoutReturn.() -> Unit) }`
- `interface TransactionWithReturn<R> : TransactionCallbacks { fun rollback(returnValue: R): Nothing; fun transaction(...); fun <R> transactionWithResult(...): R }`
- `TransactionWithoutReturn.rollback()` throws SQLDelight's internal `RollbackException` (caught by the transacter → caller sees a **silent** return). Any *other* exception escaping a transaction body also rolls the DB back, then propagates to the caller. The result-path `rollback()` exploits this: it throws `SqkonRollbackException` (our type, since SQLDelight's is internal), the DB rolls back, the exception reaches the caller.

---

## Critical Invariants

1. **`KeyValueStorageTransactionTest` (5 tests) stays green unchanged.** It is the behavioral gate for this refactor.
2. **Member-over-extension resolution.** The `internal open fun transaction`/`transactionWithResult` **members** and the same-named public **extension functions** coexist. Kotlin always resolves a member over an extension when both apply, so: in-module callers (`insert`/`update`/…) and the extension bodies hit the member; out-of-module callers (which can't see the `internal` member) hit the extension. No recursion, identical behavior.
3. **Nested transactions must route through `SqkonTransacter`.** `SqkonTransactionScope.transaction { }` calls `transacter.transaction(noEnclosing = false) { … }` (NOT the sqldelight receiver's own `transaction`) so `trxMap` records the parent→child link. This is what makes `currentParentTransactionHash()` collapse nested inserts to one `updateWriteAt` afterCommit (the dedup the tests pin).
4. **Rollback semantics:** `transaction { rollback() }` → silent (SQLDelight). `transactionWithResult { rollback() }` → throws `SqkonRollbackException` (no value to return). Both abort the DB transaction.
5. **`sealed` placement:** `SqkonTransactionScope` is `sealed`; its only implementations are `internal` classes in the **same package** (`com.mercury.sqkon.db`). This is a deliberate, documented deviation from the ticket's prose ("internal `SqkonTransaction` implements it") — `SqkonTransaction` is in `…​.internal`, and Kotlin forbids a sealed type's implementations from another package.
6. **`SqkonTransacter` stays `TransacterImpl`-backed.** Driver swap + dropping `TransacterImpl` is Phase 6. Do not change its constructor or supertype here.

---

## Tasks

### Task 1: Add the public `SqkonTransactionScope` interface

**Files:** Create `library/src/commonMain/kotlin/com/mercury/sqkon/db/SqkonTransactionScope.kt`

- [ ] **Step 1: Create the file**

```kotlin
package com.mercury.sqkon.db

/**
 * Sqkon-owned transaction scope, the receiver of [transaction] / [transactionWithResult].
 *
 * Replaces direct exposure of SQLDelight's `Transacter` / `TransactionCallbacks`. `sealed` — only
 * Sqkon provides implementations; callers must not implement it.
 */
sealed interface SqkonTransactionScope {

    /** Run [action] after the outermost enclosing transaction commits. */
    fun afterCommit(action: () -> Unit)

    /** Run [action] after the transaction rolls back. */
    fun afterRollback(action: () -> Unit)

    /**
     * Abort the transaction.
     *
     * In [transaction] this returns silently to the caller (the work is discarded). In
     * [transactionWithResult] there is no value to return, so it throws [SqkonRollbackException].
     * In either case the database transaction (and any enclosing one — there are no savepoints) is
     * rolled back.
     */
    fun rollback(): Nothing

    /**
     * Run [body] in a nested transaction. A nested rollback rolls back the enclosing transaction
     * too (no savepoint semantics).
     */
    fun transaction(body: SqkonTransactionScope.() -> Unit)
}

/**
 * Thrown out of [transactionWithResult] when [SqkonTransactionScope.rollback] is called: a
 * result-returning transaction has no value to hand back on rollback, so it aborts by throwing.
 */
class SqkonRollbackException internal constructor() :
    RuntimeException("Sqkon transaction rolled back")
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :library:compileCommonMainKotlinMetadata`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add library/src/commonMain/kotlin/com/mercury/sqkon/db/SqkonTransactionScope.kt
git commit -m "feat(arch): add public SqkonTransactionScope interface (MOB-3292)"
```

---

### Task 2: Consolidate the parent-transaction hash inside `SqkonTransacter`

**Files:** Modify `library/src/commonMain/kotlin/com/mercury/sqkon/db/internal/SqkonTransacter.kt`

- [ ] **Step 1: Replace the `parentTransactionHash()` extension with an internal member helper**

Replace lines 20-23:
```kotlin
    fun Transacter.Transaction.parentTransactionHash(): Int {
        // Walk up the chain. Top level: hash of self.
        return trxMap[this]?.parentTransactionHash() ?: this.hashCode()
    }
```
With:
```kotlin
    /**
     * Stable identity hash of the *outermost* transaction enclosing the one currently running on
     * [driver]. Used to dedup per-transaction side effects (e.g. the metadata write_at touch) so a
     * batch of nested writes only schedules one afterCommit. Must be called inside a transaction.
     */
    internal fun currentParentTransactionHash(): Int {
        val current = driver.currentTransaction()
            ?: error("currentParentTransactionHash() called outside a transaction")
        return current.outermostHash()
    }

    private fun Transacter.Transaction.outermostHash(): Int =
        trxMap[this]?.outermostHash() ?: this.hashCode()
```

(Keep the imports, `trxMap`, and both `transaction` / `transactionWithResult` overrides exactly as-is. `driver` is `TransacterImpl`'s protected field; `currentTransaction()` is on `SqlDriver`.)

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :library:compileCommonMainKotlinMetadata`
Expected: BUILD SUCCESSFUL. (`KeyValueStorage.kt` still references the old extension — that's fixed in Task 3; if you compile metadata only, it fails there. Run the full compile after Task 3.)

- [ ] **Step 3: Update the internal parent-hash test**

In `library/src/commonTest/kotlin/com/mercury/sqkon/db/internal/SqkonTransacterParentTest.kt` replace each `with(transacter) { sqlDriver.currentTransaction()!!.parentTransactionHash() }` (and the `current.parentTransactionHash()` form) with `transacter.currentParentTransactionHash()`. Final form:

```kotlin
    @Test
    fun nested_transaction_parentHash_equals_outer_hash() {
        var outerHash = 0
        var innerParentHash = 0
        transacter.transaction {
            outerHash = transacter.currentParentTransactionHash()
            transacter.transaction {
                innerParentHash = transacter.currentParentTransactionHash()
            }
        }
        assertEquals(outerHash, innerParentHash)
    }

    @Test
    fun top_level_transaction_parentHash_equals_self() {
        var selfHash = 0
        var parentHash = 0
        transacter.transaction {
            selfHash = sqlDriver.currentTransaction()!!.hashCode()
            parentHash = transacter.currentParentTransactionHash()
        }
        assertEquals(selfHash, parentHash)
    }
```

(Remove the now-unused `import` of nothing extra; `sqlDriver` is already the test field.)

- [ ] **Step 4: Commit** (after Task 3 compiles — or commit now and let the metadata-compile gap close in Task 3; prefer committing together with Task 3 if executing inline.)

```bash
git add library/src/commonMain/kotlin/com/mercury/sqkon/db/internal/SqkonTransacter.kt \
        library/src/commonTest/kotlin/com/mercury/sqkon/db/internal/SqkonTransacterParentTest.kt
git commit -m "refactor: move parent-transaction hash into SqkonTransacter (MOB-3292)"
```

---

### Task 3: Add scope implementations + extension functions; rewire `KeyValueStorage`

**Files:**
- Create `library/src/commonMain/kotlin/com/mercury/sqkon/db/Transactions.kt`
- Modify `library/src/commonMain/kotlin/com/mercury/sqkon/db/KeyValueStorage.kt`

- [ ] **Step 1: Create `Transactions.kt`**

```kotlin
package com.mercury.sqkon.db

import app.cash.sqldelight.TransactionWithReturn
import app.cash.sqldelight.TransactionWithoutReturn
import com.mercury.sqkon.db.internal.SqkonTransacter

/**
 * Run [body] in a database transaction. Replaces the SQLDelight `Transacter.transaction { }` that
 * [KeyValueStorage] used to inherit. Calling [SqkonTransactionScope.rollback] discards the work and
 * returns silently.
 */
fun <T : Any> KeyValueStorage<T>.transaction(body: SqkonTransactionScope.() -> Unit): Unit =
    transaction(body) // resolves to the internal member (members win over extensions)

/**
 * Run [body] in a database transaction and return its value. Calling [SqkonTransactionScope.rollback]
 * aborts the transaction and throws [SqkonRollbackException] (there is no value to return).
 */
fun <T : Any, R> KeyValueStorage<T>.transactionWithResult(body: SqkonTransactionScope.() -> R): R =
    transactionWithResult(body) // resolves to the internal member

/** Scope backing the `Unit` transaction path. Wraps SQLDelight's [TransactionWithoutReturn]. */
internal class SqlDelightTransactionScope(
    private val tx: TransactionWithoutReturn,
    private val transacter: SqkonTransacter,
) : SqkonTransactionScope {
    override fun afterCommit(action: () -> Unit) = tx.afterCommit(action)
    override fun afterRollback(action: () -> Unit) = tx.afterRollback(action)
    override fun rollback(): Nothing = tx.rollback()
    override fun transaction(body: SqkonTransactionScope.() -> Unit) {
        // Route nesting through SqkonTransacter so trxMap records the parent link.
        transacter.transaction(noEnclosing = false) {
            SqlDelightTransactionScope(this, transacter).body()
        }
    }
}

/** Scope backing the result path. Wraps SQLDelight's [TransactionWithReturn]; rollback throws. */
internal class SqlDelightResultTransactionScope<R>(
    @Suppress("unused") private val tx: TransactionWithReturn<R>,
    private val transacter: SqkonTransacter,
) : SqkonTransactionScope {
    override fun afterCommit(action: () -> Unit) = tx.afterCommit(action)
    override fun afterRollback(action: () -> Unit) = tx.afterRollback(action)
    override fun rollback(): Nothing = throw SqkonRollbackException()
    override fun transaction(body: SqkonTransactionScope.() -> Unit) {
        transacter.transaction(noEnclosing = false) {
            SqlDelightTransactionScope(this, transacter).body()
        }
    }
}
```

- [ ] **Step 2: Edit `KeyValueStorage.kt` — drop the sqldelight imports**

Remove these two import lines (4-5):
```kotlin
import app.cash.sqldelight.Transacter
import app.cash.sqldelight.TransactionCallbacks
```
(Keep `import com.mercury.sqkon.db.internal.SqkonTransacter`.)

- [ ] **Step 3: Edit the class header (line 48) — drop the delegate**

Change:
```kotlin
    private val transacter: SqkonTransacter,
) : Transacter by transacter {
```
To:
```kotlin
    private val transacter: SqkonTransacter,
) {
```

- [ ] **Step 4: Add the `internal open` transaction members**

Insert directly after the class header `{` (before `insert`, around line 49). These give the in-class call sites their `transaction { }` target and provide the open seam for internal subclasses:

```kotlin
    /**
     * Internal transaction seam. The public surface is the [transaction] extension function;
     * this `open` member exists so internal subclasses can wrap transaction behavior (extensions
     * can't be overridden) and so in-class callers resolve here.
     */
    internal open fun transaction(body: SqkonTransactionScope.() -> Unit) {
        transacter.transaction(noEnclosing = false) {
            SqlDelightTransactionScope(this, transacter).body()
        }
    }

    internal open fun <R> transactionWithResult(body: SqkonTransactionScope.() -> R): R =
        transacter.transactionWithResult(noEnclosing = false) {
            SqlDelightResultTransactionScope(this, transacter).body()
        }
```

- [ ] **Step 5: Convert `updateWriteAt()` to the new receiver (lines 559-573)**

Change the signature + hash computation:
```kotlin
    internal fun SqkonTransactionScope.updateWriteAt() {
        val requestHash = transacter.currentParentTransactionHash()
        if (requestHash in updateWriteHashes) return
        updateWriteHashes.add(requestHash)
        afterCommit {
            updateWriteHashes.remove(requestHash)
            scope.launch(writeDispatcher) {
                metadataQueries.transaction {
                    metadataQueries.upsertWrite(entityName, Clock.System.now())
                }
            }
        }
    }
```
(Only the receiver type and the `requestHash` line change; the `afterCommit { … }` body is identical. `afterCommit` now resolves to `SqkonTransactionScope.afterCommit`.)

- [ ] **Step 6: Verify commonMain + Android compile**

Run: `./gradlew :library:compileCommonMainKotlinMetadata :library:compileKotlinJvm :library:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL. If you see "overload resolution ambiguity" or unexpected recursion on `transaction`, confirm the members are `internal` (not `public`) and the extensions are top-level in `Transactions.kt`.

- [ ] **Step 7: Commit**

```bash
git add library/src/commonMain/kotlin/com/mercury/sqkon/db/Transactions.kt \
        library/src/commonMain/kotlin/com/mercury/sqkon/db/KeyValueStorage.kt
git commit -m "feat!: hide sqldelight transacter behind SqkonTransactionScope extension functions (MOB-3292)"
```

---

### Task 4: Test the new public API (commit + rollback)

**Files:** Modify `library/src/commonTest/kotlin/com/mercury/sqkon/db/KeyValueStorageTransactionTest.kt`

- [ ] **Step 1: Add imports** (top of file)

```kotlin
import kotlin.test.assertFailsWith
```

- [ ] **Step 2: Add two tests inside the class** (before the `private companion object`)

```kotlin
    @Test
    fun transactionWithResult_commitReturnsValue() = runTest {
        val inserted = storage.transactionWithResult {
            storage.insert("twr", TestObject())
            42
        }
        assertEquals(42, inserted)
        assertEquals(1, storage.count().first())
    }

    @Test
    fun transactionWithResult_rollbackThrowsAndAborts() = runTest {
        assertFailsWith<SqkonRollbackException> {
            storage.transactionWithResult<TestObject, Int> {
                storage.insert("twr", TestObject())
                rollback()
            }
        }
        // DB rolled back: the insert is gone.
        assertEquals(0, storage.count().first())
    }
```

- [ ] **Step 3: Run the transaction + storage tests**

Run:
```bash
./gradlew :library:jvmTest --tests "*.KeyValueStorageTransactionTest" \
  --tests "*.KeyValueStorageTest" \
  --tests "*.SqkonTransacterParentTest" \
  --tests "*.SqkonTransactionForwardingTest"
```
Expected: all PASS. The 5 pre-existing `KeyValueStorageTransactionTest` cases + `externalTransaction` + `updateWriteAtOnlyRunsOncePerTransaction` prove the refactor preserved behavior; the 2 new cases prove the result path.

If `updateWriteAtOnlyRunsOncePerTransaction` fails (more than one metadata emission): nesting isn't routing through `SqkonTransacter` — re-check Invariant 3 (scope `transaction` must call `transacter.transaction`, not `tx.transaction`).

- [ ] **Step 4: Commit**

```bash
git add library/src/commonTest/kotlin/com/mercury/sqkon/db/KeyValueStorageTransactionTest.kt
git commit -m "test: cover SqkonTransactionScope transactionWithResult commit + rollback (MOB-3292)"
```

---

### Task 5: Document the new transaction API in the README

**Files:** Modify `README.MD`

- [ ] **Step 1: Add a "Transactions" section** (after the existing usage examples; pick a sensible heading level matching neighbors)

````markdown
### Transactions

Group multiple writes into one atomic transaction with the `transaction { }` extension. Inserts,
updates and deletes inside the block commit together (or roll back together):

```kotlin
store.transaction {
    store.deleteAll()
    store.insertAll(newItems)
}
```

`rollback()` aborts the transaction and discards the work:

```kotlin
store.transaction {
    store.insert(key, value)
    if (shouldAbort) rollback() // nothing is written; returns silently
}
```

Use `afterCommit { }` / `afterRollback { }` to react once the outermost transaction settles, and
`transactionWithResult { }` when you need a return value (here `rollback()` throws
`SqkonRollbackException`, since there is no value to return):

```kotlin
val count: Int = store.transactionWithResult {
    store.insertAll(items)
    afterCommit { log("committed") }
    items.size
}
```
````

- [ ] **Step 2: Commit**

```bash
git add README.MD
git commit -m "docs: document SqkonTransactionScope transaction API (MOB-3292)"
```

---

### Task 6: Final verification, push, PR

- [ ] **Step 1: Full clean test run**

```bash
./gradlew clean
./gradlew :library:jvmTest
```
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 2: Confirm the public API no longer names the sqldelight transaction types**

```bash
grep -rn 'app.cash.sqldelight.Transacter\|TransactionCallbacks' library/src/commonMain
```
Expected: zero hits in `KeyValueStorage.kt`. Remaining hits, if any, are confined to `internal` classes (`SqkonTransacter`, the `Transactions.kt` scope wrappers, `EntityQueries`/`MetadataQueries`, paging sources) — acceptable for Phase 5; the wrappers/queries are reconciled in Phase 6.

- [ ] **Step 3: Android compile sanity** (instrumented tests run in CI per CLAUDE.md)

```bash
./gradlew :library:compileDebugKotlinAndroid
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Push branch + open PR**

```bash
git push -u origin worktree-phase-5
gh pr create \
  --title "feat!: hide sqldelight transacter behind SqkonTransactionScope (MOB-3292)" \
  --body "$(cat <<'EOF'
## Summary
Phase 5 of the SQLDelight migration (MOB-3292). Removes `app.cash.sqldelight.Transacter` and
`TransactionCallbacks` from Sqkon's public API:
- `KeyValueStorage` no longer `: Transacter by transacter`.
- New public `sealed interface SqkonTransactionScope` + `transaction { }` / `transactionWithResult { }`
  extension functions; `rollback()`, `afterCommit`, `afterRollback`, nested transactions.
- `transactionWithResult { rollback() }` throws `SqkonRollbackException` (no value to return);
  `transaction { rollback() }` returns silently (unchanged SQLDelight behavior).
- Internal machinery (`SqkonTransacter`) stays SQLDelight-backed — the driver swap is Phase 6.

**BREAKING (2.0):** consumers holding `val x: Transacter = store` or importing `TransactionCallbacks`
must move to the `transaction { }` extension. `release-please` cuts 2.0 from the `feat!:` prefix.

## Test Plan
- [ ] `./gradlew :library:jvmTest` green (gate: `KeyValueStorageTransactionTest` unchanged + 2 new
      `transactionWithResult` cases; `updateWriteAtOnlyRunsOncePerTransaction` proves the nested
      dedup still works).
- [ ] CI `run-android-tests` green.
EOF
)"
```

- [ ] **Step 5:** Finish via **superpowers:finishing-a-development-branch** (verify tests → present options). Per CLAUDE.md, do **not** create a GitHub release manually — release-please handles the 2.0 cut once merged.

---

## Verification (end-to-end)

1. **Behavior preserved** — `KeyValueStorageTransactionTest` (5 original cases) + `KeyValueStorageTest.externalTransaction` + `updateWriteAtOnlyRunsOncePerTransaction` pass unchanged.
2. **New API works** — `transactionWithResult` returns its value on commit and throws `SqkonRollbackException` (DB aborted) on `rollback()`.
3. **Public API clean** — `grep` shows no `Transacter`/`TransactionCallbacks` in `KeyValueStorage.kt`; the only `commonMain` references are `internal`.
4. **Multiplatform compiles** — `compileKotlinJvm` + `compileDebugKotlinAndroid` (+ metadata) succeed; iOS targets compile in CI.
5. **CI green** — `jvm-tests`, `Compile iOS targets`, `run-android-tests`.
6. **2.0 cut** — merged `feat!:` commit makes release-please open a `release: 2.0.0` PR.

## Risks & Mitigations

| Risk | Mitigation |
|------|------------|
| Member/extension ambiguity or accidental recursion on `transaction` | Members always win over same-signature extensions in Kotlin. Members are `internal`, extensions are top-level public. Verified by Task 3 Step 6 compile + Task 4 tests. |
| Nested writes schedule multiple `updateWriteAt` afterCommits | Scope `transaction { }` routes nesting through `SqkonTransacter.transaction` so `trxMap` records parents; `currentParentTransactionHash()` collapses them. Pinned by `updateWriteAtOnlyRunsOncePerTransaction`. |
| `sealed` + impl package rule | Scope impls live in `com.mercury.sqkon.db` (same package as the interface), `internal`. Documented deviation from ticket prose. |
| `transactionWithResult` rollback divergence from SQLDelight (`rollback(value)` returns; ours throws) | Intentional, per maintainer: a `rollback(): Nothing` has no value to return, so it throws `SqkonRollbackException`. Documented in KDoc + README. |
| Downstream Mercury consumers hold `Transacter` | Out-of-repo pre-merge inventory (ticket action item). In-repo: zero non-test external usages. |
| Transitive `Transacter` leak via public `SqkonTransacter : TransacterImpl` in the `keyValueStorage(...)` factory default | Out of Phase 5 scope; resolved in Phase 6 (MOB-3293) when `SqkonTransacter` drops `TransacterImpl` for `SqkonDriver`. Ticket exit criteria targets the explicit `KeyValueStorage` surface, which this phase clears. |
