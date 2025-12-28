plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "org.trustweave"
dependencies {
    implementation(project(":common"))     // For JSON utilities and common types
    implementation(project(":credentials:credential-api"))  // For VerifiableCredential models
    implementation(project(":credentials:plugins:oidc4vp"))  // For OIDC4VP support in WalletHolder
    implementation(project(":did:did-core"))    // For DID operations

    // Kotlin Coroutines
    implementation(libs.kotlinx.coroutines.core)

    // Kotlinx Serialization
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)

    // Note: wallet depends on credentials:credential-api for credential models, but credentials:credential-api does NOT depend on wallet
    // Note: wallet-core depends on oidc4vp plugin for WalletHolder convenience API
}

