plugins {
    id("vericore.shared")
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.geoknoesis.vericore"
version = "1.0.0-SNAPSHOT"

dependencies {
    implementation(project(":vericore-core"))
    implementation(project(":vericore-did"))
    implementation(project(":vericore-did-base"))
    implementation(project(":vericore-kms"))
    implementation(project(":vericore-spi"))
    
    // Multibase encoding for peer DIDs
    implementation("org.multiformats:multibase:1.1.2")
    
    // Test dependencies
    testImplementation(project(":vericore-testkit"))
}

