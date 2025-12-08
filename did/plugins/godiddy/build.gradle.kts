plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.trustweave.did"
dependencies {
    implementation(project(":common"))
    implementation(project(":credentials:credential-api"))
    implementation(project(":did:did-core"))
    implementation(project(":did:registrar"))
    implementation(project(":kms:kms-core"))
    implementation(libs.bundles.ktor.client)
    implementation(libs.kotlinx.datetime)

    // Test dependencies
    testImplementation(project(":testkit"))
    testImplementation(project(":anchors:anchor-core"))
}

