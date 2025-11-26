---
title: Multi-Chain Anchoring Patterns
nav_order: 8
parent: How-To Guides
keywords:
  - multi-chain
  - redundancy
  - blockchain
  - anchoring
  - caip-2
  - patterns
---

# Multi-Chain Anchoring Patterns

This guide shows you how to anchor data to multiple blockchains for redundancy and chain-agnostic operations. You'll learn patterns for sequential anchoring, parallel anchoring, and chain selection strategies.

## Prerequisites

Before you begin, ensure you have:

- ✅ TrustWeave dependencies with anchor support
- ✅ Understanding of blockchain anchoring basics
- ✅ Knowledge of CAIP-2 chain identifiers
- ✅ Access to blockchain clients (or test clients)

## Expected Outcome

After completing this guide, you will have:

- ✅ Anchored data to multiple blockchains
- ✅ Implemented redundancy patterns
- ✅ Selected chains dynamically
- ✅ Handled multi-chain errors gracefully

## Quick Example

Here's a complete example showing multi-chain anchoring:

```kotlin
import com.trustweave.anchor.BlockchainAnchorRegistry
import com.trustweave.testkit.anchor.InMemoryBlockchainAnchorClient
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class CredentialDigest(
    val credentialId: String,
    val digest: String,
    val issuer: String
)

fun main() = runBlocking {
    // Step 1: Register multiple chains
    val anchorRegistry = BlockchainAnchorRegistry()
    anchorRegistry.register("algorand:testnet", InMemoryBlockchainAnchorClient("algorand:testnet"))
    anchorRegistry.register("polygon:mainnet", InMemoryBlockchainAnchorClient("polygon:mainnet"))
    anchorRegistry.register("ethereum:sepolia", InMemoryBlockchainAnchorClient("ethereum:sepolia"))
    
    // Step 2: Create payload
    val digest = CredentialDigest(
        credentialId = "cred-123",
        digest = "z6Mk...",
        issuer = "did:key:issuer"
    )
    val payload = Json.encodeToJsonElement(digest)
    
    // Step 3: Anchor to multiple chains
    val results = listOf(
        anchorRegistry.anchor("algorand:testnet", payload),
        anchorRegistry.anchor("polygon:mainnet", payload),
        anchorRegistry.anchor("ethereum:sepolia", payload)
    )
    
    println("✅ Anchored to ${results.size} chains")
    results.forEach { result ->
        println("   ${result.ref.chainId}: ${result.ref.txHash}")
    }
}
```

**Expected Output:**
```
✅ Anchored to 3 chains
   algorand:testnet: tx-abc123...
   polygon:mainnet: tx-def456...
   ethereum:sepolia: tx-ghi789...
```

## Step-by-Step Guide

### Step 1: Register Multiple Blockchain Clients

Register all chains you want to support:

```kotlin
import com.trustweave.anchor.BlockchainAnchorRegistry
import com.trustweave.testkit.anchor.InMemoryBlockchainAnchorClient

val anchorRegistry = BlockchainAnchorRegistry()

// Register Algorand
anchorRegistry.register(
    "algorand:testnet",
    InMemoryBlockchainAnchorClient("algorand:testnet")
)

// Register Polygon
anchorRegistry.register(
    "polygon:mainnet",
    InMemoryBlockchainAnchorClient("polygon:mainnet")
)

// Register Ethereum
anchorRegistry.register(
    "ethereum:sepolia",
    InMemoryBlockchainAnchorClient("ethereum:sepolia")
)
```

**What this does:**
- ✅ Registers multiple blockchain clients
- ✅ Uses CAIP-2 chain identifiers (standard format)
- ✅ Makes all chains available via unified API

**Expected Result:** Registry with multiple chains registered.

---

### Step 2: Prepare Data for Anchoring

Create the data structure to anchor:

```kotlin
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class CredentialDigest(
    val credentialId: String,
    val digest: String,
    val issuer: String
)

val digest = CredentialDigest(
    credentialId = "cred-123",
    digest = "z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK",
    issuer = "did:key:issuer"
)

val payload = Json.encodeToJsonElement(digest)
```

**What this does:**
- ✅ Creates serializable data structure
- ✅ Serializes to JSON
- ✅ Prepares for anchoring

**Expected Result:** JSON payload ready for anchoring.

---

### Step 3: Anchor to Single Chain

Start with anchoring to one chain:

```kotlin
val result = anchorRegistry.anchor("algorand:testnet", payload)
println("Anchored: ${result.ref.txHash}")
```

