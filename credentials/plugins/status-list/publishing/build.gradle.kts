plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "org.trustweave.credentials"

dependencies {
    implementation(project(":credentials:credential-api"))
    implementation(project(":common"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    // Cloud SDK BOMs - import but only use what's needed
    implementation(platform(libs.aws.sdk.bom))
    implementation("software.amazon.awssdk:s3")
    implementation(platform(libs.azure.sdk.bom))
    implementation("com.azure:azure-storage-blob")
    implementation(platform(libs.google.cloud.bom))
    implementation("com.google.cloud:google-cloud-storage")

    testImplementation(project(":testkit"))
    testImplementation(libs.kotlinx.coroutines.test)
}
