plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "org.trustweave.chains"
dependencies {
    implementation(project(":common"))
    implementation(project(":credentials:credential-api"))
    implementation(project(":anchors:anchor-core"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation("com.algorand:algosdk:2.10.1")

    // Test dependencies
    testImplementation(project(":testkit"))
    testImplementation(project(":did:did-core")) // For did method implementations
    testImplementation(project(":kms:kms-core")) // For KeyManagementService
    testRuntimeOnly(libs.junit.jupiter.engine)
}

