# Integration Modules

VeriCore ships optional integration modules that plug providers into the SPI
and trust/runtime layers. They can now share infrastructure exposed by
`vericore-spi` and `vericore-trust`.

> 📋 **Quick Reference**: For a comprehensive table view of all supported plugins, see the [Supported Plugins](../plugins.md) page.

Add an integration when you need a concrete provider:

```kotlin
dependencies {
    // Chain plugins use hierarchical group IDs
    implementation("com.geoknoesis.vericore.chains:algorand:1.0.0-SNAPSHOT")
    
    // DID plugins use hierarchical group IDs
    implementation("com.geoknoesis.vericore.did:key:1.0.0-SNAPSHOT")
    
    // KMS plugins use hierarchical group IDs
    implementation("com.geoknoesis.vericore.kms:aws:1.0.0-SNAPSHOT")
}
```

**Result:** Gradle brings in the Algorand client plus its SPI adapter so your application can anchor digests on Algorand networks with zero additional wiring.

## Blockchain Anchor Integrations

All blockchain clients adopt the shared SPI abstractions. These modules enable anchoring credential digests to various blockchain networks.

| Plugin | Module ID | Networks | Documentation | Description |
|--------|-----------|----------|---------------|-------------|
| **Algorand** | `com.geoknoesis.vericore.chains:algorand` | Mainnet, Testnet | [Algorand Guide](algorand.md) | Production-ready anchoring for Algorand mainnet/testnet |
| **Ethereum** | `com.geoknoesis.vericore.chains:ethereum` | Mainnet, Sepolia | [Ethereum Anchor Guide](ethereum-anchor.md) | Ethereum mainnet anchoring with Sepolia testnet support |
| **Base** | `com.geoknoesis.vericore.chains:base` | Mainnet, Sepolia | [Base Anchor Guide](base-anchor.md) | Base (Coinbase L2) anchoring with fast confirmations and lower fees |
| **Arbitrum** | `com.geoknoesis.vericore.chains:arbitrum` | Mainnet, Sepolia | [Arbitrum Anchor Guide](arbitrum-anchor.md) | Arbitrum One (largest L2 by TVL) anchoring with EVM compatibility |
| **Polygon** | `com.geoknoesis.vericore.chains:polygon` | Mainnet, Mumbai | [Polygon DID Guide](polygon-did.md) | Polygon PoS anchoring with the shared SPI plumbing |
| **Optimism** | `com.geoknoesis.vericore.chains:optimism` | Mainnet, Sepolia | Documentation coming soon | Optimism L2 anchoring with Web3j integration |
| **zkSync Era** | `com.geoknoesis.vericore.chains:zksync` | Mainnet, Sepolia | Documentation coming soon | zkSync Era L2 anchoring with Web3j integration |
| **Bitcoin** | `com.geoknoesis.vericore.chains:bitcoin` | Mainnet, Testnet | Documentation coming soon | Bitcoin blockchain anchoring with OP_RETURN support via RPC |
| **Ganache** | `com.geoknoesis.vericore.chains:ganache` | Local | [Integration Modules](#blockchain-anchor-integrations) | Local developer anchoring using Ganache/Testcontainers for testing |

These modules will be consolidated over time into a single
`anchor-integrations` family that reuses common adapter plumbing.

## DID Method Integrations

VeriCore provides comprehensive DID method implementations following the W3C DID specification. All DID method plugins implement the `DidMethodService` interface.

### High Priority Methods

| Plugin | Module ID | Documentation | Description |
|--------|-----------|---------------|-------------|
| **did:key** | `com.geoknoesis.vericore.did:key` | [Key DID Guide](key-did.md) | Native did:key implementation, the most widely-used DID method. Zero external dependencies, portable public key-based DIDs |
| **did:web** | `com.geoknoesis.vericore.did:web` | [Web DID Guide](web-did.md) | Web DID method for HTTP/HTTPS-based resolution. Full W3C spec compliance with domain and path-based identifiers |
| **did:ethr** | `com.geoknoesis.vericore.did:ethr` | [Ethereum DID Guide](ethr-did.md) | Ethereum DID method with blockchain anchoring support. Integrates with Ethereum mainnet and testnets (Sepolia) |
| **did:ion** | `com.geoknoesis.vericore.did:ion` | [ION DID Guide](ion-did.md) | Microsoft ION DID method using Sidetree protocol. Bitcoin-anchored DIDs with ION node integration |

### Medium Priority Methods

| Plugin | Module ID | Documentation | Description |
|--------|-----------|---------------|-------------|
| **did:polygon** | `com.geoknoesis.vericore.did:polygon` | [Polygon DID Guide](polygon-did.md) | Polygon DID method reusing Ethereum registry pattern. Lower transaction costs than Ethereum mainnet |
| **did:sol** | `com.geoknoesis.vericore.did:sol` | [Solana DID Guide](sol-did.md) | Solana DID method with program integration. Account-based storage on Solana blockchain |
| **did:peer** | `com.geoknoesis.vericore.did:peer` | [Peer DID Guide](peer-did.md) | Peer-to-peer DID method. No external registry required, supports numalgo 0, 1, and 2 |

### Lower Priority Methods

| Plugin | Module ID | Documentation | Description |
|--------|-----------|---------------|-------------|
| **did:jwk** | `com.geoknoesis.vericore.did:jwk` | [JWK DID Guide](jwk-did.md) | W3C-standard did:jwk method using JSON Web Keys directly. Standardized approach with JWK format |
| **did:ens** | `com.geoknoesis.vericore.did:ens` | [ENS DID Guide](ens-did.md) | Ethereum Name Service (ENS) resolver integration. Human-readable DID identifiers mapped to Ethereum addresses |
| **did:plc** | `com.geoknoesis.vericore.did:plc` | [PLC DID Guide](plc-did.md) | Personal Linked Container (PLC) DID method for AT Protocol. Distributed registry with HTTP-based resolution |
| **did:cheqd** | `com.geoknoesis.vericore.did:cheqd` | [Cheqd DID Guide](cheqd-did.md) | Cheqd network DID method with payment-enabled features. Cosmos-based blockchain for identity and credentials |

## Key Management Service (KMS) Integrations

KMS integrations enable VeriCore to use various key management services for secure key generation, storage, and signing operations. All KMS plugins implement the `KeyManagementService` interface.

| Plugin | Module ID | Documentation | Key Features |
|--------|-----------|---------------|--------------|
| **AWS KMS** | `com.geoknoesis.vericore.kms:aws` | [AWS KMS Guide](aws-kms.md) | Full algorithm support (Ed25519, secp256k1, P-256/P-384/P-521, RSA). FIPS 140-3 Level 3 compliance |
| **Azure Key Vault** | `com.geoknoesis.vericore.kms:azure` | [Azure KMS Guide](azure-kms.md) | Algorithm support (secp256k1, P-256/P-384/P-521, RSA). Managed Identity and Service Principal authentication |
| **Google Cloud KMS** | `com.geoknoesis.vericore.kms:google` | [Google KMS Guide](google-kms.md) | Algorithm support (secp256k1, P-256/P-384, RSA). Application Default Credentials and service account authentication |
| **HashiCorp Vault** | `com.geoknoesis.vericore.kms:hashicorp` | [HashiCorp Vault Guide](hashicorp-vault-kms.md) | Transit engine integration with algorithm support (Ed25519, secp256k1, P-256/P-384/P-521, RSA). Token and AppRole authentication |
| **IBM Key Protect** | `com.geoknoesis.vericore.kms:ibm` | Documentation coming soon | IBM Cloud Key Protect integration with full REST API support |
| **Thales CipherTrust** | `com.geoknoesis.vericore.kms:thales` | Documentation coming soon | Thales CipherTrust Manager integration with OAuth2 authentication |
| **CyberArk Conjur** | `com.geoknoesis.vericore.kms:cyberark` | Documentation coming soon | CyberArk Conjur integration with secrets management |
| **Fortanix DSM** | `com.geoknoesis.vericore.kms:fortanix` | Documentation coming soon | Fortanix DSM multi-cloud key management integration |

## Proof Generator Integrations

VeriCore provides multiple proof generation methods for creating cryptographic proofs on verifiable credentials. All proof generators implement the `ProofGenerator` interface.

| Plugin | Module ID | Documentation | Description |
|--------|-----------|---------------|-------------|
| **JWT Proof Generator** | `com.geoknoesis.vericore.core:jwt-proof` | Documentation coming soon | JWT-based proofs using nimbus-jose-jwt. Supports Ed25519, ECDSA, and RSA algorithms |
| **BBS+ Proof Generator** | `com.geoknoesis.vericore.core:bbs-proof` | Documentation coming soon | BBS+ signature proofs for selective disclosure. Uses JSON-LD canonicalization |
| **LD-Proof Generator** | `com.geoknoesis.vericore.core:ld-proof` | Documentation coming soon | Linked Data Proofs using JSON-LD signatures. Supports multiple signature suites |

## Wallet Factory Integrations

Wallet factories enable creation of different wallet storage backends for credential management. All wallet factories implement the `WalletFactory` interface.

| Plugin | Module ID | Documentation | Description |
|--------|-----------|---------------|-------------|
| **Database Wallet** | `com.geoknoesis.vericore.core:database-wallet` | Documentation coming soon | Database-backed wallet with full CredentialStorage implementation. Supports PostgreSQL, MySQL, H2, and other JDBC-compatible databases |
| **File Wallet** | `com.geoknoesis.vericore.core:file-wallet` | Documentation coming soon | File-based wallet with local filesystem storage. Optional AES encryption support |
| **Cloud Wallet** | `com.geoknoesis.vericore.core:cloud-wallet` | Documentation coming soon | Abstract base for cloud storage wallets. Supports AWS S3, Azure Blob Storage, and Google Cloud Storage |

## Other Integrations

Additional integrations that provide bridges to other identity ecosystems or combined functionality.

| Plugin | Module ID | Documentation | Description |
|--------|-----------|---------------|-------------|
| **GoDiddy** | `com.geoknoesis.vericore.did:godiddy` | [GoDiddy Guide](godiddy.md) | HTTP bridge to DID/VC services exposed by the GoDiddy stack |
| **walt.id** | `com.geoknoesis.vericore.kms:waltid` | [walt.id Guide](waltid.md) | DID and KMS providers from the walt.id ecosystem |

Each module implements the SPI interfaces (`DidMethodService`, `KmsService`,
etc.) supplied by `vericore-spi`.

## Next Steps

- When authoring a new integration, depend on `vericore-spi` instead of
  `vericore-core` and expose providers via the adapter loader.
- Trust registries should target `vericore-trust` and optionally wrap
  themselves using `TrustRegistryServiceAdapter`.
- Future work will introduce a shared `vericore-anchor-integrations` module to
  reduce duplicate registration logic across blockchain clients.

## Related Documentation

- **[Supported Plugins](../plugins.md)** - Comprehensive table view of all supported plugins organized by category
- **[Plugin Implementation Status](../contributing/plugin-implementation-status.md)** - Current implementation status and roadmap for all plugins
- **[Plugin Lifecycle](../advanced/plugin-lifecycle.md)** - Managing plugin initialization, startup, and shutdown

## Creating Custom Plugins

For detailed guidance on implementing custom plugins, see:

- **[Creating Plugins Guide](../contributing/creating-plugins.md)** - Complete guide with examples for all plugin interfaces
  - DID methods
  - Blockchain anchor clients
  - Proof generators (JWT, BBS+, LD-Proof)
  - Key management services
  - Credential services
  - Wallet factories (Database, File, Cloud)

