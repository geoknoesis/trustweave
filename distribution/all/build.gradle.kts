plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

description = "TrustWeave All-in-One Module"

dependencies {
    // Include all core modules
    api(project(":credentials:core"))
    api(project(":kms:core"))
    api(project(":did:core"))
    api(project(":anchors:core"))
    api(project(":trust"))
    api(project(":contract"))
    
    // Include testkit for development/testing convenience
    api(project(":testkit"))
    
    // Note: Blockchain adapters (algorand, polygon, etc.) and
    // integration modules (waltid, godiddy) are NOT included by default
    // to keep dependencies minimal. Add them explicitly if needed.
}

// This module includes a facade API (TrustWeave.kt) that provides
// a unified interface to all core modules

