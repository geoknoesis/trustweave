plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.trustweave.did"
dependencies {
    implementation(project(":credentials:credential-core"))
    implementation(project(":did:did-core"))
    implementation(project(":did:registrar"))
    implementation(project(":kms:kms-core"))
    implementation(libs.bundles.ktor.client)

    // Test dependencies
    testImplementation(project(":testkit"))
    testImplementation(project(":anchors:anchor-core"))
}

