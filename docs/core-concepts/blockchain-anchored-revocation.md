---
title: Blockchain-Anchored Revocation
nav_exclude: true
---

# Blockchain-Anchored Revocation

TrustWeave provides a sophisticated revocation management system that combines fast off-chain status lists with tamper-proof blockchain anchoring. This hybrid approach gives you the performance of off-chain storage with the immutability guarantees of blockchain.

## Overview

The `BlockchainRevocationRegistry` extends `StatusListManager` to automatically anchor status list hashes to blockchain, providing:

- **Fast Revocation**: Off-chain updates are immediate (no blockchain latency)
- **Cost Efficiency**: Only anchors hashes, not full data (batched updates)
- **Tamper-Proof**: Blockchain anchors prevent status list tampering
- **Configurable Strategies**: Choose when and how often to anchor
- **Automatic Management**: Anchoring happens automatically based on your strategy

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│         BlockchainRevocationRegistry                    │
│  ┌──────────────────────────────────────────────────┐   │
│  │  StatusListManager (Off-Chain Storage)          │   │
│  │  - Fast updates                                  │   │
│  │  - Status list data                              │   │
│  └──────────────────────────────────────────────────┘   │
│                          │                                │
│                          ▼                                │
│  ┌──────────────────────────────────────────────────┐   │
│  │  AnchorStrategy (When to Anchor)                  │   │
│  │  - Periodic: Time-based or update count          │   │
│  │  - Lazy: On-demand for verification               │   │
│  │  - Hybrid: Combine both approaches              │   │
│  └──────────────────────────────────────────────────┘   │
│                          │                                │
│                          ▼                                │
│  ┌──────────────────────────────────────────────────┐   │
│  │  BlockchainAnchorClient (On-Chain Storage)       │   │
│  │  - Status list hash                              │   │
│  │  - Transaction reference                         │   │
│  └──────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────┘
```

## Key Components

### StatusListManager

The base interface for managing credential revocation status lists. Stores the actual status list data (bitstrings) off-chain for fast access.

**Key Operations:**
- `createStatusList()` - Create a new status list
- `revokeCredential()` - Revoke a credential
- `suspendCredential()` - Suspend a credential
- `checkRevocationStatus()` - Check if a credential is revoked
- `getStatusList()` - Retrieve status list data

### BlockchainRevocationRegistry

Extends `StatusListManager` to add blockchain anchoring capabilities. Automatically anchors status list hashes based on your chosen strategy.

**Key Features:**
- Delegates all operations to underlying `StatusListManager`
- Tracks pending anchors (updates since last anchor)
- Automatically triggers anchoring based on strategy
- Provides manual anchoring for immediate needs

### AnchorStrategy

Determines when status lists should be anchored to blockchain.

**Available Strategies:**

1. **PeriodicAnchorStrategy** - Anchor on schedule or after N updates
2. **LazyAnchorStrategy** - Anchor only when verification is requested
3. **HybridAnchorStrategy** - Combine periodic and lazy approaches

## Step-by-Step Guide

### Step 1: Set Up Dependencies

```kotlin
dependencies {
    implementation("org.trustweave:distribution-all:1.0.0-SNAPSHOT")
    implementation("org.trustweave:trustweave-anchor:1.0.0-SNAPSHOT")
    // Add blockchain anchor client for your chain
    implementation("org.trustweave:trustweave-anchor-algorand:1.0.0-SNAPSHOT")
}
```

### Step 2: Create Status List Manager

Choose an implementation based on your needs:

```kotlin
import org.trustweave.credential.revocation.*

// For development/testing
val statusListManager = InMemoryStatusListManager()

// For production (persistent storage)
val statusListManager = DatabaseStatusListManager(dataSource)
```

### Step 3: Configure Blockchain Anchor Client

Set up your blockchain anchor client:

```kotlin
import org.trustweave.anchor.*

val anchorClient = AlgorandBlockchainAnchorClient(
    algodUrl = "https://testnet-api.algonode.cloud",
    algodToken = "",
    chainId = "algorand:testnet"
)
```

### Step 4: Choose Anchoring Strategy

Select the strategy that fits your use case:

#### Option A: Periodic Anchoring (Recommended for High Volume)

Anchor every hour or after 100 updates:

```kotlin
import java.time.Duration

