---
title: Supported Plugins
nav_exclude: true
---

# Supported Plugins

> Comprehensive listing of all supported TrustWeave plugins organized by category. Each plugin includes links to detailed integration guides.

TrustWeave's plugin architecture enables you to integrate with various DID methods, blockchain networks, key management services, and wallet storage backends. All plugins follow the SPI (Service Provider Interface) pattern for automatic discovery and registration.

## 📋 Quick Navigation

- DID Method Plugins](#did-method-plugins)
- Blockchain Anchor Plugins](#blockchain-anchor-plugins)
- Key Management Service (KMS) Plugins](#key-management-service-kms-plugins)
- Other Integrations](#other-integrations)

---

## DID Method Plugins

DID method plugins enable TrustWeave to create, resolve, and manage DIDs using various DID method specifications. All DID method plugins implement the `DidMethodService` interface.

| Plugin | Module ID | Documentation | Description |
|--------|-----------|---------------|-------------|
| **did:key** | `org.trustweave:did-plugins-key` | [Key DID Guide](integrations/key-did.md) | Native did:key implementation - most widely-used DID method with zero external dependencies |
| **did:web** | `org.trustweave:did-plugins-web` | [Web DID Guide](integrations/web-did.md) | Web DID method for HTTP/HTTPS-based resolution with full W3C spec compliance |
| **did:ethr** | `org.trustweave:did-plugins-ethr` | [Ethereum DID Guide](integrations/ethr-did.md) | Ethereum DID method with blockchain anchoring support for mainnet and testnets |
| **did:ion** | `org.trustweave:did-plugins-ion` | [ION DID Guide](integrations/ion-did.md) | Microsoft ION DID method using Sidetree protocol with Bitcoin anchoring |
| **did:polygon** | `org.trustweave:did-plugins-polygon` | [Polygon DID Guide](integrations/polygon-did.md) | Polygon DID method with lower transaction costs than Ethereum mainnet |
| **did:sol** | `org.trustweave:did-plugins-sol` | [Solana DID Guide](integrations/sol-did.md) | Solana DID method with program integration and account-based storage |
| **did:peer** | `org.trustweave:did-plugins-peer` | [Peer DID Guide](integrations/peer-did.md) | Peer-to-peer DID method with no external registry, supports numalgo 0, 1, and 2 |
| **did:jwk** | `org.trustweave:did-plugins-jwk` | [JWK DID Guide](integrations/jwk-did.md) | W3C-standard did:jwk method using JSON Web Keys directly |
| **did:ens** | `org.trustweave:did-plugins-ens` | [ENS DID Guide](integrations/ens-did.md) | Ethereum Name Service (ENS) resolver integration with human-readable identifiers |
| **did:plc** | `org.trustweave:did-plugins-plc` | [PLC DID Guide](integrations/plc-did.md) | Personal Linked Container (PLC) DID method for AT Protocol with HTTP-based resolution |
| **did:cheqd** | `org.trustweave:did-plugins-cheqd` | [Cheqd DID Guide](integrations/cheqd-did.md) | Cheqd network DID method with payment-enabled features on Cosmos-based blockchain |

---

## Blockchain Anchor Plugins

Blockchain anchor plugins enable TrustWeave to anchor credential digests to various blockchain networks. All blockchain anchor plugins implement the `BlockchainAnchorClient` interface.

| Plugin | Module ID | Documentation | Supported Networks | Description |
|--------|-----------|---------------|-------------------|-------------|
| **Ethereum** | `org.trustweave:anchors-plugins-ethereum` | [Ethereum Anchor Guide](integrations/ethereum-anchor.md) | Mainnet, Sepolia | Ethereum mainnet anchoring with EVM-compatible transaction data storage |
| **Base** | `org.trustweave:anchors-plugins-base` | [Base Anchor Guide](integrations/base-anchor.md) | Mainnet, Sepolia | Base (Coinbase L2) anchoring with fast confirmations and lower fees |
| **Arbitrum** | `org.trustweave:anchors-plugins-arbitrum` | [Arbitrum Anchor Guide](integrations/arbitrum-anchor.md) | Mainnet, Sepolia | Arbitrum One (largest L2 by TVL) anchoring with EVM compatibility |
| **Algorand** | `org.trustweave:anchors-plugins-algorand` | [Algorand Guide](integrations/algorand.md) | Mainnet, Testnet | Algorand blockchain anchoring for production-ready anchoring |
| **Polygon** | `org.trustweave:anchors-plugins-polygon` | [Polygon DID Guide](integrations/polygon-did.md) | Mainnet, Mumbai | Polygon PoS anchoring with shared SPI plumbing |
| **Ganache** | `org.trustweave:anchors-plugins-ganache` | [Integration Modules](integrations/README.md#blockchain-anchor-integrations) | Local | Local developer anchoring using Ganache/Testcontainers for testing |

---

## Key Management Service (KMS) Plugins

KMS plugins enable TrustWeave to use various key management services for secure key generation, storage, and signing operations. All KMS plugins implement the `KeyManagementService` interface.

### Cloud KMS Providers

| Plugin | Module ID | Documentation | Key Features |
|--------|-----------|---------------|--------------|
| **AWS KMS** | `org.trustweave:kms-plugins-aws` | [AWS KMS Guide](integrations/aws-kms.md) | FIPS 140-3 Level 3, Ed25519, secp256k1, P-256/P-384/P-521, RSA |
| **Azure Key Vault** | `org.trustweave:kms-plugins-azure` | [Azure KMS Guide](integrations/azure-kms.md) | Managed Identity, Service Principal auth, secp256k1, P-256/P-384/P-521, RSA |
| **Google Cloud KMS** | `org.trustweave:kms-plugins-google` | [Google KMS Guide](integrations/google-kms.md) | Application Default Credentials, secp256k1, P-256/P-384, RSA |

### Self-Hosted KMS Providers

| Plugin | Module ID | Documentation | Key Features |
|--------|-----------|---------------|--------------|
| **HashiCorp Vault** | `org.trustweave:kms-plugins-hashicorp` | [HashiCorp Vault Guide](integrations/hashicorp-vault-kms.md) | Transit engine, Token/AppRole auth, Ed25519, secp256k1, P-256/P-384/P-521, RSA |

---

## Feature Plugins

Feature plugins provide additional capabilities for credential management, monitoring, and communication. All feature plugins follow TrustWeave's plugin architecture.

### Core Features

| Plugin | Module ID | Documentation | Description |
|--------|-----------|---------------|-------------|
| **Audit Logging** | `core:plugins:audit-logging` | [Features Guide](features/README.md) | Immutable audit logs for all operations with queryable event history |
| **Metrics & Telemetry** | `core:plugins:metrics` | [Features Guide](features/README.md) | Performance and usage metrics with statistics and percentiles |
| **Health Checks** | `core:plugins:health-checks` | [Features Guide](features/README.md) | System diagnostics and health monitoring |

### Credential Management

| Plugin | Module ID | Documentation | Description |
|--------|-----------|---------------|-------------|
| **Credential Versioning** | `core:plugins:credential-versioning` | [Features Guide](features/README.md) | Version tracking and rollback for credentials |
| **Backup & Recovery** | `core:plugins:credential-backup` | [Features Guide](features/README.md) | Export/import credentials from wallets |
| **Expiration Management** | `core:plugins:expiration-management` | [Features Guide](features/README.md) | Monitor and manage expiring credentials |
| **Credential Rendering** | `core:plugins:credential-rendering` | [Features Guide](features/README.md) | Render credentials as HTML/PDF |

### Communication & Exchange

| Plugin | Module ID | Documentation | Description |
|--------|-----------|---------------|-------------|
| **QR Code Generation** | `core:plugins:qr-code` | [Features Guide](features/README.md) | Generate QR codes for credential sharing |
| **Notifications** | `core:plugins:notifications` | [Features Guide](features/README.md) | Push notifications and webhooks for credential events |
| **OIDC4VCI** | `core:plugins:oidc4vci` | [Features Guide](features/README.md) | OpenID Connect for Verifiable Credential Issuance |
| **DIDComm v2** | `core:plugins:didcomm` | [Features Guide](features/README.md) | DIDComm v2 credential exchange protocol |
| **CHAPI** | `core:plugins:chapi` | [Features Guide](features/README.md) | Credential Handler API support |

### Advanced Features

| Plugin | Module ID | Documentation | Description |
|--------|-----------|---------------|-------------|
| **Multi-Party Issuance** | `core:plugins:multi-party-issuance` | [Features Guide](features/README.md) | Collaborative credential issuance with consensus |
| **Analytics & Reporting** | `core:plugins:analytics` | [Features Guide](features/README.md) | Analytics and trend analysis for credentials |

For detailed usage examples, see the [Features Usage Guide](features/USAGE_GUIDE.md).

---

## Other Integrations

Additional integrations that provide bridges to other identity ecosystems or combined DID/KMS functionality.

| Plugin | Module ID | Documentation | Description |
|--------|-----------|---------------|-------------|
| **GoDiddy** | `org.trustweave:did-plugins-godiddy` | [GoDiddy Guide](integrations/godiddy.md) | HTTP bridge to DID/VC services exposed by the GoDiddy stack |
| **walt.id** | `org.trustweave:kms-plugins-waltid` | [walt.id Guide](integrations/waltid.md) | DID and KMS providers from the walt.id ecosystem |

---

## Experimental / Stub Plugins

The following plugins have scaffolding in place but are not yet fully implemented. They will throw descriptive exceptions if used. Check [GitHub issues](https://github.com/geoknoesis/trustweave/issues) for implementation status.

| Plugin | Module | Status |
|--------|--------|--------|
| **Cardano** | `org.trustweave:anchors-plugins-cardano` | Transaction submission/reading not implemented |
| **StarkNet** | `org.trustweave:anchors-plugins-starknet` | Transaction operations not implemented |
| **Indy** | `org.trustweave:anchors-plugins-indy` | ATTRIB transaction operations not implemented |
| **did:btcr** | `org.trustweave:did-plugins-btcr` | Create/resolve not implemented |
| **did:tezos** | `org.trustweave:did-plugins-tezos` | Create/resolve not implemented |
| **did:orb** | `org.trustweave:did-plugins-orb` | Create/resolve not implemented |
| **did:3box** | `org.trustweave:did-plugins-threebox` | Create/resolve not implemented |
| **CloudHSM** | `org.trustweave:kms-plugins-cloudhsm` | Not implemented |
| **Entrust** | `org.trustweave:kms-plugins-entrust` | Not implemented |
| **Thales Luna** | `org.trustweave:kms-plugins-thales-luna` | Not implemented |
| **Utimaco** | `org.trustweave:kms-plugins-utimaco` | Not implemented |
| **ServiceNow** | `credentials:platforms:servicenow` | Credential issuance/verification not implemented |
| **Salesforce** | `credentials:platforms:salesforce` | Credential issuance/verification not implemented |
| **Entra ID** | `credentials:platforms:entra` | Credential issuance/verification not implemented |
| **Venafi** | `org.trustweave:kms-plugins-venafi` | Credential issuance not implemented |

---

## Usage Example

To use a plugin, simply add it as a dependency to your project:

```kotlin
dependencies {
    // DID method plugin
    implementation("org.trustweave:did-plugins-key:0.6.0")

    // Blockchain anchor plugin
    implementation("org.trustweave:anchors-plugins-ethereum:0.6.0")

    // KMS plugin
    implementation("org.trustweave:kms-plugins-aws:0.6.0")
}
```

Plugins are automatically discovered and registered via the SPI (Service Provider Interface) pattern. No extra registration call is required at runtime: providers on the classpath are considered when you build the facade:

```kotlin
import kotlinx.coroutines.runBlocking
import org.trustweave.trust.TrustWeave

fun main() = runBlocking {
    val trustWeave = TrustWeave.quickStart()
    // SPI-provided KMS / DID / anchor providers participate in resolution during build/quickStart.
}
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

## Related Documentation

- [Integration modules](integrations/README.md) — Detailed integration guides for each plugin
- [Plugin implementation status](contributing/plugin-implementation-status.md) — Current implementation status of all plugins
- [Creating plugins](contributing/creating-plugins.md) — Guide for implementing custom plugins
- [Plugin lifecycle](advanced/plugin-lifecycle.md) — Managing plugin initialization and lifecycle

