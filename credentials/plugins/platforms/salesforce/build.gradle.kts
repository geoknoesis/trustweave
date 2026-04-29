plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "org.trustweave.integrations"
dependencies {
    implementation(project(":common"))
    implementation(project(":credentials:credential-api"))


    // HTTP client for Salesforce REST API
    // Note: Salesforce SDK dependencies may not be available in public Maven repositories
    // This implementation uses HTTP client directly. For production use, add Salesforce SDK
    // from Salesforce's repository or use Salesforce REST API client when available.
    implementation(libs.okhttp)

    // JSON serialization
    implementation(libs.jackson.module.kotlin)

    // Test dependencies
    testImplementation(project(":testkit"))
}

