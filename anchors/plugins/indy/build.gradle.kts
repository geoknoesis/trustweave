plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "org.trustweave.chains"
dependencies {
    implementation(project(":common"))
    implementation(project(":credentials:credential-api"))
    implementation(project(":anchors:anchor-core"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
    implementation(libs.bundles.ktor.client)

    // Test dependencies
    testImplementation(project(":testkit"))
    testImplementation(project(":did:did-core"))
    testImplementation(project(":kms:kms-core"))
    testImplementation(project(":distribution:all")) // For TrustWeave facade
}

