plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.trustweave"
dependencies {
    implementation(project(":credentials:credential-core"))
    implementation(project(":wallet:wallet-core"))  // Wallet interfaces
    implementation(project(":anchors:anchor-core"))
    implementation(project(":did:did-core"))
    implementation(project(":kms:kms-core"))
    implementation(project(":trust"))
    
    // JUnit for test base classes
    api(libs.junit.jupiter)
    
    // Kotlin test framework
    api(libs.kotlin.test)
    
    // TestContainers for EO integration tests
    api(libs.testcontainers)
    api(libs.testcontainers.junit)
    
    // Test dependencies for example tests
    testImplementation(project(":anchors:plugins:ganache"))
}

