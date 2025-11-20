plugins {
    id("vericore.shared")
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.geoknoesis.vericore.did"
version = "1.0.0-SNAPSHOT"

dependencies {
    implementation(project(":did:vericore-did"))
    implementation(project(":did:plugins:base"))
    implementation(project(":core:vericore-core"))
    implementation(project(":kms:vericore-kms"))
    
    // HTTP client for IPFS/3Box API
    // Note: IPFS client dependencies may not be available in public Maven repositories
    // This implementation uses HTTP client directly. For production use, add IPFS SDK
    // from appropriate repository or use IPFS API client when available.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // JSON serialization
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")

    // Test dependencies
    testImplementation(project(":core:vericore-testkit"))
}

