plugins {
    id("vericore.shared")
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.geoknoesis.vericore.chains"
version = "1.0.0-SNAPSHOT"

dependencies {
    implementation(project(":chains:vericore-anchor"))
    implementation(project(":core:vericore-core"))
    
    // Web3j for Ethereum-compatible chains
    implementation("org.web3j:core:4.9.8")

    // Test dependencies
    testImplementation(project(":core:vericore-testkit"))
}

