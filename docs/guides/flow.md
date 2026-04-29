---
layout: default
title: Reactive flows
parent: Guides
nav_order: 6
---

# Reactive flows
{: .no_toc }

Every read on a `KeyValueStorage<T>` returns a `Flow`. Writes that touch the
same `entity_name` trigger active observers to re-execute their queries and
emit fresh results — no manual refresh, no event bus.

1. TOC
{:toc}

## Every read is a Flow

```kotlin
val all: Flow<List<Merchant>>          = merchants.selectAll()
val food: Flow<List<Merchant>>         = merchants.select(where = Merchant::category eq "Food")
val one: Flow<Merchant?>               = merchants.selectByKey("m_1")
val rows: Flow<List<ResultRow<Merchant>>> = merchants.selectResult()
val total: Flow<Int>                   = merchants.count()
val meta: Flow<Metadata>               = merchants.metadata()
```

These flows are **cold**. They start no work until something collects them and
they cancel cleanly when the collector cancels. The first collector triggers
the first SQL execution; subsequent emissions are driven by SQLDelight's table
notifications.

## When does it re-emit?

Any `insert`, `insertAll`, `update`, `updateAll`, `upsert`, `upsertAll`,
`delete`, `deleteByKey`, `deleteByKeys`, `deleteAll`, `deleteExpired`, or
`deleteStale` against the same `entity_name` invalidates every active select
flow for that store. Each invalidated flow re-runs its query and emits the
new result.

```mermaid
sequenceDiagram
    participant Writer
    participant SqkonStore as KeyValueStorage&lt;T&gt;
    participant SQLDelight
    participant Reader as Flow consumer
    Writer->>SqkonStore: insert(...)
    SqkonStore->>SQLDelight: INSERT
    SQLDelight->>SQLDelight: notify(entity)
    SQLDelight->>SqkonStore: re-execute
    SqkonStore->>Reader: emit(updatedList)
```

{: .note }
Notifications are scoped to one `entity_name` within one `Sqkon` instance.
Two `KeyValueStorage<Merchant>("merchants")` references built from the same
`Sqkon` share notifications; two separate `Sqkon` instances do not.

A few useful guarantees:

- **Single emission per transaction.** A bulk `insertAll` or `upsertAll` wraps
  every write in one transaction — observers see one emission for the batch,
  not N.
- **`distinctUntilChanged` on `selectResult` and `metadata`.** Re-emissions
  with identical content are dropped.
- **`selectAll`/`select` do not dedup.** If you need that, append
  `.distinctUntilChanged()` yourself.

## Compose integration

Collect with `collectAsState` for snapshot reads, or `collectAsStateWithLifecycle`
on Android for lifecycle-aware behavior:

```kotlin
@Composable
fun MerchantList(merchants: KeyValueStorage<Merchant>) {
    val list by merchants.selectAll().collectAsState(initial = emptyList())
    LazyColumn {
        items(list, key = { it.id }) { MerchantRow(it) }
    }
}
```

For anything heavier than a screen-scoped read — sorting, mapping, derived
state — promote the flow into a `ViewModel`:

```kotlin
class MerchantsViewModel(merchants: KeyValueStorage<Merchant>) : ViewModel() {
    val foodMerchants: StateFlow<List<Merchant>> =
        merchants.select(where = Merchant::category eq "Food")
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
```

`stateIn` shares a single upstream subscription across all UI collectors and
keeps the cache warm during config changes.

## Lifecycle and avoiding leaks

Sqkon flows do not leak by themselves — they're cold and cancel when the
collector cancels — but you must give them a structured scope to attach to:

- **ViewModels:** `viewModelScope` (Android) / your platform equivalent.
- **Composables:** `LaunchedEffect(key) { flow.collect { … } }` or
  `collectAsState`.
- **Tests:** the test's `MainScope()` — and **always cancel it in
  `tearDown()`** or pager-based tests will flake. See
  [Testing]({{ '/guides/testing/' | relative_url }}).

The pattern this codebase uses everywhere:

```kotlin
private val mainScope = MainScope()
// ...
@After fun tearDown() { mainScope.cancel() }
```

## Verbatim Turbine pattern

Turbine is the recommended way to test flow emissions. From
`library/src/commonTest/kotlin/com/mercury/sqkon/db/KeyValueStorageTest.kt`:

```kotlin
@Test
fun selectCount_flowUpdatesOnChange() = runTest {
    testObjectStorage.count().test {
        // Wait for first result
        val first = awaitItem()
        assertEquals(expected = 0, first)

        TestObject().also { testObjectStorage.insert(it.id, it) }
        val second = awaitItem()
        assertEquals(expected = 1, second)

        testObjectStorage.deleteAll()
        val third = awaitItem()
        assertEquals(expected = 0, third)

        expectNoEvents()
    }
}
```

Three patterns to copy:

- `awaitItem()` once for the *initial* emission, then once per write.
- `expectNoEvents()` at the end asserts no spurious re-emissions.
- A bulk write (`insertAll`, `upsertAll`) produces **one** emission, not N —
  see `selectCount_flowUpdatesOnUpsertAllOnce` in the same file.

## See also

- [Paging]({{ '/guides/paging/' | relative_url }}) — paging sources hook into the same notification stream.
- [Transactions]({{ '/guides/transactions/' | relative_url }}) — emissions fire after a transaction commits, not mid-transaction.
- [Testing]({{ '/guides/testing/' | relative_url }}) — Turbine, MainScope teardown, in-memory drivers.
- Source: `library/src/commonMain/kotlin/com/mercury/sqkon/db/KeyValueStorage.kt`
  (`select*` methods).
