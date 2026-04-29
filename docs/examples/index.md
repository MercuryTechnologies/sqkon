---
layout: default
title: Examples
nav_order: 5
has_children: true
permalink: /examples/
---

# Examples

End-to-end recipes that combine the pieces of Sqkon — `KeyValueStorage`, the
`Where` DSL, paging, expiry, and Flows — into the patterns you'll actually
ship. Each page is a complete, runnable walkthrough: data class, store wiring,
the surrounding glue (Compose, repository, ViewModel) and notes on the gotchas
worth knowing before you copy it into your app.

If you're still picking up the basics, read the
[Guides]({{ '/guides/' | relative_url }}) first — the recipes assume you're
comfortable with the query DSL and Flow-based reads.

## Recipes

- [Caching API responses]({{ '/examples/caching-api-responses/' | relative_url }}) — drop-in HTTP cache with TTL eviction.
- [Offline-first sync]({{ '/examples/offline-first/' | relative_url }}) — UI reads local, background syncs from the network.
- [Paging list with Compose]({{ '/examples/paging-list/' | relative_url }}) — `LazyColumn` backed by keyset paging.
- [Reactive search]({{ '/examples/search-feature/' | relative_url }}) — search-as-you-type with debounce and `like`.
- [Feature flags]({{ '/examples/feature-flags/' | relative_url }}) — single-row flag store observed by Compose.
