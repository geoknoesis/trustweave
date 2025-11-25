plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.kotlin.spring)
}

group = "com.trustweave.did"

dependencies {
    implementation(project(":common"))
    implementation(project(":did:did-core"))
    implementation(project(":did:registrar"))
    implementation(project(":kms:kms-core"))
    
    // Kotlin Coroutines
    implementation(libs.kotlinx.coroutines.core)
    
    // Kotlinx Serialization
    implementation(libs.kotlinx.serialization.json)
    
    // Spring Boot dependencies
    implementation(platform(libs.spring.boot.dependencies))
    implementation(libs.spring.boot.starter.web)
    
    // Test dependencies
    testImplementation(project(":testkit"))
    testImplementation(libs.spring.boot.starter.test)
}

// This is a library module, not an application, so disable bootJar
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = false
}

// Enable regular jar task for library distribution
tasks.named<Jar>("jar") {
    enabled = true
}

