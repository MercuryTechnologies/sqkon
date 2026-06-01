---
layout: default
title: Platform setup
parent: Getting Started
nav_order: 3
---

# Platform setup
{: .no_toc }

<details open markdown="block">
  <summary>Table of contents</summary>
  {: .text-delta }
1. TOC
{:toc}
</details>

Sqkon ships a single Maven artifact with platform-specific `Sqkon(...)` factory
functions. This page covers the construction details for each target and the
threading rules that apply everywhere.

## Android

The Android factory needs a `Context` and a `CoroutineScope`:

```kotlin
fun Sqkon(
    context: Context,
    scope: CoroutineScope,
    json: Json = SqkonJson { },
    dbFileName: String? = "sqkon.db",
    config: KeyValueStorage.Config = KeyValueStorage.Config(),
): Sqkon
```

### Where to instantiate

Treat the `Sqkon` instance like a database connection pool: create one per
database for the lifetime of your process. The natural place is your
`Application` subclass:

```kotlin
class MyApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val sqkon: Sqkon by lazy {
        Sqkon(context = this, scope = appScope)
    }
}
```

Inject `sqkon` (or specific `KeyValueStorage<T>` instances) into your DI graph
from there.

### Database file

By default Sqkon writes to `sqkon.db` inside your app's standard database
directory (the same place Room stores its databases). To pick a different file
name, pass `dbFileName`:

```kotlin
Sqkon(context = this, scope = appScope, dbFileName = "my-cache.db")
```

To run **in-memory** — useful for instrumented tests or transient caches that
should not survive a process restart — pass `null`:

```kotlin
Sqkon(context = this, scope = appScope, dbFileName = null)
```

{: .note }
> An older overload accepted `inMemory: Boolean` and is now deprecated. Migrate
> to `dbFileName = null` (or omit the parameter to use the default
> `"sqkon.db"`); the deprecated overload simply forwards to the new one.

## JVM

The JVM factory takes a `CoroutineScope` and a `SqkonDatabaseType`:

```kotlin
fun Sqkon(
    scope: CoroutineScope,
    json: Json = SqkonJson { },
    type: SqkonDatabaseType = SqkonDatabaseType.Memory,
    config: KeyValueStorage.Config = KeyValueStorage.Config(),
    driverConfig: SqkonDriverConfig = SqkonDriverConfig(),
): Sqkon
```

### In-memory (default)

The default is `SqkonDatabaseType.Memory`, which is ideal for unit
tests:

```kotlin
val sqkon = Sqkon(scope = scope) // in-memory by default
```

### File-backed

For a persistent JVM database, point at a file:

```kotlin
import com.mercury.sqkon.db.SqkonDatabaseType

val sqkon = Sqkon(
    scope = appScope,
    type = SqkonDatabaseType.FileBacked("data/sqkon.db"),
)
```

The path is resolved relative to the JVM's working directory — pass an absolute
path if that is ambiguous in your environment.

## Threading

Sqkon manages its own dispatchers internally. You don't need to wrap calls in
`withContext(Dispatchers.IO)` — the library does it for you.

Internally:

- Reads run on a `Dispatchers.IO`-based dispatcher with **limited parallelism = 4**, matching SQLite's default WAL connection pool size.
- Writes run on a `Dispatchers.IO`-based dispatcher with **limited parallelism = 1** (SQLite allows only one writer at a time).

This is configured in
[`Sqkon.kt`](https://github.com/MercuryTechnologies/sqkon/blob/main/library/src/commonMain/kotlin/com/mercury/sqkon/db/Sqkon.kt)
and the platform `SqkonDatabaseDriver` files.

{: .note }
> Sqkon's SQLite driver is synchronous by design; it serializes work through its
> own dispatchers (one writer, a small reader pool) rather than an async driver,
> which doesn't play well with multithreaded hosts.

The `CoroutineScope` you pass to `Sqkon(...)` is used as the parent of the
internal reactive query coroutines. Cancel that scope (typically only in tests
or when shutting down a worker process) to release database resources.

## iOS / Native

Not usable on iOS **yet**. The iOS source set is scaffolded and compiles, but the
platform `DriverFactory.createDriver()` is still a `TODO` stub — there is no public
`Sqkon(...)` factory for iOS. Now that Sqkon runs on `androidx.sqlite` (which ships a
KMP/native `BundledSQLiteDriver`), the only remaining work is wiring that actual; the
old eygraber JVM/Android-only driver is gone.

If iOS support matters to you, please open or upvote an issue on the
[GitHub repo](https://github.com/MercuryTechnologies/sqkon/issues).

## Next

- [Serialization]({{ '/getting-started/serialization/' | relative_url }}) — `Json` defaults and how to override them.
- [Concepts: Architecture]({{ '/concepts/architecture/' | relative_url }}) — what happens when you call `insert` or `select`.
