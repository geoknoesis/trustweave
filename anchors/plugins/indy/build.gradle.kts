plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.trustweave.chains"
dependencies {
    implementation(project(":credentials:credential-core"))
    implementation(project(":anchors:anchor-core"))    // Using HTTP approach initially (can be upgraded to indy-vdr later)
    implementation(libs.bundles.ktor.client)

    // Test dependencies
    testImplementation(project(":testkit"))
    testImplementation(project(":did:did-core"))
    testImplementation(project(":kms:kms-core"))
    testImplementation(project(":distribution:all")) // For TrustWeave facade
}

