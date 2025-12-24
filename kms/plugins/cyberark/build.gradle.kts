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
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // JSON serialization
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")

    // Logging
    implementation("org.slf4j:slf4j-api:2.0.9")

    // Note: CyberArk Conjur SDK dependencies are not available in public Maven repositories
    // This implementation uses HTTP client directly. For production use, add CyberArk SDK
    // from CyberArk's repository or use Conjur API client when available.

    // Test dependencies
    testImplementation(project(":testkit"))
}

