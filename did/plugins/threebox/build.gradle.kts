plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "org.trustweave.did"
dependencies {
    implementation(project(":common"))
    implementation(project(":did:did-core"))
    implementation(project(":did:plugins:base"))
    implementation(project(":credentials:credential-api"))
    implementation(project(":kms:kms-core"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    // HTTP client for IPFS/3Box API
    // Note: IPFS client dependencies may not be available in public Maven repositories
    // This implementation uses HTTP client directly. For production use, add IPFS SDK
    // from appropriate repository or use IPFS API client when available.
    implementation(libs.okhttp)

    // JSON serialization
    implementation(libs.jackson.module.kotlin)

    // Test dependencies
    testImplementation(project(":testkit"))
}

