---
layout: default
title: Feature flags
parent: Examples
nav_order: 5
---

# Feature flags

Sqkon makes a perfectly serviceable feature-flag store. You don't need a row
per flag — one row holding a single `Flags` object is enough, and the
Flow-based reads mean every part of the UI sees flag changes the moment they
land.

## The flag bag

Define every flag as a field on a single `@Serializable` data class. New
flags get added with a default value, so old persisted blobs deserialize
fine without a migration.

```kotlin
import kotlinx.serialization.Serializable

@Serializable
data class Flags(
    val newCheckout: Boolean = false,
    val betaSearch: Boolean = false,
    val maxBatchSize: Int = 100,
)
```

Defaults are load-bearing: the first time you read, the row doesn't exist
yet, and the repository falls back to `Flags()` — i.e. every default. New
fields you add later behave the same way for users on older app versions.

## The store and repository

The whole store has one row keyed by `"current"`:

```kotlin
import com.mercury.sqkon.db.KeyValueStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FlagsRepository(
    private val storage: KeyValueStorage<Flags>,
) {
    val flags: Flow<Flags> = storage
        .selectByKey("current")
        .map { it ?: Flags() }

    suspend fun update(flags: Flags) {
        storage.upsert("current", flags)
    }
}
```

Wire it up against your `Sqkon` instance:

```kotlin
val sqkon = Sqkon(context, scope) // Android; Sqkon(scope) on JVM
val flagsStore = sqkon.keyValueStorage<Flags>("flags")
val flagsRepo = FlagsRepository(flagsStore)
```

## In Compose

```kotlin
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

@Composable
fun CheckoutScreen(repo: FlagsRepository) {
    val flags by repo.flags.collectAsState(initial = Flags())

    if (flags.newCheckout) NewCheckout() else LegacyCheckout()
}
```

`collectAsState` re-renders whenever `update` lands a new `Flags` blob.
There's no observer registration to wire up and no broadcast bus to
maintain — it's the same Flow used by every other Sqkon read.

## Pushing remote updates

Sqkon doesn't fetch flags for you. Plug whichever delivery mechanism you
already use — Firebase Remote Config, LaunchDarkly, your own admin API —
into a sync function that writes through `FlagsRepository.update`:

```kotlin
suspend fun syncFromRemote(remote: RemoteFlagSource) {
    val incoming = remote.fetch() // returns a Flags
    flagsRepo.update(incoming)
}
```

Run that on app start, on push notification, or on a polling interval —
whatever fits your release cadence.

{: .note }
The single-row pattern is a deliberate trade-off: any flag change re-emits
the entire bag. If you have hundreds of flags and care about diffing, split
them into smaller groups (`Flags.checkout`, `Flags.search`) keyed by group
name. For a few dozen flags, one row is simpler and fast enough.

## Where to go next

- [Caching API responses]({{ '/examples/caching-api-responses/' | relative_url }})
  — for the remote fetch side, with TTL.
- [Serialization tips]({{ '/guides/serialization-tips/' | relative_url }}) —
  default values, optional fields, and how Sqkon handles version skew.
