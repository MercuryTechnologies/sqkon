---
layout: default
title: Performance
parent: Guides
nav_order: 10
---

# Performance
{: .no_toc }

1. TOC
{:toc}

Sqkon is fast enough for almost everything you'll throw at it — JSONB
extraction in SQLite is cheap, and every query is automatically scoped to
your store's `entity_name`, so two stores never collide. This page covers
the few places where performance actually matters and how to keep your
queries cheap.

```kotlin
@Serializable
data class Merchant(val id: String, val name: String, val category: String)
```

## Built-in indexes

The `entity` table ships with three indexes today (see
`library/src/commonMain/sqldelight/com/mercury/sqkon/db/entity.sq`):

| Index | Column | What it speeds up |
|-------|--------|-------------------|
| primary key | `(entity_name, entity_key)` | every read scoped to a single store; key lookups |
| `idx_entity_read_at` | `read_at` | stale-row eviction (`deleteStale`) |
| `idx_entity_write_at` | `write_at` | stale-row eviction (`deleteStale`) |
| `idx_entity_expires_at` | `expires_at` | `expiresAfter` filters and `deleteExpired` |

Two consequences worth internalizing:

- **Lookups by key are always indexed.** `selectByKey` / `selectByKeys` hit
  the primary key directly. Prefer them over `select(where = ::id eq key)`
  when you have the key.
- **Entity scoping is free.** Every query Sqkon issues prefilters by
  `entity_name`, which is the leading column of the primary key, so the
  per-store cost stays bounded as the database grows.

## Query planning for JSON paths

Filters on JSON fields hit `json_extract(value, '$.path')`. SQLite cannot
use a regular index on that expression — it computes it per row inside the
`entity_name` slice. That's fine for small-to-mid stores; for hot queries
on large stores, watch out for:

- **Leading wildcards in `like`.** `name like '%foo%'` always scans every
  row in the slice. Trailing wildcards (`'foo%'`) are cheaper because the
  string comparison can short-circuit, but neither uses an index on the
  JSON value.
- **Deep nested paths in hot loops.** Each `json_extract` walks the JSONB
  tree to the path you asked for. A query that filters on five levels of
  nesting per row, run thousands of times, will cost more than one
  filtering at the top level. Cache where you can.
- **Doing real work in the filter.** The DSL doesn't let you call
  arbitrary SQL functions, but if you ever drop into raw SQLDelight,
  resist the urge to wrap `json_extract` in something the planner can't
  flatten.

When in doubt, see the next section.

## Profiling with EXPLAIN QUERY PLAN

You can introspect any query Sqkon would run by inspecting the query plan
against a debug build of your database. Open the SQLite file with the
`sqlite3` CLI (or DB Browser for SQLite) and run:

```sql
EXPLAIN QUERY PLAN
SELECT * FROM entity
WHERE entity_name = 'merchants'
  AND json_extract(value, '$.category') = 'Coffee';
```

You're looking for `SEARCH entity USING ... PRIMARY KEY` — that means
SQLite is using the `(entity_name, entity_key)` index to slice down to your
store before evaluating the JSON predicate. If you see `SCAN entity`
without an index reference, the query is doing a full table scan, which
means the entity-name slicing isn't being applied (it always should be —
file an issue if you can reproduce).

## Batching writes

Prefer the bulk variants:

```kotlin
// Bad — one transaction per write, lots of overhead
for ((key, merchant) in merchants) {
    store.insert(key, merchant)
}

// Good — single transaction, one transaction-commit Flow emit
store.insertAll(merchants)
store.upsertAll(merchants)
```

`insertAll`, `updateAll`, `upsertAll` all wrap in a single transaction.
Inside Sqkon, `updateWriteAt` is also deduplicated per transaction, so
metadata updates only fire once. For batches of more than a handful of
rows, the difference is significant.

## Flow re-execution cost

Every write to an entity re-runs **every** active select Flow on that
entity — that's how change propagation works. If three ViewModels each
hold their own `selectAll()` Flow, every `upsert` triggers three queries.

Mitigations:

- **Share Flows via `stateIn`.** Compute the list once, share many
  observers:

  ```kotlin
  val merchants: StateFlow<List<Merchant>> = merchantStore
      .selectAll()
      .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
  ```

- **Filter at the query layer, not the consumer.** `select(where = ...)`
  is cheaper than pulling everything and filtering in Kotlin once the
  store grows.

- **Watch your write rate.** A loop of single inserts also fires N Flow
  re-runs, on top of the N transactions. `upsertAll` solves both.

See the [Flow guide]({{ '/guides/flow/' | relative_url }}) for the change
propagation details.

## Database size and `VACUUM`

SQLite doesn't return space to the OS after deletes — pages are reused,
but the file size stays put. Periodically calling `VACUUM` rewrites the
database compactly. Sqkon doesn't expose `VACUUM` directly, but you can
run it through the underlying SQLDelight driver:

```kotlin
sqkon.driver.execute(null, "VACUUM", 0)
```

A few caveats:

- `VACUUM` rewrites the entire file. Not something to call on every
  startup — once a week, gated on a heuristic, or after a known
  large-deletion event is fine.
- It needs roughly 2x the database size in temp space.
- Don't run it inside a transaction; it requires an exclusive lock.

For most apps this is a non-issue. If your store sees lots of
`deleteExpired` / `deleteStale` activity and the file grows monotonically,
a periodic `VACUUM` is the lever.
