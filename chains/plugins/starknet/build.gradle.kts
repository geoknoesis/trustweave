plugins {
    id("vericore.shared")
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.geoknoesis.vericore.chains"
version = "1.0.0-SNAPSHOT"

dependencies {
    implementation(project(":chains:vericore-anchor"))
    implementation(project(":core:vericore-core"))
    
    // HTTP client for StarkNet RPC API
    // Note: StarkNet uses Cairo, not EVM, so requires different approach than Web3j
    // StarkNet SDK dependencies are not available in public Maven repositories
    // This implementation uses HTTP client directly. For production use, add StarkNet SDK
    // from StarkWare's repository or use StarkNet API client when available.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // JSON serialization
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")

    // Test dependencies
    testImplementation(project(":core:vericore-testkit"))
}

