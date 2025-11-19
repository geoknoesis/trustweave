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
    implementation(project(":vericore-did-ethr")) // Reuse ethr for Ethereum integration
    implementation(project(":vericore-anchor"))
    implementation(project(":vericore-kms"))
    implementation(project(":vericore-spi"))
    
    // Web3j for Ethereum blockchain interaction
    implementation("org.web3j:core:4.10.0")
    
    // Test dependencies
    testImplementation(project(":vericore-testkit"))
}

