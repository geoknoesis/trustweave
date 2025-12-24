---
title: Integration Modules
nav_order: 90
has_children: true
---

# Integration Modules

TrustWeave ships optional integration modules that plug providers into the SPI
and trust/runtime layers. They can now share infrastructure exposed by
`TrustWeave-common` (which includes SPI interfaces) and `TrustWeave-trust`.

> 📋 **Quick Reference**: For a comprehensive table view of all supported plugins, see the [Supported Plugins](../plugins.md) page.

Add an integration when you need a concrete provider:

```kotlin
dependencies {
    // Chain plugins use hierarchical group IDs
    implementation("org.trustweave.chains:algorand:1.0.0-SNAPSHOT")

    // DID plugins use hierarchical group IDs
    implementation("org.trustweave.did:key:1.0.0-SNAPSHOT")

    // KMS plugins use hierarchical group IDs
    // Core dependencies (trustweave-kms, trustweave-common) are included transitively
    implementation("org.trustweave.kms:aws:1.0.0-SNAPSHOT")
}
```

**Result:** Gradle brings in the Algorand client plus its SPI adapter so your application can anchor digests on Algorand networks with zero additional wiring. KMS plugins automatically include `trustweave-kms` and `trustweave-common` as transitive dependencies.

## Blockchain Anchor Integrations

All blockchain clients adopt the shared SPI abstractions. These modules enable anchoring credential digests to various blockchain networks.

