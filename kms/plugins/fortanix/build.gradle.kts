plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.trustweave.kms"
dependencies {
    implementation(project(":credentials:credential-core"))
    implementation(project(":kms:kms-core"))

    // HTTP client for Fortanix DSM API
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // JSON serialization
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")

    // Test dependencies
    testImplementation(project(":testkit"))
}

