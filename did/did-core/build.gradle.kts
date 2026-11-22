plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "org.trustweave"
dependencies {
    implementation(project(":common"))
    // Re-export portable DID identifier + validator types (Did, VerificationMethodId, DidUrl, DidValidator).
    api(project(":did:did-identifiers-mp"))
    implementation(project(":kms:kms-core"))
    // Note: did:registrar is NOT a dependency to avoid circular dependency
    // HttpDidMethod uses reflection to load DefaultUniversalRegistrar if available at runtime
    // If did:registrar module is on classpath, full compliance features work automatically
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.jdk8)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
    // Logging (apps supply the SLF4J binding; api must be on runtime classpath)
    implementation(libs.slf4j.api)
    // Test dependencies
    testImplementation(libs.kotlinx.coroutines.test)
    // Other test dependencies are standardized in root build.gradle.kts
}
