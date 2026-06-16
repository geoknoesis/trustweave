plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")

}

dependencies {
    // Re-export portable identifier + validation types so downstream consumers can keep
    // importing org.trustweave.core.identifiers.* and org.trustweave.core.util.* unchanged.
    api(project(":common-mp"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)
    // Lifecycle/teardown diagnostics in DefaultPluginRegistry
    implementation(libs.slf4j.api)
    // OkHttp Dns guard for SSRF-safe outbound clients (org.trustweave.core.net)
    implementation(libs.okhttp)
    // Test dependencies are standardized in root build.gradle.kts
}

