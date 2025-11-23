plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.trustweave.did"
dependencies {
    implementation(project(":credentials:core"))
    implementation(project(":did:core"))
    implementation(project(":did:plugins:base"))
    implementation(project(":kms:core"))

    
    // HTTP client for AT Protocol integration
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // Test dependencies
    testImplementation(project(":testkit"))
}

