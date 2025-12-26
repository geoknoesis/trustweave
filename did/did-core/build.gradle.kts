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
    // Logging (compileOnly - implementations provide actual logger)
    compileOnly("org.slf4j:slf4j-api:2.0.9")
    // Test dependencies
    testImplementation(libs.kotlinx.coroutines.test)
    // Other test dependencies are standardized in root build.gradle.kts
}

