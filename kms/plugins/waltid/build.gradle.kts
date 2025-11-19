plugins {
    id("vericore.shared")
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.geoknoesis.vericore.kms"
version = "1.0.0-SNAPSHOT"

val WALTID_VERSION = "2025.1.0-PRE-RELEASE.5"

dependencies {
    implementation(project(":core:vericore-core"))
    implementation(project(":did:vericore-did"))
    implementation(project(":kms:vericore-kms"))
    implementation(project(":core:vericore-json"))

    // walt.id dependencies
    implementation("id.walt.did:waltid-did:$WALTID_VERSION")
    // Add other walt.id modules as needed:
    // implementation("id.walt.crypto:waltid-crypto:$WALTID_VERSION")
    // implementation("id.walt.credentials:waltid-verifiable-credentials:$WALTID_VERSION")

    // Test dependencies
    testImplementation(project(":core:vericore-testkit"))
}

