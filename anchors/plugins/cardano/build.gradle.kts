plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "org.trustweave.chains"

dependencies {
    implementation(project(":common"))
    implementation(project(":anchors:anchor-core"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    // HTTP client for Blockfrost API and direct submission
    implementation(libs.okhttp)

    // JSON + CBOR (Cardano metadata canonicalises to CBOR)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.jackson.dataformat.cbor)

    // Cardano transaction building & Blockfrost backend (CIP-20 transaction metadata)
    implementation("com.bloxbean.cardano:cardano-client-lib:0.5.1")
    implementation("com.bloxbean.cardano:cardano-client-backend-blockfrost:0.5.1")

    // Test dependencies — keep light to avoid coupling to broken-in-CI modules.
    testImplementation(libs.bundles.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockwebserver)
    testRuntimeOnly(libs.junit.jupiter.engine)
}
