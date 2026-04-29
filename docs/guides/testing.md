---
layout: default
title: Testing
parent: Guides
nav_order: 11
---

# Testing
{: .no_toc }

1. TOC
{:toc}

Sqkon is designed to be tested with the same in-memory SQLite driver it
uses on JVM. Tests are fast, hermetic, and don't need an Android emulator
for the vast majority of behavior. This page captures the patterns the
library itself uses so your app tests can do the same.

```kotlin
@Serializable
data class Merchant(val id: String, val name: String, val category: String)
```

## In-memory database

For tests, build a `Sqkon` against an in-memory SQLite database. Both
platforms support this through the regular public factories:

- **JVM** — `Sqkon(scope)` defaults to `AndroidxSqliteDatabaseType.Memory`,
  so a no-arg call gives you a hermetic in-memory database.
- **Android** — pass `dbFileName = null` to `Sqkon(context, scope, dbFileName = null)`
  to get an in-memory database for instrumented tests.

`DriverFactory` and Sqkon's internal driver wiring are not part of the
supported public surface — go through the `Sqkon(...)` factory instead.
`EntityQueries` / `MetadataQueries` are visible types, but constructing them
yourself bypasses the dispatcher / lifecycle setup the factory does for you;
prefer `Sqkon(...)` in app and test code alike.

## Test setup pattern

```kotlin
class MerchantStorageTest {

    private val testScope = TestScope()

    // JVM: in-memory by default
    private val sqkon: Sqkon = Sqkon(scope = testScope)
    private val storage: KeyValueStorage<Merchant> =
        sqkon.keyValueStorage("merchants")

    @After
    fun tearDown() {
        testScope.cancel()
    }

    @Test
    fun roundtrip() = testScope.runTest {
        val merchant = Merchant(id = "m-1", name = "Cafe", category = "Coffee")
        storage.insert(merchant.id, merchant)

        val actual = storage.selectByKey(merchant.id).first()
        assertEquals(merchant, actual)
    }
}
```

The library's own internal tests construct `EntityQueries` directly because
they live in the same module — that pattern is private to the library and not
the recommended consumer setup.

Notes:

- Use `runTest` from `kotlinx-coroutines-test` for suspending test
  functions — it gives you a fast virtual-time scheduler and proper Flow
  collection support.
- Each test class gets its own in-memory database via `driverFactory()`.
  Tests don't share state.

{: .warning }
> **Always cancel the scope you passed to `Sqkon` in `tearDown`.** Sqkon
> launches background coroutines on it (for `read_at` / `write_at`
> bookkeeping and `DeserializePolicy.DELETE` cleanup). If you don't cancel
> the scope, those coroutines keep running across tests, which leaks
> memory and can cause non-deterministic failures when one test sees rows
> a previous test was still cleaning up.

## Turbine for Flow assertions

Every Sqkon read returns a `Flow`. Turbine is the cleanest way to assert
on what a Flow emits over time:

```kotlin
@Test
fun selectAll_emits_when_inserting() = runTest {
    storage.selectAll().test {
        assertEquals(emptyList(), awaitItem())

        storage.insert("m-1", Merchant("m-1", "Cafe", "Coffee"))
        assertEquals(
            listOf(Merchant("m-1", "Cafe", "Coffee")),
            awaitItem(),
        )

        cancelAndIgnoreRemainingEvents()
    }
}
```

Two things to know:

- The first `awaitItem()` is the initial emission, before any writes — it
  reflects the state at the moment you started collecting.
- Each write emits a new `List<T>`. `cancelAndIgnoreRemainingEvents()`
  cleans up any in-flight metadata-bookkeeping emissions when the test
  block ends.

For one-shot reads where you don't care about subsequent emissions,
`.first()` is enough — that's what most tests in the library use.

## JVM vs Android tests

Two test loops, two purposes:

- `./gradlew jvmTest` — fast iteration loop, runs `commonTest` and
  `jvmTest`. This is where 95 % of your tests should live. Use this
  during development.
- `./gradlew allDevicesDebugAndroidTest` — Android instrumented tests on
  a managed emulator, for behavior that depends on the Android driver
  (file-backed databases, content provider integration, etc.). Slow; CI
  runs it.

The library's own CI runs both — see `.github/workflows/ci.yml`.

## What NOT to do

Two rules from the project's CLAUDE.md that apply equally to tests built
on top of Sqkon:

- **Do not add Android unit tests** (`enableUnitTest = false`). Use
  `commonTest` + `jvmTest` for fast iteration, and Android instrumented
  tests for device-specific behavior. Android unit tests on the
  Robolectric / "JVM-running-Android" path don't add coverage Sqkon
  cares about.
- **Do not set `generateAsync = true` in SQLDelight.** The Sqkon driver
  doesn't support it, and concurrency is already handled by the
  read/write coroutine dispatchers Sqkon uses internally.

## Where to put test data classes

For the library itself, shared test data classes live in
`library/src/commonTest/kotlin/com/mercury/sqkon/TestDataClasses.kt`. In
your own project, do the same — put any `@Serializable` types you reuse
across test classes in a single shared file in your test source set, so
the class hierarchy stays consistent and you don't accidentally test
against stale shapes.
