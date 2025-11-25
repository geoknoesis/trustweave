plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.trustweave"
dependencies {
    implementation(project(":common"))     // For JSON utilities and common types
    implementation(project(":credentials:credential-core"))  // For VerifiableCredential models
    implementation(project(":did:did-core"))    // For DID operations
    
    // Kotlin Coroutines
    implementation(libs.kotlinx.coroutines.core)
    
    // Kotlinx Serialization
    implementation(libs.kotlinx.serialization.json)
    
    // Note: wallet depends on credentials:credential-core for credential models, but credentials:credential-core does NOT depend on wallet
}

