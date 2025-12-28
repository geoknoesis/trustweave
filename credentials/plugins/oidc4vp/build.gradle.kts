plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "org.trustweave.credentials"

dependencies {
    // Credential API and exchange API
    implementation(project(":credentials:credential-api"))
    
    implementation(project(":did:did-core"))
    implementation(project(":kms:kms-core"))
    implementation(project(":common"))

    // Kotlinx dependencies
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)

    // HTTP client for OIDC4VP
    implementation(libs.okhttp)

    // JWT handling
    implementation(libs.nimbus.jose.jwt)

    // Test dependencies
    testImplementation(project(":testkit"))
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    
    // MockWebServer for HTTP mocking
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}

