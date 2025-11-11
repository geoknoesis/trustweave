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

    // Web3j for Ethereum-compatible chains (Ganache)
    implementation("org.web3j:core:5.0.1")

    // Testcontainers for Ganache Docker container
    testImplementation(Libs.testcontainers)
    testImplementation(Libs.testcontainersJunit)

    // Test dependencies
    testImplementation(project(":vericore-testkit"))
    testImplementation(project(":vericore-did"))
    testImplementation(project(":vericore-kms"))
}

