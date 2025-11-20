plugins {
    id("vericore.shared")
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.geoknoesis.vericore"
version = "1.0.0-SNAPSHOT"

dependencies {
    // vericore-json is a utility module that doesn't depend on vericore-core
    // It only provides JSON canonicalization and digest utilities
    // No dependencies needed - only uses kotlinx.serialization.json
}