val strategy = PeriodicAnchorStrategy(
    interval = Duration.ofHours(1),
    maxUpdates = 100
)
```

**Use Case:** High-volume revocation (100+ revocations per hour)
**Cost:** ~$0.50-$5 per hour (1 transaction)
**Latency:** 0-1 hour for blockchain confirmation

#### Option B: Lazy Anchoring (Recommended for Low Volume)

Anchor only when verification is requested:

```kotlin
val strategy = LazyAnchorStrategy(
    maxStaleness = Duration.ofDays(1) // Force anchor if older than 1 day
)
```

**Use Case:** Low-volume revocation (< 10 revocations per day)
**Cost:** ~$0.50-$5 per verification (only when needed)
**Latency:** On-demand (when verification happens)

#### Option C: Hybrid Anchoring (Recommended for Most Cases)

Combine periodic and lazy approaches:

```kotlin
val strategy = HybridAnchorStrategy(
    periodicInterval = Duration.ofHours(1),
    maxUpdates = 100,
    forceAnchorOnVerify = true // Also anchor on verification if stale
)
```

**Use Case:** Medium-volume revocation with critical verifications
**Cost:** ~$0.50-$5 per hour + occasional on-demand anchors
**Latency:** 0-1 hour for periodic, immediate for critical verifications

### Step 5: Create Blockchain Revocation Registry

Combine all components:

```kotlin
val registry = BlockchainRevocationRegistry(
    anchorClient = anchorClient,
    statusListManager = statusListManager,
    anchorStrategy = strategy,
    chainId = "algorand:testnet"
)
```

### Step 6: Create Status List

Create a status list for your issuer:

```kotlin
val statusList = registry.createStatusList(
    issuerDid = "did:key:z6Mkr...",
    purpose = StatusPurpose.REVOCATION,
    size = 131072 // 16KB bitstring (default)
)

println("Status List ID: ${statusList.id}")
println("Status List Purpose: ${statusList.credentialSubject.statusPurpose}")
```

### Step 7: Issue Credential with Status List Reference

When issuing a credential, include the status list reference:

```kotlin
import org.trustweave.credential.models.*

val credential = VerifiableCredential(
    id = "cred-123",
    type = listOf("VerifiableCredential", "UniversityDegree"),
    issuer = "did:key:z6Mkr...",
    credentialSubject = buildJsonObject {
        put("degree", "Bachelor of Science")
    },
    issuanceDate = "2024-01-01T00:00:00Z",
    credentialStatus = CredentialStatus(
        id = "${statusList.id}#${index}",
        type = "StatusList2021Entry",
        statusPurpose = "revocation",
        statusListIndex = index.toString(),
        statusListCredential = statusList.id
    )
)
```

### Step 8: Revoke Credential

Revoke a credential (anchoring happens automatically if threshold reached):

```kotlin
// Single revocation
val revoked = registry.revokeCredential(
    credentialId = "cred-123",
    statusListId = statusList.id
)

if (revoked) {
    println("Credential revoked successfully")

    // Check if anchoring was triggered
    val pending = registry.getPendingAnchor(statusList.id)
    if (pending == null) {
        println("Status list was anchored to blockchain")
    } else {
        println("Pending anchor: ${pending.updateCount} updates since last anchor")
    }
}
```

### Step 9: Batch Revocation

Revoke multiple credentials at once:

```kotlin
val credentialIds = listOf("cred-123", "cred-456", "cred-789")
val results = registry.revokeCredentials(
    credentialIds = credentialIds,
    statusListId = statusList.id
)

val successCount = results.values.count { it }
println("Revoked $successCount out of ${credentialIds.size} credentials")
```

### Step 10: Check Revocation Status

Check if a credential is revoked:

```kotlin
val status = registry.checkRevocationStatus(credential)

if (status.revoked) {
    println("Credential is revoked")
    println("Status List: ${status.statusListId}")
    if (status.reason != null) {
        println("Reason: ${status.reason}")
    }
} else {
    println("Credential is valid")
}
```

### Step 11: Verify On-Chain (Optional)

For hybrid strategies, verify that the status list is anchored:

```kotlin
val onChainStatus = registry.checkRevocationOnChain(
    credential = credential,
    chainId = "algorand:testnet"
)

// This will trigger anchoring if using HybridAnchorStrategy
// and the status list is stale
```

### Step 12: Manual Anchoring (If Needed)

Force immediate anchoring (bypasses strategy):

```kotlin
val currentStatusList = registry.getStatusList(statusList.id)
val anchorRef = registry.anchorRevocationList(
    statusList = currentStatusList!!,
    chainId = "algorand:testnet"
)

println("Status list anchored at: $anchorRef")
```

## Complete Example

Here's a complete example showing the full workflow:

```kotlin
import org.trustweave.credential.revocation.*
import org.trustweave.anchor.*
import java.time.Duration

