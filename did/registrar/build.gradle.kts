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

    compileOnly("org.slf4j:slf4j-api:2.0.9")

    // Test dependencies
    testImplementation(project(":testkit"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation(kotlin("test"))
    testImplementation(libs.junit.platform.launcher)
}

