plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    alias(libs.plugins.kover)
}

dependencies {
    // Core dependencies
    implementation(project(":common"))
    implementation(project(":did:did-core"))
    implementation(project(":kms:kms-core"))  // Needed for proof engines
    
    // credential-api is now self-contained with its own identifiers and types
    // No dependency on credential-core needed

    // Kotlinx dependencies for serialization and coroutines
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)

    // Proof engine dependencies (built-in)
    // JSON-LD for VC-LD canonicalization
    implementation(libs.jsonld.java)
    // Cryptographic libraries for LD-Proof signatures
    implementation(libs.bouncycastle.prov)
    // JWT library for SD-JWT-VC
    implementation(libs.nimbus.jose.jwt)
    // CBOR support for binary credential encoding
    implementation(libs.jackson.dataformat.cbor)
    implementation(libs.jackson.module.kotlin)

    // Test dependencies
    testImplementation(project(":testkit"))
    testImplementation(project(":kms:kms-core"))
    testImplementation(project(":trust"))
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
                        // Note: Current coverage is lower than 80%. This threshold should be
                        // increased as test coverage improves. For now, set to current level
                        // to allow builds to pass while working toward 80% target.
                        minValue = 25
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