**What this does:**
- ✅ Anchors data to specified chain
- ✅ Returns anchor reference with transaction hash
- ✅ Uses unified API regardless of chain

**Expected Result:** Anchor result with transaction reference.

---

### Step 4: Anchor to Multiple Chains Sequentially

Anchor to multiple chains one at a time:

```kotlin
val chains = listOf("algorand:testnet", "polygon:mainnet", "ethereum:sepolia")
val results = chains.mapNotNull { chainId ->
    try {
        val result = anchorRegistry.anchor(chainId, payload)
        println("✅ Anchored to $chainId: ${result.ref.txHash}")
        result
    } catch (error: BlockchainException) {
        println("❌ Failed to anchor to $chainId: ${error.message}")
        null
    }
}

println("Successfully anchored to ${results.size} of ${chains.size} chains")
```

**What this does:**
- ✅ Tries each chain sequentially
- ✅ Handles failures gracefully
- ✅ Collects successful results

**Expected Result:** List of successful anchor results.

---

### Step 5: Anchor to Multiple Chains in Parallel

Anchor to multiple chains concurrently for better performance:

```kotlin
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

val chains = listOf("algorand:testnet", "polygon:mainnet", "ethereum:sepolia")
val results = chains.map { chainId ->
    async {
        try {
            val result = anchorRegistry.anchor(chainId, payload)
            println("✅ Anchored to $chainId: ${result.ref.txHash}")
            result
        } catch (error: BlockchainException) {
            println("❌ Failed to anchor to $chainId: ${error.message}")
            null
        }
    }
}.awaitAll().filterNotNull()

println("Successfully anchored to ${results.size} of ${chains.size} chains")
```

**What this does:**
- ✅ Anchors to all chains concurrently
- ✅ Faster than sequential anchoring
- ✅ Handles failures independently

**Expected Result:** List of successful anchor results (faster).

---

## Common Patterns

### Pattern 1: Sequential Anchoring with Retry

Anchor to chains sequentially with retry logic:

```kotlin
suspend fun anchorWithRetry(
    chainId: String,
    payload: JsonElement,
    maxRetries: Int = 3
): AnchorResult? {
    repeat(maxRetries) { attempt ->
        try {
            return anchorRegistry.anchor(chainId, payload)
        } catch (error: BlockchainException) {
            if (attempt == maxRetries - 1) {
                println("Failed after $maxRetries attempts: ${error.message}")
                return null
            }
            delay(1000 * (attempt + 1)) // Exponential backoff
        }
    }
    return null
}

// Use with multiple chains
val chains = listOf("algorand:testnet", "polygon:mainnet")
val results = chains.mapNotNull { chainId ->
    anchorWithRetry(chainId, payload)
}
```

### Pattern 2: Primary and Backup Chains

Anchor to primary chain, fallback to backup if it fails:

```kotlin
suspend fun anchorWithFallback(
    payload: JsonElement,
    primaryChain: String = "algorand:testnet",
    backupChains: List<String> = listOf("polygon:mainnet", "ethereum:sepolia")
): AnchorResult? {
    // Try primary chain first
    try {
        return anchorRegistry.anchor(primaryChain, payload)
    } catch (error: BlockchainException) {
        println("Primary chain failed: ${error.message}")
    }
    
    // Try backup chains
    for (backupChain in backupChains) {
        try {
            return anchorRegistry.anchor(backupChain, payload)
        } catch (error: BlockchainException) {
            println("Backup chain $backupChain failed: ${error.message}")
        }
    }
    
    return null // All chains failed
}
```

### Pattern 3: Chain Selection by Criteria

Select chains based on criteria (cost, speed, security):

```kotlin
data class ChainCriteria(
    val maxCost: Long? = null,
    val maxLatency: Long? = null,
    val minSecurity: String? = null
)

fun selectChains(criteria: ChainCriteria): List<String> {
    val allChains = listOf(
        "algorand:testnet",    // Fast, low cost
        "polygon:mainnet",     // Very low cost
        "ethereum:sepolia",    // High security
        "bitcoin:mainnet"      // Maximum security
    )
    
    return allChains.filter { chainId ->
        // Apply selection criteria
        when (chainId) {
            "algorand:testnet" -> criteria.maxCost == null || criteria.maxCost > 1000
            "polygon:mainnet" -> criteria.maxCost == null || criteria.maxCost > 100
            "ethereum:sepolia" -> criteria.minSecurity == null || criteria.minSecurity <= "high"
            else -> true
        }
    }
}

// Use selected chains
val selectedChains = selectChains(ChainCriteria(maxCost = 500))
val results = selectedChains.mapNotNull { chainId ->
    try {
        anchorRegistry.anchor(chainId, payload)
    } catch (e: Exception) {
        null
    }
}
```

