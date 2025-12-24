---
title: ENS DID (did:ens) Integration
parent: Integration Modules
---

# ENS DID (did:ens) Integration

> This guide covers the did:ens method integration for TrustWeave. The did:ens plugin provides human-readable DID resolution using Ethereum Name Service (ENS).

## Overview

The `did/plugins/ens` module provides an implementation of TrustWeave's `DidMethod` interface using the Ethereum Name Service (ENS) resolver. This integration enables you to:

- Resolve human-readable DID identifiers (e.g., `did:ens:example.eth`)
- Map ENS domain names to Ethereum addresses
- Integrate with ENS resolver contracts
- Convert ENS names to did:ethr DIDs for resolution

## Installation

Add the did:ens module to your dependencies:

```kotlin
dependencies {
    implementation("org.trustweave.did:ens:1.0.0-SNAPSHOT")
    implementation("org.trustweave:trustweave-did:1.0.0-SNAPSHOT")
    implementation("org.trustweave.did:base:1.0.0-SNAPSHOT")
    implementation("org.trustweave.did:ethr:1.0.0-SNAPSHOT")
    implementation("org.trustweave:trustweave-anchor:1.0.0-SNAPSHOT")
    implementation("org.trustweave:distribution-all:1.0.0-SNAPSHOT")

    // Web3j for Ethereum blockchain
    implementation("org.web3j:core:4.10.0")

    // Optional: Polygon client for EVM-compatible chains
    implementation("org.trustweave.chains:polygon:1.0.0-SNAPSHOT")
}
```

## Configuration

### Basic Configuration

```kotlin
import org.trustweave.ensdid.*
import org.trustweave.anchor.*
import org.trustweave.polygon.PolygonBlockchainAnchorClient
import org.trustweave.kms.*

// Create configuration
val config = EnsDidConfig.builder()
    .ensRegistryAddress("0x00000000000C2E074eC69A0dFb2997BA6C7d2e1e") // Mainnet ENS registry
    .rpcUrl("https://eth-mainnet.g.alchemy.com/v2/YOUR_KEY")
    .chainId("eip155:1") // Mainnet
    .privateKey("0x...") // Optional: for transactions
    .build()

// Create blockchain anchor client
val anchorClient = PolygonBlockchainAnchorClient(config.chainId, config.toMap())

// Create KMS
val kms = InMemoryKeyManagementService()

// Create did:ens method
val method = EnsDidMethod(kms, anchorClient, config)
```

### Pre-configured Networks

```kotlin
// Ethereum mainnet
val mainnetConfig = EnsDidConfig.mainnet(
    rpcUrl = "https://eth-mainnet.g.alchemy.com/v2/YOUR_KEY",
    privateKey = "0x..." // Optional
)
```

### SPI Auto-Discovery

When the module is on the classpath, did:ens is automatically available:

```kotlin
import org.trustweave.did.*
import org.trustweave.anchor.*
import java.util.ServiceLoader

// Discover did:ens provider
val providers = ServiceLoader.load(DidMethodProvider::class.java)
val ensProvider = providers.find { it.supportedMethods.contains("ens") }

// Create method with required options
val options = didCreationOptions {
    property("ensRegistryAddress", "0x00000000000C2E074eC69A0dFb2997BA6C7d2e1e")
    property("rpcUrl", "https://eth-mainnet.g.alchemy.com/v2/YOUR_KEY")
    property("chainId", "eip155:1")
    property("anchorClient", anchorClient) // Required: provide anchor client
}

val method = ensProvider?.create("ens", options)
```

## Usage Examples

### Resolving a did:ens

> **Note:** did:ens does not support DID creation. You must first register an ENS domain name and link it to an Ethereum address that has a did:ethr DID.

```kotlin
val config = EnsDidConfig.mainnet("https://eth-mainnet.g.alchemy.com/v2/YOUR_KEY")
val anchorClient = PolygonBlockchainAnchorClient(config.chainId, config.toMap())
val kms = InMemoryKeyManagementService()
val method = EnsDidMethod(kms, anchorClient, config)

import org.trustweave.did.identifiers.Did
import org.trustweave.did.resolver.DidResolutionResult

// Resolve ENS domain to DID document
val did = Did("did:ens:example.eth")
val result = method.resolveDid(did)

when (result) {
    is DidResolutionResult.Success -> {
        println("Resolved: ${result.document.id}")
        println("Verification methods: ${result.document.verificationMethod.size}")
    }
    is DidResolutionResult.Failure.NotFound -> {
        println("DID not found: ${result.did.value}")
    }
    else -> println("Resolution failed")
}
```

### How it Works

1. **Extract ENS domain**: From `did:ens:example.eth`, extract `example.eth`
2. **Resolve ENS to address**: Query ENS registry to resolve `example.eth` to Ethereum address
3. **Convert to did:ethr**: Convert Ethereum address to `did:ethr:0x...`
4. **Resolve did:ethr**: Use did:ethr method to resolve the DID document
5. **Return did:ens document**: Return document with `did:ens:example.eth` as the ID

## DID Format

### ENS Domain DID

```
did:ens:example.eth
did:ens:alice.eth
did:ens:organization.eth
```

The DID identifier is the ENS domain name. The method resolves the domain to an Ethereum address and then resolves it as a did:ethr DID.

## Limitations

### No DID Creation

did:ens does not support creating DIDs. You must:

