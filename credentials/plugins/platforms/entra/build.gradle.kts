plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "org.trustweave.integrations"
dependencies {
    implementation(project(":common"))
    implementation(project(":credentials:credential-api"))


    // Microsoft Graph API client
    implementation("com.microsoft.graph:microsoft-graph:5.62.0")

    // Azure Identity for authentication
    implementation("com.azure:azure-identity:1.10.0")

    // HTTP client
    implementation(libs.okhttp)

    // JSON serialization
    implementation(libs.jackson.module.kotlin)

    // Test dependencies
    testImplementation(project(":testkit"))
}

