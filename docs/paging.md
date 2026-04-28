# Paging in Sqkon

Sqkon plugs into [AndroidX Paging 3](https://developer.android.com/topic/libraries/architecture/paging/v3-overview). Your `KeyValueStorage<T>` exposes two `PagingSource` factories — pick one and hand it to a `Pager`.

## TL;DR

| | `selectPagingSource` (offset) | `selectKeysetPagingSource` (keyset) |
|---|---|---|
| **Key type** | `Int` (row offset) | `String` (entity key at page boundary) |
| **First-page cost** | O(pageSize) | O(n) — boundaries computed once |
| **Late-page cost** | O(offset + pageSize) — grows with depth | O(log n) — same regardless of depth |
| **Random page jumps** | yes | no (`jumpingSupported = false`) |
| **Best for** | Small/medium datasets, random access | Large datasets, deep scrolling, `RemoteMediator` |

If you don't know which to pick, read [Choosing](#choosing) at the bottom.

## Why paging?

Loading 50 000 rows into memory at once burns RAM, blocks the UI thread on deserialization, and wastes work — most users only look at the first few screens. Paging 3 loads pages on demand and discards old ones as the user scrolls. Sqkon's job is to give Paging 3 a `PagingSource` that reads from your local SQLite store.

## Offset paging — `selectPagingSource`

**Mental model.** "Give me `pageSize` rows starting at row `N`." It's `SELECT … LIMIT N OFFSET M` — the SQL you already know.

```
Rows in table:  [r0][r1][r2][r3][r4][r5][r6][r7][r8][r9][r10]...
                              ^               ^
                              offset = 4      ... limit = 3 → returns [r4][r5][r6]
```

**Cost.** SQLite walks past `offset` rows before returning anything. Page 1 is cheap, page 100 is not — the engine still has to skip the first 99 × `pageSize` rows. Fine up to a few thousand items; degrades on tens of thousands.

**Code.**

```kotlin
val pager = Pager(
    config = PagingConfig(pageSize = 20, prefetchDistance = 5),
    pagingSourceFactory = {
        merchantStore.selectPagingSource(
            where = Merchant::category eq "Food",
            orderBy = listOf(OrderBy(Merchant::name, OrderDirection.ASC)),
        )
    }
)

// In a ViewModel:
val merchants: Flow<PagingData<Merchant>> = pager.flow.cachedIn(viewModelScope)
```

**Use it when** the dataset is small/medium, or you need users to deep-link into a specific page (for example "page 7" of an admin table).

## Keyset paging — `selectKeysetPagingSource`

**Mental model.** "Compute the entity-key at every page boundary up front, then look up rows by key for each page." Each page is an indexed range scan — no skipping.

```
Sorted rows:    [a1][a2][a3][b1][b2][b3][c1][c2][c3][d1]...
Boundaries:      ^           ^           ^           ^
                 k0          k1          k2          k3

load(key = k1) → SELECT … WHERE entity_key >= k1 AND entity_key < k2
              → returns [b1][b2][b3]
```

**Cost.** Boundaries are computed once when the `PagingSource` is created (a single window-function query). Each subsequent page is an indexed range scan — same cost on page 1 as on page 1000.

**Code.** Same shape as offset paging, one method name different:

```kotlin
val pager = Pager(
    config = PagingConfig(pageSize = 20, prefetchDistance = 5),
    pagingSourceFactory = {
        merchantStore.selectKeysetPagingSource(
            pageSize = 20,                                    // must match PagingConfig.pageSize
            where = Merchant::category eq "Food",
            orderBy = listOf(OrderBy(Merchant::name, OrderDirection.ASC)),
        )
    }
)

val merchants: Flow<PagingData<Merchant>> = pager.flow.cachedIn(viewModelScope)
```

**Limitations.**

- `jumpingSupported = false`. Paging 3 cannot scroll-jump to an arbitrary position — pages are loaded sequentially from the start (or from a refresh key).
- The `pageSize` argument **must equal** `PagingConfig.pageSize`. They're independent and a mismatch throws boundary math off.

**Use it when** the dataset is large, infinite-scroll, or backed by a `RemoteMediator`.

## How invalidation works

Both paging sources observe the underlying SQLite table. When a write lands, the source invalidates and Paging 3 builds a fresh one with up-to-date data — no manual refresh needed.

```mermaid
sequenceDiagram
    participant App
    participant Pager
    participant Source as PagingSource
    participant DB as SQLDelight

    App->>Pager: collect flow
    Pager->>Source: load(initial)
    Source->>DB: query.executeAsList()
    Source->>DB: addListener(this)
    DB-->>Source: rows
    Source-->>Pager: LoadResult.Page

    Note over App,DB: Later — app or RemoteMediator writes to the store
    App->>DB: insertAll(items)
    DB-->>Source: queryResultsChanged()
    Source->>Pager: invalidate()
    Pager->>Source: discard, factory builds a new one
    Pager->>Source: load(initial) on new source
```

**Key invariant:** the listener attaches the first time `load()` runs a query, **including the empty-result case**. Keyset paging registers the listener on the boundaries query when the store starts empty, so an empty-then-populated transition (typical when a `RemoteMediator` does the first network fetch) still triggers an invalidation. See [`QueryPagingSource.kt:11`](../library/src/commonMain/kotlin/com/mercury/sqkon/db/paging/QueryPagingSource.kt) and [`KeysetQueryPagingSource.kt:49-59`](../library/src/commonMain/kotlin/com/mercury/sqkon/db/paging/KeysetQueryPagingSource.kt).

## RemoteMediator pattern

Sqkon is the local cache and single source of truth. `RemoteMediator` writes into the store; the `PagingSource` reads from it and invalidates when writes land. Skeleton:

```kotlin
class MerchantsMediator(
    private val api: MerchantsApi,
    private val store: KeyValueStorage<Merchant>,
) : RemoteMediator<String, Merchant>() {
    override suspend fun load(
        loadType: LoadType,
        state: PagingState<String, Merchant>,
    ): MediatorResult = try {
        val cursor = state.lastItemOrNull()?.id
        val page = api.fetch(cursor = cursor)
        store.insertAll(page.items.associateBy { it.id })   // triggers PagingSource invalidation
        MediatorResult.Success(endOfPaginationReached = page.next == null)
    } catch (e: IOException) {
        MediatorResult.Error(e)
    }
}
```

Wire it into `Pager(remoteMediator = MerchantsMediator(api, store), pagingSourceFactory = { … })` and either paging source works.

## Choosing

1. **Need random page jumps (deep-link to "page 7")?** → offset.
2. **Scrolling thousands of rows, or backing this with `RemoteMediator`?** → keyset.
3. **Otherwise** → either works; offset is simpler — start there and switch if profiling shows late-page cost.

## Pitfalls

- **`pageSize` mismatch.** `PagingConfig.pageSize` and `selectKeysetPagingSource(pageSize = …)` are passed independently — set them to the same value or boundary math drifts.
- **`orderBy` needs a tiebreaker** when the column has duplicates. Sqkon's keyset SQL automatically appends a tiebreaker on `entity_key`; if you write your own SQL elsewhere, do the same.
- **Don't share a `PagingSource` across factories.** Paging 3 owns the lifecycle — let `pagingSourceFactory = { … }` build a fresh one each time. A `PagingSource` that has been invalidated cannot be reused.
- **Filtering on a JSON path with no index** rescans the table on every page. If you do this often, add an index in your `.sq` migration.
- **Cancel `MainScope` in tests** (see [CLAUDE.md](../CLAUDE.md)). Otherwise `Pager` keeps observing across test boundaries and tests flake.
- **`asSnapshot { … }` is a one-shot.** Inserts that happen inside the lambda won't appear in that snapshot's result. To verify a post-insert state, take a second `asSnapshot()` after the insert (see [`KeysetPagingTest.kt`](../library/src/commonTest/kotlin/com/mercury/sqkon/db/KeysetPagingTest.kt) for the pattern).

## See also

- API: [`KeyValueStorage.selectPagingSource`](../library/src/commonMain/kotlin/com/mercury/sqkon/db/KeyValueStorage.kt) (line 307), [`KeyValueStorage.selectKeysetPagingSource`](../library/src/commonMain/kotlin/com/mercury/sqkon/db/KeyValueStorage.kt) (line 348).
- Internals: [`OffsetQueryPagingSource`](../library/src/commonMain/kotlin/com/mercury/sqkon/db/paging/OffsetQueryPagingSource.kt), [`KeysetQueryPagingSource`](../library/src/commonMain/kotlin/com/mercury/sqkon/db/paging/KeysetQueryPagingSource.kt), [`QueryPagingSource`](../library/src/commonMain/kotlin/com/mercury/sqkon/db/paging/QueryPagingSource.kt).
- Runnable examples: [`OffsetPagingTest`](../library/src/commonTest/kotlin/com/mercury/sqkon/db/OffsetPagingTest.kt), [`KeysetPagingTest`](../library/src/commonTest/kotlin/com/mercury/sqkon/db/KeysetPagingTest.kt) — copy a test as a starting point.
- Upstream: [Paging 3 overview](https://developer.android.com/topic/libraries/architecture/paging/v3-overview), [`RemoteMediator`](https://developer.android.com/reference/kotlin/androidx/paging/RemoteMediator).
