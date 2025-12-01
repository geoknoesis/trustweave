package com.trustweave.examples.did_key

import com.trustweave.trust.TrustWeave
import com.trustweave.did.*
import com.trustweave.did.DidCreationOptions.KeyAlgorithm
import com.trustweave.did.DidCreationOptions.KeyPurpose
import com.trustweave.keydid.KeyDidMethod
import com.trustweave.testkit.kms.InMemoryKeyManagementService
import com.trustweave.testkit.getOrFail
import kotlinx.coroutines.runBlocking

/**
 * did:key Example - Native Implementation
 *
 * This example demonstrates the native did:key implementation, the most widely-used DID method.
 * did:key provides zero external dependencies and portable public key-based DIDs.
 *
 * Run: `./gradlew :TrustWeave-examples:runKeyDid`
 */
fun main() = runBlocking {
    println("=".repeat(70))
    println("did:key Example - Native Implementation")
    println("=".repeat(70))
    println()

    // Step 1: Setup TrustWeave with did:key
    println("Step 1: Setting up TrustWeave with did:key...")
    val kms = InMemoryKeyManagementService()

    val trustweave = TrustWeave.build {
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

    // Step 2: Create did:key with Ed25519
    println("\nStep 2: Creating did:key with Ed25519...")
    val ed25519Did = trustweave.createDid {
        method("key")
        algorithm("Ed25519")
    }.getOrFail()

    println("Created Ed25519 DID: ${ed25519Did.value}")

    // Step 3: Resolve did:key
    println("\nStep 3: Resolving did:key...")
    val resolved = trustweave.resolveDid(ed25519Did)
    when (resolved) {
        is com.trustweave.did.resolver.DidResolutionResult.Success -> {
            println("Resolved DID: ${resolved.document.id}")
            println("Document has ${resolved.document.verificationMethod.size} verification methods")
        }
        else -> {
            println("Failed to resolve DID")
        }
    }

    // Step 4: Create did:key with different algorithms
    println("\nStep 4: Creating did:key with different algorithms...")

    // secp256k1 (Ethereum-compatible)
    val secp256k1Did = trustweave.createDid {
        method("key")
        algorithm("secp256k1")
    }.getOrFail()
    println("Created secp256k1 DID: ${secp256k1Did.value}")

    // P-256 (NIST)
    val p256Did = trustweave.createDid {
        method("key")
        algorithm("P-256")
    }.getOrFail()
    println("Created P-256 DID: ${p256Did.value}")

    println("\n" + "=".repeat(70))
    println("did:key Example Complete!")
    println("=".repeat(70))
}