1. Register an ENS domain name (via ENS registrar)
2. Link it to an Ethereum address
3. Ensure that address has a did:ethr DID
4. Then resolve it as did:ens

### No DID Updates/Deactivation

did:ens does not support updating or deactivating DIDs directly. You must:

- **Update**: Update the underlying did:ethr DID
- **Deactivate**: Deactivate the underlying did:ethr DID

## ENS Resolution Process

1. **Query ENS Registry**: Resolve ENS domain to Ethereum address
2. **Convert Format**: Convert address to did:ethr format
3. **Resolve did:ethr**: Use did:ethr resolver to get DID document
4. **Replace ID**: Replace did:ethr ID with did:ens ID in document

## Configuration Options

### EnsDidConfig

```kotlin
val config = EnsDidConfig.builder()
    .ensRegistryAddress("0x00000000000C2E074eC69A0dFb2997BA6C7d2e1e") // Required: ENS registry
    .rpcUrl("https://eth-mainnet.g.alchemy.com/v2/YOUR_KEY")          // Required: RPC endpoint
    .chainId("eip155:1")                                               // Required: Chain ID
    .privateKey("0x...")                                               // Optional: for transactions
    .network("mainnet")                                                // Optional: network name
    .build()
```

### ENS Registry Addresses

| Network | Registry Address |
|---------|------------------|
| Ethereum Mainnet | `0x00000000000C2E074eC69A0dFb2997BA6C7d2e1e` |
| Sepolia Testnet | `0x00000000000C2E074eC69A0dFb2997BA6C7d2e1e` |

## Integration with TrustWeave

```kotlin
import org.trustweave.TrustWeave
import org.trustweave.ensdid.*
import org.trustweave.anchor.*
import org.trustweave.polygon.PolygonBlockchainAnchorClient

val config = EnsDidConfig.mainnet("https://eth-mainnet.g.alchemy.com/v2/YOUR_KEY")
val anchorClient = PolygonBlockchainAnchorClient(config.chainId, config.toMap())

val TrustWeave = TrustWeave.create {
    kms = InMemoryKeyManagementService()

    blockchain {
        register(config.chainId, anchorClient)
    }

    didMethods {
        + EnsDidMethod(kms!!, anchorClient, config)
    }
}

// Resolve did:ens
import org.trustweave.did.identifiers.Did

val did = Did("did:ens:example.eth")
val resolved = TrustWeave.resolveDid(did).getOrThrow()
```

## Error Handling

Common errors and solutions:

| Error | Cause | Solution |
|-------|-------|----------|
| `ensRegistryAddress is required` | Missing ENS registry | Provide ENS registry contract address |
| `rpcUrl is required` | Missing RPC endpoint | Provide Ethereum RPC URL |
| `chainId is required` | Missing chain ID | Specify chain ID (eip155:1, etc.) |
| `did:ens does not support DID creation` | Trying to create DID | Register ENS domain first, then resolve |
| `ENS domain not found` | Domain not registered | Verify ENS domain exists and is registered |
| `DID document not found` | Address has no did:ethr | Ensure address has a did:ethr DID |

## Testing

For testing without actual ENS resolution:

```kotlin
import org.trustweave.testkit.anchor.InMemoryBlockchainAnchorClient

val config = EnsDidConfig.mainnet("https://eth-mainnet.g.alchemy.com/v2/YOUR_KEY")
val anchorClient = InMemoryBlockchainAnchorClient(config.chainId)
val method = EnsDidMethod(kms, anchorClient, config)

// Note: ENS resolution requires actual ENS registry interaction
// For full testing, use a testnet or local Ethereum node
```

## Best Practices

1. **Use ENS for readability**: did:ens provides human-readable identifiers
2. **Link to did:ethr**: Ensure ENS domain is linked to an address with did:ethr DID
3. **Test on testnets**: Use Sepolia testnet for development
4. **Cache resolutions**: Cache ENS-to-address mappings for performance
5. **Error handling**: Handle cases where ENS domain doesn't exist or isn't linked

## Use Cases

### Human-Readable DIDs

```kotlin
// Instead of:
did:ethr:0x1234567890123456789012345678901234567890

// Use:
did:ens:example.eth
did:ens:alice.eth
did:ens:company.eth
```

### Organizational DIDs

Use ENS for organizational identities:

```kotlin
did:ens:TrustWeave.eth      // Company DID
did:ens:engineering.eth   // Department DID
did:ens:alice.eth         // Employee DID
```

## Troubleshooting

### ENS Domain Not Resolving

- Verify domain is registered on ENS
- Check domain is linked to an Ethereum address
- Ensure address has a valid did:ethr DID
- Verify RPC endpoint can access Ethereum mainnet

### Address Not Found

- Ensure Ethereum address has a did:ethr DID document
- Verify did:ethr resolution works for the address
- Check blockchain connectivity

## Next Steps

- See [Creating Plugins Guide](../contributing/creating-plugins.md) for custom implementations
- Review [Ethereum DID Integration](ethr-did.md) for did:ethr details
- Check [Integration Modules](README.md) for other DID methods
- Learn about [ENS Domains](https://ens.domains/)

## References

- [Ethereum Name Service (ENS)](https://ens.domains/)
- [ENS Documentation](https://docs.ens.domains/)
- [ENS Resolver Contracts](https://docs.ens.domains/contract-api-reference/ens)
- [Ethereum DID Method](https://github.com/decentralized-identity/ethr-did-resolver)

