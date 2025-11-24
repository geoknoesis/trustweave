plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.trustweave.did"
dependencies {
    implementation(project(":credentials:credential-core"))
    implementation(project(":did:did-core"))
    implementation(project(":did:plugins:base"))
    implementation(project(":kms:kms-core"))

    
    // HTTP client for web-based DID methods
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // Test dependencies
    testImplementation(project(":testkit"))
}

