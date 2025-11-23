plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.trustweave.credentials"
dependencies {
    implementation(project(":credentials:core"))
    
    // JSON-LD for canonicalization
    implementation("com.github.jsonld-java:jsonld-java:0.13.4")
    
    // Cryptographic libraries for LD-Proof signatures
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")

    // Test dependencies
    testImplementation(project(":testkit"))
}

