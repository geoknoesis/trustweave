plugins {
    id("vericore.shared")
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.geoknoesis.vericore"
version = "1.0.0-SNAPSHOT"

dependencies {
    implementation(project(":vericore-core"))
    implementation(project(":vericore-kms"))

    // Azure SDK for Key Vault
    implementation(platform("com.azure:azure-sdk-bom:1.2.15"))
    implementation("com.azure:azure-security-keyvault-keys")
    implementation("com.azure:azure-identity")

    // Test dependencies
    testImplementation(project(":vericore-testkit"))
}

