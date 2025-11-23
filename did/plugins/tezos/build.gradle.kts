plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.trustweave.did"
dependencies {
    implementation(project(":did:core"))
    implementation(project(":did:plugins:base"))
    implementation(project(":credentials:core"))
    implementation(project(":kms:core"))
    
    // Tezos SDK (using HTTP client directly)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // JSON serialization
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")

    // Test dependencies
    testImplementation(project(":testkit"))
}

