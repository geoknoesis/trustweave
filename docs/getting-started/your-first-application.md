---
title: Your First Application
nav_order: 4
parent: Getting Started
---

# Your First Application

Build a complete TrustWeave application that demonstrates the full workflow: creating DIDs, computing digests, and anchoring to blockchains.

## Complete Example: Verifiable Credential Workflow

This example shows how to:
1. Set up services (KMS, DID method, blockchain client)
2. Create a DID for an issuer
3. Create a Verifiable Credential payload
4. Compute a digest
5. Anchor the digest to a blockchain
6. Read it back and verify

The block below wires together in-memory services so you can run the whole workflow locally without external infrastructure.

```kotlin
import com.trustweave.anchor.*
import com.trustweave.did.*
import com.trustweave.json.DigestUtils
import com.trustweave.testkit.anchor.InMemoryBlockchainAnchorClient
import com.trustweave.testkit.did.DidKeyMockMethod
import com.trustweave.testkit.kms.InMemoryKeyManagementService
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlinx.serialization.Serializable

@Serializable
data class VerifiableCredentialDigest(
    val vcId: String,
    val vcDigest: String,
    val issuer: String
)

fun main() = runBlocking {
    // Step 1: Setup services
    val kms = InMemoryKeyManagementService()
    val didMethod = DidKeyMockMethod(kms)
    val anchorClient = InMemoryBlockchainAnchorClient("algorand:mainnet")
    
    val didRegistry = DidMethodRegistry().apply { register(didMethod) }
    val blockchainRegistry = BlockchainAnchorRegistry().apply {
        register("algorand:mainnet", anchorClient)
    }
    
    // Step 2: Create a DID for the issuer
    val issuerDoc = didMethod.createDid()
    val issuerDid = issuerDoc.id
    println("Created issuer DID: $issuerDid")
    
    // Step 3: Create a verifiable credential payload
    val vcPayload = buildJsonObject {
        put("vcId", "vc-12345")
        put("issuer", issuerDid)
        put("credentialSubject", buildJsonObject {
            put("id", "subject-123")
            put("type", "Person")
            put("name", "Alice")
            put("email", "alice@example.com")
        })
        put("issued", "2024-01-01T00:00:00Z")
    }
    
    // Step 4: Compute digest
    val digest = DigestUtils.sha256DigestMultibase(vcPayload)
    println("Computed VC digest: $digest")
    
    // Step 5: Create digest object and anchor it
    val digestObj = VerifiableCredentialDigest(
        vcId = "vc-12345",
        vcDigest = digest,
        issuer = issuerDid
    )
    
    val anchorResult = blockchainRegistry.anchorTyped(
        value = digestObj,
        serializer = VerifiableCredentialDigest.serializer(),
        targetChainId = "algorand:mainnet"
    )
    
    println("Anchored at: ${anchorResult.ref.txHash}")
    println("Chain: ${anchorResult.ref.chainId}")
    println("Timestamp: ${anchorResult.timestamp}")
    
    // Step 6: Verify by reading back
    val retrieved = blockchainRegistry.readTyped<VerifiableCredentialDigest>(
        ref = anchorResult.ref,
        serializer = VerifiableCredentialDigest.serializer()
    )
    
    assert(retrieved.vcDigest == digest)
    assert(retrieved.issuer == issuerDid)
    println("Verification successful!")
}
```

**Result:** Running the program prints the issuer DID, the anchored transaction metadata, and a final “Verification successful!” message, proving that the digest retrieved from the registry matches the original payload.

## Running the Example

1. Create a new Kotlin file: `TrustWeaveExample.kt`
2. Copy the code above
3. Ensure you have TrustWeave dependencies in your `build.gradle.kts`
4. Run the application

## Understanding the Code

### Service Setup

Initialise the in-memory services that stand in for production infrastructure.

```kotlin
val kms = InMemoryKeyManagementService()
val didMethod = DidKeyMockMethod(kms)
val anchorClient = InMemoryBlockchainAnchorClient("algorand:mainnet")
```

- `InMemoryKeyManagementService`: supplies Ed25519 keys on demand without external dependencies.
- `DidKeyMockMethod`: produces `did:key` documents backed by the in-memory KMS.
- `InMemoryBlockchainAnchorClient`: simulates anchoring on Algorand so you can verify results instantly.
- **Outcome:** Together they let you issue credentials and anchor digests without touching real infrastructure.

### DID Creation

Mint a DID document that represents the issuer.

```kotlin
val issuerDoc = didMethod.createDid()
```

- **What happens:** the mock method generates a new key pair, constructs a DID document, and returns it.
- **Result:** `issuerDoc.id` is the DID string you will embed in credentials and anchors.

### Digest Computation

Canonicalise and hash the credential payload.

```kotlin
val digest = DigestUtils.sha256DigestMultibase(vcPayload)
```

- **What happens:** TrustWeave applies JSON Canonicalization Scheme (JCS) and then hashes the bytes with SHA-256.
- **Result:** `digest` is a multibase string you can store or anchor; identical payloads always produce the same value.

### Blockchain Anchoring

Persist the digest to the in-memory blockchain client.

```kotlin
val anchorResult = blockchainRegistry.anchorTyped(...)
```

- **What happens:** the registry serializes `VerifiableCredentialDigest`, stores it through the registered client, and returns an `AnchorResult`.
- **Result:** `anchorResult.ref` contains the chain identifier and transaction hash you can log or share with verifiers.

### Verification

Read the anchored record and compare it with the original payload.

```kotlin
val retrieved = blockchainRegistry.readTyped<VerifiableCredentialDigest>(...)
```

- **What happens:** the client fetches the payload, deserializes it, and hands it back as `VerifiableCredentialDigest`.
- **Result:** After the equality checks succeed you know the tamper-evident record matches your original digest.

## Next Steps

- Explore [Core Concepts](../core-concepts/README.md) for deeper understanding
- Learn about [Integration Modules](../integrations/README.md) for production use

