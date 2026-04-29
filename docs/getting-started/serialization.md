---
layout: default
title: Serialization
parent: Getting Started
nav_order: 4
---

# Serialization
{: .no_toc }

<details open markdown="block">
  <summary>Table of contents</summary>
  {: .text-delta }
1. TOC
{:toc}
</details>

Sqkon stores values as JSON inside SQLite (using SQLite's JSONB representation)
and queries against that JSON. The serializer therefore controls **what is on
disk** and **what you can query**. This page explains the defaults Sqkon ships
with, why they were chosen, and how to override them.

## Default configuration

When you don't pass a `Json` to the `Sqkon(...)` factory, Sqkon uses
`SqkonJson { }` — a tuned `kotlinx.serialization.Json` instance defined in
[`KotlinSqkonSerializer.kt`](https://github.com/MercuryTechnologies/sqkon/blob/main/library/src/commonMain/kotlin/com/mercury/sqkon/db/serialization/KotlinSqkonSerializer.kt).
Its defaults are:

```kotlin
fun SqkonJson(builder: JsonBuilder.() -> Unit) = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    useArrayPolymorphism = true
    builder()
}
```

| Setting | Value | Why |
|---|---|---|
| `ignoreUnknownKeys` | `true` | Forward compatibility. When you remove or rename a field, existing rows containing the old key still deserialize cleanly. |
| `encodeDefaults` | `true` | Required to make queries on default-valued fields work. If a field is missing in JSON because it equals its default, `where Type::field eq default` would silently fail to match. |
| `useArrayPolymorphism` | `true` | Allows polymorphic serialization of sealed hierarchies that contain value classes without custom descriptors — see [the kotlinx.serialization issue](https://github.com/Kotlin/kotlinx.serialization/issues/2049#issuecomment-1271536271) for the full story. |

{: .important }
> Changing any of these defaults can silently break querying or migration
> behavior. Read the rest of this page before turning them off.

## Customizing

Pass a custom `Json` to the factory. The easiest path is to layer your tweaks
on top of `SqkonJson { }`:

```kotlin
import com.mercury.sqkon.db.serialization.SqkonJson

val json = SqkonJson {
    prettyPrint = false
    isLenient = true
}

val sqkon = Sqkon(context = this, scope = appScope, json = json)
```

You can also build a fully custom `Json` if you need to deviate from the
defaults — but doing so opts out of the guarantees above:

```kotlin
val json = Json {
    encodeDefaults = true   // keep — required for query correctness
    ignoreUnknownKeys = false // your call; see "Schema evolution" below
    serializersModule = myCustomModule
}
```

## Why these defaults?

### `ignoreUnknownKeys = true` is about schema evolution

Sqkon is a key-value store, not a managed-schema database. When you change a
data class — adding a field, removing a field, renaming with `@SerialName` —
existing rows in SQLite still hold the old JSON shape. Setting
`ignoreUnknownKeys = false` would cause every read of an old row to throw
after a refactor.

If you set this to `false`, you take responsibility for migrating values when
you change a class. See [Schema evolution]({{ '/guides/schema-evolution/' | relative_url }})
for patterns.

### `encodeDefaults = true` is about query correctness

Sqkon's `where` DSL compiles down to JSON path queries. If a field is omitted
from the stored JSON because its value matched the Kotlin default,
`Merchant::category eq "Food"` will not match a row whose `category` defaulted
to `"Food"` — the JSON path simply isn't there. Keeping `encodeDefaults = true`
ensures every property is materialized and queryable.

### `useArrayPolymorphism = true` covers sealed hierarchies

If you store a sealed type and one of its branches contains a `value class`
without a custom serializer, the standard polymorphic encoder fails. Array
polymorphism sidesteps this. If you have your own polymorphic strategy
(`classDiscriminator`, custom serializers), feel free to override.

## Custom `SqkonSerializer`

`KotlinSqkonSerializer` is the default implementation of the `SqkonSerializer`
interface. If you need to use a non-`kotlinx` serialization library — Moshi,
Jackson, protobuf — implement the interface yourself:

```kotlin
class MyMoshiSerializer(private val moshi: Moshi) : SqkonSerializer {
    override fun <T : Any> serialize(type: KType, value: T?): String? =
        value?.let { moshi.adapter<Any>(type.javaType).toJson(it) }

    override fun <T : Any> deserialize(type: KType, value: String?): T? =
        value?.let { moshi.adapter<Any>(type.javaType).fromJson(it) as? T }
}
```

{: .warning }
> Whatever serializer you plug in must produce **valid JSON** — Sqkon stores
> the result with SQLite's JSONB and queries against it. Binary formats like
> protobuf or CBOR will break querying.

Custom serializers are wired in by constructing `Sqkon` via the internal
constructor, which is intentionally not part of the stable public API today.
If you have a strong use case for a non-`kotlinx` serializer, please open an
issue.

## Next

- [Quickstart]({{ '/getting-started/quickstart/' | relative_url }}) — try the defaults end-to-end.
- [Serialization tips]({{ '/guides/serialization-tips/' | relative_url }}) — sealed classes, value classes, and migration strategies.
- [Querying guide]({{ '/guides/querying/' | relative_url }}) — what JSON paths the `where` DSL generates and why `encodeDefaults` matters.
