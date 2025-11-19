plugins {
    id("vericore.shared")
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.geoknoesis.vericore.did"
version = "1.0.0-SNAPSHOT"

dependencies {
    implementation(project(":core:vericore-core"))
    implementation(project(":did:vericore-did"))
    implementation(project(":did:plugins:base"))
    implementation(project(":chains:vericore-anchor"))
    implementation(project(":kms:vericore-kms"))
    implementation(project(":core:vericore-spi"))
    
    // HTTP client for ION node communication
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // JSON processing
    implementation("com.google.code.gson:gson:2.10.1")
    
    // Test dependencies
    testImplementation(project(":core:vericore-testkit"))
}

