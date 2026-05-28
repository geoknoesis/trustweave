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

    // JsonDidMethodLoader logs directly via org.slf4j.LoggerFactory, so slf4j-api must be on the
    // runtime classpath (implementation, not compileOnly). Apps still supply the binding.
    implementation(libs.slf4j.api)

    // Test dependencies
    testImplementation(project(":testkit"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation(kotlin("test"))
    testImplementation(libs.junit.platform.launcher)
}

