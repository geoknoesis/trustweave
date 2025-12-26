plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "org.trustweave"
dependencies {
    implementation(project(":common"))
    implementation(project(":kms:kms-core"))
    // Note: did:registrar is NOT a dependency to avoid circular dependency
    // HttpDidMethod uses reflection to load DefaultUniversalRegistrar if available at runtime
    // If did:registrar module is on classpath, full compliance features work automatically
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
    // Logging (compileOnly - implementations provide actual logger)
    compileOnly("org.slf4j:slf4j-api:2.0.9")
    // Test dependencies
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(project(":did:registrar")) // Available for tests only
    // Other test dependencies are standardized in root build.gradle.kts
}

