---
layout: default
title: Serialization tips
parent: Guides
nav_order: 8
---

# Serialization tips
{: .no_toc }

1. TOC
{:toc}

Sqkon stores your `@Serializable` Kotlin objects as JSONB blobs and resolves
field predicates by joining each row against `json_tree(entity.value)` and
matching on `fullkey LIKE '$.field' AND value <op> ?`. That means the JSON
shape your serializer produces is the schema your queries see —
`Merchant::name` only resolves if your JSON actually contains a top-level
`name` field. This page covers the serialization patterns that come up most
often.

```kotlin
@Serializable
data class Merchant(val id: String, val name: String, val category: String)
```

## The `SqkonJson` defaults

When you construct a store without specifying a serializer, Sqkon uses
`KotlinSqkonSerializer`, which in turn uses a `Json` instance built by the
`SqkonJson { }` builder. The defaults are tuned for storage and querying:

```kotlin
fun SqkonJson(builder: JsonBuilder.() -> Unit) = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    useArrayPolymorphism = true
    builder()
}
```

- `ignoreUnknownKeys = true` — old rows can be read after you remove a field
  from your data class.
- `encodeDefaults = true` — fields with default values are written to JSON,
  so they show up at their JSON path and can be queried.
- `useArrayPolymorphism = true` — required for polymorphic serialization
  when value classes are involved without custom descriptors. See the
  upstream issue linked from `KotlinSqkonSerializer.kt`.

You can extend the defaults by injecting your own `Json` into `Sqkon(...)`:

```kotlin
val sqkon = Sqkon(
    scope = appScope,
    json = SqkonJson {
        prettyPrint = false
        coerceInputValues = true
    },
)
```

{: .warning }
> Do not turn `encodeDefaults` off. Fields whose value equals the default
> will be missing from the JSON, which means `Merchant::category eq "Coffee"`
> won't match a row whose category was left at its default. Sqkon assumes
> defaults are encoded.

## Sealed classes

Sealed types are supported, with one caveat: queries can only see fields
that actually serialize to JSON. Abstract `val`s and getters do not.

```kotlin
@Serializable
sealed class Card {
    val id: Uuid get() = TODO()        // not queryable — getter, not a field

    @Serializable
    data class CreditCard(
        val key: Uuid,
        val last4: String,
    ) : Card()

    @Serializable
    data class DebitCard(
        val key: Uuid,
        val last4: String,
    ) : Card()
}
```

Use the `with` helper on the parent class to query a child's field:

```kotlin
val byKey = Card::class.with(Card.CreditCard::key) eq "1"
val byLast4 = Card::class.with(Card.CreditCard::last4) eq "1234"
```

See the [Querying guide]({{ '/guides/querying/' | relative_url }}) and
[Nested fields]({{ '/guides/nested-fields/' | relative_url }}) for the
full path-builder API.

When you need to filter or order by a value whose **field name differs
per variant** (e.g. `Active.activatedAt` vs `Pending.requestedAt`), use a
`CaseWhen<T>` — see
[Querying → CASE / WHEN]({{ '/guides/querying/#case--when-per-variant-path-selection' | relative_url }}).

## Polymorphism in stores

You can open a `KeyValueStorage<Card>` and put both `CreditCard` and
`DebitCard` rows into it. kotlinx.serialization writes a class discriminator
into each JSON object so it can pick the right concrete type on read.

{: .note }
> The default class discriminator is the field name `"type"`. If your data
> class also has a `type` field, you'll get a clash — set
> `classDiscriminator = "..."` in your `SqkonJson { }` block to choose a
> different name.

Queries on a polymorphic store see the discriminator as just another JSON
field. If you only care about fields that exist on every subtype, query
through the parent type via `with`. Querying a field that exists only on
one subtype simply won't match the others — they don't have that path.

## Value classes (`@JvmInline`)

`@JvmInline value class` types serialize as their inner value — they don't
add a JSON wrapper:

```kotlin
@JvmInline
@Serializable
value class MerchantId(val raw: String)

@Serializable
data class Merchant(val id: MerchantId, val name: String)
```

A `Merchant` with `id = MerchantId("m-42")` serializes as
`{"id": "m-42", "name": "..."}` — `id` is a string, not an object. So
queries against the JSON path bind the inner value directly:

```kotlin
val byId = merchants.select(
    where = Merchant::id eq MerchantId("m-42"),
).first()
```

The `JsonPathBuilderTest` confirms this: a path through a value class
collapses to the field's parent path (e.g. `$.testValue`, not
`$.testValue.test`).

## Recovering from deserialization errors

By default, an unreadable row throws — you broke the model and the next
read raises `SerializationException`. If you'd rather drop bad rows
silently, set the policy when you build the store:

```kotlin
val merchants = keyValueStorage<Merchant>(
    entityName = "merchants",
    entityQueries = entityQueries,
    metadataQueries = metadataQueries,
    scope = appScope,
    config = KeyValueStorage.Config(
        deserializePolicy = KeyValueStorage.Config.DeserializePolicy.DELETE,
    ),
)
```

`DeserializePolicy.DELETE` returns `null` for that row and schedules a
delete on the write dispatcher. Useful when you're shipping a breaking
model change and the cache is rebuildable. The default is
`DeserializePolicy.ERROR` and you should keep it for stores where data
loss matters.

See [Migrations]({{ '/guides/migrations/' | relative_url }}) for the
full picture.

## Custom `SqkonSerializer`

If you want to use Moshi, Gson, Protobuf, or anything else, implement
`SqkonSerializer` directly. The interface is two functions:

```kotlin
interface SqkonSerializer {
    fun <T : Any> serialize(type: KType, value: T?): String?
    fun <T : Any> deserialize(type: KType, value: String?): T?
}
```

A Moshi-backed sketch:

```kotlin
class MoshiSqkonSerializer(private val moshi: Moshi) : SqkonSerializer {
    override fun <T : Any> serialize(type: KType, value: T?): String? {
        value ?: return null
        val adapter = moshi.adapter<T>(type.javaType)
        return adapter.toJson(value)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> deserialize(type: KType, value: String?): T? {
        value ?: return null
        val adapter = moshi.adapter<T>(type.javaType)
        return adapter.fromJson(value) as T
    }
}
```

Pass it as the `serializer` argument to `keyValueStorage(...)`. Whatever
JSON shape your serializer produces is the shape your `JsonPath` queries
will see — keep field names and nesting consistent with what the DSL
expects.
