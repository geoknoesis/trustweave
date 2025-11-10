plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("vericore.shared")
}

description = "VeriCore All-in-One Module"

dependencies {
    // Include all core modules
    api(project(":vericore-core"))
    api(project(":vericore-json"))
    api(project(":vericore-kms"))
    api(project(":vericore-did"))
    api(project(":vericore-anchor"))
    api(project(":vericore-spi"))
    api(project(":vericore-trust"))
    
    // Include testkit for development/testing convenience
    api(project(":vericore-testkit"))
    
    // Note: Blockchain adapters (algorand, polygon, etc.) and
    // integration modules (waltid, godiddy) are NOT included by default
    // to keep dependencies minimal. Add them explicitly if needed.
}

// This module includes a facade API (VeriCore.kt) that provides
// a unified interface to all core modules

