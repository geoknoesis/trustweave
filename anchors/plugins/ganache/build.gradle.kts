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
    implementation("org.web3j:core:5.0.1")

    // Testcontainers for Ganache Docker container
    testImplementation(libs.testcontainers)
    testImplementation(libs.testcontainers.junit)

    // Test dependencies
    testImplementation(project(":testkit"))
    testImplementation(project(":did:did-core"))
    testImplementation(project(":kms:kms-core"))
}

