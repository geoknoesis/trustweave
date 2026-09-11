plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "org.trustweave.did"
dependencies {
    implementation(project(":common"))   // Root-level common (exceptions)
    implementation(project(":did:did-core"))
    implementation(project(":kms:kms-core"))
    implementation(project(":anchors:anchor-core"))
    implementation(project(":credentials:credential-api"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
    implementation(libs.okhttp)

    // Test dependencies
    testImplementation(project(":testkit"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(kotlin("test"))
}

