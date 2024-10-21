# Sqkon

![sqkon](assets/logo.png)

Sqkon (/sk-on/) A Kotlin Multiplatform KeyValue Store with the ability to query on values using
SQLite and JSONB.

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

### Publish to MavenCentral

**TODO PUBLISH TO MAVEN CENTRAL GH ACTIONS**

### Publish to github packages

Creating a new release will publish the artifacts to GitHub Packages.

If publishing locally you will need `GITHUB_ACTOR` and `GITHUB_TOKEN` environment variables set with
repository write permissions.

`./gradlew publishToGitHubPackages`


### Build platform artifacts

#### Android aar

- Run `./gradlew :core:assembleRelease`
- Output: `/core/build/outputs/aar/core-release.aar`

#### JVM jar

- Run `./gradlew :core:jvmJar`
- Output: `/core/build/libs/core-jvm-1.0.jar`
