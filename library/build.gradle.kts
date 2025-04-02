import app.cash.sqldelight.VERSION
import com.android.build.api.variant.HasUnitTestBuilder
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetTree

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.sqlDelight)
    alias(libs.plugins.maven.publish)
}

kotlin {
    applyDefaultHierarchyTemplate()
    androidTarget {
        publishLibraryVariants("release")
    }
    jvmToolchain(21)
    jvm()
    sourceSets {
        commonMain.dependencies {
            implementation(libs.androidx.sqlite.core)
            implementation(libs.androidx.sqlite.bundled)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.sqlDelight.androidx.driver)
            implementation(libs.sqlDelight.coroutines)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.paging.common)
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
            implementation(libs.sqlDelight.driver.android)
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
    compileSdk = 35

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

sqldelight {
    databases {
        create("SqkonDatabase") {
            // Database configuration here.
            // https://cashapp.github.io/sqldelight
            generateAsync = true
            packageName.set("com.mercury.sqkon.db")
            schemaOutputDirectory.set(file("src/commonMain/sqldelight/databases"))
            // We're technically using 3.45.0, but 3.38 is the latest supported version
            dialect("app.cash.sqldelight:sqlite-3-38-dialect:$VERSION")
        }
    }
}

androidComponents {
    beforeVariants {
        it.enableAndroidTest = true
        (it as HasUnitTestBuilder).enableUnitTest = false
    }
}
