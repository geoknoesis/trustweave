plugins {
    id("vericore.shared")
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.geoknoesis.vericore"
version = "1.0.0-SNAPSHOT"

dependencies {
    implementation(project(":vericore-core"))
    implementation(project(":vericore-kms"))

    // AWS SDK v2 for KMS
    implementation(platform("software.amazon.awssdk:bom:2.20.0"))
    implementation("software.amazon.awssdk:kms")
    implementation("software.amazon.awssdk:auth")

    // Test dependencies
    testImplementation(project(":vericore-testkit"))
}

