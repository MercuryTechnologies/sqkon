# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Sqkon is a Kotlin Multiplatform key-value storage library built on SQLDelight and kotlinx.serialization. It stores serialized Kotlin objects in SQLite and supports querying on JSON fields using JSONB. Targets: Android and JVM.

## Build Commands

```bash
# Build
./gradlew clean build

# Run JVM tests (primary development loop)
./gradlew jvmTest

# Run a specific test class
./gradlew jvmTest --tests "com.mercury.sqkon.db.KeyValueStorageTest"

# Run a specific test method
./gradlew jvmTest --tests "com.mercury.sqkon.db.KeyValueStorageTest.testInsertAndSelect"

# Verify SQLDelight migrations
./gradlew verifySqlDelightMigration

# Android instrumented tests (requires managed device setup)
./gradlew allDevicesDebugAndroidTest

# Publish to local Maven
./gradlew publishToMavenLocal
```

## Architecture

**Single module**: `:library` — all source lives here using KMP source sets:
- `commonMain` / `commonTest` — shared logic and tests
- `androidMain` / `androidInstrumentedTest` — Android-specific driver and tests
- `jvmMain` / `jvmTest` — JVM-specific driver and tests

**Core classes** (package `com.mercury.sqkon.db`):
- `Sqkon` — entry point; creates `KeyValueStorage` instances via `sqkon.keyValueStore<T>(name)`
- `KeyValueStorage<T>` — main API for CRUD, querying, paging, expiry. All queries return `Flow<T>`
- `EntityQueries` / `MetadataQueries` — SQLDelight-generated + hand-written query extensions
- `JsonPath` — type-safe WHERE clause DSL (`MyType::field eq "value"`, `MyType::child.then(Child::field)`)
- `SqkonSerializer` interface / `KotlinSqkonSerializer` — pluggable serialization strategy

**SQLDelight schema**: `library/src/commonMain/sqldelight/com/mercury/sqkon/db/` (`entity.sq`, `metadata.sq`)
**Migrations**: `library/src/commonMain/sqldelight/migrations/`

**Platform expect/actual pattern**: `Sqkon`, `SqkonDatabaseDriver`, and `EntityQueries` each have platform-specific implementations (`*.android.kt`, `*.jvm.kt`).

## Key Technical Decisions

- `generateAsync = false` in SQLDelight config — async is intentionally disabled because the driver has issues on multithreaded platforms. Coroutines handle concurrency instead.
- `-Xexpect-actual-classes` compiler flag is required for KMP expect/actual declarations.
- Android unit tests are disabled (`enableUnitTest = false`); use JVM tests for fast iteration and Android instrumented tests for device-specific behavior.
- Java 21 toolchain required.

## Testing Patterns

Tests use `kotlin.test` + `kotlinx-coroutines-test` (`runTest`) + Turbine for Flow testing. Standard test setup:

```kotlin
private val mainScope = MainScope()
private val driver = driverFactory().createDriver()
private val entityQueries = EntityQueries(driver)
private val metadataQueries = MetadataQueries(driver)
private val storage = keyValueStorage<TestObject>("test-key", entityQueries, metadataQueries, mainScope)

@After fun tearDown() { mainScope.cancel() }
```

- `driverFactory()` is an expect/actual that provides in-memory databases for tests
- Always cancel `MainScope` in tearDown to prevent resource leaks
- Test data classes are in `library/src/commonTest/kotlin/com/mercury/sqkon/TestDataClasses.kt`
- Use Turbine's `storage.selectAll().test { awaitItem() }` pattern for testing reactive queries

## CI

Two CI jobs run on push/PR to main (`.github/workflows/ci.yml`):
1. `jvm-tests` — runs `verifySqlDelightMigration` then `jvmTest`
2. `run-android-tests` — runs `allDevicesDebugAndroidTest` on managed emulator

## Publishing

Uses VannikTech Maven Publish plugin. Published to Maven Central with automatic release. Coordinates: `com.mercury.sqkon:library`. Release workflow triggers on GitHub release creation.
