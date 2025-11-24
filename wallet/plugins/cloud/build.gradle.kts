plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.trustweave.core"
dependencies {
    implementation(project(":credentials:credential-core"))
    implementation(project(":wallet:wallet-core"))  // Wallet interfaces

    
    // AWS S3
    implementation(platform("software.amazon.awssdk:bom:2.20.0"))
    implementation("software.amazon.awssdk:s3")
    
    // Azure Blob Storage
    implementation(platform("com.azure:azure-sdk-bom:1.2.15"))
    implementation("com.azure:azure-storage-blob")
    
    // Google Cloud Storage
    implementation(platform("com.google.cloud:libraries-bom:26.22.0"))
    implementation("com.google.cloud:google-cloud-storage")

    // Test dependencies
    testImplementation(project(":testkit"))
}

