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

    
    // Multibase encoding for peer DIDs (implemented inline - no external dependency needed)
    
    // Test dependencies
    testImplementation(project(":testkit"))
}

