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

    // Web3j for Ethereum blockchain
    implementation("org.web3j:core:5.0.1")

    // Test dependencies
    testImplementation(project(":core:vericore-testkit"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation(kotlin("test"))
}

