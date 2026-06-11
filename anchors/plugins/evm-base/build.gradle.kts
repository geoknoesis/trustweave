plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "org.trustweave.chains"
dependencies {
    implementation(project(":common"))
    // Exposed to the EVM plugins: AbstractEvmAnchorClient extends anchor-core's
    // AbstractBlockchainAnchorClient and surfaces web3j types (Web3j, receipts, …).
    // anchor-core itself stays web3j-free for non-EVM consumers.
    api(project(":anchors:anchor-core"))
    api(libs.web3j)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.slf4j.api)

    // Test dependencies
    testImplementation(project(":testkit"))
    testRuntimeOnly(libs.junit.jupiter.engine)
}
