plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "org.trustweave.integrations"
dependencies {
    implementation(project(":common"))
    implementation(project(":credentials:credential-api"))


    // HTTP client for Venafi API
    implementation(libs.okhttp)

    // JSON serialization
    implementation(libs.jackson.module.kotlin)

    // Test dependencies
    testImplementation(project(":testkit"))
}

