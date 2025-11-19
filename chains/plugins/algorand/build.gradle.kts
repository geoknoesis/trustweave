plugins {
    id("vericore.shared")
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.geoknoesis.vericore.chains"
version = "1.0.0-SNAPSHOT"

dependencies {
    implementation(project(":core:vericore-core"))
    implementation(project(":chains:vericore-anchor"))
    implementation(project(":core:vericore-json"))

    // Algorand SDK
    implementation("com.algorand:algosdk:2.10.1")

    // Test dependencies
    testImplementation(project(":core:vericore-testkit"))
    testImplementation(project(":did:vericore-did")) // For did method implementations
    testImplementation(project(":kms:vericore-kms")) // For KeyManagementService
}

