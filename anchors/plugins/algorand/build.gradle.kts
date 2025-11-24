plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.trustweave.chains"
dependencies {
    implementation(project(":credentials:credential-core"))
    implementation(project(":anchors:anchor-core"))
    implementation("com.algorand:algosdk:2.10.1")

    // Test dependencies
    testImplementation(project(":testkit"))
    testImplementation(project(":did:did-core")) // For did method implementations
    testImplementation(project(":kms:kms-core")) // For KeyManagementService
}

