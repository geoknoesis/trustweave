plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "org.trustweave.did"
dependencies {
    implementation(project(":common"))
    implementation(project(":did:did-core"))
    implementation(project(":did:registrar"))
    implementation(project(":kms:kms-core"))

    // Ktor server dependencies
    implementation(libs.bundles.ktor.server)

    // Database dependencies (optional, for DatabaseJobStorage)
    // Note: Only include if you plan to use DatabaseJobStorage
    // implementation(libs.bundles.database)

    // Test dependencies
    testImplementation(project(":testkit"))
}

