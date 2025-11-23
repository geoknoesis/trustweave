plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.trustweave"
dependencies {
    implementation(project(":common"))     // For JSON utilities and common types
    implementation(project(":credentials:core"))  // For VerifiableCredential models
    implementation(project(":did:core"))    // For DID operations
    // Note: wallet depends on credentials:core for credential models, but credentials:core does NOT depend on wallet
}

