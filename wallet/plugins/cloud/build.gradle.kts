plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "org.trustweave.wallet"

tasks.test {
    val dockerHost =
        System.getenv("DOCKER_HOST")?.takeIf { it.isNotBlank() }
            ?: if (System.getProperty("os.name").startsWith("Windows")) "npipe:////./pipe/dockerDesktopLinuxEngine" else null
    if (dockerHost != null) {
        environment("DOCKER_HOST", dockerHost)
        systemProperty("DOCKER_HOST", dockerHost)
    }
}

dependencies {
    implementation(project(":common"))
    implementation(project(":credentials:credential-api"))
    implementation(project(":wallet:wallet-core")) // Wallet interfaces
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)

    // AWS S3
    implementation(platform(libs.aws.sdk.bom))
    implementation("software.amazon.awssdk:s3")

    // Azure Blob Storage
    implementation(platform(libs.azure.sdk.bom))
    implementation("com.azure:azure-storage-blob")

    // Google Cloud Storage
    implementation(platform(libs.google.cloud.bom))
    implementation("com.google.cloud:google-cloud-storage")

    // Test dependencies
    testImplementation(project(":testkit"))
    testImplementation(libs.testcontainers)
}
