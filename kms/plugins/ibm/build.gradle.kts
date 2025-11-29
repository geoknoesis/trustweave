plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.trustweave.kms"
dependencies {
    implementation(project(":common"))
    implementation(project(":credentials:credential-core"))
    implementation(project(":kms:kms-core"))
    implementation(libs.kotlinx.coroutines.core)

    // HTTP client for IBM Key Protect API
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // JSON serialization
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")

    // Note: IBM Cloud SDK dependencies are not available in public Maven repositories
    // This implementation uses HTTP client directly. For production use, add IBM SDK
    // from IBM's repository or use IBM Cloud SDK when available.

    // Test dependencies
    testImplementation(project(":testkit"))
}

