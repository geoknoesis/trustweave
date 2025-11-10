plugins {
    id("vericore.shared")
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "io.geoknoesis.vericore"
version = "1.0.0-SNAPSHOT"

dependencies {
    // No inter-module dependencies; provider modules depend on this
}


