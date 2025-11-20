plugins {
    id("vericore.shared")
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.geoknoesis.vericore.core"
version = "1.0.0-SNAPSHOT"

dependencies {
    implementation(project(":core:vericore-core"))
    
    // JWT library
    implementation("com.nimbusds:nimbus-jose-jwt:9.37.3")

    // Test dependencies
    testImplementation(project(":core:vericore-testkit"))
}

