# Sqkon

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

1) Registering a Sonatype account as described here:
   https://dev.to/kotlin/how-to-build-and-publish-a-kotlin-multiplatform-library-going-public-4a8k
2) Add developer id, name, email and the project url to
   `/convention-plugins/src/main/kotlin/convention.publication.gradle.kts`
3) Add the secrets to `local.properties`:

```
signing.keyId=...
signing.password=...
signing.secretKeyRingFile=...
ossrhUsername=...
ossrhPassword=...
```

4) Run `./gradlew :core:publishAllPublicationsToSonatypeRepository`

### Build platform artifacts

#### Android aar

- Run `./gradlew :core:assembleRelease`
- Output: `/core/build/outputs/aar/core-release.aar`

#### JVM jar

- Run `./gradlew :core:jvmJar`
- Output: `/core/build/libs/core-jvm-1.0.jar`