### Pattern 4: Redundancy with Minimum Success

Ensure data is anchored to at least N chains:

```kotlin
suspend fun anchorWithMinimumSuccess(
    chains: List<String>,
    payload: JsonElement,
    minimumSuccess: Int = 2
): List<AnchorResult> {
    val results = mutableListOf<AnchorResult>()
    
    for (chainId in chains) {
        if (results.size >= minimumSuccess) {
            break // Already have enough successful anchors
        }
        
        try {
            val result = anchorRegistry.anchor(chainId, payload)
            results.add(result)
            println("✅ Anchored to $chainId (${results.size}/$minimumSuccess)")
        } catch (error: BlockchainException) {
            println("❌ Failed to anchor to $chainId: ${error.message}")
        }
    }
    
    if (results.size < minimumSuccess) {
        throw IllegalStateException(
            "Failed to anchor to minimum $minimumSuccess chains. " +
            "Only ${results.size} succeeded."
        )
    }
    
    return results
}

// Use with minimum success requirement
val chains = listOf("algorand:testnet", "polygon:mainnet", "ethereum:sepolia")
val results = anchorWithMinimumSuccess(chains, payload, minimumSuccess = 2)
```

---

## Chain-Agnostic Benefits

### Before (Chain-Specific Code)

```kotlin
// Different code for each blockchain
when (chainId) {
    "algorand:testnet" -> {
        val appId = algorandClient.createApplication(...)
        val tx = algorandClient.callApplication(appId, payload)
        val result = AnchorResult(tx.hash, "algorand:testnet", appId)
    }
    "polygon:mainnet" -> {
        val contract = polygonClient.deployContract(...)
        val tx = polygonClient.callContract(contract, payload)
        val result = AnchorResult(tx.hash, "polygon:mainnet", contract)
    }
    "ethereum:sepolia" -> {
        val contract = ethereumClient.deployContract(...)
        val tx = ethereumClient.sendTransaction(contract, payload)
        val result = AnchorResult(tx.hash, "ethereum:sepolia", contract)
    }
    else -> throw UnsupportedChainException(chainId)
}
```

**Problems:**
- ❌ Chain-specific code for each blockchain
- ❌ Hard to add new chains
- ❌ Code duplication
- ❌ Difficult to maintain

### After (Unified API)

```kotlin
// One API, any chain
val result = anchorRegistry.anchor(chainId, payload)
```

**Benefits:**
- ✅ Chain-agnostic code
- ✅ Easy to add new chains
- ✅ No code duplication
- ✅ Easy to maintain

---

## Error Handling

Handle multi-chain anchoring errors:

```kotlin
import com.trustweave.anchor.exceptions.BlockchainException

val chains = listOf("algorand:testnet", "polygon:mainnet", "ethereum:sepolia")
val results = chains.mapNotNull { chainId ->
    try {
        anchorRegistry.anchor(chainId, payload)
    } catch (error: BlockchainException) {
        when (error) {
            is BlockchainException.ChainNotRegistered -> {
                println("Chain not registered: ${error.chainId}")
            }
            is BlockchainException.TransactionFailed -> {
                println("Transaction failed on $chainId: ${error.reason}")
            }
            is BlockchainException.ConnectionFailed -> {
                println("Connection failed to $chainId: ${error.reason}")
            }
            else -> {
                println("Error on $chainId: ${error.message}")
            }
        }
        null
    }
}

if (results.isEmpty()) {
    throw IllegalStateException("Failed to anchor to any chain")
}
```

---

## Next Steps

Now that you've learned multi-chain anchoring, you can:

1. **[Anchor to Blockchain](blockchain-anchoring.md)** - Learn basic anchoring
2. **[Configure TrustWeave](configure-trustlayer.md)** - Configure blockchain clients
3. **[Read Anchored Data](blockchain-anchoring.md#reading-anchored-data)** - Retrieve anchored data
4. **[Verify Integrity](blockchain-anchoring.md#verify-integrity)** - Verify anchored data integrity

---

## Related Documentation

- **[Blockchain Anchoring](blockchain-anchoring.md)** - Basic anchoring guide
- **[Core Concepts](../core-concepts/blockchain-anchoring.md)** - Understanding anchoring
- **[CAIP-2 Standard](https://github.com/ChainAgnostic/CAIPs/blob/master/CAIPs/caip-2.md)** - Chain identifier standard

