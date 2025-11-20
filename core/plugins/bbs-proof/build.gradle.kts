plugins {
    id("vericore.shared")
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.geoknoesis.vericore.core"
version = "1.0.0-SNAPSHOT"

dependencies {
    implementation(project(":core:vericore-core"))
    
    // BBS+ signature library
    // Note: Using mattr-bbs-signatures or similar when available
    // implementation("io.mattrglobal:bbs-signatures:0.1.0")
    
    // JSON-LD for canonicalization
    implementation("com.github.jsonld-java:jsonld-java:0.13.4")

    // Test dependencies
    testImplementation(project(":core:vericore-testkit"))
}

