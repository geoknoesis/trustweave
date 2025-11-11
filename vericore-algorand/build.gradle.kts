plugins {
    id("vericore.shared")
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.geoknoesis.vericore"
version = "1.0.0-SNAPSHOT"

dependencies {
    implementation(project(":vericore-core"))
    implementation(project(":vericore-anchor"))
    implementation(project(":vericore-json"))

    // Algorand SDK
    implementation("com.algorand:algosdk:2.10.1")

    // Test dependencies
    testImplementation(project(":vericore-testkit"))
    testImplementation(project(":vericore-did")) // For did method implementations
    testImplementation(project(":vericore-kms")) // For KeyManagementService
}

