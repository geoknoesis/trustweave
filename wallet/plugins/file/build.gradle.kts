plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "org.trustweave.core"
dependencies {
    implementation(project(":common"))
    implementation(project(":credentials:credential-api"))
    implementation(project(":wallet:wallet-core"))  // Wallet interfaces
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)


    // Encryption libraries
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.78.1")

    // Test dependencies
    testImplementation(project(":testkit"))
}

