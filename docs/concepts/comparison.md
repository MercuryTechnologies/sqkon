---
layout: default
title: Comparison
parent: Concepts
nav_order: 3
---

# Comparison

Sqkon overlaps with several popular Kotlin storage libraries. None of them are
strictly better or worse — they make different trade-offs. Use this page to figure
out which one fits the problem in front of you.

## At a glance

| Capability             | Sqkon                  | Room                  | DataStore (Proto)         | Realm                  | MMKV                  | SQLDelight (raw)       |
|------------------------|------------------------|-----------------------|---------------------------|------------------------|-----------------------|------------------------|
| KMP support            | Android, JVM           | Android only          | Android only              | KMP (Android, iOS)     | Android, iOS          | KMP (broad)            |
| Typed objects          | Yes (`@Serializable`)  | Yes (entity classes)  | Yes (Proto schema)        | Yes (`RealmObject`)    | No (primitives + Parcelable) | Yes (codegen)   |
| Query DSL              | JsonPath (type-safe)   | DAO + SQL / Flow      | None — load entire object | RQL / typesafe queries | None                  | Hand-written SQL       |
| Reactive               | Flows everywhere       | Flow / LiveData       | Flow                      | Flow / Live results    | No                    | Flow via extensions    |
| Schema migrations      | None per-type          | Required per change   | Proto-evolution rules     | Required per change    | None                  | Required per change    |
| Encryption             | BYO (e.g. SQLCipher)   | BYO (e.g. SQLCipher)  | BYO                       | Built-in               | Built-in (optional)   | BYO                    |
| Setup complexity       | Low                    | Medium                | Medium                    | Medium                 | Very low              | Medium-high            |

## When Sqkon wins

- **Offline-first cache for typed objects with TTL.** You have a `Merchant` data
  class, you want to cache it for an hour, query it by category, and invalidate
  observers on write. Sqkon does this in three lines and zero migrations.
- **KMP shared logic on Android and JVM.** A shared module that runs on both an
  Android app and a JVM backend or test harness. You write the storage logic once
  and the same `Sqkon` API works on both.
- **You add new types frequently.** Because every type lives in the same physical
  table, introducing a new `@Serializable` class doesn't touch the schema, doesn't
  generate a migration, and doesn't require regenerating DAOs.

## When something else wins

- **Relational data with joins and foreign keys → Room.** If your model needs
  `@Relation`, cascading deletes, or non-trivial joins, Room (or raw SQLDelight)
  is a better fit. Sqkon is a key-value store with JSON-path filtering, not a
  relational database.
- **A handful of primitives → DataStore Preferences.** If all you need is a few
  strings, ints, and booleans for app settings, DataStore Preferences is lighter
  weight, has zero serialization overhead, and is the right tool. Reach for
  DataStore Proto when you have one structured config blob; reach for Sqkon when
  you have collections of typed objects.
- **Multi-platform including iOS today → SQLDelight (raw) or Realm.** Sqkon
  currently targets Android and JVM. If you need iOS in production right now,
  SQLDelight gives you the same SQLite foundation across all KMP targets, and
  Realm is a turnkey object database for Android + iOS.

## Honest closing

Sqkon is the best fit when you want a typed, observable, queryable cache with
minimal ceremony and you're already comfortable in the SQLDelight + kotlinx.serialization
ecosystem. It's not trying to replace Room for relational apps, DataStore for
primitives, or Realm for encrypted multi-platform object storage — those libraries
are excellent at what they do. If your shape is "collections of serializable
objects, queried by their fields, observed reactively, expired on a TTL, possibly
paginated," Sqkon is the shortest path. If it isn't, one of the alternatives above
probably is.
