plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.trustweave.chains"
dependencies {
    implementation(project(":anchors:anchor-core"))
    implementation(project(":common"))
    implementation(project(":credentials:credential-core"))
    
    // Web3j for Ethereum-compatible chains (zkSync is EVM-compatible)
    implementation("org.web3j:core:4.9.8")
    
    // HTTP client for zkSync API
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // Test dependencies
    testImplementation(project(":testkit"))
}

