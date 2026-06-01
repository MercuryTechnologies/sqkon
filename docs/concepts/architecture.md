---
layout: default
title: Architecture
parent: Concepts
nav_order: 2
---

# Architecture

Sqkon takes typed Kotlin objects, runs them through `kotlinx.serialization` to JSON,
and stores them as JSONB blobs in a single SQLite table. Reads and writes go through a
hand-written query layer on top of `androidx.sqlite` (the bundled SQLite build), behind
an internal `SqkonDriver` abstraction that gives us type-safe Kotlin call sites, Flow
invalidation, and a single driver shape across Android and JVM. Queries built with the `JsonPath` DSL
compile down to `json_tree`-based predicates against the stored JSON — pushing all
filtering into SQLite's query planner instead of materializing rows in Kotlin.

## Components

```mermaid
flowchart LR
    App["App code"] --> KVS["KeyValueStorage&lt;T&gt;"]
    KVS --> Ser["KotlinSqkonSerializer"]
    KVS --> JP["JsonPath DSL"]
    Ser --> SD["EntityQueries"]
    JP --> SD
    SD --> Drv["SqkonDriver (androidx.sqlite)"]
    Drv --> DB[("SQLite + JSONB")]
```

- **`Sqkon`** — the entry point. Holds the query objects, a serializer, a
  `CoroutineScope`, and read/write dispatchers. Use `sqkon.keyValueStorage<T>(name)`
  to spawn typed stores.
- **`KeyValueStorage<T>`** — the per-type façade. Exposes `insert`, `update`,
  `upsert`, `select`, `selectByKey`, `selectByKeys`, paging sources, and TTL
  helpers. Every read returns a `Flow`.
- **`KotlinSqkonSerializer`** — wraps `kotlinx.serialization.json.Json` with sane
  defaults. You can pass your own `Json` instance to the `Sqkon` constructor.
- **`JsonPath` DSL** — turns Kotlin property references and operators (`eq`, `neq`,
  `inList`, `notInList`, `like`, `gt`, `lt`, plus `and`, `or`, `not`) into
  parameterized `WHERE` fragments that join the row against `json_tree(entity.value)`
  and match by `fullkey LIKE '$.field' AND value <op> ?`. The final row payload is
  pulled out separately with `json_extract` in the `SELECT`.
- **`EntityQueries` / `MetadataQueries`** — hand-written query classes over the
  internal `SqkonDriver`, targeting the two tables described below.
- **`SqkonDriver`** — Sqkon's own synchronous SQLite driver contract. The production
  implementation, `AndroidxSqkonDriver`, runs on `androidx.sqlite` (bundled build) on
  both platforms via a connection pool and per-connection prepared-statement cache.

## Lifecycle of an insert

1. Caller invokes `storage.insert(key, value, expiresAt = ...)`.
2. The serializer encodes `T` to a JSON byte array using the configured `Json`
   instance.
3. `EntityQueries` runs an `INSERT` through the driver (or no-op if
   `ignoreIfExists = true` and the row exists) inside a transaction.
4. SQLite stores the row with `entity_name`, `entity_key`, `value` (JSONB),
   `added_at`, `updated_at`, optional `expires_at`, and `write_at`.
5. On commit, the driver notifies the affected query keys via its listener registry.
6. Any active `select(...)` Flows re-execute their underlying query.
7. Consumers see a fresh emission with the new row included.

## Lifecycle of a query

1. Caller composes a `Where<T>` — for example,
   `Merchant::category eq "Food" and Merchant::name like "Chi%"`.
2. The DSL compiles each operator to a SQL fragment that joins the row against
   `json_tree(entity.value)` and filters by
   `fullkey LIKE '$.field' AND value = ?` (or `LIKE`, `IN`, `>`, `<`, `IS NOT`, etc.),
   all with parameter placeholders.
3. The driver runs the parameterized query against SQLite. Filtering happens inside
   the database — Kotlin never sees rows that don't match.
4. Returned blobs are deserialized back to `T` on the read dispatcher.
5. The Flow keeps observing; subsequent writes that touch the same query trigger
   re-emission.

## Why JSONB?

- **One physical table for every type.** Adding a new `@Serializable` data class
  requires zero schema changes and zero migrations — just call
  `keyValueStorage<NewType>("name")`.
- **Filtering pushes into SQLite's planner.** `json_tree` and `json_extract`
  are native SQLite functions. Predicates execute alongside index scans and
  key lookups, not in app code.
- **The `entity_name` slice is always applied first.** Every query Sqkon
  emits prefilters by `entity_name` via the primary-key index before the
  JSON-tree walk begins, so per-store cost stays bounded as the database
  grows.

## Schema

Sqkon uses two tables. The hand-rolled schema (CREATE statements + migrations) lives at:

- [`library/src/commonMain/kotlin/com/mercury/sqkon/db/internal/schema/SqkonSchema.kt`](https://github.com/MercuryTechnologies/sqkon/blob/main/library/src/commonMain/kotlin/com/mercury/sqkon/db/internal/schema/SqkonSchema.kt)

### `entity`

The single table that holds every value you store. Composite primary key is
`(entity_name, entity_key)` — `entity_name` is the namespace you pass to
`keyValueStorage<T>(name)`, and `entity_key` is the per-row key.

| Column        | Type    | Notes                                          |
|---------------|---------|------------------------------------------------|
| `entity_name` | TEXT    | Store name; part of the primary key.           |
| `entity_key`  | TEXT    | Per-row key; part of the primary key.          |
| `value`       | BLOB    | JSONB-encoded payload.                         |
| `added_at`    | INTEGER | UTC epoch millis; set on insert.               |
| `updated_at`  | INTEGER | UTC epoch millis; bumped on update.            |
| `expires_at`  | INTEGER | Optional UTC epoch millis; powers TTL queries. |
| `write_at`    | INTEGER | UTC epoch millis of last write.                |
| `read_at`     | INTEGER | UTC epoch millis of last observed read.        |

Indexes ship for `read_at`, `write_at`, and `expires_at` — see
[Performance: built-in indexes]({{ '/guides/performance/#built-in-indexes' | relative_url }})
for the full table and what each one speeds up.

### `metadata`

A small per-store table tracking the last read and write times across an entire
store. Useful for cache freshness checks and for purging stale entries.

| Column        | Type    | Notes                                |
|---------------|---------|--------------------------------------|
| `entity_name` | TEXT    | Primary key; one row per store.      |
| `lastReadAt`  | INTEGER | Mapped to `kotlinx.datetime.Instant`.|
| `lastWriteAt` | INTEGER | Mapped to `kotlinx.datetime.Instant`.|

{: .highlight }
> The driver is synchronous; Sqkon serializes work through its own coroutine
> dispatchers (one writer, a small reader pool) rather than relying on an async
> SQLite driver, which doesn't play well with multithreaded hosts.
