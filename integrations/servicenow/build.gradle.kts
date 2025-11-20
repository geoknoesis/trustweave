plugins {
    id("vericore.shared")
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.geoknoesis.vericore.integrations"
version = "1.0.0-SNAPSHOT"

dependencies {
    implementation(project(":core:vericore-core"))
    implementation(project(":core:vericore-spi"))
    
    // HTTP client for ServiceNow REST API
    // Note: ServiceNow SDK dependencies may not be available in public Maven repositories
    // This implementation uses HTTP client directly. For production use, add ServiceNow SDK
    // from ServiceNow's repository or use ServiceNow REST API client when available.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // JSON serialization
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")

    // Test dependencies
    testImplementation(project(":core:vericore-testkit"))
}

