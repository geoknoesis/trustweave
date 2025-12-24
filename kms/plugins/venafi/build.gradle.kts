plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "org.trustweave.integrations"
dependencies {
    implementation(project(":common"))
    implementation(project(":credentials:credential-api"))


    // HTTP client for Venafi API
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // JSON serialization
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")

    // Test dependencies
    testImplementation(project(":testkit"))
}

