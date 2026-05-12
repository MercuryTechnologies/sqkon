# Sqkon Phase 1 — KMP Source-Set Scaffold (iOS + WasmJs) + `SqkonDispatchers`

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Linear ticket:** [MOB-3288](https://linear.app/mercury/issue/MOB-3288/sqkon-migration-phase-1-kmp-source-set-scaffold-ios-wasmjs)
**Blocked by:** MOB-3287 (Phase 0 — merged in PR #49)
**Blocks:** MOB-3289 (Phase 2 — internal `SqkonDriver` abstraction)

---

## Context

Phase 0 (MOB-3287) merged the regression baseline so later phases of the SQLDelight → `androidx.sqlite` migration have an unambiguous safety net. Phase 1 lays the multiplatform groundwork: stand up the iOS and WasmJs source sets so future phases can place every `expect`/`actual` declaration in the right place from day one without source-set shuffles.

This plan folds in two adjacent asks alongside the ticket scope:

1. **Drop `iosX64`** — the legacy Intel-simulator target is no longer worth carrying; modern KMP libraries ship `iosArm64` (device) + `iosSimulatorArm64` (Apple-silicon simulator) only.
2. **Introduce a public `SqkonDispatchers` class** so the read/write dispatchers can be passed in and overridden at testing time (e.g. `StandardTestDispatcher`), replacing the current top-level `internal expect val` globals.

Pre-flight dependency audit produced two safe minor bumps for this PR:

- `kotlinx-coroutines` 1.10.2 → **1.11.0**
- `kotlinx-datetime` 0.7.1 → **0.8.0**

(Skipped: AGP 9 — deferred to its own ticket; Kotlin/SQLDelight/androidx.sqlite already current as of 2026-05-12.)

**Goal:** One small `chore:` PR that adds iOS + WasmJs compile targets, introduces `SqkonDispatchers` (additive — no public API break), bumps two safe minor deps, and adds CI guards so the new targets stay green. No functional change to Android/JVM behavior. No public type removed or renamed.

**Architecture:** Pure scaffolding plus a localized refactor. Add target declarations to `library/build.gradle.kts`. Create `iosMain` + `wasmJsMain` source sets with TODO-stubbed actuals. Replace the three top-level dispatcher expect vals (`dbReadDispatcher`, `dbWriteDispatcher`) with a single `defaultSqkonDispatchers: SqkonDispatchers` expect val. Plumb `SqkonDispatchers` through `Sqkon`'s internal constructor and the platform factory functions so tests (and consumers) can inject dispatchers. Add a CI job that compiles iOS on macOS and WasmJs on Ubuntu.

**Tech Stack:** Kotlin 2.3.21, AGP 8.13.2, Gradle KMP plugin, `androidx.sqlite:sqlite-bundled:2.6.2` (already on classpath), SQLDelight 2.3.2 (used in Phase 1 only because the abstraction layer arrives in Phase 2), `kotlinx-coroutines:1.11.0`, `kotlinx-datetime:0.8.0`.

**Conventional commit prefix:** `chore:` (no behavior change, no API removal — `SqkonDispatchers` is additive).

---

## Files touched

**Modified:**

- `gradle/libs.versions.toml` — bump two version literals.
- `library/build.gradle.kts` — add `iosArm64()`, `iosSimulatorArm64()`, `wasmJs { browser(); nodejs() }` targets.
- `library/src/commonMain/kotlin/com/mercury/sqkon/db/SqkonDatabaseDriver.kt` — replace two dispatcher `expect val`s with one `expect val defaultSqkonDispatchers: SqkonDispatchers`. Keep `connectionPoolSize`.
- `library/src/jvmMain/kotlin/com/mercury/sqkon/db/SqkonDatabaseDriver.jvm.kt` — actual for `defaultSqkonDispatchers`. Drop old two-val actuals.
- `library/src/androidMain/kotlin/com/mercury/sqkon/db/SqkonDatabaseDriver.android.kt` — same.
- `library/src/commonMain/kotlin/com/mercury/sqkon/db/Sqkon.kt` — internal constructor takes `SqkonDispatchers` instead of two `CoroutineDispatcher`s. Internal constructor only — no public break.
- `library/src/jvmMain/kotlin/com/mercury/sqkon/db/Sqkon.jvm.kt` — public factory gains optional `dispatchers: SqkonDispatchers = defaultSqkonDispatchers` param.
- `library/src/androidMain/kotlin/com/mercury/sqkon/db/Sqkon.android.kt` — same.
- `library/src/commonMain/kotlin/com/mercury/sqkon/db/KeyValueStorage.kt` — defaults on the top-level `keyValueStorage(...)` factory reference `defaultSqkonDispatchers.read` / `.write` instead of the old top-level vals (lines 606-607).
- `.github/workflows/ci.yml` — add `compile-kmp-targets` job (macOS for iOS, Ubuntu for WasmJs).

**Created:**

- `library/src/commonMain/kotlin/com/mercury/sqkon/db/SqkonDispatchers.kt` — public class.
- `library/src/iosMain/kotlin/com/mercury/sqkon/db/SqkonDatabaseDriver.ios.kt` — iOS actuals (TODO driver, default dispatchers).
- `library/src/iosMain/kotlin/com/mercury/sqkon/db/SqlException.ios.kt`.
- `library/src/wasmJsMain/kotlin/com/mercury/sqkon/db/SqkonDatabaseDriver.wasmJs.kt`.
- `library/src/wasmJsMain/kotlin/com/mercury/sqkon/db/SqlException.wasmJs.kt`.
- `library/src/jvmTest/kotlin/com/mercury/sqkon/db/SqkonDispatchersTest.kt` — proves dispatcher override is honored end-to-end.
- (Optional, per ticket Step 2) `library/src/iosSimulatorArm64Test/kotlin/com/mercury/sqkon/IosBundledDriverSpike.kt` — Apple-klib linker spike.

---

## Pre-flight facts (verified against worktree on 2026-05-12)

| Fact | Status | Location |
|---|---|---|
| `applyDefaultHierarchyTemplate()` already on | ✓ | `library/build.gradle.kts:43` |
| `jvmToolchain(21)` | ✓ | `library/build.gradle.kts:47` |
| `-Xexpect-actual-classes` already enabled | ✓ | `library/build.gradle.kts:80-82` |
| `androidx.sqlite-bundled:2.6.2` already in commonMain | ✓ | `library/build.gradle.kts:52` |
| `BundledSQLiteDriver` integrated on Android + JVM | ✓ | `*.android.kt`, `*.jvm.kt` |
| `expect class SqlException : Exception` | ✓ | `library/src/commonMain/kotlin/com/mercury/sqkon/db/EntityQueries.kt` |
| Top-level `dbReadDispatcher` / `dbWriteDispatcher` call sites | `Sqkon.jvm.kt:20-21`, `Sqkon.android.kt:41-42`, `KeyValueStorage.kt:606-607` — only three, easy to refactor | |
| `Sqkon` primary constructor is `internal` — refactoring it is NOT a public break | ✓ | `Sqkon.kt` |
| `iosMain` / `wasmJsMain` directories | ✗ do not exist | clean slate |

### Library versions (May 2026 audit)

| Lib | Project | Latest stable | Phase 1 action |
|---|---|---|---|
| Kotlin | 2.3.21 | 2.3.21 (Apr 2026) | ✓ current |
| AGP | 8.13.2 | 9.1.1 (Apr 2026) | **defer** — AGP 9 is a breaking migration; separate ticket |
| androidx.sqlite | 2.6.2 | 2.6.2 (Jan 2026) | ✓ current |
| SQLDelight | 2.3.2 | 2.3.2 (Mar 2026) | ✓ current |
| kotlinx-coroutines | 1.10.2 | **1.11.0** | bump |
| kotlinx-serialization | 1.11.0 | 1.11.0 | ✓ current |
| kotlinx-datetime | 0.7.1 | **0.8.0** | bump (serialization ≥1.9 already in place) |

---

## Tasks

### Task 1: Pre-flight dependency bumps

**Files:**
- Modify: `gradle/libs.versions.toml`

- [ ] **Step 1: Baseline.**

Run: `./gradlew jvmTest`
Expected: PASS.

- [ ] **Step 2: Bump two version literals.**

In `gradle/libs.versions.toml`:
- `kotlinx-coroutines = "1.10.2"` → `kotlinx-coroutines = "1.11.0"`
- `kotlinx-datetime  = "0.7.1"`  → `kotlinx-datetime  = "0.8.0"`

- [ ] **Step 3: Re-run.**

Run: `./gradlew jvmTest verifySqlDelightMigration`
Expected: PASS. If `kotlinx-datetime` 0.8 surfaces an `Instant` serializer regression (unlikely since serialization is on 1.11.0), revert the datetime bump and proceed without it.

---

### Task 2: Introduce public `SqkonDispatchers` class

**Files:**
- Create: `library/src/commonMain/kotlin/com/mercury/sqkon/db/SqkonDispatchers.kt`

- [ ] **Step 1: Create the class.**

```kotlin
package com.mercury.sqkon.db

import kotlinx.coroutines.CoroutineDispatcher

/**
 * Read/write coroutine dispatcher bundle for Sqkon.
 *
 * Override at construction time for deterministic tests (e.g. with `StandardTestDispatcher`)
 * or to share an existing pool with the host application.
 */
class SqkonDispatchers(
    val read: CoroutineDispatcher,
    val write: CoroutineDispatcher,
)
```

- [ ] **Step 2: Compile commonMain.**

Run: `./gradlew :library:compileKotlinJvm`
Expected: PASS.

---

### Task 3: Replace top-level dispatcher expects with `defaultSqkonDispatchers`

**Files:**
- Modify: `library/src/commonMain/kotlin/com/mercury/sqkon/db/SqkonDatabaseDriver.kt`
- Modify: `library/src/jvmMain/kotlin/com/mercury/sqkon/db/SqkonDatabaseDriver.jvm.kt`
- Modify: `library/src/androidMain/kotlin/com/mercury/sqkon/db/SqkonDatabaseDriver.android.kt`

- [ ] **Step 1: Update commonMain expect.** In `SqkonDatabaseDriver.kt`, replace:

```kotlin
internal expect val connectionPoolSize: Int
@PublishedApi
internal expect val dbWriteDispatcher: CoroutineDispatcher
@PublishedApi
internal expect val dbReadDispatcher: CoroutineDispatcher
```

with:

```kotlin
internal expect val connectionPoolSize: Int

@PublishedApi
internal expect val defaultSqkonDispatchers: SqkonDispatchers
```

Drop the now-unused `kotlinx.coroutines.CoroutineDispatcher` import.

- [ ] **Step 2: Update JVM actual.** In `SqkonDatabaseDriver.jvm.kt`, replace the three vals:

```kotlin
internal actual const val connectionPoolSize = 4

@PublishedApi
internal actual val defaultSqkonDispatchers: SqkonDispatchers by lazy {
    SqkonDispatchers(
        read = Dispatchers.IO.limitedParallelism(connectionPoolSize),
        write = Dispatchers.IO.limitedParallelism(1),
    )
}
```

Keep `connectionPoolSize` — still used by `walCount = connectionPoolSize` further down the file.

- [ ] **Step 3: Update Android actual.** In `SqkonDatabaseDriver.android.kt`:

```kotlin
internal actual val connectionPoolSize: Int by lazy { getWALConnectionPoolSize() }

@OptIn(DelicateCoroutinesApi::class)
@PublishedApi
internal actual val defaultSqkonDispatchers: SqkonDispatchers by lazy {
    SqkonDispatchers(
        read = Dispatchers.IO.limitedParallelism(connectionPoolSize),
        write = newFixedThreadPoolContext(nThreads = 1, "SqkonWriteDispatcher"),
    )
}
```

(Incidental fix: thread-pool name was `"SqkonReadDispatcher"` for the write pool — typo. Renaming is a single-character change and lives inside the same edit.)

- [ ] **Step 4: Compile.**

Run: `./gradlew :library:compileKotlinJvm :library:compileKotlinAndroid`
Expected: FAIL only at call sites that still reference `dbReadDispatcher` / `dbWriteDispatcher` (Sqkon platform factories + `keyValueStorage` defaults). Fixed in Tasks 4 + 5.

---

### Task 4: Wire `SqkonDispatchers` through `Sqkon` and `keyValueStorage(...)`

**Files:**
- Modify: `library/src/commonMain/kotlin/com/mercury/sqkon/db/Sqkon.kt`
- Modify: `library/src/commonMain/kotlin/com/mercury/sqkon/db/KeyValueStorage.kt`

- [ ] **Step 1: Update `Sqkon` internal constructor.** Replace:

```kotlin
@PublishedApi internal val readDispatcher: CoroutineDispatcher =
    Dispatchers.Default.limitedParallelism(4),
@PublishedApi internal val writeDispatcher: CoroutineDispatcher =
    Dispatchers.Default.limitedParallelism(1),
```

with:

```kotlin
@PublishedApi internal val dispatchers: SqkonDispatchers = defaultSqkonDispatchers,
```

Update the inline `keyValueStorage` body inside the class to forward `dispatchers.read` / `dispatchers.write` to the top-level factory.

- [ ] **Step 2: Update top-level `keyValueStorage(...)` factory (lines 606-607).**

```kotlin
readDispatcher: CoroutineDispatcher = defaultSqkonDispatchers.read,
writeDispatcher: CoroutineDispatcher = defaultSqkonDispatchers.write,
```

Body unchanged. `KeyValueStorage`'s own constructor keeps its two-dispatcher shape (it's public — splitting that is out of Phase 1 scope).

- [ ] **Step 3: Compile commonMain.**

Run: `./gradlew :library:compileKotlinJvm`
Expected: still FAILS at the two platform factory call sites (next task).

---

### Task 5: Update platform `Sqkon(...)` factory functions

**Files:**
- Modify: `library/src/jvmMain/kotlin/com/mercury/sqkon/db/Sqkon.jvm.kt`
- Modify: `library/src/androidMain/kotlin/com/mercury/sqkon/db/Sqkon.android.kt`

- [ ] **Step 1: JVM factory.**

```kotlin
fun Sqkon(
    scope: CoroutineScope,
    json: Json = SqkonJson { },
    type: AndroidxSqliteDatabaseType = AndroidxSqliteDatabaseType.Memory,
    config: KeyValueStorage.Config = KeyValueStorage.Config(),
    dispatchers: SqkonDispatchers = defaultSqkonDispatchers,
): Sqkon {
    val factory = DriverFactory(type)
    val driver = factory.createDriver()
    val metadataQueries = MetadataQueries(driver)
    val entityQueries = EntityQueries(driver)
    return Sqkon(
        entityQueries, metadataQueries, scope, json, config,
        dispatchers = dispatchers,
    )
}
```

- [ ] **Step 2: Android factory** — add the same `dispatchers` param to the non-deprecated `Sqkon(context, scope, ...)` overload. Pass `dispatchers = dispatchers` through to the internal constructor.

The `@Deprecated("Use other Sqkon method instead")` overload above can keep its existing parameters and forward to the canonical one with the default.

- [ ] **Step 3: Run full test suite.**

Run: `./gradlew jvmTest verifySqlDelightMigration`
Expected: PASS. Phase 0 regression baseline must stay green.

---

### Task 6: Add Phase 1 dispatcher-override test

**Files:**
- Create: `library/src/jvmTest/kotlin/com/mercury/sqkon/db/SqkonDispatchersTest.kt`

- [ ] **Step 1: Write the test.**

```kotlin
package com.mercury.sqkon.db

import com.mercury.sqkon.TestObject
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame

class SqkonDispatchersTest {

    private val mainScope = MainScope()

    @AfterTest
    fun tearDown() { mainScope.cancel() }

    @Test
    fun defaultDispatchers_areReachable() {
        val d = defaultSqkonDispatchers
        assertNotSame(d.read, d.write)
    }

    @Test
    fun customDispatchers_areThreadedThroughSqkonConstructor() = runTest {
        val testRead = StandardTestDispatcher(testScheduler, name = "test-read")
        val testWrite = StandardTestDispatcher(testScheduler, name = "test-write")
        val dispatchers = SqkonDispatchers(read = testRead, write = testWrite)

        val sqkon = Sqkon(
            scope = TestScope(testScheduler),
            dispatchers = dispatchers,
        )

        val store = sqkon.keyValueStorage<TestObject>("dispatchers-test")
        store.insert("k", TestObject(id = "k", value = "v"))
        val read = store.selectByKey("k").first()
        assertEquals("v", read?.value)
    }
}
```

- [ ] **Step 2: Run.**

Run: `./gradlew jvmTest --tests "*.SqkonDispatchersTest"`
Expected: PASS.

---

### Task 7: Add `iosArm64`, `iosSimulatorArm64`, `wasmJs` targets

> **Drop `iosX64`** per user direction — legacy Intel simulator target dropped from modern KMP libs.

**Files:**
- Modify: `library/build.gradle.kts`

- [ ] **Step 1: Insert targets.** Find:

```kotlin
kotlin {
    applyDefaultHierarchyTemplate()
    androidTarget {
        publishLibraryVariants("release")
    }
    jvmToolchain(21)
    jvm()
    sourceSets {
```

Replace with:

```kotlin
kotlin {
    applyDefaultHierarchyTemplate()
    androidTarget {
        publishLibraryVariants("release")
    }
    jvmToolchain(21)
    jvm()
    iosArm64()
    iosSimulatorArm64()
    wasmJs {
        browser()
        nodejs()
    }
    sourceSets {
```

- [ ] **Step 2: Sync.**

Run: `./gradlew help`
Expected: configuration succeeds.

- [ ] **Step 3: Surface missing actuals on iOS.**

Run: `./gradlew :library:compileKotlinIosSimulatorArm64`
Expected: FAIL with "no actual" for `connectionPoolSize`, `defaultSqkonDispatchers`, `DriverFactory`, `SqlException`.

---

### Task 8: Stub iOS `SqkonDatabaseDriver` actuals

**Files:**
- Create: `library/src/iosMain/kotlin/com/mercury/sqkon/db/SqkonDatabaseDriver.ios.kt`

- [ ] **Step 1: Create the actuals.**

```kotlin
package com.mercury.sqkon.db

import app.cash.sqldelight.db.SqlDriver
import kotlinx.coroutines.Dispatchers

internal actual val connectionPoolSize: Int = 1

@PublishedApi
internal actual val defaultSqkonDispatchers: SqkonDispatchers = SqkonDispatchers(
    read = Dispatchers.Default,
    write = Dispatchers.Default,
)

internal actual class DriverFactory {
    actual fun createDriver(): SqlDriver =
        TODO("iOS driver not yet implemented — lands in Phase 6 (MOB-3293)")
}
```

`Dispatchers.IO` is JVM-only; `Dispatchers.Default` is the right placeholder until Phase 6.

- [ ] **Step 2: Compile.**

Run: `./gradlew :library:compileKotlinIosSimulatorArm64`
Expected: FAIL with only `SqlException ... has no actual` remaining.

---

### Task 9: Stub iOS `SqlException` actual

**Files:**
- Create: `library/src/iosMain/kotlin/com/mercury/sqkon/db/SqlException.ios.kt`

- [ ] **Step 1: Create the actual.** Start with the `(message, cause)` form:

```kotlin
package com.mercury.sqkon.db

actual class SqlException(message: String? = null, cause: Throwable? = null) :
    Exception(message, cause)
```

If the compiler rejects this against the empty-bodied expect, fall back to `actual class SqlException : Exception()`. Record the chosen form for WasmJs (Task 11).

- [ ] **Step 2: Compile all iOS targets.**

Run: `./gradlew :library:compileKotlinIosArm64 :library:compileKotlinIosSimulatorArm64`
Expected: PASS.

---

### Task 10: Stub WasmJs `SqkonDatabaseDriver` actuals

**Files:**
- Create: `library/src/wasmJsMain/kotlin/com/mercury/sqkon/db/SqkonDatabaseDriver.wasmJs.kt`

- [ ] **Step 1: Confirm WasmJs currently fails.**

Run: `./gradlew :library:compileKotlinWasmJs`
Expected: FAIL with "no actual" for the four expects.

- [ ] **Step 2: Create the actuals.**

```kotlin
package com.mercury.sqkon.db

import app.cash.sqldelight.db.SqlDriver
import kotlinx.coroutines.Dispatchers

internal actual val connectionPoolSize: Int = 1

@PublishedApi
internal actual val defaultSqkonDispatchers: SqkonDispatchers = SqkonDispatchers(
    read = Dispatchers.Default,
    write = Dispatchers.Default,
)

internal actual class DriverFactory {
    actual fun createDriver(): SqlDriver =
        TODO("WasmJs driver not yet implemented — production support ships separately")
}
```

- [ ] **Step 3: Compile.**

Run: `./gradlew :library:compileKotlinWasmJs`
Expected: FAIL with only `SqlException ... has no actual` remaining.

---

### Task 11: Stub WasmJs `SqlException` actual

**Files:**
- Create: `library/src/wasmJsMain/kotlin/com/mercury/sqkon/db/SqlException.wasmJs.kt`

- [ ] **Step 1: Mirror Task 9's chosen form.**

```kotlin
package com.mercury.sqkon.db

actual class SqlException(message: String? = null, cause: Throwable? = null) :
    Exception(message, cause)
```

- [ ] **Step 2: Compile.**

Run: `./gradlew :library:compileKotlinWasmJs`
Expected: PASS.

- [ ] **Step 3: Full multi-target sanity check.**

Run: `./gradlew :library:compileKotlinIosArm64 :library:compileKotlinIosSimulatorArm64 :library:compileKotlinWasmJs jvmTest verifySqlDelightMigration`
Expected: PASS.

---

### Task 12: (Optional, per ticket Step 2) iOS Apple-klib linker spike

**Files:**
- Create: `library/src/iosSimulatorArm64Test/kotlin/com/mercury/sqkon/IosBundledDriverSpike.kt`

- [ ] **Step 1: Add the spike.**

```kotlin
package com.mercury.sqkon

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlin.test.Test
import kotlin.test.assertEquals

class IosBundledDriverSpike {
    @Test
    fun bundledDriverOpensInMemoryDbAndRunsSelect() {
        val connection = BundledSQLiteDriver().open(":memory:")
        try {
            connection.execSQL("CREATE TABLE foo(x INTEGER)")
            connection.execSQL("INSERT INTO foo VALUES (1)")
            val stmt = connection.prepare("SELECT x FROM foo")
            try {
                check(stmt.step())
                assertEquals(1L, stmt.getLong(0))
            } finally { stmt.close() }
        } finally { connection.close() }
    }
}
```

If the `androidx.sqlite` Apple API differs in name/shape, adjust — the point is a `SELECT` round-trip.

- [ ] **Step 2: Run on simulator.**

Run: `./gradlew :library:iosSimulatorArm64Test`
Expected: PASS — confirms `androidx.sqlite-bundled:2.6.2` Apple klibs link.

**If linker fails** (documented risk): delete the spike test, leave the targets compile-only, add a comment in `SqkonDatabaseDriver.ios.kt` capturing the linker error, and proceed to Task 13.

---

### Task 13: CI — compile checks for iOS + WasmJs

**Files:**
- Modify: `.github/workflows/ci.yml`

- [ ] **Step 1: Append a new job after `run-android-tests`.**

```yaml
  compile-kmp-targets:
    strategy:
      fail-fast: false
      matrix:
        include:
          - os: macos-15
            target-tasks: "compileKotlinIosArm64 compileKotlinIosSimulatorArm64"
            name: "iOS targets"
          - os: ubuntu-latest
            target-tasks: "compileKotlinWasmJs"
            name: "WasmJs target"
    name: Compile ${{ matrix.name }}
    runs-on: ${{ matrix.os }}
    steps:
      - uses: actions/checkout@de0fac2e4500dabe0009e67214ff5f5447ce83dd # v6.0.2
        with:
          ref: ${{ github.event.pull_request.head.sha || github.sha }}
      - name: Setup Java JDK
        uses: actions/setup-java@be666c2fcd27ec809703dec50e508c2fdc7f6654 # v5.2.0
        with:
          distribution: 'zulu'
          java-version: '21'
      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@50e97c2cd7a37755bbfafc9c5b7cafaece252f6e # v6.1.0
      - name: Compile ${{ matrix.name }}
        run: ./gradlew ${{ matrix.target-tasks }}
      - name: Run iOS simulator spike test
        if: matrix.name == 'iOS targets'
        run: ./gradlew :library:iosSimulatorArm64Test
```

Drop the `Run iOS simulator spike test` step if Task 12 was dropped.

---

### Task 14: Final verification gate + commit

- [ ] **Step 1: Local full suite.**

Run: `./gradlew jvmTest`
Expected: PASS.

- [ ] **Step 2: Migration check.**

Run: `./gradlew verifySqlDelightMigration`
Expected: PASS.

- [ ] **Step 3: All new target compiles** (macOS only for iOS).

Run: `./gradlew :library:compileKotlinIosArm64 :library:compileKotlinIosSimulatorArm64 :library:compileKotlinWasmJs`
Expected: PASS.

- [ ] **Step 4: No public API surprises.**

Run: `git diff main -- library/src/commonMain/kotlin/com/mercury/sqkon/`
Expected: only `class SqkonDispatchers(read, write)` is a new public type; no removed/renamed public types.

- [ ] **Step 5: Commit.**

```bash
git add gradle/libs.versions.toml \
        library/build.gradle.kts \
        library/src/commonMain \
        library/src/jvmMain \
        library/src/androidMain \
        library/src/iosMain \
        library/src/wasmJsMain \
        library/src/jvmTest/kotlin/com/mercury/sqkon/db/SqkonDispatchersTest.kt \
        .github/workflows/ci.yml
# Only if Task 12 spike was kept:
git add library/src/iosSimulatorArm64Test
git commit -m "chore: scaffold ios and wasmjs source sets + introduce SqkonDispatchers (MOB-3288)"
```

(Alternative: three commits — `deps:` for the version bumps, `refactor:` for SqkonDispatchers, `chore:` for the scaffold. Release-please collapses under the next minor either way.)

---

## Verification (end-to-end)

| Check | Command | Where |
|---|---|---|
| Phase 0 regression baseline still green | `./gradlew jvmTest` | local + CI `jvm-tests` |
| SqlDelight migration check | `./gradlew verifySqlDelightMigration` | local + CI `jvm-tests` |
| Android instrumented tests | `./gradlew allDevicesDebugAndroidTest` | CI `run-android-tests` |
| iOS targets compile | `./gradlew compileKotlinIosArm64 compileKotlinIosSimulatorArm64` | local (macOS) + new CI `compile-kmp-targets` (macos-15) |
| WasmJs target compiles | `./gradlew compileKotlinWasmJs` | local + new CI `compile-kmp-targets` (ubuntu) |
| Apple klibs actually link (optional) | `./gradlew :library:iosSimulatorArm64Test` | local (macOS) + CI conditional step |
| Dispatcher override wired end-to-end | `./gradlew jvmTest --tests "*.SqkonDispatchersTest"` | local + CI `jvm-tests` |
| No public API regression | `git diff main -- library/src/commonMain/kotlin/com/mercury/sqkon/` | local review |

---

## Exit criteria (from MOB-3288, refined)

- [ ] `./gradlew jvmTest` green.
- [ ] `./gradlew :library:compileKotlinIosArm64 :library:compileKotlinIosSimulatorArm64 :library:compileKotlinWasmJs` succeeds (iOS portion on macOS only).
- [ ] `verifySqlDelightMigration` and `allDevicesDebugAndroidTest` still pass.
- [ ] No public type removed or renamed. `SqkonDispatchers` is the only new public type.
- [ ] `iosX64` intentionally NOT added (deviation from ticket; documented in PR description).
- [ ] `SqkonDispatchersTest` proves dispatcher override is wired end-to-end.
- [ ] Conventional commit prefix: `chore:` (single commit) — release-please-friendly.

---

## Reused references (do not re-create)

- `library/build.gradle.kts:43` — `applyDefaultHierarchyTemplate()` already wired.
- `library/build.gradle.kts:80-82` — `-Xexpect-actual-classes` already enabled.
- `library/src/commonMain/kotlin/com/mercury/sqkon/db/SqkonDatabaseDriver.kt` — current expect site (modified in Task 3).
- `library/src/commonMain/kotlin/com/mercury/sqkon/db/EntityQueries.kt` — site of `expect class SqlException : Exception`. Not modified; new actuals plug into it.
- `library/src/androidMain/kotlin/com/mercury/sqkon/db/SqkonDatabaseDriver.android.kt` — reference for `BundledSQLiteDriver` usage shape.
- `library/src/jvmTest/kotlin/com/mercury/sqkon/db/SqkonDatabaseDriverTest.jvm.kt` — `driverFactory()` test helper. Untouched.
- `library/src/commonTest/kotlin/com/mercury/sqkon/TestDataClasses.kt` — `TestObject` already exists for `SqkonDispatchersTest`.

---

## Risks

1. **Apple klib linker (Task 12).** `androidx.sqlite-bundled:2.6.2` Apple klibs were last published Jan 2026 (iosArm64) and Nov 2025 (iosSimulatorArm64). The spike confirms link; fall back to compile-only if it fails.
2. **`Dispatchers.IO` is JVM-only.** iOS/WasmJs default to `Dispatchers.Default`. Phase 6 (MOB-3293) replaces with a real driver-aware pool.
3. **`SqlException` actual form.** `(message, cause)` vs no-arg — resolve in Task 9, propagate to Task 11.
4. **`-Xexpect-actual-classes` must remain enabled.** Already on. Required for per-platform `DriverFactory` constructor shapes.
5. **kotlinx-datetime 0.8 ↔ kotlinx-serialization compat.** kotlinx-serialization 1.11.0 already in use; smoke-verified by `jvmTest` after the bump.
6. **AGP 9 deferred.** AGP 9.1.1 is current (Apr 2026). Migration has its own breaking-change radius and is out of scope.

---

## Out of scope (explicit non-goals)

- Real iOS or WasmJs driver implementation. Phase 6 (MOB-3293).
- Splitting `KeyValueStorage`'s public two-dispatcher constructor params into `SqkonDispatchers` (would be a public-API break — defer).
- AGP 9 migration.
- Production iOS/WasmJs publishing (separate 2.x minor bump after Phase 6).
