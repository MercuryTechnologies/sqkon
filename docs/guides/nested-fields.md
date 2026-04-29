---
layout: default
title: Nested fields
parent: Guides
nav_order: 2
---

# Nested fields
{: .no_toc }

1. TOC
{:toc}

Real data classes nest. Sqkon's `JsonPathBuilder` lets you point at a field
inside a nested object — or inside an element of a list — using the same
`KProperty1` references you already use for top-level fields.

```kotlin
@Serializable
data class Merchant(
    val id: String,
    val name: String,
    val location: Location,
    val tags: List<Tag>,
)

@Serializable data class Location(val city: String, val zip: String)
@Serializable data class Tag(val name: String, val score: Int)
```

## Why a builder?

A `KProperty1<Merchant, Location>` knows about exactly one hop. Kotlin
reflection can't chain `Merchant::location` into `Location::city` and give you
a single property reference — there's no such thing in the language.

`JsonPathBuilder` fills that gap. It walks the chain you describe and emits a
JSON path string (`$.location.city`) plus the serial descriptors needed to
handle value classes, sealed types, and collection elements correctly. Every
operator that takes a `KProperty1` also has an overload that takes a
`JsonPathBuilder<T>`, so the rest of the Where DSL is unchanged.

## `.then()` — nested objects

Chain into a child object with `.then(<child property>)`:

```kotlin
merchants.select(
    where = Merchant::location.then(Location::city) eq "Brooklyn",
).first()
```

That builds the path `$.location.city`. You can chain further by passing a
block:

```kotlin
val builder = Merchant::location.builder {
    then(Location::city)
}
// builder.buildPath() == "$.location.city"
```

`.then()` works for arbitrary depth — keep chaining `then(...)` blocks until
you reach the leaf value you want to match on.

## `.then()` into collection elements

When the next hop is a `List` (or any `Collection`), use the same `.then()`
function — Kotlin picks the right overload from the property's type. Sqkon
emits the JSON path with `[%]` so JSONB matches against **any element**:

```kotlin
merchants.select(
    where = Merchant::tags.then(Tag::name) eq "vegan",
).first()
```

That builds `$.tags[%].name`, which JSONB evaluates as "any element of `tags`
whose `name` is `'vegan'`".

> The same `.then(...)` symbol covers nested-object hops AND list-element hops.
> The compiler picks the overload by inspecting the property's type
> (`KProperty1<R, Foo>` vs. `KProperty1<R, Collection<Foo>>`). At the JVM level
> the list overload is named `thenList` (via `@JvmName`), but you always call
> it as `.then(...)` from Kotlin. There is no `.thenList(...)` Kotlin symbol.
{: .note }

A few common shapes:

```kotlin
// List<Object> — chain into a property of each element
val builder = Merchant::tags.then(Tag::name)
// path: $.tags[%].name

// Top-level Collection of Strings — start from the class
val builder2 = Merchant::class.withList(Merchant::aliases) { /* leaf */ }
// path: $.aliases[%]
```

The `JsonPathBuilderTest.kt` suite is the source of truth for what each
combination produces — `build_with_then_list`, `build_with_list_then`, and
`build_with_list_path` are the cases you'll hit most.

{: .note }
> Filtering on a list element matches if **any** element satisfies the
> predicate. There's no built-in "all elements satisfy X" — combine with
> `not(... someElement neq X)` if you need it, but think hard about the
> resulting query plan first.

## `@SerialName` interoperability

Sqkon stores values as JSON, so the field name in the database is the **serial
name** — not always the Kotlin property name. When they differ, you have two
options.

**1. Pass the serial name explicitly:**

```kotlin
val builder = Merchant::serialName.builder(serialName = "different_name")
// builder.buildPath() == "$.different_name"
```

**2. Or annotate at the data class:**

```kotlin
@Serializable
data class Merchant(
    @SerialName("snake_case_name") val camelCaseName: String,
    /* ... */
)
```

In the second form, the builder uses the property's Kotlin name by default —
not the serial name. Most code paths in Sqkon detect serial names from the
`SerialDescriptor`, but property-name overrides like `@SerialName` on a single
field are not reflected automatically. The pragmatic rule:

- If you renamed exactly one field with `@SerialName`, pass the serial name
  through `builder(serialName = "...")` or as the `serialName` parameter on
  `.then()`.
- If you renamed via a serializer-level convention (snake_case naming
  strategy, etc.), the descriptor carries the right name and the builder uses
  it automatically.

For value classes (`@JvmInline value class`), Sqkon inlines them into the
parent path correctly — `$.testValue` rather than `$.testValue.test`. Sealed
classes get a `[1]` discriminator (`$.sealed[1].boolean`); see the
`build_with_sealed_path` test for the exact shape.

## Ordering on nested paths

Same builder, same operators — pass it to `OrderBy`:

```kotlin
merchants.selectAll(
    orderBy = listOf(
        OrderBy(Merchant::location.then(Location::city)),
    ),
).first()
```

See [Ordering]({{ '/guides/ordering/' | relative_url }}) for direction and
multi-field precedence.

## Where to next

- [Querying]({{ '/guides/querying/' | relative_url }}) — operator reference and combinators.
- [Ordering]({{ '/guides/ordering/' | relative_url }}) — sort by nested or top-level fields.
- [Performance]({{ '/guides/performance/' | relative_url }}) — when nested-path queries get expensive.
