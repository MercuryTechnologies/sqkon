---
layout: default
title: What it is, what it isn't
parent: Concepts
nav_order: 1
---

# What it is, what it isn't

Sqkon is a small, opinionated library. Knowing where it shines — and where it
deliberately doesn't — saves you from fighting it later.

## Sqkon IS

- A **Kotlin Multiplatform key-value store** for Android and JVM, built on
  [SQLDelight](https://cashapp.github.io/sqldelight/) and SQLite.
- **JSONB-queryable**: values are stored as JSONB blobs, and you can filter on any
  field of your serialized type using SQLite's native JSON operators.
- **Reactive**: every read returns a `Flow`. Writes invalidate the relevant queries,
  and active observers re-emit fresh results automatically.
- **Type-safe** at the call site: the `JsonPath` DSL turns Kotlin property references
  (`Merchant::name eq "Chipotle"`) into parameterized SQL — no string concatenation,
  no DAO boilerplate.
- **TTL-aware**: every row carries optional `expires_at`, `read_at`, and `write_at`
  timestamps, with built-in helpers for purging stale or expired rows.
- **Paging 3 ready**: ships keyset and offset `PagingSource` implementations for use
  with AndroidX Paging — both on Android and on JVM.
- **Single physical table**: one `entity` table holds every type you store. No per-type
  schema migrations, no DAO regeneration when you add a field.

## Sqkon IS NOT

- **Not a relational ORM.** There are no foreign keys, no joins, no `@Relation`
  annotations. If you need normalized relational data, use Room or write SQLDelight
  queries directly.
- **Not a sync engine.** Sqkon stores data locally. It does not replicate to a server,
  resolve conflicts, or merge offline edits. Pair it with your own networking layer.
- **Not a network cache library.** Sqkon is the storage layer. Libraries like
  [Store](https://github.com/MobileNativeFoundation/Store) handle fetch-and-cache
  semantics on top of a store like this one.
- **Not a full-text search index.** JSONB queries are exact-match, range, or `LIKE` —
  fine for filtering and lookups, not for ranked text search. Use SQLite FTS5 or a
  dedicated search index for that.
- **Not a distributed or cloud store.** No multi-device sync, no server backend, no
  cross-process replication.
- **Not encrypted by default.** The SQLite database is a regular on-disk file. If you
  need at-rest encryption, layer SQLCipher (Android) or your platform's keystore-backed
  encryption underneath.

## When to choose Sqkon

- **Offline cache for typed objects.** API responses you want to read later, with TTL
  so they don't go stale forever.
- **App state that outlives the process.** User settings, draft forms, last-known
  values — anything more structured than a `Preferences` key.
- **Feature flag and config storage.** Typed, queryable, observable, and trivially
  testable.
- **Paginated lists of typed data.** Keyset paging plus reactive Flows means a screen
  re-renders correctly when the underlying cache updates.

## When to look elsewhere

- **Highly relational data with joins.** Reach for [Room](https://developer.android.com/training/data-storage/room)
  on Android, or write SQLDelight queries directly for full SQL power.
- **Storing only primitives.** [DataStore Preferences](https://developer.android.com/topic/libraries/architecture/datastore)
  is lighter weight if you only need a handful of strings, ints, and booleans.
- **Encrypted-at-rest by default.** [Realm](https://www.mongodb.com/docs/realm/sdk/kotlin/)
  and [MMKV](https://github.com/Tencent/MMKV) ship encryption out of the box; Sqkon
  expects you to BYO if you need it.
- **iOS today.** Sqkon currently targets Android and JVM only. iOS is on the roadmap
  but not shipping yet — see the [comparison page]({{ '/concepts/comparison/' | relative_url }})
  for alternatives.

## Stability

Sqkon is on a 1.x line and follows semver strictly through
[Conventional Commits](https://www.conventionalcommits.org/) +
[release-please](https://github.com/googleapis/release-please). Most releases
are additive; breaking changes are rare and require an explicit `!`:

- `feat:` → minor bump
- `fix:` / `perf:` / `deps:` → patch bump
- `feat!:` / `fix!:` → **major** bump (used sparingly)

In practice that means `1.x` upgrades are safe within the line — no breaking
changes will land in a minor or patch release. Every release is published to
Maven Central as `com.mercury.sqkon:library:{% raw %}{{ site.sqkon_version }}{% endraw %}`.

{: .note }
> Comparing Sqkon against Room, DataStore, Realm, MMKV, or raw SQLDelight? See
> [Comparison]({{ '/concepts/comparison/' | relative_url }}) for a side-by-side
> matrix and per-scenario recommendations.
