package org.trustweave.trust.benchmarks

import org.trustweave.trust.TrustWeave
import org.trustweave.trust.types.*
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import org.trustweave.testkit.did.DidKeyMockMethod
import org.trustweave.testkit.getOrFail
import kotlinx.coroutines.runBlocking
// JMH imports - uncomment when JMH plugin is configured:
// import org.openjdk.jmh.annotations.*
// import org.openjdk.jmh.runner.Runner
// import org.openjdk.jmh.runner.options.OptionsBuilder
// import java.util.concurrent.TimeUnit

/**
 * Performance benchmarks for TrustWeave operations.
 * 
 * **Setup Instructions:**
 * 1. Add JMH plugin to trust/build.gradle.kts:
 *    ```kotlin
 *    plugins {
 *        id("me.champeau.jmh") version "0.7.2"
 *    }
 *    ```
 * 2. Add JMH dependencies:
 *    ```kotlin
 *    jmhImplementation(project(":testkit"))
 *    jmhImplementation(libs.kotlinx.coroutines.core)
 *    ```
 * 3. Uncomment JMH imports and annotations below
 * 4. Run with: `./gradlew :trust:jmh`
 * 
 * These benchmarks measure:
 * - DID creation performance
 * - Credential issuance performance
 * - Credential verification performance
 * - DID resolution performance
 */
// Uncomment when JMH plugin is configured:
// @BenchmarkMode(Mode.AverageTime)
// @OutputTimeUnit(TimeUnit.MILLISECONDS)
// @State(Scope.Benchmark)
// @Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
// @Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
// @Fork(1)
open class TrustWeaveBenchmarks {
    
    private lateinit var trustWeave: TrustWeave
    private lateinit var issuerDid: Did
    private lateinit var holderDid: Did
    private var issuerKeyId: String = ""
    
    // @Setup  // Uncomment when JMH is configured
    fun setup() = runBlocking {
        val kms = InMemoryKeyManagementService()
        val didMethod = DidKeyMockMethod(kms)
        
        trustWeave = TrustWeave.build {
            factories(
                kmsFactory = org.trustweave.testkit.kms.TestkitKmsFactory(),
                didMethodFactory = org.trustweave.testkit.did.TestkitDidMethodFactory()
            )
            keys {
                provider("inMemory")
                algorithm("Ed25519")
            }
            did {
                method("key") {
                    algorithm("Ed25519")
                }
            }
        }
        
        // Create DIDs for benchmarking
        issuerDid = trustWeave.createDid { method("key") }.getOrFail()
        holderDid = trustWeave.createDid { method("key") }.getOrFail()
        
        // Get issuer key ID
        val issuerDoc = trustWeave.resolveDid(issuerDid).getOrFail()
        issuerKeyId = issuerDoc.verificationMethod.firstOrNull()?.id?.value?.substringAfter("#")
            ?: error("No verification method found")
    }
    
    /**
     * Benchmark DID creation performance.
     */
    // @Benchmark  // Uncomment when JMH is configured
    fun benchmarkCreateDid(): Did = runBlocking {
        trustWeave.createDid { method("key") }.getOrFail()
    }
    
    /**
     * Benchmark credential issuance performance.
     */
    // @Benchmark  // Uncomment when JMH is configured
    fun benchmarkIssueCredential(): VerifiableCredential = runBlocking {
        trustWeave.issue {
            credential {
                type("BenchmarkCredential")
                issuer(issuerDid)
                subject {
                    id(holderDid)
                    "test" to "value"
                }
            }
            signedBy(issuerDid, issuerKeyId)
        }.getOrFail()
    }
    
    /**
     * Benchmark credential verification performance.
     */
    // @Benchmark  // Uncomment when JMH is configured
    fun benchmarkVerifyCredential(): VerificationResult = runBlocking {
        val credential = trustWeave.issue {
            credential {
                type("BenchmarkCredential")
                issuer(issuerDid)
                subject {
                    id(holderDid)
                    "test" to "value"
                }
            }
            signedBy(issuerDid, issuerKeyId)
        }.getOrFail()
        
        trustWeave.verify {
            credential(credential)
        }
    }
    
    /**
     * Benchmark DID resolution performance.
     */
    // @Benchmark  // Uncomment when JMH is configured
    fun benchmarkResolveDid(): DidDocument = runBlocking {
        trustWeave.resolveDid(issuerDid).getOrFail()
    }
}

/**
 * Main function to run benchmarks.
 * 
 * **Usage:** 
 * 1. Configure JMH plugin (see class documentation)
 * 2. Uncomment imports and annotations
 * 3. Run with `./gradlew :trust:jmh` or call this main function
 */
// Uncomment when JMH is configured:
// fun main(args: Array<String>) {
//     val opt = OptionsBuilder()
//         .include(TrustWeaveBenchmarks::class.java.simpleName)
//         .build()
//     
//     Runner(opt).run()
// }

