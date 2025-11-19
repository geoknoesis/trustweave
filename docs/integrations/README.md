# Integration Modules

VeriCore ships optional integration modules that plug providers into the SPI
and trust/runtime layers. They can now share infrastructure exposed by
`vericore-spi` and `vericore-trust`.

Add an integration when you need a concrete provider:

```kotlin
dependencies {
    implementation("com.geoknoesis.vericore:vericore-algorand:1.0.0-SNAPSHOT")
}
```

**Result:** Gradle brings in the Algorand client plus its SPI adapter so your application can anchor digests on Algorand networks with zero additional wiring.

## Blockchain Anchor Integrations

All blockchain clients adopt the shared SPI abstractions:

- `vericore-algorand` – production-ready anchoring for Algorand mainnet/testnet.
- `vericore-polygon` – Polygon PoS anchoring with the shared SPI plumbing.
- `vericore-ganache` – Local developer anchoring using Ganache/Testcontainers.

These modules will be consolidated over time into a single
`anchor-integrations` family that reuses common adapter plumbing.

## DID Method Integrations

VeriCore provides comprehensive DID method implementations following the W3C DID specification:

### High Priority Methods

- `vericore-did-web` – Web DID method for HTTP/HTTPS-based resolution. Full W3C spec compliance with domain and path-based identifiers. See [Web DID Integration Guide](web-did.md).
- `vericore-did-ethr` – Ethereum DID method with blockchain anchoring support. Integrates with Ethereum mainnet and testnets (Sepolia). See [Ethereum DID Integration Guide](ethr-did.md).
- `vericore-did-ion` – Microsoft ION DID method using Sidetree protocol. Bitcoin-anchored DIDs with ION node integration. See [ION DID Integration Guide](ion-did.md).

### Medium Priority Methods

- `vericore-did-polygon` – Polygon DID method reusing Ethereum registry pattern. Lower transaction costs than Ethereum mainnet. See [Polygon DID Integration Guide](polygon-did.md).
- `vericore-did-sol` – Solana DID method with program integration. Account-based storage on Solana blockchain. See [Solana DID Integration Guide](sol-did.md).
- `vericore-did-peer` – Peer-to-peer DID method. No external registry required, supports numalgo 0, 1, and 2. See [Peer DID Integration Guide](peer-did.md).

### Lower Priority Methods

- `vericore-did-ens` – Ethereum Name Service (ENS) resolver integration. Human-readable DID identifiers mapped to Ethereum addresses. See [ENS DID Integration Guide](ens-did.md).
- `vericore-did-plc` – Personal Linked Container (PLC) DID method for AT Protocol. Distributed registry with HTTP-based resolution. See [PLC DID Integration Guide](plc-did.md).
- `vericore-did-cheqd` – Cheqd network DID method with payment-enabled features. Cosmos-based blockchain for identity and credentials. See [Cheqd DID Integration Guide](cheqd-did.md).

### Other DID / KMS Integrations

- `vericore-godiddy` – HTTP bridge to DID/VC services exposed by the GoDiddy stack.
- `vericore-waltid` – DID and KMS providers from the walt.id ecosystem.
- `vericore-aws-kms` – AWS Key Management Service integration with full algorithm support (Ed25519, secp256k1, P-256/P-384/P-521, RSA). See [AWS KMS Integration Guide](aws-kms.md).
- `vericore-azure-kms` – Azure Key Vault integration with algorithm support (secp256k1, P-256/P-384/P-521, RSA). Supports Managed Identity and Service Principal authentication. See [Azure Key Vault Integration Guide](azure-kms.md).
- `vericore-google-kms` – Google Cloud Key Management Service integration with algorithm support (secp256k1, P-256/P-384, RSA). Supports Application Default Credentials and service account authentication. See [Google Cloud KMS Integration Guide](google-kms.md).
- `vericore-hashicorp-kms` – HashiCorp Vault Transit engine integration with algorithm support (Ed25519, secp256k1, P-256/P-384/P-521, RSA). Supports token and AppRole authentication. See [HashiCorp Vault KMS Integration Guide](hashicorp-vault-kms.md).
- `vericore-indy` – Hyperledger Indy DID/KMS integration (permissioned networks).

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

