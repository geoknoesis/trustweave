plugins {
    id("vericore.shared")
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.geoknoesis.vericore.kms"
version = "1.0.0-SNAPSHOT"

dependencies {
    implementation(project(":core:vericore-core"))
    implementation(project(":kms:vericore-kms"))

    // AWS CloudHSM SDK
    implementation(platform("software.amazon.awssdk:bom:2.20.0"))
    implementation("software.amazon.awssdk:cloudhsmv2")
    implementation("software.amazon.awssdk:cloudhsm")

    // Test dependencies
    testImplementation(project(":core:vericore-testkit"))
}

