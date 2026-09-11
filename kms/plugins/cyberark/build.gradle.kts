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

    // HTTP client for CyberArk Conjur API
    implementation(libs.okhttp)

    // JSON serialization
    implementation(libs.jackson.module.kotlin)
    implementation(libs.kotlinx.serialization.json)

    // Logging
    implementation(libs.slf4j.api)

    // Note: CyberArk Conjur SDK dependencies are not available in public Maven repositories
    // This implementation uses HTTP client directly. For production use, add CyberArk SDK
    // from CyberArk's repository or use Conjur API client when available.

    // Test dependencies
    testImplementation(project(":testkit"))
}

