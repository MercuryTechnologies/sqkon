# CLAUDE.md

Sqkon is a Kotlin Multiplatform key-value storage library built on SQLDelight and kotlinx.serialization. It stores serialized Kotlin objects in SQLite and supports querying on JSON fields using JSONB. Targets: Android and JVM.

## Build & Test

Run `./gradlew jvmTest` for the primary development loop. Always run this before pushing.

```bash
./gradlew jvmTest                          # Run all JVM tests
./gradlew jvmTest --tests "*.KeyValueStorageTest"  # Single test class
./gradlew verifySqlDelightMigration        # Verify SQLDelight migrations
./gradlew allDevicesDebugAndroidTest       # Android instrumented tests (CI only)
./gradlew publishToMavenLocal              # Local Maven for integration testing
```

## Architecture

Single `:library` module with KMP source sets (`commonMain`, `androidMain`, `jvmMain` + test counterparts).

**Core classes** (package `com.mercury.sqkon.db`):
- `Sqkon` — entry point; use `sqkon.keyValueStore<T>(name)` to create stores
- `KeyValueStorage<T>` — CRUD, querying, paging, expiry; all queries return `Flow<T>`
- `EntityQueries` / `MetadataQueries` — SQLDelight-generated + hand-written extensions
- `JsonPath` — type-safe WHERE DSL (`MyType::field eq "value"`)

**SQLDelight schema**: `library/src/commonMain/sqldelight/com/mercury/sqkon/db/`
**Migrations**: `library/src/commonMain/sqldelight/migrations/`

Platform-specific code uses expect/actual (`*.android.kt`, `*.jvm.kt`).

## Rules

- Do not set `generateAsync = true` in SQLDelight — the driver breaks on multithreaded platforms. Coroutines handle concurrency.
- Keep the `-Xexpect-actual-classes` compiler flag — required for KMP expect/actual.
- Do not add Android unit tests (`enableUnitTest = false`). Use JVM tests for fast iteration, Android instrumented tests for device-specific behavior.
- Java 21 toolchain is required. Do not downgrade.

## Testing

Use `kotlin.test` + `kotlinx-coroutines-test` (`runTest`) + Turbine for Flow assertions.

```kotlin
private val mainScope = MainScope()
private val driver = driverFactory().createDriver()
private val entityQueries = EntityQueries(driver)
private val metadataQueries = MetadataQueries(driver)
private val storage = keyValueStorage<TestObject>("test-key", entityQueries, metadataQueries, mainScope)

@After fun tearDown() { mainScope.cancel() }
```

- Always cancel `MainScope` in `tearDown`.
- Use `driverFactory()` for in-memory test databases.
- Put test data classes in `library/src/commonTest/kotlin/com/mercury/sqkon/TestDataClasses.kt`.
- Use Turbine: `storage.selectAll().test { awaitItem() }`.

## Commits

Use [Conventional Commits](https://www.conventionalcommits.org/). Release-please uses these to determine version bumps and generate the changelog.

| Prefix | Version bump | Example |
|--------|-------------|---------|
| `feat:` | minor | `feat: add keyset paging support` |
| `fix:` | patch | `fix: null handling in JsonPath` |
| `feat!:` / `fix!:` | **major** | `feat!: remove deprecated expiry API` |
| `perf:` | patch | `perf: optimize JSONB query plan` |
| `deps:` | patch | `deps: upgrade SQLDelight to 2.1` |
| `docs:` | none | `docs: update README examples` |
| `chore:` | none | `chore: update CI action versions` |

## CI & Releases

**CI** (`.github/workflows/ci.yml`) runs on every push/PR to `main`:
1. `jvm-tests` — `verifySqlDelightMigration` then `jvmTest`
2. `run-android-tests` — `allDevicesDebugAndroidTest` on managed emulator

**Releases** are automated via [release-please](https://github.com/googleapis/release-please):
1. Merge PRs to `main` with conventional commits
2. Release-please opens a PR titled `release: <version>` that accumulates changes and updates `CHANGELOG.md`
3. Merge the release PR → GitHub Release is created → deploy workflow validates semver, runs `jvmTest`, then publishes to Maven Central

Published coordinates: `com.mercury.sqkon:library`. Do not create GitHub releases manually — let release-please handle it.
