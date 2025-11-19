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
    implementation(project(":kms:vericore-kms"))
    implementation(project(":core:vericore-spi"))
    
    // Multibase encoding for peer DIDs (implemented inline - no external dependency needed)
    
    // Test dependencies
    testImplementation(project(":core:vericore-testkit"))
}

