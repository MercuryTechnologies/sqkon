---
layout: default
title: Ordering
parent: Guides
nav_order: 3
---

# Ordering
{: .no_toc }

1. TOC
{:toc}

`select` and `selectAll` accept an `orderBy: List<OrderBy<T>>` parameter. The
list order **is** the precedence — first entry sorts first, ties broken by the
second, and so on.

## `OrderBy` DSL

```kotlin
merchants.selectAll(
    orderBy = listOf(
        OrderBy(Merchant::score, OrderDirection.DESC),
    ),
).first()
```

`OrderBy` has two reified factory functions:

- `OrderBy(property: KProperty1<T, V>, direction: OrderDirection? = null)` —
  for top-level fields.
- `OrderBy(builder: JsonPathBuilder<T>, direction: OrderDirection? = null)` —
  for nested fields built with `.then()` / `.thenList()`.

Both produce the same `OrderBy<T>` value, which the store turns into a
`json_tree(...)` join plus an `ORDER BY ...` clause.

## Multi-field ordering

```kotlin
val ordered = merchants.selectAll(
    orderBy = listOf(
        OrderBy(Merchant::category, OrderDirection.ASC),
        OrderBy(Merchant::score, OrderDirection.DESC),
    ),
).first()
```

This sorts by `category` ascending, then breaks ties by `score` descending —
the same semantics as SQL's `ORDER BY category ASC, score DESC`. The list is
1:1 with the SQL clause, so put the **most important** sort key first.

This pattern is exercised verbatim in `selectAll_orderBy_EntityValueThenName`:

```kotlin
testObjectStorage.selectAll(
    orderBy = listOf(
        OrderBy(TestObject::value, direction = OrderDirection.ASC),
        OrderBy(TestObject::name, direction = OrderDirection.ASC),
    ),
).first()
```

## Ordering on nested fields

Pass any `JsonPathBuilder` you'd pass to a Where operator:

```kotlin
merchants.selectAll(
    orderBy = listOf(
        OrderBy(Merchant::location.then(Location::city)),
    ),
).first()
```

Reference: `selectAll_orderBy_EntityChildAddedBy` orders by
`TestObject::child.then(TestObjectChild::createdAt)` descending.

## Direction

`OrderDirection` is a simple enum:

```kotlin
enum class OrderDirection(val value: String) {
    ASC(value = "ASC"),
    DESC(value = "DESC"),
}
```

The `direction` parameter is **nullable** — pass `null` (or omit it) and
Sqkon emits no direction keyword, leaving SQLite to apply its default of
`ASC`. The KDoc on the field calls this out explicitly:

```
internal val direction: OrderDirection? = null,
// Sqlite defaults to ASC when not specified
```

So these two are equivalent today:

```kotlin
OrderBy(Merchant::score)                        // SQLite default → ASC
OrderBy(Merchant::score, OrderDirection.ASC)    // Explicit ASC
```

Be explicit when sort order matters for tests or for readers — the explicit
form survives any future change to defaults.

## Ordering and `Instant`

The same string-comparison rule from [Querying]({{ '/guides/querying/' | relative_url }})
applies — ISO-8601 timestamps sort lexicographically the same as
chronologically, so ordering on `createdAt` "just works" without any
conversion:

```kotlin
merchants.selectAll(
    orderBy = listOf(
        OrderBy(Merchant::createdAt, OrderDirection.DESC),
    ),
).first()
```

## Where to next

- [Querying]({{ '/guides/querying/' | relative_url }}) — combine ordering with filters.
- [Paging]({{ '/guides/paging/' | relative_url }}) — keyset paging requires a stable, total order.
- [Performance]({{ '/guides/performance/' | relative_url }}) — un-indexed `ORDER BY` on large tables.
