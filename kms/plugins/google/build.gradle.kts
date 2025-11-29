plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.trustweave.kms"
dependencies {
    implementation(project(":common"))    // Root-level common (exceptions)
    implementation(project(":credentials:credential-core"))
    implementation(project(":kms:kms-core"))
    implementation(libs.kotlinx.coroutines.core)

    // Google Cloud KMS SDK
    implementation(platform("com.google.cloud:libraries-bom:26.38.0"))
    implementation("com.google.cloud:google-cloud-kms")

    // Google Auth for credentials
    implementation("com.google.auth:google-auth-library-oauth2-http")

    // Test dependencies
    testImplementation(project(":testkit"))
}

