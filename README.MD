# Sqkon

![sqkon](assets/logo.png)

Sqkon (/sk-on/) A Kotlin Multiplatform KeyValue Store with the ability to query on values using
SQLite and JSONB.

![Maven Central Version](https://img.shields.io/maven-central/v/com.mercury.sqkon/library)
![GitHub branch check runs](https://img.shields.io/github/check-runs/MercuryTechnologies/sqkon/main)

## Usage

```kotlin
// Create a new instance of Sqkon
val sqkon = Sqkon(
    context = context // (only for Android)
)

// Create a Store for each type/entity you want to create 
val merchantStore = sqkon.keyValueStore<Merchant>("merchant")

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

## Installation

### Gradle

Multiplatform projects (Android, JVM, iOS (coming soon))

```kotlin
commonMain {
    dependencies {
        implementation("com.mercury.sqkon:library:1.0.0-alpha01")
    }
}
```

Or you can use the platform specific dependencies, e.g: Android only:

```kotlin
dependencies {
    implementation("com.mercury.sqkon:library-android:1.0.0-alpha01")
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

### Build platform artifacts

#### Android aar

- Run `./gradlew :core:assembleRelease`

#### JVM jar

- Run `./gradlew :core:jvmJar`
