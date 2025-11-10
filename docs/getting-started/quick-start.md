# Quick Start

Get started with VeriCore in 5 minutes! This guide will walk you through creating your first VeriCore application.

## Step 1: Add Dependencies

Add VeriCore to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("io.geoknoesis.vericore:vericore-core:1.0.0-SNAPSHOT")
    implementation("io.geoknoesis.vericore:vericore-json:1.0.0-SNAPSHOT")
    implementation("io.geoknoesis.vericore:vericore-kms:1.0.0-SNAPSHOT")
    implementation("io.geoknoesis.vericore:vericore-did:1.0.0-SNAPSHOT")
    implementation("io.geoknoesis.vericore:vericore-anchor:1.0.0-SNAPSHOT")
    testImplementation("io.geoknoesis.vericore:vericore-testkit:1.0.0-SNAPSHOT")
}
```

## Step 2: Compute a JSON Digest

```kotlin
import io.geoknoesis.vericore.json.DigestUtils
import kotlinx.serialization.json.*

fun main() {
    val json = buildJsonObject {
        put("vcId", "vc-12345")
        put("issuer", "did:web:example.com")
    }
    
    // Canonicalize and compute digest
    val digest = DigestUtils.sha256DigestMultibase(json)
    println("Digest: $digest") // e.g., "uABC123..."
}
```

## Step 3: Create a DID

```kotlin
import io.geoknoesis.vericore.did.DidMethodRegistry
import io.geoknoesis.vericore.testkit.did.DidKeyMockMethod
import io.geoknoesis.vericore.testkit.kms.InMemoryKeyManagementService
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val kms = InMemoryKeyManagementService()
    val didMethod = DidKeyMockMethod(kms)
    val didRegistry = DidMethodRegistry().apply { register(didMethod) }

    val document = didMethod.createDid(mapOf("algorithm" to "Ed25519"))
    println("Created DID: ${document.id}")

    val resolution = didRegistry.resolve(document.id)
    println("Resolved DID document id: ${resolution.document?.id}")
}
```

## Step 4: Anchor Data to Blockchain

```kotlin
import io.geoknoesis.vericore.anchor.BlockchainAnchorRegistry
import io.geoknoesis.vericore.testkit.anchor.InMemoryBlockchainAnchorClient
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement

@Serializable
data class VerifiableCredentialDigest(
    val vcId: String,
    val vcDigest: String
)

fun main() = runBlocking {
    val client = InMemoryBlockchainAnchorClient(
        chainId = "algorand:mainnet",
        contract = "app-123"
    )
    val blockchainRegistry = BlockchainAnchorRegistry().apply {
        register("algorand:mainnet", client)
    }

    val digest = VerifiableCredentialDigest(
        vcId = "vc-12345",
        vcDigest = "uABC123..."
    )

    val payload = Json.encodeToJsonElement(VerifiableCredentialDigest.serializer(), digest)
    val result = client.writePayload(payload)

    println("Anchored at: ${result.ref.txHash}")

    val retrievedPayload = client.readPayload(result.ref).payload
    val retrieved = Json.decodeFromJsonElement(VerifiableCredentialDigest.serializer(), retrievedPayload)

    println("Retrieved digest subject: ${retrieved.vcId}")
}
```

## Step 5: Complete Workflow

```kotlin
import io.geoknoesis.vericore.anchor.BlockchainAnchorRegistry
import io.geoknoesis.vericore.did.DidMethodRegistry
import io.geoknoesis.vericore.json.DigestUtils
import io.geoknoesis.vericore.testkit.anchor.InMemoryBlockchainAnchorClient
import io.geoknoesis.vericore.testkit.did.DidKeyMockMethod
import io.geoknoesis.vericore.testkit.kms.InMemoryKeyManagementService
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlinx.serialization.encodeToString

@Serializable
data class VerifiableCredentialDigest(
    val vcId: String,
    val vcDigest: String,
    val issuer: String
)

fun main() = runBlocking {
    val kms = InMemoryKeyManagementService()
    val didMethod = DidKeyMockMethod(kms)
    val anchorClient = InMemoryBlockchainAnchorClient("algorand:mainnet")
    val didRegistry = DidMethodRegistry().apply { register(didMethod) }
    val blockchainRegistry = BlockchainAnchorRegistry().apply { register("algorand:mainnet", anchorClient) }

    val issuerDoc = didMethod.createDid()
    val issuerDid = issuerDoc.id

    val vcPayload = buildJsonObject {
        put("id", "urn:vc:quick-start-1")
        put("issuer", issuerDid)
        put("credentialSubject", buildJsonObject {
            put("id", "did:key:holder-123")
            put("type", "Person")
        })
    }

    val digest = DigestUtils.sha256DigestMultibase(vcPayload)
    val digestRecord = VerifiableCredentialDigest(
        vcId = "urn:vc:quick-start-1",
        vcDigest = digest,
        issuer = issuerDid
    )

    val payload = Json.encodeToJsonElement(VerifiableCredentialDigest.serializer(), digestRecord)
    val anchorResult = anchorClient.writePayload(payload)

    println("Issuer DID: $issuerDid")
    println("Anchored digest: ${anchorResult.ref.txHash}")

    val retrievedPayload = anchorClient.readPayload(anchorResult.ref).payload
    val retrieved = Json.decodeFromJsonElement(VerifiableCredentialDigest.serializer(), retrievedPayload)

    require(retrieved.vcDigest == digest) {
        "Anchored digest mismatch: expected $digest, got ${retrieved.vcDigest}"
    }
    println("Digest verification succeeded.")
}
```

## What's Next?

- [Your First Application](your-first-application.md) - Build a more complete example
- [Core Concepts](core-concepts/README.md) - Learn the fundamentals
- [Examples](examples/README.md) - Explore more examples

