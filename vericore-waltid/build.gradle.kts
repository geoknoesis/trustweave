plugins {
    id("vericore.shared")
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "io.geoknoesis.vericore"
version = "1.0.0-SNAPSHOT"

val WALTID_VERSION = "2025.1.0-PRE-RELEASE.5"

dependencies {
    implementation(project(":vericore-core"))
    implementation(project(":vericore-did"))
    implementation(project(":vericore-kms"))
    implementation(project(":vericore-json"))

    // walt.id dependencies
    implementation("id.walt.did:waltid-did:$WALTID_VERSION")
    // Add other walt.id modules as needed:
    // implementation("id.walt.crypto:waltid-crypto:$WALTID_VERSION")
    // implementation("id.walt.credentials:waltid-verifiable-credentials:$WALTID_VERSION")

    // Test dependencies
    testImplementation(project(":vericore-testkit"))
}

