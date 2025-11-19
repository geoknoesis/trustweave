plugins {
    id("vericore.shared")
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.geoknoesis.vericore"
version = "1.0.0-SNAPSHOT"

dependencies {
    implementation(project(":vericore-core"))
    implementation(project(":vericore-kms"))
    implementation(project(":vericore-spi"))

    // Google Cloud KMS SDK
    implementation(platform("com.google.cloud:libraries-bom:26.38.0"))
    implementation("com.google.cloud:google-cloud-kms")
    
    // Google Auth for credentials
    implementation("com.google.auth:google-auth-library-oauth2-http")

    // Test dependencies
    testImplementation(project(":vericore-testkit"))
}

