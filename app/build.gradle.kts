plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.gymlog.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.dcmcand.gymlog"
        minSdk = 31
        targetSdk = 36
        // Source of truth for the version: clean literals so F-Droid's update checker can parse
        // them and rebuild each new git tag reproducibly. Bump both on every release (versionCode
        // scheme: major*10000 + minor*100 + patch), then tag v<versionName>; the release
        // workflow refuses a tag that doesn't match.
        versionCode = 20002
        versionName = "2.0.2"
    }

    val keystoreFile = System.getenv("KEYSTORE_FILE")
    if (keystoreFile != null) {
        signingConfigs {
            create("release") {
                storeFile = file(keystoreFile)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // R8: drop unused code and resources (mostly library code; the APK was ~48 MB).
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Otherwise AGP embeds the git revision, so the APK would depend on how the source was
            // checked out (F-Droid rebuilds it and compares byte for byte).
            vcsInfo.include = false
            signingConfig = if (keystoreFile != null) {
                signingConfigs.getByName("release")
            } else {
                null
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    dependenciesInfo {
        // AGP otherwise stores Google-encrypted dependency metadata in the APK signing block.
        // F-Droid publishes our signed APK as-is (reproducible builds) and rejects that blob.
        // The bundle keeps it (default) for Google Play.
        includeInApk = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.vico.compose.m3)
    implementation(libs.pebblekit2.client)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}
