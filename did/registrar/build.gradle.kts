plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "org.trustweave"
dependencies {
    implementation(project(":common"))
    implementation(project(":did:did-core"))
    implementation(project(":kms:kms-core"))
    // kotlinx dependencies are transitively available from common and did:core
    // but explicitly added here for compilation
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)

    // Test dependencies
    testImplementation(project(":testkit"))
}

