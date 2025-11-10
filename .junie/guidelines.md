# Sqkon Development Guidelines

## Project Overview
Sqkon is a Kotlin Multiplatform library for key-value storage built on top of SQLDelight and AndroidX SQLite. It provides a type-safe, coroutine-based API with Flow support for reactive data access.

## Build Configuration

### Kotlin Multiplatform Setup
- **Targets**: Android and JVM
- **JVM Toolchain**: Java 21
- **Kotlin Version**: 2.2.20
- **Gradle**: Uses Kotlin DSL (`build.gradle.kts`)

### Important Compiler Flags
The project uses `-Xexpect-actual-classes` compiler flag for multiplatform expect/actual declarations. This is configured in:
- Root `build.gradle.kts` for all subprojects
- `library/build.gradle.kts` for the library module

### SQLDelight Configuration
Located in `library/build.gradle.kts`:
```kotlin
sqldelight {
    databases {
        create("SqkonDatabase") {
            generateAsync = false  // Disabled due to driver issues on multithreaded platforms
            packageName.set("com.mercury.sqkon.db")
            schemaOutputDirectory.set(file("src/commonMain/sqldelight/databases"))
            dialect("app.cash.sqldelight:sqlite-3-38-dialect:$VERSION")
        }
    }
}
```

**Key Point**: `generateAsync = false` is intentionally disabled as async operations are problematic with coroutines on multithreaded platforms (driver limitation).

### Source Sets
- `commonMain`: Common code for all platforms
- `commonTest`: Shared test code
- `androidMain`: Android-specific implementations
- `jvmMain`: JVM-specific implementations
- `androidInstrumentedTest`: Android instrumented tests
- `jvmTest`: JVM test implementations

## Testing

### Test Framework Stack
- **kotlin.test**: Core assertions and annotations
- **JUnit**: Test structure (`@Test`, `@After` annotations)
- **kotlinx-coroutines-test**: `runTest` for coroutine testing
- **Turbine**: Flow testing library (`test{}`, `awaitItem()`, `expectNoEvents()`)
- **Cash App Paging Testing**: For pagination testing

### Running Tests

#### Run all tests for the library module:
```bash
./gradlew :library:test
```

#### Run tests for specific platform:
```bash
./gradlew :library:jvmTest          # JVM tests only
./gradlew :library:testDebugUnitTest # Android unit tests (disabled by config)
```

#### Run a specific test class:
```bash
./gradlew :library:jvmTest --tests "com.mercury.sqkon.SimpleGuidelineTest"
```

#### Run a specific test method:
```bash
./gradlew :library:jvmTest --tests "com.mercury.sqkon.SimpleGuidelineTest.basicInsertAndSelect"
```

### Android Testing Notes
- **Unit tests are disabled** for Android target: `enableUnitTest = false` in `build.gradle.kts`
- **Instrumented tests are enabled**: Run on Android devices/emulators
- **Managed device configured**: `mediumPhoneApi35` (API 35, aosp-atd)
- Test runner: `androidx.test.runner.AndroidJUnitRunner`

### Writing Tests

#### Basic Test Structure
```kotlin
package com.mercury.sqkon

import com.mercury.sqkon.db.*
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals

class MyTest {
    private val mainScope = MainScope()
    private val driver = driverFactory().createDriver()
    private val entityQueries = EntityQueries(driver)
    private val metadataQueries = MetadataQueries(driver)
    private val storage = keyValueStorage<TestObject>(
        "my-test", entityQueries, metadataQueries, mainScope
    )

    @After
    fun tearDown() {
        mainScope.cancel()
    }

    @Test
    fun myTest() = runTest {
        // Test code here
    }
}
```

#### Key Components:
1. **MainScope**: Required for background database operations
2. **driverFactory()**: Expect/actual function providing platform-specific test drivers
   - JVM: In-memory database (`AndroidxSqliteDatabaseType.Memory`)
   - Android: Context-based test database
3. **tearDown**: Always cancel MainScope to prevent resource leaks
4. **runTest**: Wraps suspend test functions for coroutine testing

#### Testing Flows with Turbine
```kotlin
import app.cash.turbine.test

@Test
fun flowTest() = runTest {
    storage.selectAll().test {
        val items = awaitItem()
        assertEquals(0, items.size)
        
        storage.insert("key", TestObject())
        val updatedItems = awaitItem()
        assertEquals(1, updatedItems.size)
        
        expectNoEvents()
    }
}
```

#### Testing with turbineScope
```kotlin
import app.cash.turbine.turbineScope

@Test
fun multipleFlowsTest() = runTest {
    turbineScope {
        val flow1 = storage.selectAll().testIn(backgroundScope)
        val flow2 = storage.count().testIn(backgroundScope)
        
        val items = flow1.awaitItem()
        val count = flow2.awaitItem()
        // assertions...
    }
}
```

