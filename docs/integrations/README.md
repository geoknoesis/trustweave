# Integration Modules

VeriCore ships optional integration modules that plug providers into the SPI
and trust/runtime layers. They can now share infrastructure exposed by
`vericore-spi` and `vericore-trust`.

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

All blockchain clients adopt the shared SPI abstractions:

- `com.geoknoesis.vericore.chains:algorand` – production-ready anchoring for Algorand mainnet/testnet. See [Algorand Integration Guide](algorand.md).
- `com.geoknoesis.vericore.chains:ethereum` – Ethereum mainnet anchoring with Sepolia testnet support. See [Ethereum Anchor Guide](ethereum-anchor.md).
- `com.geoknoesis.vericore.chains:base` – Base (Coinbase L2) anchoring with fast confirmations and lower fees. See [Base Anchor Guide](base-anchor.md).
- `com.geoknoesis.vericore.chains:arbitrum` – Arbitrum One (largest L2 by TVL) anchoring with EVM compatibility. See [Arbitrum Anchor Guide](arbitrum-anchor.md).
- `com.geoknoesis.vericore.chains:polygon` – Polygon PoS anchoring with the shared SPI plumbing. See [Polygon DID Integration Guide](polygon-did.md) for Polygon DID method; blockchain anchoring follows similar patterns.
- `com.geoknoesis.vericore.chains:ganache` – Local developer anchoring using Ganache/Testcontainers. See [Integration Modules](README.md#blockchain-anchor-integrations) for usage examples.

These modules will be consolidated over time into a single
`anchor-integrations` family that reuses common adapter plumbing.

## DID Method Integrations

VeriCore provides comprehensive DID method implementations following the W3C DID specification:

### High Priority Methods

- `com.geoknoesis.vericore.did:key` – Native did:key implementation, the most widely-used DID method. Zero external dependencies, portable public key-based DIDs. See [Key DID Integration Guide](key-did.md).
- `com.geoknoesis.vericore.did:web` – Web DID method for HTTP/HTTPS-based resolution. Full W3C spec compliance with domain and path-based identifiers. See [Web DID Integration Guide](web-did.md).
- `com.geoknoesis.vericore.did:ethr` – Ethereum DID method with blockchain anchoring support. Integrates with Ethereum mainnet and testnets (Sepolia). See [Ethereum DID Integration Guide](ethr-did.md).
- `com.geoknoesis.vericore.did:ion` – Microsoft ION DID method using Sidetree protocol. Bitcoin-anchored DIDs with ION node integration. See [ION DID Integration Guide](ion-did.md).

### Medium Priority Methods

- `com.geoknoesis.vericore.did:polygon` – Polygon DID method reusing Ethereum registry pattern. Lower transaction costs than Ethereum mainnet. See [Polygon DID Integration Guide](polygon-did.md).
- `com.geoknoesis.vericore.did:sol` – Solana DID method with program integration. Account-based storage on Solana blockchain. See [Solana DID Integration Guide](sol-did.md).
- `com.geoknoesis.vericore.did:peer` – Peer-to-peer DID method. No external registry required, supports numalgo 0, 1, and 2. See [Peer DID Integration Guide](peer-did.md).

### Lower Priority Methods

- `com.geoknoesis.vericore.did:jwk` – W3C-standard did:jwk method using JSON Web Keys directly. Standardized approach with JWK format. See [JWK DID Integration Guide](jwk-did.md).
- `com.geoknoesis.vericore.did:ens` – Ethereum Name Service (ENS) resolver integration. Human-readable DID identifiers mapped to Ethereum addresses. See [ENS DID Integration Guide](ens-did.md).
- `com.geoknoesis.vericore.did:plc` – Personal Linked Container (PLC) DID method for AT Protocol. Distributed registry with HTTP-based resolution. See [PLC DID Integration Guide](plc-did.md).
- `com.geoknoesis.vericore.did:cheqd` – Cheqd network DID method with payment-enabled features. Cosmos-based blockchain for identity and credentials. See [Cheqd DID Integration Guide](cheqd-did.md).

### Other DID / KMS Integrations

- `com.geoknoesis.vericore.did:godiddy` – HTTP bridge to DID/VC services exposed by the GoDiddy stack. See [GoDiddy Integration Guide](godiddy.md).
- `com.geoknoesis.vericore.kms:waltid` – DID and KMS providers from the walt.id ecosystem. See [walt.id Integration Guide](waltid.md).
- `com.geoknoesis.vericore.kms:aws` – AWS Key Management Service integration with full algorithm support (Ed25519, secp256k1, P-256/P-384/P-521, RSA). See [AWS KMS Integration Guide](aws-kms.md).
- `com.geoknoesis.vericore.kms:azure` – Azure Key Vault integration with algorithm support (secp256k1, P-256/P-384/P-521, RSA). Supports Managed Identity and Service Principal authentication. See [Azure Key Vault Integration Guide](azure-kms.md).
- `com.geoknoesis.vericore.kms:google` – Google Cloud Key Management Service integration with algorithm support (secp256k1, P-256/P-384, RSA). Supports Application Default Credentials and service account authentication. See [Google Cloud KMS Integration Guide](google-kms.md).
- `com.geoknoesis.vericore.kms:hashicorp` – HashiCorp Vault Transit engine integration with algorithm support (Ed25519, secp256k1, P-256/P-384/P-521, RSA). Supports token and AppRole authentication. See [HashiCorp Vault KMS Integration Guide](hashicorp-vault-kms.md).
- `com.geoknoesis.vericore.chains:indy` – Hyperledger Indy DID/KMS integration (permissioned networks). See [Integration Modules](README.md#other-did--kms-integrations) for usage patterns.

Each module implements the SPI interfaces (`DidMethodService`, `KmsService`,
etc.) supplied by `vericore-spi`.

## Next Steps

- When authoring a new integration, depend on `vericore-spi` instead of
  `vericore-core` and expose providers via the adapter loader.
- Trust registries should target `vericore-trust` and optionally wrap
  themselves using `TrustRegistryServiceAdapter`.
- Future work will introduce a shared `vericore-anchor-integrations` module to
  reduce duplicate registration logic across blockchain clients.

## Creating Custom Plugins

For detailed guidance on implementing custom plugins, see:

- **[Creating Plugins Guide](../contributing/creating-plugins.md)** - Complete guide with examples for all plugin interfaces
  - DID methods
  - Blockchain anchor clients
  - Proof generators
  - Key management services
  - Credential services
  - Wallet factories

