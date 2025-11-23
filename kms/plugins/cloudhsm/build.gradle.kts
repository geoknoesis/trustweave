plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.trustweave.kms"
dependencies {
    implementation(project(":credentials:core"))
    implementation(project(":kms:core"))

    // AWS CloudHSM SDK
    implementation(platform("software.amazon.awssdk:bom:2.20.0"))
    implementation("software.amazon.awssdk:cloudhsmv2")
    implementation("software.amazon.awssdk:cloudhsm")

    // Test dependencies
    testImplementation(project(":testkit"))
}