### Test Data Models
Test models are defined in `library/src/commonTest/kotlin/com/mercury/sqkon/TestDataClasses.kt`:
- `TestObject`: Main test entity with various field types
- `TestObjectChild`: Nested object with timestamps
- `TestValue`: Value class example
- `TestSealed`: Sealed interface implementations
- `TestEnum`: Enum example

These models demonstrate serialization of:
- Nested objects
- Value classes (@JvmInline)
- Sealed interfaces
- Nullable fields
- Lists
- Custom SerialNames
- Enums

### Test Utilities
Located in `library/src/commonTest/kotlin/com/mercury/sqkon/TestUtilsExt.kt`:

**`until()` function**: Poll-based assertion helper
```kotlin
suspend fun until(timeout: Duration = 5.seconds, block: suspend () -> Boolean)
```

Usage:
```kotlin
val results = mutableListOf<Int>()
backgroundScope.launch { /* populate results */ }
until { results.size > 5 }
```

## Development Information

### Expect/Actual Pattern
The project extensively uses Kotlin Multiplatform's expect/actual mechanism:
- **Common**: Declares `expect` functions/classes
- **Platform-specific**: Provides `actual` implementations

Key expect/actual declarations:
- `DriverFactory`: Database driver creation
- `driverFactory()`: Test database setup
- `Sqkon.create()`: Platform-specific initialization

### SQLDelight Schema Management
- Schema files: `library/src/commonMain/sqldelight/com/mercury/sqkon/db/`
  - `entity.sq`: Entity storage queries
  - `metadata.sq`: Metadata tracking queries
- Migrations: `library/src/commonMain/sqldelight/migrations/`
- Schema output: `library/src/commonMain/sqldelight/databases/`

### Core Architecture
- **KeyValueStorage**: Main API for type-safe storage operations
- **EntityQueries**: Generated SQLDelight queries for entities
- **MetadataQueries**: Generated SQLDelight queries for metadata
- **KotlinSqkonSerializer**: Kotlinx.serialization-based JSON serialization
- **Flow-based**: All queries return Flows for reactive updates

### Key Libraries
- **SQLDelight 2.1.0**: Type-safe SQL generation
- **AndroidX SQLite**: Cross-platform SQLite driver
- **Kotlinx Serialization 1.9.0**: JSON serialization
- **Kotlinx Coroutines 1.10.2**: Async operations
- **Kotlinx Datetime 0.6.2**: Timestamp handling
- **Cash App Paging 3.3.0**: Pagination support

### Publishing
- Configured for Maven Central and GitHub Packages
- Uses `com.vanniktech.maven.publish` plugin
- Version from `VERSION_NAME` property or `RELEASE_VERSION` environment variable
- Automatic release enabled for Maven Central

### Code Style Notes
- **Coroutines**: All database operations are suspend functions
- **Flows**: Reactive data streams with automatic updates
- **Transactions**: Use `storage.transaction { }` for atomic operations
- **JSON Path**: Supports querying nested JSON fields with type-safe builders
- **Value Classes**: Supported for inline wrapping of primitives
- **Sealed Interfaces**: Supported with proper SerialName annotations

### Common Patterns

#### Creating Storage
```kotlin
val storage = keyValueStorage<MyType>(
    storageKey = "my-storage",
    entityQueries = entityQueries,
    metadataQueries = metadataQueries,
    mainScope = mainScope
)
```

#### Querying with Conditions
```kotlin
// Simple equality
storage.select(where = MyType::field eq "value")

// Nested field access
storage.select(where = MyType::child.then(Child::field) eq "value")

// Combining conditions
storage.select(
    where = (MyType::field1 eq "value1").and(MyType::field2 eq "value2")
)

// Ordering
storage.selectAll(
    orderBy = listOf(OrderBy(MyType::field, OrderDirection.ASC))
)
```

#### Working with Transactions
```kotlin
storage.transaction {
    storage.deleteAll()
    storage.insertAll(newItems)
    // All or nothing - atomic operation
}
```

### Debugging Tips
- Enable SQLDelight query logging by setting appropriate log levels
- Use `slowWrite = true` on EntityQueries for testing transaction boundaries
- Flow emissions can be tested with Turbine's `expectNoEvents()` to ensure operations are properly batched
- Check that MainScope is properly cancelled in tests to avoid resource leaks

## Build Commands

### Clean build:
```bash
./gradlew clean build
```

### Run all tests:
```bash
./gradlew test
```

### Publish to local Maven:
```bash
./gradlew publishToMavenLocal
```

### Check version:
```bash
./gradlew version
```
