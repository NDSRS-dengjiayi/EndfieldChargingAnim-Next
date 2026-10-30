import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.lemoneko.endfieldcharge"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.lemoneko.endfieldcharge"
        // Standalone no-root mode targets HarmonyOS/EMUI on Android 12 (API 31).
        minSdk = 31
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    testImplementation(libs.junit)
    // android.jar only ships throwing stubs for org.json, so the JVM tests need the real one.
    testImplementation(libs.json)
}
