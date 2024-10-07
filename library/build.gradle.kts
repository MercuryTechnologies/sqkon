plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.sqlDelight)
    alias(libs.plugins.axion.release)
}

group = "com.mercury.sqkon"
version = scmVersion.version

kotlin {
    applyDefaultHierarchyTemplate()
    jvmToolchain(11)
    androidTarget {
        publishLibraryVariants("release")
    }
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
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
        }

        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.sqlDelight.driver.android)
            implementation(libs.sqlite.requery.android)
        }

        jvmMain.dependencies {
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.sqlDelight.driver.sqlite)
        }

    }

}

android {
    namespace = "com.mercury.sqkon"
    compileSdk = 34

    defaultConfig {
        minSdk = 21
    }
}

sqldelight {
    databases {
        create("SqkonDatabase") {
            // Database configuration here.
            // https://cashapp.github.io/sqldelight
            generateAsync = true
            packageName.set("com.mercury.sqkon.db")
        }
    }
}
