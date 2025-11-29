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
    implementation(project(":common"))
    implementation(project(":credentials:credential-core"))
    implementation(project(":did:did-core"))
    implementation(project(":kms:kms-core"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation("id.walt.did:waltid-did:$WALTID_VERSION")
    // Add other walt.id modules as needed:
    // implementation("id.walt.crypto:waltid-crypto:$WALTID_VERSION")
    // implementation("id.walt.credentials:waltid-verifiable-credentials:$WALTID_VERSION")

    // Test dependencies
    testImplementation(project(":testkit"))
}

