---
title: Supported Plugins
parent: API Reference
nav_order: 90
redirect_from:
  - /plugins/
---

# Supported Plugins

> Comprehensive listing of all supported TrustWeave plugins organized by category. Each plugin includes links to detailed integration guides.

TrustWeave's plugin architecture enables you to integrate with various DID methods, blockchain networks, key management services, and wallet storage backends. All plugins follow the SPI (Service Provider Interface) pattern for automatic discovery and registration.

> **Maturity note:** TrustWeave's 7 core platform modules (`common`, `trust`, `credentials:credential-api`, `did:did-core`, `kms:kms-core`, `wallet:wallet-core`, `anchors:anchor-core`) are **Supported (GA)**. Plugins, servers, and signature suites listed below are **Experimental** unless explicitly noted; the 12 pure stubs are excluded from `distribution-bom` and Maven Central publishing. See the [Module maturity matrix](module-maturity.md) and [Assessed module capabilities](assessed-capabilities.md).

## 📋 Quick Navigation

- [DID Method Plugins](#did-method-plugins)
- [Blockchain Anchor Plugins](#blockchain-anchor-plugins)
- [Key Management Service (KMS) Plugins](#key-management-service-kms-plugins)
- [Wallet Storage Plugins](#wallet-storage-plugins)
- [Credential Format and Exchange Plugins](#credential-format-and-exchange-plugins)
- [Signature Suites (eIDAS QES)](#signature-suites-eidas-qes)
- [Trust Registry and Observability](#trust-registry-and-observability)
- [Server Modules](#server-modules)
- [Other Integrations](#other-integrations)
- [Pure Stubs (Not Published)](#pure-stubs-not-published)

---

## DID Method Plugins

DID method plugins enable TrustWeave to create, resolve, and manage DIDs using various DID method specifications. All DID method plugins implement the `DidMethod` interface.

| Plugin | Module ID | Documentation | Description |
|--------|-----------|---------------|-------------|
| **did:key** | `org.trustweave:did-plugins-key` | [Key DID Guide](../how-to/integrations/key-did.md) | Native `did:key` implementation with zero external dependencies (bundled in `distribution-all`) |
| **did:web** | `org.trustweave:did-plugins-web` | [Web DID Guide](../how-to/integrations/web-did.md) | Web DID method for HTTP/HTTPS-based resolution |
| **did:ethr** | `org.trustweave:did-plugins-ethr` | [Ethereum DID Guide](../how-to/integrations/ethr-did.md) | **In-memory only (not mainnet ERC-1056).** Experimental in-process store; DIDs do not persist across restarts and are not resolvable by standard ERC-1056 resolvers |
| **did:ion** | `org.trustweave:did-plugins-ion` | [ION DID Guide](../how-to/integrations/ion-did.md) | Microsoft ION DID method using Sidetree protocol |
| **did:polygon** | `org.trustweave:did-plugins-polygon` | [Polygon DID Guide](../how-to/integrations/polygon-did.md) | **In-memory only (not mainnet ERC-1056).** Experimental in-process store; DIDs do not persist across restarts and are not resolvable by standard Polygon DID resolvers |
| **did:sol** | `org.trustweave:did-plugins-sol` | [Solana DID Guide](../how-to/integrations/sol-did.md) | Solana DID method (experimental) |
| **did:peer** | `org.trustweave:did-plugins-peer` | [Peer DID Guide](../how-to/integrations/peer-did.md) | Peer-to-peer DID method with no external registry, supports numalgo 0, 1, and 2 |
| **did:jwk** | `org.trustweave:did-plugins-jwk` | [JWK DID Guide](../how-to/integrations/jwk-did.md) | W3C `did:jwk` method using JSON Web Keys directly |
| **did:plc** | `org.trustweave:did-plugins-plc` | [PLC DID Guide](../how-to/integrations/plc-did.md) | Personal Linked Container (PLC) DID method (resolution supported; write operations not implemented) |
| **did:cheqd** | `org.trustweave:did-plugins-cheqd` | [Cheqd DID Guide](../how-to/integrations/cheqd-did.md) | Cheqd network DID method (experimental) |
| **did:orb** | `org.trustweave:did-plugins-orb` | See module README | Sidetree Orb DID method (experimental) |
| **did:ebsi** | `org.trustweave:did-plugins-ebsi` | See module README | European Blockchain Services Infrastructure (EBSI) DID method (experimental) |

---

## Blockchain Anchor Plugins

Blockchain anchor plugins enable TrustWeave to anchor credential digests to various blockchain networks. All blockchain anchor plugins implement the `BlockchainAnchorClient` interface.

| Plugin | Module ID | Documentation | Supported Networks | Description |
|--------|-----------|---------------|-------------------|-------------|
| **Ethereum** | `org.trustweave:anchors-plugins-ethereum` | [Ethereum Anchor Guide](../how-to/integrations/ethereum-anchor.md) | Mainnet, Sepolia | Ethereum anchoring with EVM-compatible transaction data storage |
| **Base** | `org.trustweave:anchors-plugins-base` | [Base Anchor Guide](../how-to/integrations/base-anchor.md) | Mainnet, Sepolia | Base (Coinbase L2) anchoring |
| **Arbitrum** | `org.trustweave:anchors-plugins-arbitrum` | [Arbitrum Anchor Guide](../how-to/integrations/arbitrum-anchor.md) | Mainnet, Sepolia | Arbitrum One anchoring |
| **Optimism** | `org.trustweave:anchors-plugins-optimism` | See module README | Mainnet, Sepolia | Optimism L2 anchoring (experimental) |
| **zkSync** | `org.trustweave:anchors-plugins-zksync` | See module README | Mainnet, Sepolia | zkSync Era L2 anchoring (experimental) |
| **Algorand** | `org.trustweave:anchors-plugins-algorand` | [Algorand Guide](../how-to/integrations/algorand.md) | Mainnet, Testnet | Algorand blockchain anchoring |
| **Polygon** | `org.trustweave:anchors-plugins-polygon` | [Integration Modules](../how-to/integrations/README.md#blockchain-anchor-integrations) | Mainnet, Amoy | Polygon PoS anchoring |
| **Bitcoin** | `org.trustweave:anchors-plugins-bitcoin` | See module README | Mainnet, Testnet | Bitcoin anchoring via OP_RETURN with BitcoinJ |
| **Cardano** | `org.trustweave:anchors-plugins-cardano` | See module README | Mainnet, Preprod, Preview | Cardano anchoring via Blockfrost (experimental) |
| **Indy** | `org.trustweave:anchors-plugins-indy` | See module README | Indy ledgers | Hyperledger Indy anchoring via VDR proxy (experimental) |
| **Ganache** | `org.trustweave:anchors-plugins-ganache` | [Integration Modules](../how-to/integrations/README.md#blockchain-anchor-integrations) | Local | Local developer anchoring using Ganache/Testcontainers for testing |

---

## Key Management Service (KMS) Plugins

KMS plugins enable TrustWeave to use various key management services for secure key generation, storage, and signing operations. All KMS plugins implement the `KeyManagementService` interface.

### Cloud KMS Providers

| Plugin | Module ID | Documentation | Key Features |
|--------|-----------|---------------|--------------|
| **AWS KMS** | `org.trustweave:kms-plugins-aws` | [AWS KMS Guide](../how-to/integrations/aws-kms.md) | FIPS 140-3 Level 3, secp256k1, P-256/P-384/P-521, RSA (Note: AWS KMS does **not** support Ed25519) |
| **Azure Key Vault** | `org.trustweave:kms-plugins-azure` | [Azure KMS Guide](../how-to/integrations/azure-kms.md) | Managed Identity, Service Principal auth, secp256k1, P-256/P-384/P-521, RSA |
| **Google Cloud KMS** | `org.trustweave:kms-plugins-google` | [Google KMS Guide](../how-to/integrations/google-kms.md) | Application Default Credentials, secp256k1, P-256/P-384, RSA |
| **AWS CloudHSM** | `org.trustweave:kms-plugins-cloudhsm` | See module README | AWS CloudHSM integration (experimental) |

### Self-Hosted & Enterprise KMS Providers

| Plugin | Module ID | Documentation | Key Features |
|--------|-----------|---------------|--------------|
| **HashiCorp Vault** | `org.trustweave:kms-plugins-hashicorp` | [HashiCorp Vault Guide](../how-to/integrations/hashicorp-vault-kms.md) | Transit engine, Token/AppRole auth, Ed25519, secp256k1, P-256/P-384/P-521, RSA |
| **PKCS#11 / HSM** | `org.trustweave:kms-plugins-pkcs11` | See module README | Generic PKCS#11 provider for hardware HSMs (SoftHSM2 tested), token-based authentication |
| **IBM Key Protect** | `org.trustweave:kms-plugins-ibm` | [IBM Key Protect Guide](../how-to/integrations/ibm-key-protect-kms.md) | IBM Cloud Key Protect with IAM auth, key wrap/unwrap, EC and RSA keys |
| **Thales CipherTrust** | `org.trustweave:kms-plugins-thales` | See module README | Thales CipherTrust Manager KMS client (experimental) |
| **Fortanix DSM** | `org.trustweave:kms-plugins-fortanix` | See module README | Fortanix Data Security Manager client (experimental) |
| **CyberArk Conjur** | `org.trustweave:kms-plugins-cyberark` | See module README | CyberArk Conjur secrets/KMS client (experimental) |
| **In-Memory** | `org.trustweave:kms-plugins-inmemory` | [In-Memory KMS Guide](../how-to/integrations/inmemory-kms.md) | Ephemeral in-memory key storage for development and tests (bundled in `distribution-all`) |

---

## Wallet Storage Plugins

Persistent wallet storage plugins implementing `WalletFactory` and `Wallet` for credential storage, querying, and recovery.

| Plugin | Module ID | Description |
|--------|-----------|-------------|
| **Database Wallet** | `org.trustweave:wallet-plugins-database` | PostgreSQL / H2 JDBC-backed wallet storage (`DatabaseWalletFactory`) with tags, collections, and pagination |
| **File Wallet** | `org.trustweave:wallet-plugins-file` | Local encrypted filesystem wallet storage (`FileWalletFactory`) |
| **Cloud Wallet** | `org.trustweave:wallet-plugins-cloud` | Cloud object-storage wallet adapter (`CloudWalletFactory`) |

---

## Credential Format and Exchange Plugins

Credential plugins provide format support (mDL / mdoc, JAdES), status / revocation, agent authorization, and exchange / discovery protocols. They live under `credentials/plugins/*` and are pulled in alongside `credentials:credential-api`.

### Exchange protocols

| Plugin | Module ID | Description |
|--------|-----------|-------------|
| **OIDC4VCI** | `org.trustweave:credentials-plugins-oidc4vci` | OpenID for Verifiable Credential Issuance |
| **OIDC4VP** | `org.trustweave:credentials-plugins-oidc4vp` | OpenID for Verifiable Presentations |
| **SIOPv2** | `org.trustweave:credentials-plugins-siop` | Self-Issued OpenID Provider v2 |
| **Presentation Exchange** | `org.trustweave:credentials-plugins-presentation-exchange` | DIF Presentation Exchange request / definition matching |
| **DIDComm v2** | `org.trustweave:credentials-plugins-didcomm` | DIDComm v2 messaging and credential exchange |
| **CHAPI** | `org.trustweave:credentials-plugins-chapi` | Credential Handler API for browser-based wallets |
| **OpenID Federation** | `org.trustweave:credentials-plugins-openid-federation` | OpenID Federation 1.0 (issuer / verifier discovery) |
| **EUDIW** | `org.trustweave:credentials-plugins-eudiw` | EU Digital Identity Wallet profile glue |
| **Entra Verified ID** | `org.trustweave:credentials-plugins-platforms-entra` | Microsoft Entra Verified ID exchange protocol adapter (experimental) |

### Credential formats and proofs

| Plugin | Module ID | Description |
|--------|-----------|-------------|
| **mDL / mdoc** | `org.trustweave:credentials-plugins-mdl` | ISO/IEC 18013-5 mobile driving licence (mdoc) format |
| **JAdES (credential proof)** | `org.trustweave:credentials-plugins-jades` | ETSI JAdES JSON Advanced Electronic Signature proofs on credentials |

### Agent authorization (agentic commerce)

| Plugin | Module ID | Documentation | Description |
|--------|-----------|---------------|-------------|
| **Verifiable Intent** | `org.trustweave:credentials-plugins-verifiable-intent` | [Verifiable Intent Plugin](features/verifiable-intent.md) | Mastercard/Google Verifiable Intent — layered SD-JWT/ES256 delegation chain proving what a user authorized an AI agent to do |
| **AVP Micro** | `org.trustweave:credentials-plugins-avp-micro` | See module README | Agent Verification Protocol micro-delegation and authorization primitives |

### Status and revocation

| Plugin | Module ID | Description |
|--------|-----------|-------------|
| **StatusList (bitstring)** | `org.trustweave:credentials-plugins-status-list-bitstring` | W3C Bitstring Status List v1.0 |
| **StatusList (token)** | `org.trustweave:credentials-plugins-status-list-token` | IETF Token Status List |
| **StatusList (publishing)** | `org.trustweave:credentials-plugins-status-list-publishing` | Publishing helpers for status lists (local file, S3, Azure Blob, GCS) |
| **StatusList (database)** | `org.trustweave:credentials-plugins-status-list-database` | Database-backed status list storage |
| **StatusList (server)** | `org.trustweave:credentials-plugins-status-list-server` | HTTP server for status list documents |
| **Anchor adapter** | `org.trustweave:credentials-plugins-anchor` | Anchor credentials / status data to a configured chain |

Audit logging, notifications, rendering and similar application-level helpers are not shipped as TrustWeave APIs; the [Features Usage Guide](features/USAGE_GUIDE.md) lists what ships and how to build the rest.

---

## Signature Suites (eIDAS QES)

Advanced electronic signature support aligned with ETSI / eIDAS for qualified electronic signatures. See [eIDAS QES design](../core-concepts/architecture/eidas-qes-design.md) for the overall architecture.

| Plugin | Module ID | Description |
|--------|-----------|-------------|
| **TSA core** | `org.trustweave:signatures-tsa-core` | RFC 3161 time-stamp client and configuration (BouncyCastle backend) |
| **Trust lists** | `org.trustweave:signatures-trust-lists` | ETSI TS 119 612 trust list parser, LOTL signature verifier, trust-anchor resolver |
| **JAdES** | `org.trustweave:signatures-jades` | ETSI TS 119 182-1 JSON Advanced Electronic Signatures |
| **CAdES** | `org.trustweave:signatures-cades` | ETSI TS 119 122 CMS Advanced Electronic Signatures |
| **XAdES** | `org.trustweave:signatures-xades` | ETSI TS 119 132 XML Advanced Electronic Signatures (B-B, B-T, B-LT, B-LTA) |
| **ETSI validation** | `org.trustweave:signatures-etsi-validation` | ETSI EN 319 102 signature validation report engine |

*(Note: `signatures:pades` is currently a pure stub and is excluded from `distribution-bom` and Maven Central publishing — see [Pure Stubs](#pure-stubs-not-published).)*

---

## Trust Registry and Observability

Pluggable trust registry for issuer/holder/verifier trust relationships, plus OpenTelemetry instrumentation.

| Module | Module ID | Description |
|--------|-----------|-------------|
| **Trust Registry core** | `org.trustweave:trust-registry-trust-registry-core` | SPI and core registry types |
| **Database registry** | `org.trustweave:trust-registry-plugins-database` | Relational-database-backed trust registry (`DatabaseTrustRegistryFactory`) |
| **Trust Registry server** | `org.trustweave:trust-registry-trust-registry-server` | Ktor-based HTTP server exposing the registry |
| **Observability** | `org.trustweave:observability` | OpenTelemetry tracing and metrics hooks for TrustWeave operations |

---

## Server Modules

Ready-to-run HTTP server modules wrapping TrustWeave plugins (Ktor and Spring Boot).

| Module | Module ID | Description |
|--------|-----------|-------------|
| **OIDC4VCI issuer server** | `org.trustweave:credentials-oidc4vci-server` | Ktor issuer server implementing the OIDC4VCI protocol |
| **VC API server** | `org.trustweave:credentials-vc-api-server` | Ktor server implementing the W3C VC API (issue/verify) |
| **AVP authorization server** | `org.trustweave:credentials-avp-authorization-server` | Ktor server for Agent Verification Protocol authorization flows |
| **Status List server** | `org.trustweave:credentials-plugins-status-list-server` | HTTP server publishing W3C Bitstring / Token status list documents |
| **DID Registrar server (Ktor)** | `org.trustweave:did-registrar-server-ktor` | Ktor server implementing the Universal Registrar interface for supported DID methods |
| **DID Registrar server (Spring)** | `org.trustweave:did-registrar-server-spring` | Spring Boot server implementing the Universal Registrar interface for supported DID methods |
| **Trust Registry server** | `org.trustweave:trust-registry-trust-registry-server` | Ktor server exposing trust registry lookup and management APIs |

---

## Other Integrations

Additional integrations that provide bridges to other identity ecosystems or combined DID/KMS functionality.

| Plugin | Module ID | Documentation | Description |
|--------|-----------|---------------|-------------|
| **GoDiddy** | `org.trustweave:did-plugins-godiddy` | [GoDiddy Guide](../how-to/integrations/godiddy.md) | HTTP bridge to DID/VC services exposed by the GoDiddy stack |
| **walt.id** | `org.trustweave:kms-plugins-waltid` | [walt.id Guide](../how-to/integrations/waltid.md) | DID and KMS providers from the walt.id ecosystem |

---

## Pure Stubs (Not Published)

The following **12 pure stub modules** exist in the source tree as scaffolding only. Their operations throw `UnsupportedOperationException` / `InvalidOperation`. They are **excluded from `distribution-bom` and Maven Central publishing** and marked `stub` in [Assessed module capabilities](assessed-capabilities.md). Check [GitHub issues](https://github.com/geoknoesis/trustweave/issues) for implementation status.

| Plugin | Gradle Module | Status |
|--------|---------------|--------|
| **StarkNet** | `anchors:plugins:starknet` | Stub — transaction operations not implemented |
| **Salesforce** | `credentials:plugins:platforms:salesforce` | Stub — credential issuance/verification not implemented |
| **ServiceNow** | `credentials:plugins:platforms:servicenow` | Stub — credential issuance/verification not implemented |
| **did:btcr** | `did:plugins:btcr` | Stub — create/resolve not implemented |
| **did:ens** | `did:plugins:ens` | Stub — ENS name-to-address lookup not implemented |
| **did:tezos** | `did:plugins:tezos` | Stub — create/resolve not implemented |
| **did:3box** | `did:plugins:threebox` | Stub — create/resolve not implemented |
| **Entrust nShield** | `kms:plugins:entrust` | Stub — vendor SDK/HSM integration not implemented |
| **Thales Luna** | `kms:plugins:thales-luna` | Stub — vendor SDK/HSM integration not implemented |
| **Utimaco** | `kms:plugins:utimaco` | Stub — vendor SDK/HSM integration not implemented |
| **Venafi** | `kms:plugins:venafi` | Stub — Venafi integration not implemented |
| **PAdES** | `signatures:pades` | Stub — PDF signature skeleton not implemented |

---

## Usage Example

To use a plugin, simply add it as a dependency to your project:

```kotlin
dependencies {
    // DID method plugin
    implementation("org.trustweave:did-plugins-key:0.7.0")

    // Blockchain anchor plugin
    implementation("org.trustweave:anchors-plugins-ethereum:0.7.0")

    // KMS plugin
    implementation("org.trustweave:kms-plugins-aws:0.7.0")
}
```

Plugins are automatically discovered and registered via the SPI (Service Provider Interface) pattern. No extra registration call is required at runtime: providers on the classpath are considered when you build the facade:

```kotlin
import org.trustweave.trust.quickStart
import kotlinx.coroutines.runBlocking
import org.trustweave.trust.TrustWeave

fun main() = runBlocking {
    val trustWeave = TrustWeave.quickStart()
    // SPI-provided KMS / DID / anchor providers participate in resolution during build/quickStart.
}
```

---

## Creating Custom Plugins

If you need to create a custom plugin, see the [Creating Plugins Guide](../contributing/creating-plugins.md) for detailed instructions on implementing:

- DID methods
- Blockchain anchor clients
- Key management services
- Proof generators
- Wallet factories
- Credential services

---

## Related Documentation

- [Integration modules](../how-to/integrations/README.md) — Detailed integration guides for each plugin
- [Plugin implementation roadmap](../contributing/plugin-implementation-roadmap.md) — Current implementation status of all plugins
- [Creating plugins](../contributing/creating-plugins.md) — Guide for implementing custom plugins
- [Plugin lifecycle](advanced/plugin-lifecycle.md) — Managing plugin initialization and lifecycle

