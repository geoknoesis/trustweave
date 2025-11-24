plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.trustweave.did"
dependencies {
    implementation(project(":credentials:credential-core"))
    implementation(project(":did:did-core"))
    implementation(project(":did:plugins:base"))
    implementation(project(":anchors:anchor-core"))
    implementation(project(":kms:kms-core"))

    
    // Web3j for Ethereum blockchain interaction
    implementation("org.web3j:core:4.10.0")
    
    // Test dependencies
    testImplementation(project(":testkit"))
}

