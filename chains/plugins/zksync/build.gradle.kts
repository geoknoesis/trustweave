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
    
    // Web3j for Ethereum-compatible chains (zkSync is EVM-compatible)
    implementation("org.web3j:core:4.9.8")
    
    // HTTP client for zkSync API
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Test dependencies
    testImplementation(project(":core:vericore-testkit"))
}

