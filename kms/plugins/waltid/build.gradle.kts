plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.trustweave.kms"
// Add waltid maven repositories
repositories {
    maven("https://maven.waltid.dev/releases")
    maven("https://maven.waltid.dev/snapshots")
}

val WALTID_VERSION = "2025.1.0-PRE-RELEASE.5"

dependencies {
    // API dependencies - exposed transitively to consumers
    api(project(":common"))
    api(project(":kms:kms-core"))
    api(project(":did:did-core"))  // waltid also provides DID methods
    
    // Implementation dependencies - internal only
    implementation(project(":credentials:credential-api"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
    implementation("id.walt.did:waltid-did:$WALTID_VERSION")

    // Logging
    implementation("org.slf4j:slf4j-api:2.0.9")
    // Add other walt.id modules as needed:
    // implementation("id.walt.crypto:waltid-crypto:$WALTID_VERSION")
    // implementation("id.walt.credentials:waltid-verifiable-credentials:$WALTID_VERSION")

    // Test dependencies
    testImplementation(project(":testkit"))
}

