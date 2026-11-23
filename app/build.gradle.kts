import java.io.FileInputStream
import java.util.Properties

// Release signing secrets live in keystore.properties at the repo root
// (gitignored). Absent on machines without the key (e.g. CI) — release
// signing simply won't be configured there.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) load(FileInputStream(keystorePropertiesFile))
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
}

// Kotlin static analysis. Report-only: ignoreFailures keeps detekt from ever
// failing the build — it surfaces bugs/smells without blocking development.
detekt {
    buildUponDefaultConfig = true
    ignoreFailures = true
    basePath = rootDir.absolutePath
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
}

android {
    namespace = "com.scrollnumb.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.scrollnumb.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 44
        versionName = "1.2.22"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = rootProject.file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            // Package native-library debug symbols into the AAB so Play
            // Console crash/ANR reports show readable stack frames instead of
            // raw addresses (fixes the "no debug symbols" upload warning).
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
            }
            // R8 full minification + resource shrinking: strips dead code,
            // unused resources, and obfuscates — the "garbage offloading" pass.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    lint {
        // Translations are intentionally partial — untranslated strings fall back
        // to the default locale (MissingTranslation), and some locales still carry
        // keys dropped from the default (ExtraTranslation). Both are i18n hygiene,
        // not functional bugs, and were masking real lint errors by aborting it.
        disable += setOf("MissingTranslation", "ExtraTranslation")
    }

    bundle {
        // The app switches locale at runtime (in-app language picker). Per-language
        // splits would strip non-active locales from the install, breaking that
        // switch in an App Bundle release. Ship every locale in the base module.
        language {
            enableSplit = false
        }
    }
}

// Export the Room schema JSON for every version so migrations can be written
// and validated against a known baseline. The v3 schema is captured on the
// next build; future version bumps require a matching Migration.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.animation)
    implementation(libs.androidx.foundation)
    debugImplementation(libs.androidx.ui.tooling)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Image Loading
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Image cropping — move/zoom/crop UI for profile photos (CanHub fork)
    implementation("com.vanniktech:android-image-cropper:4.6.0")

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Lottie

    // Glance Widget
    implementation(libs.androidx.glance.appwidget)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // Firebase
    // Google Play in-app review (native rating sheet) + flexible in-app update
    implementation("com.google.android.play:review:2.0.2")
    implementation("com.google.android.play:review-ktx:2.0.2")
    implementation("com.google.android.play:app-update:2.1.0")
    implementation("com.google.android.play:app-update-ktx:2.1.0")

    // Unit tests — guards the XP/week/league invariants (pure functions)
    testImplementation("junit:junit:4.13.2")
}
