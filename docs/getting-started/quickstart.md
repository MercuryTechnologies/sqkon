---
layout: default
title: Quickstart
parent: Getting Started
nav_order: 2
---

# Quickstart
{: .no_toc }

<details open markdown="block">
  <summary>Table of contents</summary>
  {: .text-delta }
1. TOC
{:toc}
</details>

This page walks you through inserting, querying, and observing data with Sqkon
end-to-end. By the end you'll have a working `Merchant` store backed by SQLite
with JSONB queries and reactive `Flow` observation.

If you haven't added the dependency yet, see
[Installation]({{ '/getting-started/installation/' | relative_url }}).

## 1. Define your model

Sqkon stores values by serializing them with `kotlinx.serialization`. Annotate
your data class with `@Serializable`:

```kotlin
import kotlinx.serialization.Serializable

@Serializable
data class Merchant(
    val id: String,
    val name: String,
    val category: String,
)
```

{: .note }
> Sqkon queries against the JSON representation of your value, so any field you
> want to filter, sort, or paginate on must be a serialized property — not a
> getter or computed field. See [Serialization]({{ '/getting-started/serialization/' | relative_url }})
> for the gotchas with sealed classes and inheritance.

## 2. Create a `Sqkon` instance

You need exactly one `Sqkon` per database. Treat it like a connection pool:
construct it once during app startup and share it.

### Android

```kotlin
class MyApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val sqkon: Sqkon by lazy {
        Sqkon(
            context = this,
            scope = appScope,
            // dbFileName = null   // pass null for in-memory (testing)
        )
    }
}
```

### JVM

```kotlin
import com.mercury.sqkon.db.SqkonDatabaseType

val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

val sqkon = Sqkon(
    scope = appScope,
    type = SqkonDatabaseType.FileBacked("sqkon.db"),
)
```

For tests, drop the `type` argument — the JVM default is
`SqkonDatabaseType.Memory`.

## 3. Get a typed store

Every type you want to persist gets its own `KeyValueStorage`. The store name
is your "table" — pick something stable.

```kotlin
val merchants: KeyValueStorage<Merchant> =
    sqkon.keyValueStorage<Merchant>("merchants")
```

## 4. Insert, update, and delete

Every method that mutates the store — the `insert*`, `update`, `upsert`, and
`delete*` families (including `deleteAll`, `deleteByKey(s)`, `deleteExpired`,
`deleteStale`) — is a **blocking transaction that runs the SQLite work
synchronously on the calling thread**; none of them are `suspend`. Only the
follow-up metadata bookkeeping (read/write timestamps) is dispatched onto Sqkon's
internal write dispatcher; the write itself happens inline on your thread.

So you must keep writes **off the Android main thread** yourself — wrap them in
`withContext(Dispatchers.IO) { ... }` (or call them from a background dispatcher)
to avoid blocking the UI and triggering ANRs:

```kotlin
withContext(Dispatchers.IO) {
    merchants.insert(key = chipotle.id, value = chipotle)
}
```

Reads (`select*` / `count` / `metadata`) return `Flow`s and are already dispatched
onto Sqkon's internal read dispatcher, so those you can collect from any context.

```kotlin
val chipotle = Merchant(id = "1", name = "Chipotle", category = "Food")
val patagonia = Merchant(id = "2", name = "Patagonia", category = "Apparel")

// Insert
merchants.insert(key = chipotle.id, value = chipotle)

// Bulk insert
merchants.insertAll(
    mapOf(
        chipotle.id to chipotle,
        patagonia.id to patagonia,
    )
)

// Update existing or insert if missing
merchants.upsert(key = chipotle.id, value = chipotle.copy(name = "Chipotle Mexican Grill"))

// Delete by key
merchants.deleteByKey("2")

// Delete with a predicate
merchants.delete(where = Merchant::category eq "Food")

// Wipe the entire store
merchants.deleteAll()
```

## 5. Query

`select` returns a `Flow` that emits the current results and re-emits whenever
matching rows change.

```kotlin
import kotlinx.coroutines.flow.first

// One-shot read of a single key
val one: Merchant? = merchants.selectByKey("1").first()

// Filter + sort + limit
val foodMerchants: Flow<List<Merchant>> = merchants.select(
    where = Merchant::category eq "Food",
    orderBy = listOf(OrderBy(Merchant::name, OrderDirection.ASC)),
    limit = 50,
)

// Combine predicates
val cheapEats = merchants.select(
    where = (Merchant::category eq "Food").and(Merchant::name like "Chi%")
)

// Count without materializing rows
val foodCount: Flow<Int> = merchants.count(where = Merchant::category eq "Food")
```

Where DSL operators: `eq`, `neq`, `inList`, `notInList`, `like`, `gt`, `lt`,
plus `not(...)`, `.and(...)`, and `.or(...)`. See the [querying guide]({{ '/guides/querying/' | relative_url }})
for the full reference.

## 6. Observe changes

Because `select` returns `Flow`, you can collect it from a `ViewModel`,
`LifecycleScope`, or any coroutine context.

```kotlin
appScope.launch {
    merchants.selectAll().collect { all ->
        println("Store has ${all.size} merchants")
    }
}
```

Inserts, updates, and deletes from anywhere in your app will trigger a new
emission automatically — on commit, Sqkon's driver notifies the affected query
keys and active Flows re-run their query.

## 7. Cleanup

In tests, cancel the scope you passed to `Sqkon` to release the SQLite
connection pool:

```kotlin
@After fun tearDown() {
    appScope.cancel()
}
```

In production, the application-scoped `Sqkon` lives for the process lifetime —
no cleanup is needed.

## Next steps

- [Platform setup]({{ '/getting-started/platform-setup/' | relative_url }}) — Android Application wiring, JVM file paths, threading rules.
- [Serialization]({{ '/getting-started/serialization/' | relative_url }}) — defaults, customization, and sealed classes.
- [Querying guide]({{ '/guides/querying/' | relative_url }}) — the full `JsonPath` DSL with examples.
