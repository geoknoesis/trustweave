plugins {
    kotlin("jvm")
    `java-test-fixtures`
    alias(libs.plugins.kover)
}

group = "org.trustweave"
dependencies {
    implementation(project(":common"))   // Root-level common (exceptions, common utilities)
    // KMS uses TrustWeaveException from root common module

    // Kotlinx coroutines for suspend functions in KeyManagementService interface
    implementation(libs.kotlinx.coroutines.core)
    
    // SLF4J for logging utilities (optional - plugins provide implementation)
    compileOnly(libs.slf4j.api)

    // Test dependencies - add inmemory plugin for testing KeyManagementServices factory
    testImplementation(project(":kms:plugins:inmemory"))
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter.api)
    // Add SLF4J for testing logging utilities
    testImplementation(libs.slf4j.api)
    
    // TestFixtures dependencies - exposed to consumers
    testFixturesImplementation(project(":common"))
    testFixturesImplementation(libs.kotlinx.coroutines.core)
    testFixturesImplementation(libs.kotlin.test)
    testFixturesImplementation(libs.junit.jupiter.api)
}

// Configure Kover for test coverage
kover {
    reports {
        filters {
            excludes {
                classes(
                    "*.*Test",
                    "*.*Test\$*",
                    "*.*TestKt",
                    "*.*TestKt\$*"
                )
            }
        }
        verify {
            rule {
                bound {
                    // Set minimum coverage threshold to 70%
                    // This is a reasonable target for a core interface module
                    minValue = 70
                }
            }
        }
    }
}

// Ensure Kover directories exist before test task runs
tasks.test.configure {
    doFirst {
        layout.buildDirectory.dir("tmp/test").get().asFile.mkdirs()
    }
}

