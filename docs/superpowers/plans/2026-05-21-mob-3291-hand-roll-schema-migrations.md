# Sqkon Phase 4 — Hand-Roll Schema & Migrations, Retire `verifySqlDelightMigration`

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Linear ticket:** [MOB-3291](https://linear.app/mercury/issue/MOB-3291/sqkon-migration-phase-4-hand-roll-schema-and-migrations-retire)
**Branch:** `worktree-phase-4` (worktree at `.claude/worktrees/phase-4/`)
**Final plan location:** Once approved, move this file to `docs/superpowers/plans/2026-05-21-mob-3291-hand-roll-schema-migrations.md` (matches the Phase 1 plan convention at `docs/superpowers/plans/2026-05-12-sqkon-phase-1-kmp-source-set-scaffold.md`).

**Goal:** Replace SQLDelight-generated `SqkonDatabase.Schema` with our own hand-rolled `SqkonSchema` / `SqkonDatabaseSchema`. Delete `.sq` / `.sqm` / `1.db` files. Drop the `sqldelight { databases { ... } }` block. Remove `verifySqlDelightMigration` from CI. Ship a thin `toSqlDelightSchema()` bridge so the `eygraber/AndroidxSqliteDriver` keeps receiving `SqlSchema<QueryResult.Value<Unit>>` until Phase 6 swaps the driver entirely.

**Architecture:** Add a tiny schema abstraction (`SqkonSchema`) parallel to the existing `SqkonDriver` abstraction from Phase 3. A single concrete object (`SqkonDatabaseSchema`) holds the version, fresh-create DDL (matched statement-for-statement against the generated `SqkonDatabaseImpl.Schema.create()`), and the v1→v2 migration body (verbatim from `1.sqm`). A `SqkonSchema.toSqlDelightSchema()` extension adapts it to SQLDelight's `SqlSchema<QueryResult.Value<Unit>>` so `AndroidxSqliteDriver(schema = ...)` keeps compiling. Phase 0's `SchemaParityTest` + `SchemaMigrationTest` are the new correctness gate.

**Tech Stack:** Kotlin Multiplatform (commonMain, jvmMain, androidMain), `app.cash.sqldelight` (bridge only), `androidx.sqlite-bundled` (BundledSQLiteDriver), `kotlin.test` + `kotlinx-coroutines-test` + Turbine.

---

## Context

Sqkon is migrating off SQLDelight in seven phases. Phases 0–3 are merged:
- **Phase 0** (MOB-3287): `SchemaParityTest`, `SchemaMigrationTest`, `SchemaSnapshotUtil`, `sqkon-schema-v2.snapshot` resource — the regression suite that replaces `verifySqlDelightMigration`.
- **Phase 1** (MOB-3288): iOS source-set scaffold + `SqkonDispatchers`.
- **Phase 3** (MOB-3289): Internal `SqkonDriver` / `SqkonStatement` / `SqkonCursor` / `SqkonTransaction` abstraction with a `SqlDelightSqkonDriver` adapter wrapping `app.cash.sqldelight.db.SqlDriver`. `EntityQueries` and `MetadataQueries` were hand-rolled against `SqkonDriver` in MOB-3289 / MOB-3290.

Phase 4 (this plan) decouples the **schema** layer the same way Phase 3 decoupled queries. It removes SQLDelight's role as schema authority — `.sq` files, the migration `.sqm` file, and the binary `databases/1.db` fixture all go away. The `sqldelight { databases { } }` Gradle block is dropped (no more codegen) but the plugin alias stays so build config isn't churned twice (Phase 7 removes the plugin).

The `AndroidxSqliteDriver` constructor signature still demands `SqlSchema<QueryResult.Value<Unit>>`, so Phase 4 ships a one-extension-function bridge (`SqkonSchema.toSqlDelightSchema()`). Phase 6 will replace `AndroidxSqliteDriver` with native Sqkon drivers and delete the bridge.

---

## File Structure

**New files:**
- `library/src/commonMain/kotlin/com/mercury/sqkon/db/internal/schema/SqkonSchema.kt` — interface (version + create + migrate).
- `library/src/commonMain/kotlin/com/mercury/sqkon/db/internal/schema/SqkonDatabaseSchema.kt` — concrete v2 schema + v1→v2 migration body.

**Modified files:**
- `library/src/commonMain/kotlin/com/mercury/sqkon/db/internal/sqldelight/SqlDelightSqkonDriver.kt` — add `internal fun SqkonSchema.toSqlDelightSchema(): SqlSchema<QueryResult.Value<Unit>>`.
- `library/src/jvmMain/kotlin/com/mercury/sqkon/db/SqkonDatabaseDriver.jvm.kt` — pass `SqkonDatabaseSchema.toSqlDelightSchema()` to `AndroidxSqliteDriver(schema = ...)`.
- `library/src/androidMain/kotlin/com/mercury/sqkon/db/SqkonDatabaseDriver.android.kt` — same.
- `library/src/jvmTest/kotlin/com/mercury/sqkon/db/SchemaMigrationTest.kt` — switch the migration call from `SqkonDatabase.Schema.migrate(driver, 1L, 2L)` to the new schema (via bridge, since the underlying `SqlDriver` is what `driver.execute(...)` uses for v1 fixture setup).
- `library/build.gradle.kts` — delete the `sqldelight { databases { create("SqkonDatabase") { ... } } }` block (lines ~120–131). Keep the plugin alias so Phase 7 can yank it cleanly.
- `.github/workflows/ci.yml` — delete the `Verify SqlDelight Migration` step (lines 33–34).

**Deleted files:**
- `library/src/commonMain/sqldelight/com/mercury/sqkon/db/sqldelight/entity.sq`
- `library/src/commonMain/sqldelight/com/mercury/sqkon/db/sqldelight/metadata.sq`
- `library/src/commonMain/sqldelight/migrations/1.sqm`
- `library/src/commonMain/sqldelight/databases/1.db`
- The empty `library/src/commonMain/sqldelight/` tree after removal.

**Untouched:** `EntityQueries.kt`, `MetadataQueries.kt`, all `SqkonDriver`/`SqkonQuery`/`SqkonTransaction` code, `KeyValueStorage`, `Sqkon.kt`, `SqkonDispatchers.kt`. Phase 3 already hand-rolled the query path.

---

## Existing Code to Reuse

- **`SqkonDriver` interface** at `library/src/commonMain/kotlin/com/mercury/sqkon/db/internal/SqkonDriver.kt`. Its `executeUpdate(identifier, sql, parameters, binders)` method is exactly what `SqkonDatabaseSchema.create()` / `migrate()` will call (no binders, `parameters = 0`).
- **`SqlDelightSqkonDriver`** at `library/src/commonMain/kotlin/com/mercury/sqkon/db/internal/sqldelight/SqlDelightSqkonDriver.kt`. Wraps a `SqlDriver` as a `SqkonDriver`. The bridge adapter constructs one of these inline when SQLDelight hands us a raw `SqlDriver` during `create()` / `migrate()`.
- **`SchemaParityTest`** at `library/src/jvmTest/kotlin/com/mercury/sqkon/db/SchemaParityTest.kt`. No change needed — it goes through `driverFactory().createDriver()`, which now resolves to our hand-rolled schema via the bridge.
- **`SchemaSnapshotUtil`** at `library/src/jvmTest/kotlin/com/mercury/sqkon/db/SchemaSnapshotUtil.kt`. Snapshot dumper used by both parity and migration tests. No change.
- **`sqkon-schema-v2.snapshot`** at `library/src/jvmTest/resources/sqkon-schema-v2.snapshot`. Already exists. No regeneration needed — it was authored in Phase 0 against the same v2 shape we're hand-rolling here.

---

## Critical Invariants

1. **Statement order**: `SqkonDatabaseSchema.create(driver)` MUST emit exactly: entity CREATE TABLE → metadata CREATE TABLE → `CREATE INDEX idx_entity_read_at` → `CREATE INDEX idx_entity_write_at` → `CREATE INDEX idx_entity_expires_at`. Matches `SqkonDatabaseImpl.Schema.create()` byte-for-byte. `SchemaParityTest` is the canary.
2. **Migration guard**: `migrate()` body runs iff `oldVersion <= 1 && newVersion > 1`. Same guard the generated code uses — ensures a degenerate `(0, 2)` call still does the right thing.
3. **Migration body order** matches `1.sqm` verbatim: `CREATE TABLE metadata` → `ALTER entity ADD read_at` → `ALTER entity ADD write_at` → `UPDATE entity SET write_at = CURRENT_TIMESTAMP` → 3× `CREATE INDEX`.
4. **AfterVersion callbacks**: ignored by the bridge. Grep confirms no caller registers any. If a future migration needs them, extend `SqkonSchema.migrate(...)` then.
5. **`version = 2L`** — same Long type SQLDelight uses.

---

## Tasks

### Task 1: Add `SqkonSchema` interface

**Files:**
- Create: `library/src/commonMain/kotlin/com/mercury/sqkon/db/internal/schema/SqkonSchema.kt`

- [ ] **Step 1: Create the file with the interface**

```kotlin
package com.mercury.sqkon.db.internal.schema

import com.mercury.sqkon.db.internal.SqkonDriver

internal interface SqkonSchema {
    val version: Long

    fun create(driver: SqkonDriver)

    fun migrate(driver: SqkonDriver, oldVersion: Long, newVersion: Long)
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :library:compileCommonMainKotlinMetadata`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add library/src/commonMain/kotlin/com/mercury/sqkon/db/internal/schema/SqkonSchema.kt
git commit -m "feat(arch): add internal SqkonSchema interface (MOB-3291)"
```

---

### Task 2: Add `SqkonDatabaseSchema` concrete object

**Files:**
- Create: `library/src/commonMain/kotlin/com/mercury/sqkon/db/internal/schema/SqkonDatabaseSchema.kt`

- [ ] **Step 1: Create the file**

```kotlin
package com.mercury.sqkon.db.internal.schema

import com.mercury.sqkon.db.internal.SqkonDriver

internal object SqkonDatabaseSchema : SqkonSchema {

    override val version: Long = 2L

    override fun create(driver: SqkonDriver) {
        driver.executeUpdate(
            identifier = null,
            sql = """
                |CREATE TABLE entity (
                |    entity_name TEXT NOT NULL,
                |    entity_key TEXT NOT NULL,
                |    -- UTC timestamp in milliseconds
                |    added_at INTEGER NOT NULL DEFAULT CURRENT_TIMESTAMP,
                |    -- UTC timestamp in milliseconds
                |    updated_at INTEGER NOT NULL DEFAULT CURRENT_TIMESTAMP,
                |    -- UTC timestamp in milliseconds
                |    expires_at INTEGER,
                |    -- JSONB Blob use jsonb_ operators
                |    value BLOB NOT NULL,
                |    -- UTC timestamp in milliseconds
                |    read_at INTEGER,
                |    -- UTC timestamp in milliseconds
                |    write_at INTEGER,
                |    PRIMARY KEY (entity_name, entity_key)
                |)
            """.trimMargin(),
            parameters = 0,
        )
        driver.executeUpdate(
            identifier = null,
            sql = """
                |CREATE TABLE metadata (
                |    entity_name TEXT NOT NULL PRIMARY KEY,
                |    lastReadAt INTEGER,
                |    lastWriteAt INTEGER
                |)
            """.trimMargin(),
            parameters = 0,
        )
        driver.executeUpdate(null, "CREATE INDEX idx_entity_read_at ON entity (read_at)", 0)
        driver.executeUpdate(null, "CREATE INDEX idx_entity_write_at ON entity (write_at)", 0)
        driver.executeUpdate(null, "CREATE INDEX idx_entity_expires_at ON entity (expires_at)", 0)
    }

    override fun migrate(driver: SqkonDriver, oldVersion: Long, newVersion: Long) {
        if (oldVersion <= 1 && newVersion > 1) {
            driver.executeUpdate(
                identifier = null,
                sql = """
                    |CREATE TABLE metadata (
                    |    entity_name TEXT NOT NULL PRIMARY KEY,
                    |    lastReadAt INTEGER,
                    |    lastWriteAt INTEGER
                    |)
                """.trimMargin(),
                parameters = 0,
            )
            driver.executeUpdate(null, "ALTER TABLE entity ADD COLUMN read_at INTEGER", 0)
            driver.executeUpdate(null, "ALTER TABLE entity ADD COLUMN write_at INTEGER", 0)
            driver.executeUpdate(null, "UPDATE entity SET write_at = CURRENT_TIMESTAMP", 0)
            driver.executeUpdate(null, "CREATE INDEX idx_entity_read_at ON entity (read_at)", 0)
            driver.executeUpdate(null, "CREATE INDEX idx_entity_write_at ON entity (write_at)", 0)
            driver.executeUpdate(null, "CREATE INDEX idx_entity_expires_at ON entity (expires_at)", 0)
        }
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :library:compileCommonMainKotlinMetadata`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add library/src/commonMain/kotlin/com/mercury/sqkon/db/internal/schema/SqkonDatabaseSchema.kt
git commit -m "feat: hand-roll SqkonDatabaseSchema v2 (MOB-3291)"
```

---

### Task 3: Add the `toSqlDelightSchema()` bridge adapter

**Files:**
- Modify: `library/src/commonMain/kotlin/com/mercury/sqkon/db/internal/sqldelight/SqlDelightSqkonDriver.kt`

- [ ] **Step 1: Append the extension function to the file**

Add (after the existing `SqlDelightSqkonDriver` class, at file end):

```kotlin
internal fun SqkonSchema.toSqlDelightSchema(): SqlSchema<QueryResult.Value<Unit>> =
    object : SqlSchema<QueryResult.Value<Unit>> {
        override val version: Long = this@toSqlDelightSchema.version

        override fun create(driver: SqlDriver): QueryResult.Value<Unit> {
            this@toSqlDelightSchema.create(SqlDelightSqkonDriver(driver))
            return QueryResult.Unit
        }

        override fun migrate(
            driver: SqlDriver,
            oldVersion: Long,
            newVersion: Long,
            vararg callbacks: AfterVersion,
        ): QueryResult.Value<Unit> {
            this@toSqlDelightSchema.migrate(SqlDelightSqkonDriver(driver), oldVersion, newVersion)
            return QueryResult.Unit
        }
    }
```

Required imports (add to top of file if not present):
```kotlin
import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlSchema
import com.mercury.sqkon.db.internal.schema.SqkonSchema
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :library:compileCommonMainKotlinMetadata`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add library/src/commonMain/kotlin/com/mercury/sqkon/db/internal/sqldelight/SqlDelightSqkonDriver.kt
git commit -m "feat(arch): SqkonSchema → SqlSchema bridge for AndroidxSqliteDriver (MOB-3291)"
```

---

### Task 4: Update `SchemaMigrationTest` to call the new schema

**Files:**
- Modify: `library/src/jvmTest/kotlin/com/mercury/sqkon/db/SchemaMigrationTest.kt`

The current test calls `SqkonDatabase.Schema.migrate(driver, 1L, 2L).value`. After Phase 4 deletes the `sqldelight {} databases {}` block, `SqkonDatabase.Schema` will not exist. The test must call `SqkonDatabaseSchema.migrate(...)` through the bridge (or directly, wrapping the test's `SqlDriver` in `SqlDelightSqkonDriver`).

- [ ] **Step 1: Read the file to find the migration call site**

Run: `grep -n 'SqkonDatabase.Schema.migrate' library/src/jvmTest/kotlin/com/mercury/sqkon/db/SchemaMigrationTest.kt`
Expected: one or two hits.

- [ ] **Step 2: Replace the call**

Replace:
```kotlin
SqkonDatabase.Schema.migrate(driver, 1L, 2L).value
```
With:
```kotlin
SqkonDatabaseSchema.migrate(SqlDelightSqkonDriver(driver), 1L, 2L)
```

Add imports:
```kotlin
import com.mercury.sqkon.db.internal.schema.SqkonDatabaseSchema
import com.mercury.sqkon.db.internal.sqldelight.SqlDelightSqkonDriver
```

Remove the now-unused `import com.mercury.sqkon.db.sqldelight.SqkonDatabase` (or whatever the current import path is).

If the test also has a v1 noop schema (`override fun migrate(..., vararg callbacks: AfterVersion): QueryResult.Value<Unit> = QueryResult.Value(Unit)`) for fixture setup, leave it — that bit fakes a v1 `SqlSchema` for the driver constructor and is unrelated to our migration call.

- [ ] **Step 3: Run the test against the bridge (generated schema still present)**

Run: `./gradlew :library:jvmTest --tests "*.SchemaMigrationTest"`
Expected: PASS — same assertions, now via hand-rolled migration.

- [ ] **Step 4: Run the parity test too**

Run: `./gradlew :library:jvmTest --tests "*.SchemaParityTest"`
Expected: PASS — schema snapshot still matches (we haven't switched the driver yet, but the migration body parity is proven by Task 4 Step 3).

- [ ] **Step 5: Commit**

```bash
git add library/src/jvmTest/kotlin/com/mercury/sqkon/db/SchemaMigrationTest.kt
git commit -m "test: route SchemaMigrationTest through SqkonDatabaseSchema (MOB-3291)"
```

---

### Task 5: Wire `SqkonDatabaseSchema.toSqlDelightSchema()` into the JVM driver factory

**Files:**
- Modify: `library/src/jvmMain/kotlin/com/mercury/sqkon/db/SqkonDatabaseDriver.jvm.kt`

- [ ] **Step 1: Replace the `schema = ...` argument**

Before:
```kotlin
return AndroidxSqliteDriver(
    driver = BundledSQLiteDriver(),
    databaseType = databaseType,
    schema = SqkonDatabase.Schema,
    configuration = ...,
)
```

After:
```kotlin
return AndroidxSqliteDriver(
    driver = BundledSQLiteDriver(),
    databaseType = databaseType,
    schema = SqkonDatabaseSchema.toSqlDelightSchema(),
    configuration = ...,
)
```

Update imports:
- Remove `import com.mercury.sqkon.db.sqldelight.SqkonDatabase`
- Add `import com.mercury.sqkon.db.internal.schema.SqkonDatabaseSchema`
- Add `import com.mercury.sqkon.db.internal.sqldelight.toSqlDelightSchema`

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :library:compileKotlinJvm`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Run the full JVM test suite**

Run: `./gradlew :library:jvmTest`
Expected: all green — `SchemaParityTest` now exercises the hand-rolled `create()` (driver factory uses bridge), `SchemaMigrationTest` exercises the hand-rolled `migrate()`.

If `SchemaParityTest` fails: the statement order in `SqkonDatabaseSchema.create()` does not match `SqkonDatabaseImpl.Schema.create()`. Compare the snapshot diff in the failure output against the expected `sqkon-schema-v2.snapshot` and fix.

- [ ] **Step 4: Commit**

```bash
git add library/src/jvmMain/kotlin/com/mercury/sqkon/db/SqkonDatabaseDriver.jvm.kt
git commit -m "feat: JVM driver factory uses SqkonDatabaseSchema (MOB-3291)"
```

---

### Task 6: Wire `SqkonDatabaseSchema.toSqlDelightSchema()` into the Android driver factory

**Files:**
- Modify: `library/src/androidMain/kotlin/com/mercury/sqkon/db/SqkonDatabaseDriver.android.kt`

- [ ] **Step 1: Mirror the JVM change**

Before:
```kotlin
return AndroidxSqliteDriver(
    connectionFactory = SqkonAndroidxSqliteConnectionFactory(bundledDriver),
    databaseType = ...,
    schema = SqkonDatabase.Schema,
    configuration = ...,
)
```

After:
```kotlin
return AndroidxSqliteDriver(
    connectionFactory = SqkonAndroidxSqliteConnectionFactory(bundledDriver),
    databaseType = ...,
    schema = SqkonDatabaseSchema.toSqlDelightSchema(),
    configuration = ...,
)
```

Update imports the same way as Task 5 Step 1.

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :library:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add library/src/androidMain/kotlin/com/mercury/sqkon/db/SqkonDatabaseDriver.android.kt
git commit -m "feat: Android driver factory uses SqkonDatabaseSchema (MOB-3291)"
```

---

### Task 7: Delete `.sq` / `.sqm` / `.db` files

**Files (delete):**
- `library/src/commonMain/sqldelight/com/mercury/sqkon/db/sqldelight/entity.sq`
- `library/src/commonMain/sqldelight/com/mercury/sqkon/db/sqldelight/metadata.sq`
- `library/src/commonMain/sqldelight/migrations/1.sqm`
- `library/src/commonMain/sqldelight/databases/1.db`
- Resulting empty directory tree under `library/src/commonMain/sqldelight/`.

- [ ] **Step 1: Confirm nothing else references the `com.mercury.sqkon.db.sqldelight` package outside generated code**

Run:
```bash
grep -rn 'com.mercury.sqkon.db.sqldelight' library/src --include='*.kt'
```
Expected: zero non-test hits (Task 4 already cleaned the test). If any hit remains, fix it now (replace with `internal.schema.SqkonDatabaseSchema` or remove import).

- [ ] **Step 2: Delete the files and the empty `sqldelight/` tree**

```bash
git rm library/src/commonMain/sqldelight/com/mercury/sqkon/db/sqldelight/entity.sq
git rm library/src/commonMain/sqldelight/com/mercury/sqkon/db/sqldelight/metadata.sq
git rm library/src/commonMain/sqldelight/migrations/1.sqm
git rm library/src/commonMain/sqldelight/databases/1.db
# Remove now-empty parent dirs (git rm doesn't touch dirs but they need to go from disk):
rm -rf library/src/commonMain/sqldelight
```

- [ ] **Step 3: Commit**

```bash
git commit -m "chore: delete sqldelight .sq/.sqm/.db sources (MOB-3291)"
```

---

### Task 8: Drop the `sqldelight { databases { ... } }` block from `library/build.gradle.kts`

**Files:**
- Modify: `library/build.gradle.kts`

The ticket calls out lines 90–104, but the explore found the block at lines 120–131. Match the block by content, not line number.

- [ ] **Step 1: Locate the block**

Run: `grep -n '^sqldelight' library/build.gradle.kts`

- [ ] **Step 2: Delete the entire `sqldelight { databases { create("SqkonDatabase") { ... } } }` block**

Before:
```kotlin
sqldelight {
    databases {
        create("SqkonDatabase") {
            generateAsync = false
            packageName.set("com.mercury.sqkon.db.sqldelight")
            schemaOutputDirectory.set(file("src/commonMain/sqldelight/databases"))
            dialect("app.cash.sqldelight:sqlite-3-38-dialect:$VERSION")
        }
    }
}
```

After: block removed entirely. Keep the `alias(libs.plugins.sqldelight)` line in the `plugins { }` block — Phase 7 removes the plugin alias.

- [ ] **Step 3: Verify Gradle still configures and compiles**

Run: `./gradlew :library:compileKotlinJvm`
Expected: BUILD SUCCESSFUL. (No generated `SqkonDatabaseImpl.kt` anymore — confirms nothing imports it.)

- [ ] **Step 4: Run the full JVM test suite**

Run: `./gradlew :library:jvmTest`
Expected: all green. This is the moment of truth — hand-rolled schema is now the sole source.

- [ ] **Step 5: Commit**

```bash
git add library/build.gradle.kts
git commit -m "build: drop sqldelight databases block (MOB-3291)"
```

---

### Task 9: Remove `verifySqlDelightMigration` from CI

**Files:**
- Modify: `.github/workflows/ci.yml`

- [ ] **Step 1: Locate the step**

Run: `grep -n 'verifySqlDelightMigration' .github/workflows/ci.yml`
Expected: lines 33–34 (per explore).

- [ ] **Step 2: Delete the step**

Before:
```yaml
      - name: Verify SqlDelight Migration
        run: ./gradlew verifySqlDelightMigration
```

After: lines removed. `jvmTest` (which runs immediately after) already executes `SchemaParityTest` + `SchemaMigrationTest` — the new gate.

- [ ] **Step 3: Confirm no other references**

Run: `grep -rn 'verifySqlDelightMigration' . --exclude-dir=.git --exclude-dir=build`
Expected: zero hits.

- [ ] **Step 4: Commit**

```bash
git add .github/workflows/ci.yml
git commit -m "ci: drop verifySqlDelightMigration step, SchemaParityTest is the gate (MOB-3291)"
```

---

### Task 10: Final end-to-end verification

- [ ] **Step 1: Clean build + full test**

```bash
./gradlew clean
./gradlew :library:jvmTest
```
Expected: BUILD SUCCESSFUL, all tests pass, including:
- `SchemaParityTest.freshSchema_matchesFixture` — proves `SqkonDatabaseSchema.create()` matches the v2 snapshot byte-for-byte.
- `SchemaMigrationTest.migrate_v1_to_v2_matchesV2Snapshot_andPreservesData` — proves `SqkonDatabaseSchema.migrate(1, 2)` matches the snapshot structurally and preserves seeded data.
- All `EntityQueries*Test`, `KeyValueStorage*Test`, `JsonbStorageTest`, `KeysetPagingTest`, `OffsetPagingTest`, `JsonPathBuilderTest`, `StatementCacheEvictionTest`, transaction tests, dispatcher test — these prove the bridge passes traffic correctly.

- [ ] **Step 2: Confirm `.sq` deletion is total**

```bash
find library/src -name '*.sq' -o -name '*.sqm' -o -name '*.db'
```
Expected: no output.

- [ ] **Step 3: Confirm no surviving references to `SqkonDatabase` generated type**

```bash
grep -rn 'com.mercury.sqkon.db.sqldelight.SqkonDatabase' library/src
```
Expected: no output.

- [ ] **Step 4: Squash-merge candidate commit message** (only if squashing PR commits)

```
feat: hand-roll schema and migrations, retire sqldelight plugin databases

Implements MOB-3291. Replaces SQLDelight-generated SqkonDatabase.Schema
with internal SqkonSchema/SqkonDatabaseSchema. Bridges to
SqlSchema<QueryResult.Value<Unit>> for AndroidxSqliteDriver until Phase 6.
SchemaParityTest + SchemaMigrationTest replace verifySqlDelightMigration.
```

If keeping per-task commits, leave them as-is — each one is conventional and bisectable.

- [ ] **Step 5: Open PR**

Use `gh pr create` with title `feat: hand-roll schema and migrations, retire sqldelight plugin databases (MOB-3291)` and body referencing Linear ticket + summary.

---

## Verification (end-to-end)

After all tasks complete, the following must hold:

1. **Schema parity** — `./gradlew :library:jvmTest --tests "*.SchemaParityTest"` passes. The hand-rolled `create()` produces a schema byte-for-byte identical to the v2 snapshot.
2. **Migration parity** — `./gradlew :library:jvmTest --tests "*.SchemaMigrationTest"` passes. The hand-rolled `migrate(1, 2)` upgrades a v1 fixture to v2 with data preserved and `write_at` backfilled.
3. **Full suite** — `./gradlew :library:jvmTest` passes. Every existing test continues to pass, proving the bridge correctly routes SQL through `SqkonDriver` → `SqlDriver` → `BundledSQLiteDriver`.
4. **CI** — Push the branch; the GitHub Actions `jvm-tests` job is green and the (now-removed) `Verify SqlDelight Migration` step no longer exists in the workflow.
5. **Tree hygiene** — `find library/src -name '*.sq' -o -name '*.sqm' -o -name '*.db'` returns nothing; `grep -rn 'com.mercury.sqkon.db.sqldelight' library/src` returns nothing.
6. **Android sanity** (optional, CI-only per CLAUDE.md) — `./gradlew :library:compileDebugKotlinAndroid` succeeds locally; instrumented `allDevicesDebugAndroidTest` is exercised in CI.

## Risks & Mitigations

| Risk | Mitigation |
|------|------------|
| Statement order in `SqkonDatabaseSchema.create()` drifts from generated emit order | `SchemaParityTest` is exact-match against `sqkon-schema-v2.snapshot`. Snapshot uses `PRAGMA table_info` + sorted `sqlite_master` rows — drift surfaces immediately. |
| Migration body diverges from `1.sqm` | `SchemaMigrationTest` seeds a v1 row, runs the hand-rolled migrate, asserts row survives + `write_at` backfilled + structural snapshot matches. |
| `AfterVersion` callback path silently breaks future migrations | Bridge ignores `vararg callbacks`. Grep confirms no caller registers any today. When Phase 5+ needs them, extend `SqkonSchema.migrate(...)` signature. |
| Removing `sqldelight { databases { } }` block accidentally removes the plugin too | Only the `databases { ... }` inner block goes; the `alias(libs.plugins.sqldelight)` plugin entry stays. Phase 7 yanks the plugin entirely. |
| Some test or platform code still imports generated `com.mercury.sqkon.db.sqldelight.SqkonDatabase` | Task 7 Step 1 + Task 10 Step 3 grep checks catch this. Task 4 pre-emptively rewrites the only known reference (`SchemaMigrationTest`). |
| `1.db` deletion breaks SQLDelight's "verify migration" baseline before its task is removed | We delete the `verifySqlDelightMigration` CI step in Task 9 and the gradle databases block in Task 8 — both happen *after* we've stopped relying on the generated schema (Task 5/6). The plugin task technically still exists locally but is never invoked. |
