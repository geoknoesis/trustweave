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

    // AWS SDK v2 for KMS
    implementation(platform(libs.aws.sdk.bom))
    implementation("software.amazon.awssdk:kms")
    implementation("software.amazon.awssdk:auth")

    // Test dependencies
    testImplementation(project(":testkit"))
}

