---
layout: default
title: Installation
parent: Getting Started
nav_order: 1
---

# Installation
{: .no_toc }

<details open markdown="block">
  <summary>Table of contents</summary>
  {: .text-delta }
1. TOC
{:toc}
</details>

Sqkon is published to Maven Central as `com.mercury.sqkon:library`. It is a Kotlin
Multiplatform library targeting **Android** and **JVM**. The current version is
`{% raw %}{{ site.sqkon_version }}{% endraw %}`.

## Prerequisites

- **Kotlin** 2.0+ (Sqkon is currently built against Kotlin 2.3.x)
- **Java toolchain** 21 (required for the build; consumers compile against the artifacts)
- **Android** `minSdk` 23+ (Android 6.0 Marshmallow)
- The [`kotlinx-serialization`](https://github.com/Kotlin/kotlinx.serialization) Gradle plugin applied to your module — Sqkon stores values as JSON

## Add Maven Central

Sqkon is on Maven Central, no special repositories are needed.

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google() // only required if you also depend on AndroidX libraries
    }
}
```

{: .note }
> If your project already pulls AndroidX or Compose dependencies, `mavenCentral()`
> is almost certainly already configured — no change needed.

## Add the dependency

### Kotlin Multiplatform (`commonMain`)

```kotlin
// build.gradle.kts
plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("com.mercury.sqkon:library:{% raw %}{{ site.sqkon_version }}{% endraw %}")
        }
    }
}
```

### Android-only

```kotlin
// build.gradle.kts (Android module)
plugins {
    id("com.android.library") // or com.android.application
    kotlin("android")
    kotlin("plugin.serialization")
}

dependencies {
    implementation("com.mercury.sqkon:library:{% raw %}{{ site.sqkon_version }}{% endraw %}")
}
```

### JVM-only

```kotlin
// build.gradle.kts (JVM module)
plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    implementation("com.mercury.sqkon:library:{% raw %}{{ site.sqkon_version }}{% endraw %}")
}
```

## Version catalog

If you use a Gradle version catalog, add Sqkon to `gradle/libs.versions.toml`:

```toml
[versions]
sqkon = "{% raw %}{{ site.sqkon_version }}{% endraw %}"

[libraries]
sqkon = { module = "com.mercury.sqkon:library", version.ref = "sqkon" }
```

Then reference it in your build script:

```kotlin
dependencies {
    implementation(libs.sqkon)
}
```

{: .note }
> Sqkon brings its own SQLDelight driver and the AndroidX SQLite bundled native
> library transitively. You do **not** need to add SQLDelight or
> `androidx.sqlite` directly unless you use them outside Sqkon.

## Verify

Run a quick build to confirm resolution:

```bash
./gradlew :app:dependencies --configuration releaseRuntimeClasspath | grep sqkon
```

You should see `com.mercury.sqkon:library:{% raw %}{{ site.sqkon_version }}{% endraw %}` in the
resolved tree.

## Next

- [Quickstart]({{ '/getting-started/quickstart/' | relative_url }}) — define a model and run your first query.
- [Platform setup]({{ '/getting-started/platform-setup/' | relative_url }}) — Android and JVM construction details.
