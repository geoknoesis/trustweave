plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "org.trustweave.chains"
dependencies {
    implementation(project(":common"))
    implementation(project(":credentials:credential-api"))
    // Shared EVM anchor base (exposes anchor-core and web3j transitively)
    api(project(":anchors:plugins:evm-base"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    // Testcontainers for Ganache Docker container
    testImplementation(libs.testcontainers)
    testImplementation(libs.testcontainers.junit)

    // Test dependencies
    testImplementation(project(":testkit"))
    testImplementation(project(":did:did-core"))
    testImplementation(project(":kms:kms-core"))
}

