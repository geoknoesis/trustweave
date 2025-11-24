plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.trustweave.credentials"
dependencies {
    implementation(project(":credentials:credential-core"))
    
    // BBS+ signature library
    // Note: Using mattr-bbs-signatures or similar when available
    // implementation("io.mattrglobal:bbs-signatures:0.1.0")
    
    // JSON-LD for canonicalization
    implementation("com.github.jsonld-java:jsonld-java:0.13.4")

    // Test dependencies
    testImplementation(project(":testkit"))
}

