plugins {
    id("vericore.shared")
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "io.geoknoesis.vericore"
version = "1.0.0-SNAPSHOT"

dependencies {
    implementation(project(":vericore-core"))
    implementation(project(":vericore-json"))
    implementation(project(":vericore-anchor"))
    implementation(project(":vericore-did"))
    implementation(project(":vericore-kms"))
    
    // TestContainers for EO integration tests
    api(Libs.testcontainers)
    api(Libs.testcontainersJunit)
    
    // Test dependencies for example tests
    testImplementation(project(":vericore-ganache"))
}

