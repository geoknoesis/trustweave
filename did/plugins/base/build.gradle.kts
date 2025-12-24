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
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Test dependencies
    testImplementation(project(":testkit"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation(kotlin("test"))
}

