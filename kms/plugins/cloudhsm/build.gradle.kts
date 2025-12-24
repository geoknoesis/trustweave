plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "org.trustweave.kms"
dependencies {
    // API dependencies - exposed transitively to consumers
    api(project(":common"))
    api(project(":kms:kms-core"))
    
    // Implementation dependencies - internal only
    implementation(project(":credentials:credential-api"))
    implementation(libs.kotlinx.coroutines.core)

    // AWS CloudHSM SDK
    implementation(platform("software.amazon.awssdk:bom:2.20.0"))
    implementation("software.amazon.awssdk:cloudhsmv2")
    implementation("software.amazon.awssdk:cloudhsm")

    // Test dependencies
    testImplementation(project(":testkit"))
}