| Plugin | Module ID | Networks | Documentation | Description |
|--------|-----------|----------|---------------|-------------|
| **Algorand** | `org.trustweave.chains:algorand` | Mainnet, Testnet | [Algorand Guide](algorand.md) | Production-ready anchoring for Algorand mainnet/testnet |
| **Ethereum** | `org.trustweave.chains:ethereum` | Mainnet, Sepolia | [Ethereum Anchor Guide](ethereum-anchor.md) | Ethereum mainnet anchoring with Sepolia testnet support |
| **Base** | `org.trustweave.chains:base` | Mainnet, Sepolia | [Base Anchor Guide](base-anchor.md) | Base (Coinbase L2) anchoring with fast confirmations and lower fees |
| **Arbitrum** | `org.trustweave.chains:arbitrum` | Mainnet, Sepolia | [Arbitrum Anchor Guide](arbitrum-anchor.md) | Arbitrum One (largest L2 by TVL) anchoring with EVM compatibility |
| **Polygon** | `org.trustweave.chains:polygon` | Mainnet, Mumbai | [Polygon DID Guide](polygon-did.md) | Polygon PoS anchoring with the shared SPI plumbing |
| **Optimism** | `org.trustweave.chains:optimism` | Mainnet, Sepolia | Documentation coming soon | Optimism L2 anchoring with Web3j integration |
| **zkSync Era** | `org.trustweave.chains:zksync` | Mainnet, Sepolia | Documentation coming soon | zkSync Era L2 anchoring with Web3j integration |
| **Bitcoin** | `org.trustweave.chains:bitcoin` | Mainnet, Testnet | Documentation coming soon | Bitcoin blockchain anchoring with OP_RETURN support via RPC |
| **Ganache** | `org.trustweave.chains:ganache` | Local | [Integration Modules](#blockchain-anchor-integrations) | Local developer anchoring using Ganache/Testcontainers for testing |

These modules will be consolidated over time into a single
`anchor-integrations` family that reuses common adapter plumbing.

## DID Method Integrations

TrustWeave provides comprehensive DID method implementations following the W3C DID specification. All DID method plugins implement the `DidMethodService` interface.

| Plugin | Module ID | Documentation | Description |
|--------|-----------|---------------|-------------|
| **did:key** | `org.trustweave.did:key` | [Key DID Guide](key-did.md) | Native did:key implementation, the most widely-used DID method. Zero external dependencies, portable public key-based DIDs |
| **did:web** | `org.trustweave.did:web` | [Web DID Guide](web-did.md) | Web DID method for HTTP/HTTPS-based resolution. Full W3C spec compliance with domain and path-based identifiers |
| **did:ethr** | `org.trustweave.did:ethr` | [Ethereum DID Guide](ethr-did.md) | Ethereum DID method with blockchain anchoring support. Integrates with Ethereum mainnet and testnets (Sepolia) |
| **did:ion** | `org.trustweave.did:ion` | [ION DID Guide](ion-did.md) | Microsoft ION DID method using Sidetree protocol. Bitcoin-anchored DIDs with ION node integration |
| **did:polygon** | `org.trustweave.did:polygon` | [Polygon DID Guide](polygon-did.md) | Polygon DID method reusing Ethereum registry pattern. Lower transaction costs than Ethereum mainnet |
| **did:sol** | `org.trustweave.did:sol` | [Solana DID Guide](sol-did.md) | Solana DID method with program integration. Account-based storage on Solana blockchain |
| **did:peer** | `org.trustweave.did:peer` | [Peer DID Guide](peer-did.md) | Peer-to-peer DID method. No external registry required, supports numalgo 0, 1, and 2 |
| **did:jwk** | `org.trustweave.did:jwk` | [JWK DID Guide](jwk-did.md) | W3C-standard did:jwk method using JSON Web Keys directly. Standardized approach with JWK format |
| **did:ens** | `org.trustweave.did:ens` | [ENS DID Guide](ens-did.md) | Ethereum Name Service (ENS) resolver integration. Human-readable DID identifiers mapped to Ethereum addresses |
| **did:plc** | `org.trustweave.did:plc` | [PLC DID Guide](plc-did.md) | Personal Linked Container (PLC) DID method for AT Protocol. Distributed registry with HTTP-based resolution |
| **did:cheqd** | `org.trustweave.did:cheqd` | [Cheqd DID Guide](cheqd-did.md) | Cheqd network DID method with payment-enabled features. Cosmos-based blockchain for identity and credentials |

## Key Management Service (KMS) Integrations

KMS integrations enable TrustWeave to use various key management services for secure key generation, storage, and signing operations. All KMS plugins implement the `KeyManagementService` interface.

| Plugin | Module ID | Documentation | Key Features |
|--------|-----------|---------------|--------------|
| **AWS KMS** | `org.trustweave.kms:aws` | [AWS KMS Guide](aws-kms.md) | Full algorithm support (Ed25519, secp256k1, P-256/P-384/P-521, RSA). FIPS 140-3 Level 3 compliance |
| **Azure Key Vault** | `org.trustweave.kms:azure` | [Azure KMS Guide](azure-kms.md) | Algorithm support (secp256k1, P-256/P-384/P-521, RSA). Managed Identity and Service Principal authentication |
| **Google Cloud KMS** | `org.trustweave.kms:google` | [Google KMS Guide](google-kms.md) | Algorithm support (secp256k1, P-256/P-384, RSA). Application Default Credentials and service account authentication |
| **HashiCorp Vault** | `org.trustweave.kms:hashicorp` | [HashiCorp Vault Guide](hashicorp-vault-kms.md) | Transit engine integration with algorithm support (Ed25519, secp256k1, P-256/P-384/P-521, RSA). Token and AppRole authentication |
| **IBM Key Protect** | `org.trustweave.kms:ibm` | [IBM Key Protect Guide](ibm-key-protect-kms.md) | IBM Cloud Key Protect integration with full REST API support. FIPS 140-3 Level 4 compliance |
| **InMemory KMS** | `org.trustweave.kms:inmemory` | [InMemory KMS Guide](inmemory-kms.md) | Native TrustWeave in-memory KMS for development and testing. No external dependencies |
| **Thales CipherTrust** | `org.trustweave.kms:thales` | Documentation coming soon | Thales CipherTrust Manager integration with OAuth2 authentication |
| **CyberArk Conjur** | `org.trustweave.kms:cyberark` | Documentation coming soon | CyberArk Conjur integration with secrets management |
| **Fortanix DSM** | `org.trustweave.kms:fortanix` | Documentation coming soon | Fortanix DSM multi-cloud key management integration |

## Proof Generator Integrations

TrustWeave provides multiple proof generation methods for creating cryptographic proofs on verifiable credentials. All proof generators implement the `ProofGenerator` interface.

| Plugin | Module ID | Documentation | Description |
|--------|-----------|---------------|-------------|
| **JWT Proof Generator** | `org.trustweave.core:jwt-proof` | Documentation coming soon | JWT-based proofs using nimbus-jose-jwt. Supports Ed25519, ECDSA, and RSA algorithms |
| **BBS+ Proof Generator** | `org.trustweave.core:bbs-proof` | Documentation coming soon | BBS+ signature proofs for selective disclosure. Uses JSON-LD canonicalization |
| **LD-Proof Generator** | `org.trustweave.core:ld-proof` | Documentation coming soon | Linked Data Proofs using JSON-LD signatures. Supports multiple signature suites |

## Wallet Factory Integrations

Wallet factories enable creation of different wallet storage backends for credential management. All wallet factories implement the `WalletFactory` interface.

| Plugin | Module ID | Documentation | Description |
|--------|-----------|---------------|-------------|
| **Database Wallet** | `org.trustweave.core:database-wallet` | Documentation coming soon | Database-backed wallet with full CredentialStorage implementation. Supports PostgreSQL, MySQL, H2, and other JDBC-compatible databases |
| **File Wallet** | `org.trustweave.core:file-wallet` | Documentation coming soon | File-based wallet with local filesystem storage. Optional AES encryption support |
| **Cloud Wallet** | `org.trustweave.core:cloud-wallet` | Documentation coming soon | Abstract base for cloud storage wallets. Supports AWS S3, Azure Blob Storage, and Google Cloud Storage |

## Other Integrations

Additional integrations that provide bridges to other identity ecosystems or combined functionality.

| Plugin | Module ID | Documentation | Description |
|--------|-----------|---------------|-------------|
| **GoDiddy** | `org.trustweave.did:godiddy` | [GoDiddy Guide](godiddy.md) | HTTP bridge to DID/VC services exposed by the GoDiddy stack |
| **walt.id** | `org.trustweave.kms:waltid` | [walt.id Guide](waltid.md) | DID and KMS providers from the walt.id ecosystem |

Each module implements the SPI interfaces (`DidMethodService`, `KmsService`,
etc.) supplied by `TrustWeave-common` (which includes SPI interfaces).

## Next Steps

- When authoring a new integration, depend on `TrustWeave-common` (which includes SPI interfaces) and expose providers via the adapter loader.
- Trust registries should target `TrustWeave-trust` and optionally wrap
  themselves using `TrustRegistryServiceAdapter`.
- Future work will introduce a shared `TrustWeave-anchor-integrations` module to
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

