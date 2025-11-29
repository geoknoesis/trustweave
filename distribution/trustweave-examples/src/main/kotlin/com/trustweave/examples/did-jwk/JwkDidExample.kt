package com.trustweave.examples.did_jwk

import com.trustweave.TrustWeave
import com.trustweave.did.*
import com.trustweave.did.DidCreationOptions.KeyAlgorithm
import com.trustweave.did.DidCreationOptions.KeyPurpose
import com.trustweave.jwkdid.JwkDidMethod
import com.trustweave.testkit.kms.InMemoryKeyManagementService
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

    val trustweave = TrustWeave.create {
        this.kms = kms

        didMethods {
            + (JwkDidMethod(kms) as DidMethod)
        }
    }

    // Step 2: Create did:jwk with Ed25519
    println("\nStep 2: Creating did:jwk with Ed25519...")
    val ed25519Did = trustweave.dids.create("jwk") {
        algorithm = KeyAlgorithm.ED25519
        purpose(KeyPurpose.AUTHENTICATION)
        purpose(KeyPurpose.ASSERTION)
    }

    println("Created Ed25519 DID: ${ed25519Did.id}")
    println("Verification methods: ${ed25519Did.verificationMethod.size}")

    // Step 3: Resolve did:jwk
    println("\nStep 3: Resolving did:jwk...")
    val resolved = trustweave.dids.resolve(ed25519Did.id)
    println("Resolved DID: ${resolved.document?.id}")
    println("Document has ${resolved.document?.verificationMethod?.size} verification methods")

    // Step 4: Create did:jwk with different key types
    println("\nStep 4: Creating did:jwk with different key types...")

    // secp256k1 (EC type)
    val secp256k1Did = trustweave.dids.create("jwk") {
        algorithm = KeyAlgorithm.SECP256K1
    }
    println("Created secp256k1 DID: ${secp256k1Did.id}")

    // P-256 (EC type)
    val p256Did = trustweave.dids.create("jwk") {
        algorithm = KeyAlgorithm.P256
    }
    println("Created P-256 DID: ${p256Did.id}")

    println("\n" + "=".repeat(70))
    println("did:jwk Example Complete!")
    println("=".repeat(70))
}

