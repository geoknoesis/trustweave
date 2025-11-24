plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.trustweave.chains"
dependencies {
    implementation(project(":anchors:anchor-core"))
    implementation(project(":credentials:credential-core"))
    
    // Web3j for Ethereum-compatible chains
    implementation("org.web3j:core:4.9.8")

    // Test dependencies
    testImplementation(project(":testkit"))
}

