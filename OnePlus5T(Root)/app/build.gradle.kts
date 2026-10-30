import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    // Same code base as the Harmony build; only the applicationId and the install floor differ so
    // both variants can be installed side by side.
    namespace = "com.lemoneko.endfieldcharge"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.lemoneko.endfieldcharge.root"
        // OnePlus 5T ships Android 8.1 (API 27); keep 26 so 8.0 builds also install.
        minSdk = 26
        // Deliberately an Oreo-era target: this build is meant for the OnePlus 5T, and targeting
        // 28 keeps foreground-service, notification and storage behaviour at the level the device
        // actually implements.
        targetSdk = 28
        versionCode = 1
        versionName = "0.1.0-root"
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

    lint {
        // Sideloaded build for one Oreo device; the Play Store target-SDK floor does not apply.
        disable += "ExpiredTargetSdkVersion"
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
