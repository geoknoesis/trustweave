plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "org.trustweave.chains"
dependencies {
    implementation(project(":common"))
    // Shared EVM anchor base (exposes anchor-core and web3j transitively)
    api(project(":anchors:plugins:evm-base"))
    implementation(project(":credentials:credential-api"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    // Test dependencies
    testImplementation(project(":testkit"))
}

