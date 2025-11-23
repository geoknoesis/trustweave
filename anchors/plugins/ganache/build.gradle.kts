plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.trustweave.chains"
dependencies {
    implementation(project(":credentials:core"))
    implementation(project(":anchors:core"))
    implementation("org.web3j:core:5.0.1")

    // Testcontainers for Ganache Docker container
    testImplementation(libs.testcontainers)
    testImplementation(libs.testcontainers.junit)

    // Test dependencies
    testImplementation(project(":testkit"))
    testImplementation(project(":did:core"))
    testImplementation(project(":kms:core"))
}

