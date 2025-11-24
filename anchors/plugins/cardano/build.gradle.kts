plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.trustweave.chains"
dependencies {
    implementation(project(":anchors:anchor-core"))
    implementation(project(":credentials:credential-core"))
    
    // HTTP client for Cardano API
    // Note: Cardano uses UTXO model, different from account-based chains
    // Cardano SDK dependencies may not be available in public Maven repositories
    // This implementation uses HTTP client directly. For production use, add Cardano SDK
    // from appropriate repository or use Cardano API client when available.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // JSON serialization
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")

    // Test dependencies
    testImplementation(project(":testkit"))
}

