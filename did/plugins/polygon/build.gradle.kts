plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.trustweave.did"
dependencies {
    implementation(project(":credentials:core"))
    implementation(project(":did:core"))
    implementation(project(":did:plugins:base"))
    implementation(project(":did:plugins:ethr")) // Reuse ethr implementation
    implementation(project(":anchors:core"))
    implementation(project(":kms:core"))

    
    // Web3j for Polygon blockchain interaction
    implementation("org.web3j:core:4.10.0")
    
    // Test dependencies
    testImplementation(project(":testkit"))
}

