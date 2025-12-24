plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "org.trustweave.core"
dependencies {
    implementation(project(":common"))
    implementation(project(":credentials:credential-api"))
    implementation(project(":wallet:wallet-core"))  // Wallet interfaces
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)


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

