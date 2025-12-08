plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    // Core dependencies
    implementation(project(":common"))
    implementation(project(":did:did-core"))
    implementation(project(":kms:kms-core"))  // Needed for proof engines
    
    // credential-api is now self-contained with its own identifiers and types
    // No dependency on credential-core needed

    // Kotlinx dependencies for serialization and coroutines
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)

    // Proof engine dependencies (built-in)
    // JSON-LD for VC-LD canonicalization
    implementation(libs.jsonld.java)
    // Cryptographic libraries for LD-Proof signatures
    implementation(libs.bouncycastle.prov)
    // JWT library for SD-JWT-VC
    implementation(libs.nimbus.jose.jwt)

    // Test dependencies
    testImplementation(project(":testkit"))
    testImplementation(project(":kms:kms-core"))
    testImplementation(project(":trust"))
}

