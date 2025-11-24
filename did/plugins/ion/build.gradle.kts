plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.trustweave.did"
dependencies {
    implementation(project(":credentials:credential-core"))
    implementation(project(":did:did-core"))
    implementation(project(":did:plugins:base"))
    implementation(project(":anchors:anchor-core"))
    implementation(project(":kms:kms-core"))

    
    // HTTP client for ION node communication
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // JSON processing
    implementation("com.google.code.gson:gson:2.10.1")
    
    // Test dependencies
    testImplementation(project(":testkit"))
}

