---
layout: home
title: Home
nav_order: 1
description: "Type-safe JSONB-powered key-value store for Kotlin Multiplatform."
permalink: /
---

<div class="hero">
  <h1 class="hero__title">Sqkon</h1>
  <p class="hero__tagline">Type-safe JSONB-powered key-value store for Kotlin Multiplatform.</p>
  <p>
    <a href="{{ '/getting-started/' | relative_url }}" class="btn btn-primary fs-5 mb-4 mb-md-0 mr-2">Get Started</a>
    <a href="https://github.com/MercuryTechnologies/sqkon" class="btn fs-5 mb-4 mb-md-0">View on GitHub</a>
    <a href="{{ '/api/' | relative_url }}" class="btn fs-5 mb-4 mb-md-0">API Reference</a>
  </p>
</div>

[![Maven Central Version](https://img.shields.io/maven-central/v/com.mercury.sqkon/library)](https://central.sonatype.com/artifact/com.mercury.sqkon/library)
[![GitHub branch check runs](https://img.shields.io/github/check-runs/MercuryTechnologies/sqkon/main)](https://github.com/MercuryTechnologies/sqkon/actions)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://github.com/MercuryTechnologies/sqkon/blob/main/LICENSE)

## Install

{% raw %}
```kotlin
// build.gradle.kts
dependencies {
    implementation("com.mercury.sqkon:library:{{ site.sqkon_version }}")
}
```
{% endraw %}

## 30-second taste

Android needs a `Context`; JVM needs a `CoroutineScope`. See [Platform setup]({{ '/getting-started/platform-setup/' | relative_url }}) for both.

```kotlin
@Serializable
data class Merchant(
    val id: String,
    val name: String,
    val category: String,
)

// Android needs a Context + scope; JVM needs a scope only.
val sqkon = Sqkon(context = applicationContext, scope = appScope)
val merchants = sqkon.keyValueStorage<Merchant>("merchants")

// Insert (sync) — Sqkon dispatches the SQLite work internally.
merchants.insert("m_1", Merchant("m_1", "Chipotle", "Food"))

// Observe — emits whenever the store changes. Collect from a coroutine.
appScope.launch {
    merchants.selectAll().collect { list ->
        println("Now have ${list.size} merchants")
    }
}

// One-shot reads use Flow.first() inside a suspend block.
suspend fun loadFood(): List<Merchant> =
    merchants.select(where = Merchant::category eq "Food").first()
```

## Why Sqkon?

<div class="feature-grid" markdown="0">
  <a class="feature-card" href="{{ '/getting-started/platform-setup/' | relative_url }}">
    <span class="feature-card__title">Kotlin Multiplatform</span>
    <span class="feature-card__desc">One codebase, Android + JVM. iOS on the roadmap.</span>
  </a>
  <a class="feature-card" href="{{ '/guides/querying/' | relative_url }}">
    <span class="feature-card__title">JSONB queries</span>
    <span class="feature-card__desc">Query nested fields and lists with a type-safe DSL — no manual SQL, no DAOs.</span>
  </a>
  <a class="feature-card" href="{{ '/guides/flow/' | relative_url }}">
    <span class="feature-card__title">Reactive Flows</span>
    <span class="feature-card__desc">Every read returns a <code>Flow</code>. Writes auto-invalidate observers.</span>
  </a>
  <a class="feature-card" href="{{ '/guides/paging/' | relative_url }}">
    <span class="feature-card__title">AndroidX Paging</span>
    <span class="feature-card__desc">Built-in keyset and offset <code>PagingSource</code>s for Compose and views.</span>
  </a>
  <a class="feature-card" href="{{ '/guides/expiry/' | relative_url }}">
    <span class="feature-card__title">TTL / Expiry</span>
    <span class="feature-card__desc">First-class expiry on every entry. Perfect for caches.</span>
  </a>
  <a class="feature-card" href="{{ '/concepts/architecture/' | relative_url }}">
    <span class="feature-card__title">Built on SQLDelight</span>
    <span class="feature-card__desc">Battle-tested SQLite, with type-safe codegen underneath.</span>
  </a>
</div>

## Next steps

- [Quickstart →]({{ '/getting-started/quickstart/' | relative_url }})
- [What it is, what it isn't →]({{ '/concepts/what-it-is/' | relative_url }})
- [Querying with JsonPath →]({{ '/guides/querying/' | relative_url }})
- [Compare with Room, DataStore, Realm, MMKV →]({{ '/concepts/comparison/' | relative_url }})
- [Platform setup (Android & JVM) →]({{ '/getting-started/platform-setup/' | relative_url }})
- [API reference →]({{ '/api/' | relative_url }})
