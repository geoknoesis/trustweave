# TrustWeave Indy Adapter

Hyperledger Indy blockchain adapter for TrustWeave, providing blockchain anchoring capabilities for Indy ledger pools.

## Overview

This module implements `BlockchainAnchorClient` for Hyperledger Indy ledgers, allowing you to anchor data to Indy pools such as Sovrin Mainnet, Sovrin Staging, and BCovrin Testnet.

## Chain IDs

**Type-Safe Chain IDs (Recommended)**:
```kotlin
import com.trustweave.anchor.ChainId

val sovrinMainnet = ChainId.Indy.SovrinMainnet      // "indy:mainnet:sovrin"
val sovrinStaging = ChainId.Indy.SovrinStaging     // "indy:testnet:sovrin-staging"
val bcovrinTestnet = ChainId.Indy.BCovrinTestnet   // "indy:testnet:bcovrin"
```

**String-based Chain IDs (Legacy)**:
Supported chain ID format: `indy:<network>:<pool-name>`

- `indy:mainnet:sovrin` - Sovrin Mainnet
- `indy:testnet:sovrin-staging` - Sovrin Staging
- `indy:testnet:bcovrin` - BCovrin Testnet

## Usage

### Adding Dependency

```kotlin
dependencies {
    implementation("com.trustweave:TrustWeave-indy:1.0.0-SNAPSHOT")
}
```

### Automatic Discovery via SPI

```kotlin
import com.trustweave.anchor.indy.IndyIntegration
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    // Automatically discover and register Indy adapters
    val result = IndyIntegration.discoverAndRegister()
    
    println("Registered chains: ${result.registeredChains}")
}
```

### Manual Setup with Type-Safe Options

```kotlin
import com.trustweave.anchor.indy.IndyIntegration
import com.trustweave.anchor.options.IndyOptions
import com.trustweave.anchor.ChainId
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    // Type-safe chain ID
    val chainId = ChainId.Indy.BCovrinTestnet
    
    // Type-safe options (compile-time validation)
    val options = IndyOptions(
        walletName = "my-wallet",
        walletKey = "wallet-key",
        did = "did:indy:testnet:...",
        poolEndpoint = "https://test.bcovrin.vonx.io" // Optional
    )
    
    // Setup with type-safe configuration
    val result = IndyIntegration.setup(
        chainIds = listOf(chainId.toString()),
        options = options.toMap()
    )
    println("Registered chains: ${result.registeredChains}")
}
```

### Manual Setup (Legacy Map-based Options)

```kotlin
import com.trustweave.anchor.indy.IndyIntegration
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    // Setup with wallet credentials for real transactions
    val result = IndyIntegration.setup(
        chainIds = listOf("indy:testnet:bcovrin"),
        options = mapOf(
            "walletName" to "my-wallet",
            "walletKey" to "wallet-key",
            "did" to "did:indy:testnet:...",
            "poolEndpoint" to "https://test.bcovrin.vonx.io" // Optional
        )
    )
    println("Registered chains: ${result.registeredChains}")
}
```

### Anchoring Data with Type-Safe Configuration

```kotlin
import com.trustweave.anchor.*
import com.trustweave.anchor.ChainId
import com.trustweave.anchor.indy.IndyIntegration
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlinx.serialization.Serializable

@Serializable
data class VerifiableCredentialDigest(
    val vcId: String,
    val vcDigest: String
)

fun main() = runBlocking {
    // Type-safe chain ID
    val chainId = ChainId.Indy.BCovrinTestnet
    
    // Register Indy client (uses in-memory fallback for testing)
    val integration = IndyIntegration.setup(
        chainIds = listOf(chainId.toString()),
        options = emptyMap() // Uses in-memory fallback for testing
    )
    val registry = integration.registry
    
    // Anchor data
    val digest = VerifiableCredentialDigest(
        vcId = "vc-12345",
        vcDigest = "uABC123..."
    )
    
    val result = registry.anchorTyped(
        value = digest,
        serializer = VerifiableCredentialDigest.serializer(),
        targetChainId = chainId.toString()
    )
    
    println("Anchored at: ${result.ref.txHash}")
}
```

## Implementation Notes

### Current Status

The initial implementation provides:
- ✅ Chain ID parsing and validation
- ✅ In-memory fallback for testing (no wallet required)
- ✅ SPI discovery support
- ⚠️ **TODO**: Real Indy ledger integration (requires indy-vdr or Indy SDK)

### Indy Integration Options

1. **indy-vdr** (Recommended): Use `indy-vdr` library (Rust-based, with potential Java/Kotlin bindings)
2. **HTTP-based**: Direct HTTP calls to Indy Node pool endpoints
3. **Indy SDK** (Deprecated): Legacy SDK (not recommended)

### Transaction Type

Indy uses **ATTRIB transactions** to store data on the ledger. The implementation stores payload data as a DID attribute.

## Requirements

- Java 21+
- Kotlin 2.2.0+
- Access to Indy ledger pool (for production use)

## Testing

Tests use in-memory fallback mode and don't require a running Indy pool:

```bash
./gradlew :TrustWeave-indy:test
```

## Future Enhancements

- [ ] Full indy-vdr integration
- [ ] Support for Indy-specific features (schemas, credential definitions)
- [ ] Wallet management integration
- [ ] Pool connection pooling
- [ ] Transaction retry logic

