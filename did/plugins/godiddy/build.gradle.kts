plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.trustweave.did"
dependencies {
    implementation(project(":credentials:core"))
    implementation(project(":did:core"))
    implementation(project(":kms:core"))
    implementation(libs.bundles.ktor.client)

    // Test dependencies
    testImplementation(project(":testkit"))
    testImplementation(project(":anchors:core"))
}