suspend fun main() {
    // 1. Set up components
    val statusListManager = InMemoryStatusListManager()
    val anchorClient = /* your blockchain anchor client */

    // 2. Create registry with periodic strategy
    val registry = BlockchainRevocationRegistry(
        anchorClient = anchorClient,
        statusListManager = statusListManager,
        anchorStrategy = PeriodicAnchorStrategy(
            interval = Duration.ofHours(1),
            maxUpdates = 100
        ),
        chainId = "algorand:testnet"
    )

    // 3. Create status list
    val statusList = registry.createStatusList(
        issuerDid = "did:key:z6Mkr...",
        purpose = StatusPurpose.REVOCATION
    )
    println("Created status list: ${statusList.id}")

    // 4. Issue credential with status list reference
    val credential = /* issue credential with statusList.id */

    // 5. Revoke credential (automatic anchoring if threshold reached)
    registry.revokeCredential("cred-123", statusList.id)

    // 6. Check revocation status
    val status = registry.checkRevocationStatus(credential)
    println("Revoked: ${status.revoked}")

    // 7. Check anchor status
    val lastAnchor = registry.getLastAnchorTime(statusList.id)
    if (lastAnchor != null) {
        println("Last anchored: $lastAnchor")
    }
}
```

## Strategy Comparison

| Strategy | Cost per 1000 Revocations | Latency | Best For |
|---------|--------------------------|---------|----------|
| **Periodic (hourly)** | $0.50-$5 | 0-1 hour | High volume (>100/hour) |
| **Periodic (daily)** | $0.50-$5 | 0-24 hours | Cost-sensitive, low urgency |
| **Lazy** | $0.50-$5 | On-demand | Low volume (<10/day) |
| **Hybrid** | $0.50-$5 + occasional | 0-1 hour + on-demand | Most use cases |

## Monitoring and Management

### Check Pending Anchors

Monitor pending anchors to see when the next anchor will happen:

```kotlin
val pending = registry.getPendingAnchor(statusList.id)
if (pending != null) {
    println("Pending updates: ${pending.updateCount}")
    println("Last update: ${pending.lastUpdate}")
    println("Time since last anchor: ${Duration.between(pending.lastAnchorTime, Instant.now())}")
}
```

### Check Last Anchor Time

See when a status list was last anchored:

```kotlin
val lastAnchor = registry.getLastAnchorTime(statusList.id)
if (lastAnchor != null) {
    println("Last anchored: $lastAnchor")
    val age = Duration.between(lastAnchor, Instant.now())
    println("Age: ${age.toHours()} hours")
}
```

### Get Status List Statistics

Get detailed statistics about a status list:

```kotlin
val stats = registry.getStatusListStatistics(statusList.id)
if (stats != null) {
    println("Total capacity: ${stats.totalCapacity}")
    println("Used indices: ${stats.usedIndices}")
    println("Revoked count: ${stats.revokedCount}")
    println("Available indices: ${stats.availableIndices}")
}
```

## Best Practices

1. **Choose the Right Strategy**
   - High volume → Periodic (hourly)
   - Low volume → Lazy
   - Critical verifications → Hybrid

2. **Monitor Pending Anchors**
   - Check `getPendingAnchor()` regularly
   - Set up alerts for high update counts

3. **Handle Anchoring Failures**
   - Implement retry logic for failed anchors
   - Log anchor failures for debugging
   - Consider fallback strategies

4. **Optimize Update Batching**
   - Use `revokeCredentials()` for batch operations
   - Use `updateStatusListBatch()` for bulk updates

5. **Test Your Strategy**
   - Test with realistic update patterns
   - Monitor costs and latency
   - Adjust thresholds based on usage

## Troubleshooting

### Anchoring Not Happening

**Problem:** Status lists aren't being anchored automatically.

**Solutions:**
- Check if `anchorClient` is properly configured
- Verify `chainId` is set correctly
- Check strategy thresholds (may not have reached them)
- Use manual anchoring for immediate needs

### High Costs

**Problem:** Too many blockchain transactions.

**Solutions:**
- Increase `interval` in PeriodicAnchorStrategy
- Increase `maxUpdates` threshold
- Switch to LazyAnchorStrategy for low volume
- Use cheaper blockchain (testnet, Layer 2)

### Slow Verification

**Problem:** Verification takes too long.

**Solutions:**
- Use HybridAnchorStrategy with `forceAnchorOnVerify = true`
- Pre-anchor status lists before verification
- Use faster blockchain (Layer 2, optimized chains)

## Related Documentation

- [Blockchain Anchoring](./blockchain-anchoring.md) - General blockchain anchoring guide
- [Verifiable Credentials](./verifiable-credentials.md) - Credential management
- [Status List Manager API](../api-reference/core-api.md#status-list-manager) - API reference

