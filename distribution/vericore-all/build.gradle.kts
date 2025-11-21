plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("vericore.shared")
}

description = "VeriCore All-in-One Module"

dependencies {
    // Include all core modules
    api(project(":core:vericore-core"))
    api(project(":core:vericore-json"))
    api(project(":kms:vericore-kms"))
    api(project(":did:vericore-did"))
    api(project(":chains:vericore-anchor"))
    api(project(":core:vericore-spi"))
    api(project(":core:vericore-trust"))
    api(project(":core:vericore-contract"))
    
    // Include testkit for development/testing convenience
    api(project(":core:vericore-testkit"))
    
    // Note: Blockchain adapters (algorand, polygon, etc.) and
    // integration modules (waltid, godiddy) are NOT included by default
    // to keep dependencies minimal. Add them explicitly if needed.
}

// This module includes a facade API (VeriCore.kt) that provides
// a unified interface to all core modules

