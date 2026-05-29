plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "org.trustweave.referencewallet"
    compileSdk = 34

    defaultConfig {
        applicationId = "org.trustweave.referencewallet"
        minSdk = 29  // Android 10 — covers >97% of devices, allows StrongBox-aware key storage
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        // Wire the demo backend URL into BuildConfig so Settings can display it
        // and so the source doesn't hard-code an environment-specific host.
        buildConfigField(
            "String",
            "DEMO_BACKEND_BASE_URL",
            "\"${project.findProperty("DEMO_BACKEND_BASE_URL") ?: "http://10.0.2.2:3000"}\"",
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildTypes {
        release {
            // Don't shrink for the demo build — keeps stack traces readable when something goes wrong on stage.
            isMinifyEnabled = false
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Encrypted storage for holder key + credentials. EncryptedSharedPreferences uses
    // Android Keystore as the wrapping-key root, so the on-disk file is non-extractable
    // without device unlock (Phase 2 baseline; Phase 2.5 binds the holder signing key
    // directly to Keystore once we have an Ed25519-on-Keystore strategy).
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Networking
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Crypto — Ed25519 + did:key encoding
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")

    // Serialization for VC-JWT payloads + DTOs
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")

    // Debug tools
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
