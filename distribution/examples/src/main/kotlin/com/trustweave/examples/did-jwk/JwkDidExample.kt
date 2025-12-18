package com.trustweave.examples.did_jwk

import com.trustweave.trust.TrustWeave
import com.trustweave.trust.dsl.credential.KeyAlgorithms.ED25519
import com.trustweave.trust.dsl.credential.KmsProviders.IN_MEMORY
import com.trustweave.did.*
import com.trustweave.did.KeyAlgorithm
import com.trustweave.did.KeyPurpose
import com.trustweave.jwkdid.JwkDidMethod
import com.trustweave.testkit.kms.InMemoryKeyManagementService
import com.trustweave.testkit.getOrFail
import kotlinx.coroutines.runBlocking

/**
 * did:jwk Example - W3C Standard Implementation
 *
 * This example demonstrates the did:jwk implementation using JSON Web Keys directly.
 * did:jwk provides a W3C-standard approach to DIDs using JWK format.
 *
 * Run: `./gradlew :TrustWeave-examples:runJwkDid`
 */
fun main() = runBlocking {
    println("=".repeat(70))
    println("did:jwk Example - W3C Standard Implementation")
    println("=".repeat(70))
    println()

    // Step 1: Setup TrustWeave with did:jwk
    println("Step 1: Setting up TrustWeave with did:jwk...")
    val kms = InMemoryKeyManagementService()

    val trustweave = TrustWeave.build {
        keys {
            provider(IN_MEMORY)
            algorithm(ED25519)
        }
        did {
            method("jwk") {
                algorithm(ED25519)
            }
        }
    }

    // Step 2: Create did:jwk with Ed25519
    println("\nStep 2: Creating did:jwk with Ed25519...")
    val ed25519Did = trustweave.createDid {
        method("jwk")
        algorithm(ED25519)
    }.getOrFail()

    println("Created Ed25519 DID: ${ed25519Did.value}")

    // Step 3: Resolve did:jwk
    println("\nStep 3: Resolving did:jwk...")
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

    // Step 4: Create did:jwk with different key types
    println("\nStep 4: Creating did:jwk with different key types...")

    // secp256k1 (EC type)
    val secp256k1Did = trustweave.createDid {
        method("jwk")
        algorithm("secp256k1")
    }.getOrFail()
    println("Created secp256k1 DID: ${secp256k1Did.value}")

    // P-256 (EC type)
    val p256Did = trustweave.createDid {
        method("jwk")
        algorithm("P-256")
    }.getOrFail()
    println("Created P-256 DID: ${p256Did.value}")

    println("\n" + "=".repeat(70))
    println("did:jwk Example Complete!")
    println("=".repeat(70))
}

