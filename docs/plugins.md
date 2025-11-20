# Supported Plugins

> Comprehensive listing of all supported VeriCore plugins organized by category. Each plugin includes links to detailed integration guides.

VeriCore's plugin architecture enables you to integrate with various DID methods, blockchain networks, key management services, and wallet storage backends. All plugins follow the SPI (Service Provider Interface) pattern for automatic discovery and registration.

## 📋 Quick Navigation

- [DID Method Plugins](#did-method-plugins)
- [Blockchain Anchor Plugins](#blockchain-anchor-plugins)
- [Key Management Service (KMS) Plugins](#key-management-service-kms-plugins)
- [Other Integrations](#other-integrations)

---

## DID Method Plugins

DID method plugins enable VeriCore to create, resolve, and manage DIDs using various DID method specifications. All DID method plugins implement the `DidMethodService` interface.

### High Priority Methods

| Plugin | Module ID | Status | Documentation | Description |
|--------|-----------|--------|---------------|-------------|
| **did:key** | `com.geoknoesis.vericore.did:key` | ✅ Production | [Key DID Guide](integrations/key-did.md) | Native did:key implementation - most widely-used DID method with zero external dependencies |
| **did:web** | `com.geoknoesis.vericore.did:web` | ✅ Production | [Web DID Guide](integrations/web-did.md) | Web DID method for HTTP/HTTPS-based resolution with full W3C spec compliance |
| **did:ethr** | `com.geoknoesis.vericore.did:ethr` | ✅ Production | [Ethereum DID Guide](integrations/ethr-did.md) | Ethereum DID method with blockchain anchoring support for mainnet and testnets |
| **did:ion** | `com.geoknoesis.vericore.did:ion` | ✅ Production | [ION DID Guide](integrations/ion-did.md) | Microsoft ION DID method using Sidetree protocol with Bitcoin anchoring |

### Medium Priority Methods

| Plugin | Module ID | Status | Documentation | Description |
|--------|-----------|--------|---------------|-------------|
| **did:polygon** | `com.geoknoesis.vericore.did:polygon` | ✅ Production | [Polygon DID Guide](integrations/polygon-did.md) | Polygon DID method with lower transaction costs than Ethereum mainnet |
| **did:sol** | `com.geoknoesis.vericore.did:sol` | ✅ Production | [Solana DID Guide](integrations/sol-did.md) | Solana DID method with program integration and account-based storage |
| **did:peer** | `com.geoknoesis.vericore.did:peer` | ✅ Production | [Peer DID Guide](integrations/peer-did.md) | Peer-to-peer DID method with no external registry, supports numalgo 0, 1, and 2 |

### Lower Priority Methods

| Plugin | Module ID | Status | Documentation | Description |
|--------|-----------|--------|---------------|-------------|
| **did:jwk** | `com.geoknoesis.vericore.did:jwk` | ✅ Production | [JWK DID Guide](integrations/jwk-did.md) | W3C-standard did:jwk method using JSON Web Keys directly |
| **did:ens** | `com.geoknoesis.vericore.did:ens` | ✅ Production | [ENS DID Guide](integrations/ens-did.md) | Ethereum Name Service (ENS) resolver integration with human-readable identifiers |
| **did:plc** | `com.geoknoesis.vericore.did:plc` | ✅ Production | [PLC DID Guide](integrations/plc-did.md) | Personal Linked Container (PLC) DID method for AT Protocol with HTTP-based resolution |
| **did:cheqd** | `com.geoknoesis.vericore.did:cheqd` | ✅ Production | [Cheqd DID Guide](integrations/cheqd-did.md) | Cheqd network DID method with payment-enabled features on Cosmos-based blockchain |

---

## Blockchain Anchor Plugins

Blockchain anchor plugins enable VeriCore to anchor credential digests to various blockchain networks. All blockchain anchor plugins implement the `BlockchainAnchorClient` interface.

| Plugin | Module ID | Status | Documentation | Supported Networks | Description |
|--------|-----------|--------|---------------|-------------------|-------------|
| **Ethereum** | `com.geoknoesis.vericore.chains:ethereum` | ✅ Production | [Ethereum Anchor Guide](integrations/ethereum-anchor.md) | Mainnet, Sepolia | Ethereum mainnet anchoring with EVM-compatible transaction data storage |
| **Base** | `com.geoknoesis.vericore.chains:base` | ✅ Production | [Base Anchor Guide](integrations/base-anchor.md) | Mainnet, Sepolia | Base (Coinbase L2) anchoring with fast confirmations and lower fees |
| **Arbitrum** | `com.geoknoesis.vericore.chains:arbitrum` | ✅ Production | [Arbitrum Anchor Guide](integrations/arbitrum-anchor.md) | Mainnet, Sepolia | Arbitrum One (largest L2 by TVL) anchoring with EVM compatibility |
| **Algorand** | `com.geoknoesis.vericore.chains:algorand` | ✅ Production | [Algorand Guide](integrations/algorand.md) | Mainnet, Testnet | Algorand blockchain anchoring for production-ready anchoring |
| **Polygon** | `com.geoknoesis.vericore.chains:polygon` | ✅ Production | [Polygon DID Guide](integrations/polygon-did.md) | Mainnet, Mumbai | Polygon PoS anchoring with shared SPI plumbing |
| **Ganache** | `com.geoknoesis.vericore.chains:ganache` | ✅ Development | [Integration Modules](integrations/README.md#blockchain-anchor-integrations) | Local | Local developer anchoring using Ganache/Testcontainers for testing |

---

## Key Management Service (KMS) Plugins

KMS plugins enable VeriCore to use various key management services for secure key generation, storage, and signing operations. All KMS plugins implement the `KeyManagementService` interface.

### Cloud KMS Providers

| Plugin | Module ID | Status | Documentation | Key Features |
|--------|-----------|--------|---------------|--------------|
| **AWS KMS** | `com.geoknoesis.vericore.kms:aws` | ✅ Production | [AWS KMS Guide](integrations/aws-kms.md) | FIPS 140-3 Level 3, Ed25519, secp256k1, P-256/P-384/P-521, RSA |
| **Azure Key Vault** | `com.geoknoesis.vericore.kms:azure` | ✅ Production | [Azure KMS Guide](integrations/azure-kms.md) | Managed Identity, Service Principal auth, secp256k1, P-256/P-384/P-521, RSA |
| **Google Cloud KMS** | `com.geoknoesis.vericore.kms:google` | ✅ Production | [Google KMS Guide](integrations/google-kms.md) | Application Default Credentials, secp256k1, P-256/P-384, RSA |

### Self-Hosted KMS Providers

| Plugin | Module ID | Status | Documentation | Key Features |
|--------|-----------|--------|---------------|--------------|
| **HashiCorp Vault** | `com.geoknoesis.vericore.kms:hashicorp` | ✅ Production | [HashiCorp Vault Guide](integrations/hashicorp-vault-kms.md) | Transit engine, Token/AppRole auth, Ed25519, secp256k1, P-256/P-384/P-521, RSA |

---

## Other Integrations

Additional integrations that provide bridges to other identity ecosystems or combined DID/KMS functionality.

| Plugin | Module ID | Status | Documentation | Description |
|--------|-----------|--------|---------------|-------------|
| **GoDiddy** | `com.geoknoesis.vericore.did:godiddy` | ✅ Production | [GoDiddy Guide](integrations/godiddy.md) | HTTP bridge to DID/VC services exposed by the GoDiddy stack |
| **walt.id** | `com.geoknoesis.vericore.kms:waltid` | ✅ Production | [walt.id Guide](integrations/waltid.md) | DID and KMS providers from the walt.id ecosystem |

---

## Usage Example

To use a plugin, simply add it as a dependency to your project:

```kotlin
dependencies {
    // DID method plugin
    implementation("com.geoknoesis.vericore.did:key:1.0.0-SNAPSHOT")
    
    // Blockchain anchor plugin
    implementation("com.geoknoesis.vericore.chains:ethereum:1.0.0-SNAPSHOT")
    
    // KMS plugin
    implementation("com.geoknoesis.vericore.kms:aws:1.0.0-SNAPSHOT")
}
```

Plugins are automatically discovered and registered via the SPI (Service Provider Interface) pattern. No additional configuration is required - they will be available when you create a `VeriCore` instance:

```kotlin
val vericore = VeriCore.create()
// Plugins are automatically available!
```

---

## Creating Custom Plugins

If you need to create a custom plugin, see the [Creating Plugins Guide](contributing/creating-plugins.md) for detailed instructions on implementing:

- DID methods
- Blockchain anchor clients
- Key management services
- Proof generators
- Wallet factories
- Credential services

---

## Plugin Status Legend

- ✅ **Production**: Fully implemented, tested, and production-ready
- 🚧 **Development**: In development or beta status
- ⚠️ **Experimental**: Early stage or experimental implementation

---

## Related Documentation

- [Integration Modules](integrations/README.md) - Detailed integration guides for each plugin
- [Plugin Implementation Status](contributing/plugin-implementation-status.md) - Current implementation status of all plugins
- [Creating Plugins](contributing/creating-plugins.md) - Guide for implementing custom plugins
- [Plugin Lifecycle](advanced/plugin-lifecycle.md) - Managing plugin initialization and lifecycle

