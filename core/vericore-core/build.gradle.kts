plugins {
    id("vericore.shared")
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.geoknoesis.vericore"
version = "1.0.0-SNAPSHOT"

dependencies {
    implementation(kotlin("reflect"))
    implementation(project(":core:vericore-spi"))
    implementation(project(":did:vericore-did"))
    // Test dependencies
    testImplementation(project(":core:vericore-testkit"))
    testImplementation(project(":did:vericore-did"))
    testImplementation(project(":kms:vericore-kms"))
}

