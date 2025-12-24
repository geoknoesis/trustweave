plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "org.trustweave"
dependencies {
    implementation(project(":common"))
    implementation(project(":kms:kms-core"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
    // Test dependencies are standardized in root build.gradle.kts
}

