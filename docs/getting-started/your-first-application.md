# Your First Application

Build a complete VeriCore application that demonstrates the full workflow: creating DIDs, computing digests, and anchoring to blockchains.

## Complete Example: Verifiable Credential Workflow

This example shows how to:
1. Set up services (KMS, DID method, blockchain client)
2. Create a DID for an issuer
3. Create a Verifiable Credential payload
4. Compute a digest
5. Anchor the digest to a blockchain
6. Read it back and verify

```kotlin
import io.geoknoesis.vericore.anchor.*
import io.geoknoesis.vericore.did.*
import io.geoknoesis.vericore.json.DigestUtils
import io.geoknoesis.vericore.testkit.anchor.InMemoryBlockchainAnchorClient
import io.geoknoesis.vericore.testkit.did.DidKeyMockMethod
import io.geoknoesis.vericore.testkit.kms.InMemoryKeyManagementService
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
    val issuerDoc = didMethod.createDid(mapOf("algorithm" to "Ed25519"))
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
    
    val anchorResult = anchorTyped(
        value = digestObj,
        serializer = VerifiableCredentialDigest.serializer(),
        targetChainId = "algorand:mainnet"
    )
    
    println("Anchored at: ${anchorResult.ref.txHash}")
    println("Chain: ${anchorResult.ref.chainId}")
    println("Timestamp: ${anchorResult.timestamp}")
    
    // Step 6: Verify by reading back
    val retrieved = readTyped<VerifiableCredentialDigest>(
        ref = anchorResult.ref,
        serializer = VerifiableCredentialDigest.serializer()
    )
    
    assert(retrieved.vcDigest == digest)
    assert(retrieved.issuer == issuerDid)
    println("Verification successful!")
}
```

## Running the Example

1. Create a new Kotlin file: `VeriCoreExample.kt`
2. Copy the code above
3. Ensure you have VeriCore dependencies in your `build.gradle.kts`
4. Run the application

## Understanding the Code

### Service Setup

```kotlin
val kms = InMemoryKeyManagementService()
val didMethod = DidKeyMockMethod(kms)
val anchorClient = InMemoryBlockchainAnchorClient("algorand:mainnet")
```

- `InMemoryKeyManagementService`: In-memory KMS for testing
- `DidKeyMockMethod`: Mock DID method implementation
- `InMemoryBlockchainAnchorClient`: In-memory blockchain client for testing

### DID Creation

```kotlin
val issuerDoc = didMethod.createDid(mapOf("algorithm" to "Ed25519"))
```

Creates a DID using the Ed25519 algorithm. The DID document contains verification methods and authentication capabilities.

### Digest Computation

```kotlin
val digest = DigestUtils.sha256DigestMultibase(vcPayload)
```

Computes a SHA-256 digest with multibase encoding. The JSON is automatically canonicalized (keys sorted) before hashing.

### Blockchain Anchoring

```kotlin
val anchorResult = anchorTyped(...)
```

Anchors the digest object to the blockchain. Returns an `AnchorResult` with a reference (`AnchorRef`) that can be used to read the data back.

### Verification

```kotlin
val retrieved = readTyped<VerifiableCredentialDigest>(...)
```

Reads the anchored data back from the blockchain and verifies it matches the original digest.

## Next Steps

- Explore [Core Concepts](core-concepts/README.md) for deeper understanding
- Check out [Examples](examples/README.md) for more scenarios
- Learn about [Integration Modules](integrations/README.md) for production use

