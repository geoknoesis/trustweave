plugins {
    id("vericore.shared")
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.geoknoesis.vericore.core"
version = "1.0.0-SNAPSHOT"

dependencies {
    implementation(project(":core:vericore-core"))
    
    // JSON-LD for canonicalization
    implementation("com.github.jsonld-java:jsonld-java:0.13.4")
    
    // Cryptographic libraries for LD-Proof signatures
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")

    // Test dependencies
    testImplementation(project(":core:vericore-testkit"))
}

