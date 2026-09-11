plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "org.trustweave.wallet"

dependencies {
    implementation(project(":common"))
    implementation(project(":credentials:credential-api"))
    implementation(project(":wallet:wallet-core")) // Wallet interfaces
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)

    // Logging
    implementation(libs.slf4j.api)

    // Encryption libraries
    implementation(libs.bouncycastle.prov)
    implementation(libs.bouncycastle.pkix)

    // Test dependencies
    testImplementation(project(":testkit"))
}
