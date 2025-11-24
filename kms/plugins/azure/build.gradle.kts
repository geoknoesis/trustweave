plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.trustweave.kms"
dependencies {
    implementation(project(":credentials:credential-core"))
    implementation(project(":kms:kms-core"))

    // Azure SDK for Key Vault
    implementation(platform("com.azure:azure-sdk-bom:1.2.15"))
    implementation("com.azure:azure-security-keyvault-keys")
    implementation("com.azure:azure-identity")

    // Test dependencies
    testImplementation(project(":testkit"))
}

