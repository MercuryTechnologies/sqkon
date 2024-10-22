plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.mercury.sqkon.sample"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mercury.sqkon.sample"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    //noinspection UseTomlInstead
    //https://maven.pkg.github.com/MercuryTechnologies/sqkon/com/mercury/sqkon/library-android/0.1.0-alpha01/library-android-0.1.0-alpha01.pom	355 ms	0 B	0 B/s
    //implementation("com.mercury.sqkon:library-android:0.1.0-alpha03")
}