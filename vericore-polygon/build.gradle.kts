plugins {
    id("vericore.shared")
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "io.geoknoesis.vericore"
version = "1.0.0-SNAPSHOT"

dependencies {
    implementation(project(":vericore-core"))
    implementation(project(":vericore-anchor"))
    implementation(project(":vericore-json"))

    // Web3j for Polygon/Ethereum-compatible chains
    implementation("org.web3j:core:5.0.1")

    // Test dependencies
    testImplementation(project(":vericore-testkit"))
}

