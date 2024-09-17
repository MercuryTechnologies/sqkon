plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.sqlDelight)
    //id("convention.publication")
}

group = "com.mercury.sqkon"
version = "1.0"

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
            implementation(libs.kotlinx.serialization.json.get().toString()) {
                // Fix https://github.com/Kotlin/kotlinx.serialization/issues/2810
                exclude("org.jetbrains.kotlin")
            }
            implementation(libs.kotlinx.datetime)
        }

        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
            implementation(kotlin("test"))
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
