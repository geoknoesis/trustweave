plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.trustweave.chains"
dependencies {
    implementation(project(":credentials:core"))
    implementation(project(":anchors:core"))
    implementation("com.algorand:algosdk:2.10.1")

    // Test dependencies
    testImplementation(project(":testkit"))
    testImplementation(project(":did:core")) // For did method implementations
    testImplementation(project(":kms:core")) // For KeyManagementService
}

