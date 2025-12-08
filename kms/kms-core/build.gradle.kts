plugins {
    kotlin("jvm")
    `java-test-fixtures`
}

group = "com.trustweave"
dependencies {
    implementation(project(":common"))   // Root-level common (exceptions, common utilities)
    // KMS uses TrustWeaveException from root common module

    // Kotlinx coroutines for suspend functions in KeyManagementService interface
    implementation(libs.kotlinx.coroutines.core)
    
    // SLF4J for logging utilities (optional - plugins provide implementation)
    compileOnly("org.slf4j:slf4j-api:2.0.9")

    // Test dependencies - add inmemory plugin for testing KeyManagementServices factory
    testImplementation(project(":kms:plugins:inmemory"))
    testImplementation(libs.kotlin.test)
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.1")
    
    // TestFixtures dependencies - exposed to consumers
    testFixturesImplementation(project(":common"))
    testFixturesImplementation(libs.kotlinx.coroutines.core)
    testFixturesImplementation(libs.kotlin.test)
    testFixturesImplementation("org.junit.jupiter:junit-jupiter-api:5.10.1")
}

