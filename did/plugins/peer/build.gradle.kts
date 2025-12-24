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
    implementation(project(":kms:kms-core"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)


    // Multibase encoding for peer DIDs (implemented inline - no external dependency needed)

    // Test dependencies
    testImplementation(project(":testkit"))
}

