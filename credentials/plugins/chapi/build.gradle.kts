plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.trustweave.credentials"

dependencies {
    implementation(project(":credentials:credential-api"))
    implementation(project(":did:did-core"))
    implementation(project(":common"))

    // Kotlinx dependencies
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    // Test dependencies
    testImplementation(project(":testkit"))
}

