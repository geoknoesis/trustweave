plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")

}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)
    // Test dependencies are standardized in root build.gradle.kts
}

