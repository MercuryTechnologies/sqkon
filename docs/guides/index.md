---
layout: default
title: Guides
nav_order: 4
has_children: true
permalink: /guides/
---

# Guides

Task-oriented walkthroughs for the things you'll actually do with Sqkon — query
your data, page through it, expire it, and keep your tests fast. Each page is
self-contained; skim the list and jump to whatever you need.

If you're new, the first three pages are the fundamentals — querying, traversing
nested fields, and ordering results. The rest cover capabilities you'll reach
for as your store grows.

## Querying & shaping results

- [Querying]({{ '/guides/querying/' | relative_url }}) — type-safe Where DSL, operators, AND/OR/NOT composition.
- [Nested fields]({{ '/guides/nested-fields/' | relative_url }}) — `.then()` and `.thenList()` for paths into nested objects and lists.
- [Ordering]({{ '/guides/ordering/' | relative_url }}) — sort with `OrderBy`, multi-field precedence, ASC/DESC.

## Reading at scale

- [Paging]({{ '/guides/paging/' | relative_url }}) — `selectPagingSource` (offset) and `selectKeysetPagingSource` (keyset).
- [Flow]({{ '/guides/flow/' | relative_url }}) — every read returns a `Flow`; how change propagation and dedup work.
- [Performance]({{ '/guides/performance/' | relative_url }}) — when JSONB is fast, when it isn't, and how to keep queries cheap.

## Lifecycle & data integrity

- [Expiry]({{ '/guides/expiry/' | relative_url }}) — TTL semantics, eviction strategies, and stale reads.
- [Transactions]({{ '/guides/transactions/' | relative_url }}) — multi-write atomicity and Flow emission timing.
- [Migrations]({{ '/guides/migrations/' | relative_url }}) — schema and serialization migrations.

## Working with your data classes

- [Serialization tips]({{ '/guides/serialization-tips/' | relative_url }}) — `@SerialName`, value classes, sealed hierarchies, enums.
- [Testing]({{ '/guides/testing/' | relative_url }}) — in-memory drivers, MainScope teardown, Turbine patterns.
