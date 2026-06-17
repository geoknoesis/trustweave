plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "org.trustweave.did"
dependencies {
    implementation(project(":common"))
    implementation(project(":did:did-core"))
    implementation(project(":did:plugins:base"))
    implementation(project(":credentials:credential-api"))
    implementation(project(":kms:kms-core"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    // BitcoinJ for Bitcoin blockchain interaction
    implementation("org.bitcoinj:bitcoinj-core:0.17.1")

    // HTTP client
    implementation(libs.okhttp)

    // Test dependencies
    testImplementation(project(":testkit"))
}

