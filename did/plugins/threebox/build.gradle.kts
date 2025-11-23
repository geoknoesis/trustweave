plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.trustweave.did"
dependencies {
    implementation(project(":did:core"))
    implementation(project(":did:plugins:base"))
    implementation(project(":credentials:core"))
    implementation(project(":kms:core"))
    
    // HTTP client for IPFS/3Box API
    // Note: IPFS client dependencies may not be available in public Maven repositories
    // This implementation uses HTTP client directly. For production use, add IPFS SDK
    // from appropriate repository or use IPFS API client when available.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // JSON serialization
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")

    // Test dependencies
    testImplementation(project(":testkit"))
}

