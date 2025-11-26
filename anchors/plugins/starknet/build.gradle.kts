plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.trustweave.chains"
dependencies {
    implementation(project(":anchors:anchor-core"))
    implementation(project(":common"))
    implementation(project(":credentials:credential-core"))
    
    // HTTP client for StarkNet RPC API
    // Note: StarkNet uses Cairo, not EVM, so requires different approach than Web3j
    // StarkNet SDK dependencies are not available in public Maven repositories
    // This implementation uses HTTP client directly. For production use, add StarkNet SDK
    // from StarkWare's repository or use StarkNet API client when available.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // JSON serialization
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // Test dependencies
    testImplementation(project(":testkit"))
}

