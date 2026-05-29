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
    // Test dependencies are standardized in root build.gradle.kts
}

