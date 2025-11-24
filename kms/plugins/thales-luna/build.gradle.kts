plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.trustweave.kms"
dependencies {
    implementation(project(":credentials:credential-core"))
    implementation(project(":kms:kms-core"))

    // HTTP client for Thales Luna HSM API
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // JSON serialization
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")

    // Test dependencies
    testImplementation(project(":testkit"))
}

