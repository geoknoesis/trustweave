plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

description = "TrustWeave All-in-One Module"

dependencies {
    // Include all core modules
    api(project(":credentials:credential-api"))
    api(project(":kms:kms-core"))
    api(project(":did:did-core"))
    api(project(":anchors:anchor-core"))
    api(project(":trust"))
    api(project(":contract"))
    api(project(":wallet:wallet-core"))
    api(project(":common"))

    // Real did:key method (produces resolvable did:key:z6Mk… that encode the signing key).
    // Expose its base too so consumers see the AbstractDidMethod -> DidMethod supertype chain.
    api(project(":did:plugins:base"))
    api(project(":did:plugins:key"))

    // NOTE: :testkit is intentionally NOT included — it is a test-support module and must
    // never leak onto consumers' production classpath. Add it as a testImplementation
    // dependency in your own build if you want the in-memory test doubles.

    // Serialization
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    // Note: Blockchain adapters (algorand, polygon, etc.) and
    // integration modules (waltid, godiddy) are NOT included by default
    // to keep dependencies minimal. Add them explicitly if needed.
}

// This module includes a facade API (TrustWeave.kt) that provides
// a unified interface to all core modules

