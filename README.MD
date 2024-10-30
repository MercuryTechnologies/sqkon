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
kotlinx-serialization plugin. If you are not using kotlinx serialization, I suggest you read about it
here: https://github.com/Kotlin/kotlinx.serialization.

```kotlin 

### Build platform artifacts

#### Android aar

- Run `./gradlew :core:assembleRelease`

#### JVM jar

- Run `./gradlew :core:jvmJar`
