---
layout: default
title: Caching API responses
parent: Examples
nav_order: 1
---

# Caching API responses

Most apps wrap their HTTP layer in a thin cache: hit the network on first read,
remember the result for some TTL, then short-circuit on subsequent reads.
Sqkon's `expiresAt` parameter does the bookkeeping for you — set a deadline
when you write, pass `expiresAfter = Clock.System.now()` when you read, and
expired rows simply don't come back.

## The cache value

Wrap the API payload in a small envelope so you can store metadata alongside
the body:

```kotlin
import kotlinx.serialization.Serializable

@Serializable
data class CachedResponse<T>(
    val payload: T,
    val statusCode: Int,
)
```

Make sure `T` itself is `@Serializable` — Sqkon delegates to
`kotlinx.serialization`, so the usual rules apply.

## The cache class

The cache exposes a single suspending entry point: `getOrFetch`. It returns the
cached value when a non-expired one exists, otherwise it calls your `fetch`
lambda and writes the result with a 15-minute TTL.

`selectByKey` does NOT apply expiry filtering — it returns whatever row is in
the store. Use `selectByKeys(..., expiresAfter = ...)` for the single-key
read; it's the supported way to combine an exact-key lookup with TTL
filtering, and it stays correct even when `deleteExpired` hasn't run yet.

```kotlin
import com.mercury.sqkon.db.KeyValueStorage
import kotlinx.coroutines.flow.first
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

class ApiCache<T : Any>(
    private val storage: KeyValueStorage<CachedResponse<T>>,
    private val clock: Clock = Clock.System,
    private val ttl: kotlin.time.Duration = 15.minutes,
) {
    suspend fun getOrFetch(
        key: String,
        fetch: suspend () -> CachedResponse<T>,
    ): CachedResponse<T> {
        // Filter expired rows at read time — selectByKey alone does NOT.
        val cached = storage
            .selectByKeys(listOf(key), expiresAfter = clock.now())
            .first()
            .firstOrNull()
        if (cached != null) return cached

        val fresh = fetch()
        storage.upsert(key, fresh, expiresAt = clock.now() + ttl)
        return fresh
    }
}
```

A simpler alternative if you're confident `deleteExpired` runs often enough:
keep `selectByKey` and accept that an expired row may be returned in the
small window between expiry and the next purge. Choose based on how stale
"stale" is allowed to be.

The envelope shape (`CachedResponse<T>`) is also easy to grow later — add
`etag`, `lastModified`, or response headers without breaking the schema.

{: .note }
Inject `clock` rather than calling `Clock.System.now()` directly. Tests can
substitute a fixed instant or a `TestClock` and assert TTL behavior without
sleeping.

## Wiring the store

```kotlin
import com.mercury.sqkon.db.Sqkon
import com.mercury.sqkon.db.keyValueStorage

val sqkon = Sqkon(context, scope) // Android; on JVM use Sqkon(scope)
val merchantApiStore = sqkon.keyValueStorage<CachedResponse<MerchantList>>("api.merchants")
val cache = ApiCache(merchantApiStore)

suspend fun loadMerchants(): MerchantList =
    cache.getOrFetch("page=1") {
        CachedResponse(payload = api.fetchMerchants(), statusCode = 200)
    }.payload
```

## Periodic cleanup

`expiresAt` only blocks expired rows from queries that ask for it — the rows
themselves linger until you delete them. Run `deleteExpired` periodically to
reclaim space.

**Android** — schedule a `CoroutineWorker` via WorkManager:

```kotlin
class CachePurgeWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        merchantApiStore.deleteExpired() // defaults to Clock.System.now()
        return Result.success()
    }
}
```

**JVM** — a simple coroutine loop in your application scope works fine:

```kotlin
applicationScope.launch {
    while (isActive) {
        merchantApiStore.deleteExpired()
        delay(1.hours)
    }
}
```

{: .important }
`deleteExpired` runs in a transaction. If you have many stores, call it on
each — it's scoped per `entityName`.

## Where to go next

- [Expiry guide]({{ '/guides/expiry/' | relative_url }}) — the full TTL story.
- [Offline-first sync]({{ '/examples/offline-first/' | relative_url }}) — for
  when you also want to read while the network is down.
