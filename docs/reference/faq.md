---
layout: default
title: FAQ
parent: Reference
nav_order: 2
---

# FAQ
{: .no_toc }

<details open markdown="block">
  <summary>Table of contents</summary>
  {: .text-delta }
- TOC
{:toc}
</details>

---

## Why not Room?

Room is a great fit for highly relational data — multiple tables, foreign keys, joins, and DAOs. Sqkon is designed for typed key-value storage with a single conceptual table per type. If your data shape is "objects in collections" rather than "rows in tables", Sqkon is simpler and lets you query JSON fields directly without writing schema migrations for every change. See the full [comparison]({{ '/concepts/comparison/' | relative_url }}) for a side-by-side breakdown.

## How big can my objects be?

SQLite has a default row size cap of around 1 GB, but the practical limit is much smaller. Sqkon stores each object as a single JSON string in one row, which means very large objects pay a serialization and JSONB indexing cost on every write. Keep individual objects under a few MB. For larger blobs (PDFs, images, attachments), store them externally — for example on the file system or object storage — and reference them by path or URL inside your Sqkon-stored object.

## Can I encrypt the database?

Sqkon uses [`androidx.sqlite`](https://developer.android.com/jetpack/androidx/releases/sqlite) under the hood. To encrypt the database file, swap to a SQLCipher-backed driver. Today this requires building a custom `Sqkon` instance with your own driver factory; native, first-class encryption support may come later. Track [GitHub issues](https://github.com/MercuryTechnologies/sqkon/issues) for updates.

## Is iOS supported?

Not currently. Sqkon targets Android and JVM today. iOS is on the roadmap — track progress and add your vote via [GitHub issues](https://github.com/MercuryTechnologies/sqkon/issues).

## How do I upgrade between Sqkon versions?

Read the [changelog]({{ '/reference/changelog/' | relative_url }}) for breaking changes between releases. The library handles its own internal SQL schema migrations transparently, so upgrading the dependency is usually a non-event. For changes to *your own* data shapes — adding a field, renaming a property, switching a type — see the [migrations guide]({{ '/guides/migrations/' | relative_url }}) for safe-evolution patterns.

## Does Sqkon work with KMM / Compose Multiplatform?

Yes for `commonMain` code targeting the supported platforms (Android and JVM today). The public API lives in `commonMain`, so it should remain stable when iOS targets land — but the driver story will need additional wiring, so expect the iOS rollout to ship alongside platform-specific guidance rather than transparently.

## Can I use Sqkon outside coroutines?

All Sqkon read APIs return `Flow<...>`. To bridge to RxJava, use the `Flow.asObservable()` extension from [`kotlinx-coroutines-rx3`](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-rx3/). To bridge to LiveData on Android, use the `flow.asLiveData()` extension from [`androidx.lifecycle:lifecycle-livedata-ktx`](https://developer.android.com/jetpack/androidx/releases/lifecycle). Both adapt cold flows into the equivalent reactive primitive without losing change notifications.

## Where does Sqkon store the database file?

On Android, in your app's database directory under the file name passed as `dbFileName` (defaults to `"sqkon.db"`). On JVM, use `AndroidxSqliteDatabaseType.File("path")` to control the location explicitly, or `AndroidxSqliteDatabaseType.Memory` for an in-memory database during tests. The latter is what `driverFactory()` returns in Sqkon's own test suite.

## What happens to data after `KeyValueStorage` is garbage collected?

The data persists on disk — the storage handle is just an API wrapper. Calling `Sqkon.keyValueStorage<T>("same-name")` again returns a new handle pointing at the same rows. There is no `close()` requirement on individual stores; the underlying driver is owned by the `Sqkon` instance.

{: .note }
Don't see your question here? [Open an issue](https://github.com/MercuryTechnologies/sqkon/issues/new) or check the existing discussions on GitHub.
