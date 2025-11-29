plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.trustweave.core"
dependencies {
    implementation(project(":common"))
    implementation(project(":credentials:credential-core"))
    implementation(project(":wallet:wallet-core"))  // Wallet interfaces
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)


    // Encryption libraries
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.78.1")

    // Test dependencies
    testImplementation(project(":testkit"))
}

