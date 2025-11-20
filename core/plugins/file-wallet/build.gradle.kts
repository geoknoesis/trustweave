plugins {
    id("vericore.shared")
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.geoknoesis.vericore.core"
version = "1.0.0-SNAPSHOT"

dependencies {
    implementation(project(":core:vericore-core"))
    implementation(project(":core:vericore-spi"))
    
    // Encryption libraries
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.78.1")

    // Test dependencies
    testImplementation(project(":core:vericore-testkit"))
}

