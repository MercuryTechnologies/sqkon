---
layout: default
title: Migrations
parent: Guides
nav_order: 9
---

# Migrations
{: .no_toc }

1. TOC
{:toc}

There are two things that can change over time in a Sqkon-backed app: the
**SQLite schema** Sqkon owns (the `entity` and `metadata` tables) and the
**shape of your `@Serializable` data classes** (the JSON written into rows).
Sqkon handles the first for you. The second is on you, but the patterns are
small.

```kotlin
@Serializable
data class Merchant(val id: String, val name: String, val category: String)
```

## Library schema migrations are automatic

Sqkon owns its SQLite schema. The tables, indexes, and any future schema
changes live in the library, not in your project:

- Schema definitions: `library/src/commonMain/sqldelight/com/mercury/sqkon/db/`
- Migrations: `library/src/commonMain/sqldelight/migrations/`

When you bump the Sqkon version and the version brings a new schema
revision, SQLDelight applies the migration the first time the database is
opened. You don't write SQL, you don't run migrations, and you don't need
to worry about coordinating versions across modules — the library does it.

The shipped migration today:

- `1.sqm` — adds the `metadata` table, adds `read_at` / `write_at` columns
  to `entity`, and creates the supporting indexes.

## Verifying schema migrations

When the library itself adds a migration, SQLDelight provides a
verification task that ensures the migration plus the new schema produce
the same result as a clean install. CI runs this on every change:

```bash
./gradlew verifySqlDelightMigration
```

If you're contributing to Sqkon and you change `entity.sq` or add a `.sqm`
file, run this locally before you push. It's also part of the project's CI
flow (`.github/workflows/ci.yml`).

## Data shape migrations

Your data class will evolve. There are three categories of change, in
increasing order of work.

### Additive — adding a new field with a default

Safe and free with the default `SqkonJson` settings (`ignoreUnknownKeys`,
`encodeDefaults`):

```kotlin
@Serializable
data class Merchant(
    val id: String,
    val name: String,
    val category: String,
    val rating: Double = 0.0,    // new field, default value
)
```

- Old rows lack `rating`. On read, kotlinx.serialization fills in the
  default — `0.0`.
- New writes include `rating` because `encodeDefaults = true`.
- Existing rows continue to work without rewriting them.

If you query on the new field, only rows written after the change will
match (old rows physically don't have the path until they're rewritten).
Run a `selectAll().first()` + `upsertAll(...)` if you need to backfill —
see "One-shot data migration" below.

### Removing or renaming a field

Two options:

**1. Alias with `@SerialName`** — keeps reading old data, writes the new
shape:

```kotlin
@Serializable
data class Merchant(
    val id: String,
    @SerialName("name") val displayName: String,    // was: val name
    val category: String,
)
```

The Kotlin field is `displayName`, but JSON is still `"name"`. Old rows
read; new writes still produce `"name"`. No migration needed.

**2. One-shot data migration** — read all rows, transform, write back. Run
once on app startup, gated by a version flag in shared preferences or a
metadata table:

```kotlin
suspend fun migrateV1ToV2(merchants: KeyValueStorage<Merchant>) {
    val all = merchants.selectAll().first()
    merchants.upsertAll(all.associateBy { it.id })
}
```

`upsertAll` runs in a single transaction, so the migration is atomic. Run
it before any other reads return to the UI.

### Type changes

If a field's type changes (e.g. `String` → `Int`, or `String` → an enum),
kotlinx.serialization can't bridge it for you. Options:

- Write a custom `KSerializer<T>` that accepts both shapes for a release.
- Keep a transient old-shape data class, read with that, then write with
  the new one in a one-shot migration.
- If the data is rebuildable, use `DeserializePolicy.DELETE` (next
  section) and let the next sync replace the rows.

## Recovering from breaking changes without a migration

When the cache is rebuildable from your server and the schema change is
breaking, the simplest path is to drop unreadable rows on read:

```kotlin
val merchants = keyValueStorage<Merchant>(
    "merchants", entityQueries, metadataQueries, appScope,
    config = KeyValueStorage.Config(
        deserializePolicy = KeyValueStorage.Config.DeserializePolicy.DELETE,
    ),
)
```

See [Serialization tips]({{ '/guides/serialization-tips/' | relative_url }}#recovering-from-deserialization-errors)
for the trade-offs. The default is `ERROR` and you should keep that for
stores where the data isn't easy to regenerate.

## Reference

- Schema: `library/src/commonMain/sqldelight/com/mercury/sqkon/db/entity.sq`
- Migrations directory: `library/src/commonMain/sqldelight/migrations/`
  - `1.sqm` — adds `metadata` table, `read_at`/`write_at` columns, indexes
- CI verify task: `./gradlew verifySqlDelightMigration`
