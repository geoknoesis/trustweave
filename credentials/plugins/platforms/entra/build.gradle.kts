plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.trustweave.integrations"
dependencies {
    implementation(project(":common"))
    implementation(project(":credentials:credential-core"))


    // Microsoft Graph API client
    implementation("com.microsoft.graph:microsoft-graph:5.62.0")

    // Azure Identity for authentication
    implementation("com.azure:azure-identity:1.10.0")

    // HTTP client
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // JSON serialization
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")

    // Test dependencies
    testImplementation(project(":testkit"))
}

