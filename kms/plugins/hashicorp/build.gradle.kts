plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "org.trustweave.kms"
dependencies {
    // API dependencies - exposed transitively to consumers
    api(project(":common"))
    api(project(":kms:kms-core"))

    // Implementation dependencies - internal only
    implementation(project(":credentials:credential-api"))
    implementation(libs.kotlinx.coroutines.core)

    // Vault Java client
    implementation(libs.vault.java.driver)

    // HTTP client for Vault API
    implementation(libs.okhttp)

    // JSON serialization
    implementation(libs.jackson.module.kotlin)
    implementation(libs.jackson.databind)

    // Logging
    implementation(libs.slf4j.api)

    // Test dependencies
    testImplementation(project(":testkit"))
}
