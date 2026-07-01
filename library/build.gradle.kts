import com.android.build.api.variant.HasUnitTestBuilder
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetTree

import java.net.URI

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.dokka)
}

dokka {
    dokkaPublications.html {
        moduleName.set("Sqkon")
        includes.from(rootDir.resolve("README.MD"))
    }
    dokkaSourceSets.configureEach {
        skipEmptyPackages.set(true)
        skipDeprecated.set(false)
        sourceLink {
            localDirectory.set(file("src"))
            remoteUrl.set(URI("https://github.com/MercuryTechnologies/sqkon/blob/main/library/src"))
            remoteLineSuffix.set("#L")
        }
        listOf(
            "kotlinx-serialization" to "https://kotlinlang.org/api/kotlinx.serialization/",
            "kotlinx-coroutines"    to "https://kotlinlang.org/api/kotlinx.coroutines/",
        ).forEach { (name, url) ->
            externalDocumentationLinks.register(name) {
                this.url.set(URI(url))
            }
        }
    }
}

kotlin {
    applyDefaultHierarchyTemplate()
    androidTarget {
        publishLibraryVariants("release")
    }
    jvmToolchain(21)
    jvm()
    iosArm64()
    iosSimulatorArm64()
    sourceSets {
        commonMain.dependencies {
            // androidx.sqlite + collection are internal-only — keep them off the consumer classpath.
            implementation(libs.androidx.collection)
            implementation(libs.androidx.sqlite.core)
            implementation(libs.androidx.sqlite.bundled)
            // These appear in Sqkon's public API and must be `api` so they land transitively on the
            // consumer's compile classpath: coroutines (Flow<List<T>>, CoroutineScope), serialization
            // (Json on Sqkon/SqkonSerializer), and paging (PagingSource from select*PagingSource). #78
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.serialization.json)
            api(libs.paging.common)
            // Don't include other paging, just the base to generate pageable queries
        }

        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
            implementation(kotlin("test"))
            implementation(libs.paging.testing)
            implementation(libs.turbine)
        }

        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
        }

        jvmMain.dependencies {
            implementation(libs.kotlinx.coroutines.swing)
        }

    }
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        instrumentedTestVariant.sourceSetTree.set(KotlinSourceSetTree.test)
    }
}

android {
    namespace = "com.mercury.sqkon"
    compileSdk = 36

    defaultConfig {
        minSdk = 23
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    @Suppress("UnstableApiUsage")
    testOptions {
        managedDevices {
            localDevices {
                create("mediumPhoneApi35") {
                    device = "Medium Phone"
                    apiLevel = 35
                    systemImageSource = "aosp-atd"
                }
            }
        }
    }
}

dependencies {
    androidTestImplementation(libs.androidx.test.monitor)
    androidTestImplementation(libs.androidx.test.runner)
}

androidComponents {
    beforeVariants {
        it.enableAndroidTest = true
        (it as HasUnitTestBuilder).enableUnitTest = false
    }
}

// The paging benchmark (PagingBenchmark) is skipped unless sqkon.benchmark=true. Gradle forks a
// separate JVM for tests, so forward the property (and optional row count) into that JVM. Scoped to
// jvmTest — the only task that runs the benchmark — so -Dsqkon.benchmark doesn't become an input on
// unrelated Test tasks and churn their up-to-date/cache state.
tasks.withType<Test>().matching { it.name == "jvmTest" }.configureEach {
    systemProperty("sqkon.benchmark", providers.systemProperty("sqkon.benchmark").getOrElse("false"))
    providers.systemProperty("sqkon.benchmark.rows").orNull
        ?.let { systemProperty("sqkon.benchmark.rows", it) }
}

// MOB-3294: keep SQLDelight/eygraber imports out of the codebase after the androidx.sqlite
// migration. Checks imports only, so the Apache-2.0 attribution KDoc that names the upstream
// projects is left untouched. Wired into `check` and invoked directly in CI (see ci.yml).
val checkNoSqlDelightImports by tasks.registering {
    group = "verification"
    description = "Fails if any app.cash.sqldelight or com.eygraber.sqldelight import returns under library/src."
    val srcRoot = layout.projectDirectory.dir("src").asFile
    doLast {
        val offenders = srcRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { i, line ->
                    val trimmed = line.trimStart()
                    val isImport = trimmed.startsWith("import ") &&
                        ("app.cash.sqldelight" in trimmed || "com.eygraber.sqldelight" in trimmed)
                    if (isImport) "${file.relativeTo(srcRoot)}:${i + 1}: ${line.trim()}" else null
                }
            }
            .toList()
        if (offenders.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("SQLDelight/eygraber imports must not return after the androidx.sqlite migration (MOB-3294):")
                    offenders.forEach { appendLine("  $it") }
                },
            )
        }
    }
}

tasks.named("check") { dependsOn(checkNoSqlDelightImports) }
