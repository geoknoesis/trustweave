plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "org.trustweave.chains"
dependencies {
    implementation(project(":common"))
    implementation(project(":anchors:anchor-core"))
    implementation(project(":credentials:credential-api"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    // HTTP client for StarkNet RPC API
    // Note: StarkNet uses Cairo, not EVM, so requires different approach than Web3j
    // StarkNet SDK dependencies are not available in public Maven repositories
    // This implementation uses HTTP client directly. For production use, add StarkNet SDK
    // from StarkWare's repository or use StarkNet API client when available.
    implementation(libs.okhttp)

    // JSON serialization
    implementation(libs.jackson.module.kotlin)
    implementation(libs.kotlinx.serialization.json)

    // Test dependencies
    testImplementation(project(":testkit"))
}

