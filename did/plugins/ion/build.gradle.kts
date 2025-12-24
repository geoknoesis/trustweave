plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "org.trustweave.did"
dependencies {
    implementation(project(":common"))
    implementation(project(":credentials:credential-api"))
    implementation(project(":did:did-core"))
    implementation(project(":did:plugins:base"))
    implementation(project(":anchors:anchor-core"))
    implementation(project(":kms:kms-core"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)


    // HTTP client for ION node communication
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // JSON processing
    implementation("com.google.code.gson:gson:2.10.1")

    // Test dependencies
    testImplementation(project(":testkit"))
}

