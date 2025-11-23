plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.trustweave.chains"
dependencies {
    implementation(project(":credentials:core"))
    implementation(project(":anchors:core"))    // Using HTTP approach initially (can be upgraded to indy-vdr later)
    implementation(libs.bundles.ktor.client)

    // Test dependencies
    testImplementation(project(":testkit"))
    testImplementation(project(":did:core"))
    testImplementation(project(":kms:core"))
    testImplementation(project(":distribution:all")) // For TrustWeave facade
}

