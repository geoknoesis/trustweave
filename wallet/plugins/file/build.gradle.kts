plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.trustweave.core"
dependencies {
    implementation(project(":credentials:core"))
    implementation(project(":wallet:core"))  // Wallet interfaces

    
    // Encryption libraries
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.78.1")

    // Test dependencies
    testImplementation(project(":testkit"))
}

