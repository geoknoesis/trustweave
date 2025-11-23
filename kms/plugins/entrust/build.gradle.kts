plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.trustweave.kms"
dependencies {
    implementation(project(":credentials:core"))
    implementation(project(":kms:core"))

    // HTTP client for Entrust nShield HSM API
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // JSON serialization
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")

    // Test dependencies
    testImplementation(project(":testkit"))
}

