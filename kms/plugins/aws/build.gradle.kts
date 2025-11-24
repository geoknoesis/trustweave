plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.trustweave.kms"
dependencies {
    implementation(project(":credentials:credential-core"))
    implementation(project(":kms:kms-core"))

    // AWS SDK v2 for KMS
    implementation(platform("software.amazon.awssdk:bom:2.20.0"))
    implementation("software.amazon.awssdk:kms")
    implementation("software.amazon.awssdk:auth")

    // Test dependencies
    testImplementation(project(":testkit"))
}

