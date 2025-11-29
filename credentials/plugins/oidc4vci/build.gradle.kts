plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.trustweave.credentials"

dependencies {
    implementation(project(":credentials:credential-core"))
    implementation(project(":did:did-core"))
    implementation(project(":kms:kms-core"))
    implementation(project(":common"))

    // Kotlinx dependencies
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    // HTTP client for OIDC4VCI
    implementation(libs.okhttp)

    // JWT handling
    implementation(libs.nimbus.jose.jwt)

    // Test dependencies
    testImplementation(project(":testkit"))
}

