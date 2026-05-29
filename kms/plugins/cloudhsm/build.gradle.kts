plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "org.trustweave.kms"

dependencies {
    // API dependencies - exposed transitively to consumers
    api(project(":common"))
    api(project(":kms:kms-core"))

    // Reuse the PKCS#11 plugin for actual cryptographic operations
    implementation(project(":kms:plugins:pkcs11"))

    // Implementation dependencies - internal only
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.slf4j.api)

    // AWS CloudHSM SDK (cluster management; cryptography goes through PKCS#11)
    implementation(platform("software.amazon.awssdk:bom:2.20.0"))
    implementation("software.amazon.awssdk:cloudhsmv2")
    implementation("software.amazon.awssdk:cloudhsm")

    // Test dependencies
    testImplementation(libs.bundles.test)
    testRuntimeOnly(libs.bundles.test.runtime)
    testImplementation(libs.kotlinx.coroutines.test)
}
