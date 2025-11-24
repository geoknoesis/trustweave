plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.trustweave"
dependencies {
    implementation(project(":common"))
    implementation(libs.kotlinx.coroutines.core)
    // Test dependencies are standardized in root build.gradle.kts
}

