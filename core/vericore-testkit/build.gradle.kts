plugins {
    id("vericore.shared")
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.geoknoesis.vericore"
version = "1.0.0-SNAPSHOT"

dependencies {
    implementation(project(":core:vericore-core"))
    implementation(project(":core:vericore-spi"))
    implementation(project(":core:vericore-json"))
    implementation(project(":chains:vericore-anchor"))
    implementation(project(":did:vericore-did"))
    implementation(project(":kms:vericore-kms"))
    implementation(project(":core:vericore-trust"))
    
    // JUnit for test base classes
    api(Libs.junitJupiter)
    
    // Kotlin test framework
    api(Libs.kotlinTest)
    
    // TestContainers for EO integration tests
    api(Libs.testcontainers)
    api(Libs.testcontainersJunit)
    
    // Test dependencies for example tests
    testImplementation(project(":chains:plugins:ganache"))
}

