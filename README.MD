# Sqkon

![sqkon](assets/logo.png)

Sqkon (/sk-on/) A Kotlin Multiplatform KeyValue Store with the ability to query on values using
SQLite and JSONB.

[![Maven Central Version](https://img.shields.io/maven-central/v/com.mercury.sqkon/library)](https://central.sonatype.com/artifact/com.mercury.sqkon/library)
[![GitHub branch check runs](https://img.shields.io/github/check-runs/MercuryTechnologies/sqkon/main)](https://github.com/MercuryTechnologies/sqkon/actions)
[![Docs](https://img.shields.io/badge/docs-mercurytechnologies.github.io%2Fsqkon-272735?style=flat&labelColor=272735&color=5266EB)](https://mercurytechnologies.github.io/sqkon/)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

## Documentation

📘 **Full docs at [mercurytechnologies.github.io/sqkon](https://mercurytechnologies.github.io/sqkon/)** — guides, examples, API reference (Dokka), and architecture overview.

| Topic | Link |
|---|---|
| Quickstart | [getting-started/quickstart](https://mercurytechnologies.github.io/sqkon/getting-started/quickstart/) |
| Querying with the type-safe DSL | [guides/querying](https://mercurytechnologies.github.io/sqkon/guides/querying/) |
| Paging | [guides/paging](https://mercurytechnologies.github.io/sqkon/guides/paging/) |
| Sealed classes & polymorphism | [guides/serialization-tips](https://mercurytechnologies.github.io/sqkon/guides/serialization-tips/) |
| API reference (Dokka) | [/api/](https://mercurytechnologies.github.io/sqkon/api/) |

## Usage

```kotlin
// Create a new instance of Sqkon
val sqkon = Sqkon(
    context = context // (only for Android)
)

// Create a Store for each type/entity you want to create
val merchantStore = sqkon.keyValueStorage<Merchant>("merchant")

// Insert a new entity
val merchant = Merchant(
    id = MerchantKey("1"),
    name = "Chipotle",
    category = "Food"
)

// Insert, similar to a SQL INSERT, no table definition needed.
merchantStore.insert(key = merchant.id.value, value = merchant)

// Query on any field 
val flow: Flow<List<Merchant>> = merchantStore.select(where = Merchant::name like "Chi%")


// Example entity
@Serializable
data class Merchant(
    val id: MerchantKey,
    val name: String,
    val category: String,
)
```

### Sealed (Subclass) Classes
Sealed and subclasses are supported, but there are some caveats, as searching on json paths on works
on data that is serialized to json. I.e. getters/setters are not queryable.

Take the following example:

```kotlin
sealed class Card {
    val id: Uuid
    val cardNumber: String
    @Serializable
    data class CreditCard(
        val key: Uuid,
        val last4: String,
        val expiry: String,
    ) : Card() {
        override val id: Uuid get() = key
        override val cardNumber: String get() = last4
    }

    @Serializable
    data class DebitCard(
        val key: Uuid,
        val last4: String,
        val expiry: String,
    ) : Card() {
        override val id: Uuid get() = key
        override val cardNumber: String get() = last4
    }
}
```

As `id` and `cardNumber` are abstract properties of the sealed class, they never get serialized to 
json, so they would not be queryable. (Unless you made your concrete classes override and serialize them.)

`with` will accepts sub types of the parent class, please open issues of complex data structures if stuck.

The following would be valid and invalid queries:
```kotlin
// These will search across the sealed class fields 
val idWhere = Card::class.with(CreditCard::key) eq "1"
val last4Where = Card::class.with(CreditCard::last4) eq "1234"
// This will not work tho as cardNumber is a getter
val cardNumberWhere = Card::cardNumber eq "1234"

```

### CASE / WHEN: choosing a path per variant

The `with`-style queries above match a single field across variants. When you need to **filter or order by a value that lives under a different field name in each variant**, use a `CaseWhen<T>` expression. It compiles to a SQL `CASE WHEN … END` over the sealed discriminator.

```kotlin
@Serializable
sealed interface Status {
    val id: String
    @Serializable @SerialName("Active")
    data class Active(override val id: String, val activatedAt: Long) : Status
    @Serializable @SerialName("Pending")
    data class Pending(override val id: String, val requestedAt: Long) : Status
}

// Build a value expression: "the timestamp that means 'when this happened',
// regardless of which variant the row holds".
val effectiveTime: CaseWhen<Status> = Status::class.case {
    whenIs<Status.Active>(Status::class.with(Status.Active::activatedAt))
    whenIs<Status.Pending>(Status::class.with(Status.Pending::requestedAt))
}

// Filter: rows whose effective timestamp is after a pivot, across both variants.
storage.select(where = effectiveTime gt 1_700_000_000L)

// Order: sort the mixed result set by the per-variant timestamp.
storage.select(orderBy = listOf(OrderBy(effectiveTime, OrderDirection.DESC)))
```

Supported comparison operators on `CaseWhen<T>`: `eq`, `neq`, `gt`, `gte`, `lt`, `lte`, plus `isNull()` / `isNotNull()` (rows whose variant has no matching `whenIs` branch fall through to NULL). `case { … }` is also available on a sealed *property* (e.g. `MyEntity::status.case<MyEntity, Status> { … }`) for the common case where the sealed type is nested inside a larger object.

## Paging

Sqkon provides two `PagingSource` factories on `KeyValueStorage` that plug into AndroidX Paging 3:

- `selectPagingSource(...)` — offset-based, supports random page jumps, simple.
- `selectKeysetPagingSource(pageSize, ...)` — keyset-based, constant cost per page, recommended for large lists and `RemoteMediator`-backed feeds.

Full guide with examples, diagrams, and a chooser: [Paging](https://mercurytechnologies.github.io/sqkon/guides/paging/).

## Installation

### Gradle

Multiplatform projects (Android, JVM; iOS on the roadmap):

```kotlin
commonMain {
    dependencies {
        implementation("com.mercury.sqkon:library:1.3.2")
    }
}
```

Android-only or JVM-only projects use the same coordinate — Gradle resolves the
right artifact for your target platform:

```kotlin
dependencies {
    implementation("com.mercury.sqkon:library:1.3.2")
}
```

## Project Requirements

The project is built upon [SQLDelight](https://github.com/sqldelight/sqldelight)
and [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization), these are transitive
dependencies, but you will not be able to use the library with applying the
kotlinx-serialization plugin. If you are not using kotlinx serialization, I suggest you read about
it
here: https://github.com/Kotlin/kotlinx.serialization.

## Expiry/Cache Busting

Sqkon doesn't provide default cache busting out of the box, but it does provide the tools to do
this if that's what you require.

- `KeyValueStore.selectResult` will expose a ResultRow with a `expiresAt`, `writeAt` and `readAt`
  fields, with this you can handle cache busting yourself.
- Most methods support `expiresAt`, `expiresAfter` which let you set expiry times, we don't auto purge fields that have "expired" use
  use `deleteExpired` to remove them. We track `readAt`,`writeAt` when rows are read/written too.
- We provide `deleteWhere`, `deleteExpired`, `deleteStale`, the docs explain there differences.

## Testing

```bash
# Run all tests
./gradlew :library:allTests

# JVM tests only
./gradlew :library:jvmTest

# Android tests (managed devices - recommended for CI)
./gradlew :library:allDevicesDebugAndroidTest

# Android tests (connected device/emulator)
./gradlew :library:connectedDebugAndroidTest

# Specific test class
./gradlew :library:jvmTest --tests "com.mercury.sqkon.db.KeyValueStorageTest"

# Specific test method
./gradlew :library:jvmTest --tests "com.mercury.sqkon.db.KeyValueStorageTest.testInsertAndSelect"
```

Test reports: `library/build/reports/tests/jvmTest/index.html` (JVM), `library/build/reports/androidTests/connected/debug/index.html` (Android)

### Build platform artifacts

#### Android aar

- Run `./gradlew :library:assembleRelease`

#### JVM jar

- Run `./gradlew :library:jvmJar`
