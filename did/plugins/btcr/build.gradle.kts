plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.trustweave.did"
dependencies {
    implementation(project(":did:did-core"))
    implementation(project(":did:plugins:base"))
    implementation(project(":credentials:credential-core"))
    implementation(project(":kms:kms-core"))
    
    // BitcoinJ for Bitcoin blockchain interaction
    implementation("org.bitcoinj:bitcoinj-core:0.16.2")
    
    // HTTP client
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Test dependencies
    testImplementation(project(":testkit"))
}

