---
title: Anchor to Blockchain
nav_order: 5
parent: How-To Guides
keywords:
  - blockchain
  - anchor
  - anchoring
  - tamper evidence
  - timestamping
  - digest
---

# Anchor to Blockchain

This guide shows you how to anchor data to blockchains for tamper evidence and timestamping. You'll learn how to choose a blockchain, anchor data, read anchored data, and implement multi-chain anchoring.

## Quick Example

Here's a complete example that anchors a credential digest to a blockchain:

```kotlin
import com.trustweave.trust.TrustLayer
import com.trustweave.core.TrustWeaveError
import com.trustweave.testkit.anchor.InMemoryBlockchainAnchorClient
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable

@Serializable
data class CredentialDigest(
    val credentialId: String,
    val digest: String,
    val issuer: String
)

fun main() = runBlocking {
    try {
        // Create TrustLayer with blockchain support
        val trustLayer = TrustLayer.build {
            keys {
                provider("inMemory")
                algorithm("Ed25519")
            }
            blockchains {
                "algorand:testnet" to InMemoryBlockchainAnchorClient("algorand:testnet")
            }
        }

        // Create credential digest
        val digest = CredentialDigest(
            credentialId = "cred-123",
            digest = "z6Mk...",  // SHA-256 digest
            issuer = "did:key:issuer"
        )

        // Anchor to blockchain
        val anchorResult = trustLayer.anchor {
            data(digest)
            chain("algorand:testnet")
        }

        println("✅ Anchored at: ${anchorResult.ref.txHash}")
        println("   Chain: ${anchorResult.ref.chainId}")
        println("   Timestamp: ${anchorResult.timestamp}")
    } catch (error: TrustWeaveError) {
        when (error) {
            is TrustWeaveError.ChainNotRegistered -> {
                println("❌ Chain not registered: ${error.chainId}")
            }
            else -> {
                println("❌ Error: ${error.message}")
            }
        }
    }
}
```

**Expected Output:**
```
✅ Anchored at: tx-abc123...
   Chain: algorand:testnet
   Timestamp: 2024-01-01T00:00:00Z
```

## Step-by-Step Guide

### Step 1: Configure Blockchain Support

Register blockchain clients in TrustLayer:

```kotlin
val trustLayer = TrustLayer.build {
    keys {
        provider("inMemory")
        algorithm("Ed25519")
    }
    blockchains {
        "algorand:testnet" to InMemoryBlockchainAnchorClient("algorand:testnet")
        // Add more chains as needed
    }
}
```

### Step 2: Prepare Data for Anchoring

Create the data structure to anchor:

```kotlin
@Serializable
data class CredentialDigest(
    val credentialId: String,
    val digest: String,
    val issuer: String
)

val digest = CredentialDigest(
    credentialId = "cred-123",
    digest = "z6Mk...",
    issuer = "did:key:issuer"
)
```

### Step 3: Anchor the Data

Anchor data to the blockchain:

```kotlin
val anchorResult = trustLayer.anchor {
    data(digest)
    chain("algorand:testnet")
}
```

### Step 4: Store Anchor Reference

Save the anchor reference for later verification:

```kotlin
val anchorRef = anchorResult.ref
// Store in database: anchorRef.chainId, anchorRef.txHash, anchorRef.blockNumber
```

## Choosing a Blockchain

Select a blockchain based on your requirements:

| Blockchain | Use Case | Latency | Cost | Notes |
|------------|----------|---------|------|-------|
| **Algorand** | Production, fast finality | ~4s | Low | Recommended for most use cases |
| **Polygon** | Ethereum-compatible, low cost | ~2s | Very low | Good for high-volume anchoring |
| **Ethereum** | Maximum security | ~15s | High | Use for high-value credentials |
| **Base** | Layer 2, low cost | ~2s | Very low | Ethereum-compatible |
| **Arbitrum** | Layer 2, fast | ~1s | Low | Ethereum-compatible |

### Testnet vs Mainnet

- **Testnet**: Use for development and testing (free, no real value)
- **Mainnet**: Use for production (costs real tokens)

## Anchoring Data

### Basic Anchoring

Anchor any serializable data:

```kotlin
@Serializable
data class MyData(val id: String, val value: String)

val data = MyData(id = "123", value = "test")

val anchorResult = trustLayer.anchor {
    data(data)
    chain("algorand:testnet")
}
```

### Anchoring Credentials

Anchor credential digests:

```kotlin
// Issue credential first
val credential = trustLayer.issue { ... }

// Create digest
val digest = CredentialDigest(
    credentialId = credential.id,
    digest = computeDigest(credential),
    issuer = credential.issuer
)

// Anchor digest
val anchorResult = trustLayer.anchor {
    data(digest)
    chain("algorand:testnet")
}
```

### What Gets Stored On-Chain?

**Important:** Only the **digest** (hash) is stored on-chain, not the full data. The data is:
1. Serialized to JSON
2. Canonicalized using JCS
3. Hashed (SHA-256)
4. The hash is stored on-chain

The original data must be stored separately (database, IPFS, etc.) if you need to retrieve it later.

## Reading Anchored Data

### Read by Anchor Reference

Read anchored data using the anchor reference:

```kotlin
val anchorRef = AnchorRef(
    chainId = "algorand:testnet",
    txHash = "tx-abc123..."
)

val data = trustLayer.readAnchor<CredentialDigest> {
    ref(anchorRef)
}
```

