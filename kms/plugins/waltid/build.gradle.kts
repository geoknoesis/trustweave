plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "org.trustweave.kms"
// Add waltid maven repositories
repositories {
    maven("https://maven.waltid.dev/releases")
    maven("https://maven.waltid.dev/snapshots")
}

val WALTID_VERSION = "2025.1.0-PRE-RELEASE.5"

dependencies {
    // API dependencies - exposed transitively to consumers
    api(project(":common"))
    api(project(":kms:kms-core"))
    api(project(":did:did-core"))  // waltid also provides DID methods
    
    // Implementation dependencies - internal only
    implementation(project(":credentials:credential-api"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    // Force kotlinx.datetime version to ensure Clock.System is available
    implementation(libs.kotlinx.datetime) {
        version {
            strictly(libs.versions.kotlinx.datetime.get())
        }
    }
    implementation("id.walt.did:waltid-did:$WALTID_VERSION") {
        // Exclude kotlinx.datetime from waltid if it brings an incompatible version
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-datetime")
    }

    // Logging
    implementation("org.slf4j:slf4j-api:2.0.9")
    // Add other walt.id modules as needed:
    // implementation("id.walt.crypto:waltid-crypto:$WALTID_VERSION")
    // implementation("id.walt.credentials:waltid-verifiable-credentials:$WALTID_VERSION")

    // Test dependencies
    testImplementation(project(":testkit"))
    testImplementation(libs.kotlinx.datetime) // Ensure datetime is available in tests
}

