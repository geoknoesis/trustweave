plugins {
    id("vericore.shared")
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "io.geoknoesis.vericore"
version = "1.0.0-SNAPSHOT"

dependencies {
    implementation(kotlin("reflect"))
    implementation(project(":vericore-spi"))
    implementation(project(":vericore-trust"))
    // Test dependencies
    testImplementation(project(":vericore-testkit"))
    testImplementation(project(":vericore-did"))
    testImplementation(project(":vericore-kms"))
}

