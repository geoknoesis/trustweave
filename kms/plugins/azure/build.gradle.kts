plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "org.trustweave.kms"
dependencies {
    // API dependencies - exposed transitively to consumers
    api(project(":common"))
    api(project(":kms:kms-core"))
    
    // Implementation dependencies - internal only
    implementation(project(":credentials:credential-api"))
    implementation(libs.kotlinx.coroutines.core)

    // Azure SDK for Key Vault
    implementation(platform(libs.azure.sdk.bom))
    implementation("com.azure:azure-security-keyvault-keys")
    implementation("com.azure:azure-identity")

    // Logging
    implementation(libs.slf4j.api)

    // Test dependencies
    testImplementation(project(":testkit"))
}

