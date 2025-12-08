plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.trustweave.chains"
dependencies {
    implementation(project(":common"))
    implementation(project(":anchors:anchor-core"))
    implementation(project(":credentials:credential-api"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    // Web3j for Ethereum-compatible chains
    implementation("org.web3j:core:4.9.8")

    // Test dependencies
    testImplementation(project(":testkit"))
}

