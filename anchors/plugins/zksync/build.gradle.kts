plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "org.trustweave.chains"
dependencies {
    implementation(project(":common"))
    implementation(project(":anchors:anchor-core"))
    implementation(project(":credentials:credential-api"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    // Web3j for Ethereum-compatible chains (zkSync is EVM-compatible)
    implementation("org.web3j:core:4.9.8")

    // HTTP client for zkSync API
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Test dependencies
    testImplementation(project(":testkit"))
}

