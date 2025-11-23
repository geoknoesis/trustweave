plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.trustweave.did"
dependencies {
    implementation(project(":credentials:core"))
    implementation(project(":did:core"))
    implementation(project(":did:plugins:base"))
    implementation(project(":anchors:core"))
    implementation(project(":kms:core"))

    
    // Solana RPC via HTTP client (direct JSON-RPC)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // Test dependencies
    testImplementation(project(":testkit"))
}

