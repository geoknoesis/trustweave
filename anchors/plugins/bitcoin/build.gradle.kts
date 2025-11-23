plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.trustweave.chains"
dependencies {
    implementation(project(":anchors:core"))
    implementation(project(":credentials:core"))
    
    // BitcoinJ for Bitcoin blockchain interaction
    implementation("org.bitcoinj:bitcoinj-core:0.16.2")
    
    // HTTP client
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Test dependencies
    testImplementation(project(":testkit"))
}

