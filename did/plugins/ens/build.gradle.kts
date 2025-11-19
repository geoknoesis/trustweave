plugins {
    id("vericore.shared")
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.geoknoesis.vericore.did"
version = "1.0.0-SNAPSHOT"

dependencies {
    implementation(project(":core:vericore-core"))
    implementation(project(":did:vericore-did"))
    implementation(project(":did:plugins:base"))
    implementation(project(":did:plugins:ethr")) // Reuse ethr for Ethereum integration
    implementation(project(":chains:vericore-anchor"))
    implementation(project(":kms:vericore-kms"))
    implementation(project(":core:vericore-spi"))
    
    // Web3j for Ethereum blockchain interaction
    implementation("org.web3j:core:4.10.0")
    
    // Test dependencies
    testImplementation(project(":core:vericore-testkit"))
}

