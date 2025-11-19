plugins {
    id("vericore.shared")
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.geoknoesis.vericore"
version = "1.0.0-SNAPSHOT"

dependencies {
    implementation(project(":vericore-core"))
    implementation(project(":vericore-did"))
    implementation(project(":vericore-did-base"))
    implementation(project(":vericore-anchor"))
    implementation(project(":vericore-kms"))
    implementation(project(":vericore-spi"))
    
    // Solana Web3 library
    implementation("com.solanaj:solanaj:1.0.0") // Or use newer solana-web3j if available
    // Alternative: use direct HTTP client for Solana RPC
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // Test dependencies
    testImplementation(project(":vericore-testkit"))
}

