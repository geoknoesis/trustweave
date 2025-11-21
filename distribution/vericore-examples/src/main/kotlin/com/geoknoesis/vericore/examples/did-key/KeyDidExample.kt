package com.geoknoesis.vericore.examples.did_key

import com.geoknoesis.vericore.VeriCore
import com.geoknoesis.vericore.did.*
import com.geoknoesis.vericore.did.DidCreationOptions.KeyAlgorithm
import com.geoknoesis.vericore.did.DidCreationOptions.KeyPurpose
import com.geoknoesis.vericore.keydid.KeyDidMethod
import com.geoknoesis.vericore.testkit.kms.InMemoryKeyManagementService
import kotlinx.coroutines.runBlocking

/**
 * did:key Example - Native Implementation
 * 
 * This example demonstrates the native did:key implementation, the most widely-used DID method.
 * did:key provides zero external dependencies and portable public key-based DIDs.
 * 
 * Run: `./gradlew :vericore-examples:runKeyDid`
 */
fun main() = runBlocking {
    println("=".repeat(70))
    println("did:key Example - Native Implementation")
    println("=".repeat(70))
    println()
    
    // Step 1: Setup VeriCore with did:key
    println("Step 1: Setting up VeriCore with did:key...")
    val kms = InMemoryKeyManagementService()
    
    val vericore = VeriCore.create {
        this.kms = kms
        
        didMethods {
            + (KeyDidMethod(kms) as DidMethod)
        }
    }
    
    // Step 2: Create did:key with Ed25519
    println("\nStep 2: Creating did:key with Ed25519...")
    val ed25519Did = vericore.dids.create("key") {
        algorithm = KeyAlgorithm.ED25519
        purpose(KeyPurpose.AUTHENTICATION)
        purpose(KeyPurpose.ASSERTION)
    }
    
    println("Created Ed25519 DID: ${ed25519Did.id}")
    println("Verification methods: ${ed25519Did.verificationMethod.size}")
    
    // Step 3: Resolve did:key
    println("\nStep 3: Resolving did:key...")
    val resolved = vericore.dids.resolve(ed25519Did.id)
    println("Resolved DID: ${resolved.document?.id}")
    println("Document has ${resolved.document?.verificationMethod?.size} verification methods")
    
    // Step 4: Create did:key with different algorithms
    println("\nStep 4: Creating did:key with different algorithms...")
    
    // secp256k1 (Ethereum-compatible)
    val secp256k1Did = vericore.dids.create("key") {
        algorithm = KeyAlgorithm.SECP256K1
    }
    println("Created secp256k1 DID: ${secp256k1Did.id}")
    
    // P-256 (NIST)
    val p256Did = vericore.dids.create("key") {
        algorithm = KeyAlgorithm.P256
    }
    println("Created P-256 DID: ${p256Did.id}")
    
    println("\n" + "=".repeat(70))
    println("did:key Example Complete!")
    println("=".repeat(70))
}

