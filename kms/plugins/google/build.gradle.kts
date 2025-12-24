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

    // Google Cloud KMS SDK
    implementation(platform("com.google.cloud:libraries-bom:26.38.0"))
    implementation("com.google.cloud:google-cloud-kms")

    // Google Auth for credentials
    implementation("com.google.auth:google-auth-library-oauth2-http")

    // Logging
    implementation("org.slf4j:slf4j-api:2.0.9")

    // Test dependencies
    testImplementation(project(":testkit"))
}