### Verify Integrity

The read operation automatically verifies that the data matches the on-chain digest:

```kotlin
try {
    val data = trustLayer.readAnchor<CredentialDigest> {
        ref(anchorRef)
    }
    println("✅ Data verified against on-chain digest")
} catch (error: TrustWeaveError) {
    if (error is TrustWeaveError.ValidationFailed) {
        println("❌ Data verification failed - possible tampering!")
    }
}
```

## Multi-Chain Anchoring

Anchor the same data to multiple blockchains for redundancy:

### Sequential Anchoring

Anchor to multiple chains one at a time:

```kotlin
val chains = listOf("algorand:testnet", "polygon:testnet")
val anchorResults = chains.mapNotNull { chainId ->
    try {
        val anchor = trustLayer.anchor {
            data(digest)
            chain(chainId)
        }
        println("✅ Anchored to $chainId: ${anchor.ref.txHash}")
        anchor
    } catch (error: TrustWeaveError) {
        println("❌ Failed to anchor to $chainId: ${error.message}")
        null
    }
}
```

### Parallel Anchoring

Anchor to multiple chains concurrently:

```kotlin
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

val chains = listOf("algorand:testnet", "polygon:testnet")
val anchorResults = chains.map { chainId ->
    async {
        try {
            trustLayer.anchor {
                data(digest)
                chain(chainId)
            }
        } catch (error: TrustWeaveError) {
            println("Failed to anchor to $chainId: ${error.message}")
            null
        }
    }
}.awaitAll().filterNotNull()
```

### Store All References

Store all anchor references for verification:

```kotlin
val anchorRefs = anchorResults.map { it.ref }
// Store in database for later verification
database.saveAnchorRefs(credentialId, anchorRefs)
```

## Common Patterns

### Pattern 1: Anchor with Retry

Retry anchoring on failure:

```kotlin
suspend fun anchorWithRetry(
    data: CredentialDigest,
    chainId: String,
    maxRetries: Int = 3
): AnchorResult? {
    repeat(maxRetries) { attempt ->
        try {
            return trustLayer.anchor {
                this.data(data)
                chain(chainId)
            }
        } catch (error: TrustWeaveError) {
            if (attempt == maxRetries - 1) {
                println("Failed after $maxRetries attempts: ${error.message}")
                return null
            }
            kotlinx.coroutines.delay(1000 * (attempt + 1)) // Exponential backoff
        }
    }
    return null
}
```

### Pattern 2: Anchor with Timeout

Add timeout to anchoring operations:

```kotlin
import kotlinx.coroutines.withTimeout

val anchorResult = withTimeout(30000) { // 30 second timeout
    trustLayer.anchor {
        data(digest)
        chain("algorand:testnet")
    }
}
```

### Pattern 3: Verify Anchor Exists

Check if an anchor exists before reading:

```kotlin
suspend fun verifyAnchorExists(ref: AnchorRef): Boolean {
    return try {
        trustLayer.readAnchor<CredentialDigest> {
            this.ref(ref)
        }
        true
    } catch (error: TrustWeaveError) {
        false
    }
}
```

## Error Handling

Handle anchoring errors gracefully:

```kotlin
try {
    val anchorResult = trustLayer.anchor {
        data(digest)
        chain("algorand:testnet")
    }
    // Use anchor result
} catch (error: TrustWeaveError) {
    when (error) {
        is TrustWeaveError.ChainNotRegistered -> {
            println("Chain not registered: ${error.chainId}")
            println("Available chains: ${error.availableChains}")
        }
        is TrustWeaveError.ValidationFailed -> {
            println("Validation failed: ${error.reason}")
        }
        is TrustWeaveError.Unknown -> {
            println("Blockchain error: ${error.message}")
            // Could be network issue, insufficient funds, etc.
        }
        else -> {
            println("Error: ${error.message}")
        }
    }
}
```

## Integration Guides

For production use, see integration guides for specific blockchains:

- **[Algorand Integration](../integrations/algorand.md)** - Algorand anchoring setup
- **[Ethereum Integration](../integrations/ethereum-anchor.md)** - Ethereum anchoring setup
- **[Polygon Integration](../integrations/polygon-did.md)** - Polygon anchoring setup
- **[Base Integration](../integrations/base-anchor.md)** - Base anchoring setup

## API Reference

For complete API documentation, see:
- **[Core API - anchor()](../api-reference/core-api.md#anchor)** - Complete parameter reference
- **[Core API - readAnchor()](../api-reference/core-api.md#readanchor)** - Reading anchored data

## Related Concepts

- **[Blockchain Anchoring](../core-concepts/blockchain-anchoring.md)** - Understanding anchoring concepts
- **[JSON Canonicalization](../core-concepts/json-canonicalization.md)** - How data is canonicalized

## Related How-To Guides

- **[Issue Credentials](issue-credentials.md)** - Issue credentials to anchor
- **[Create DIDs](create-dids.md)** - Create issuer DIDs

## Next Steps

**Ready to integrate?**
- [Algorand Integration](../integrations/algorand.md) - Set up Algorand anchoring
- [Ethereum Integration](../integrations/ethereum-anchor.md) - Set up Ethereum anchoring

**Want to learn more?**
- [Blockchain Anchoring Concept](../core-concepts/blockchain-anchoring.md) - Deep dive into anchoring
- [Your First Application](../getting-started/your-first-application.md) - Complete anchoring example

