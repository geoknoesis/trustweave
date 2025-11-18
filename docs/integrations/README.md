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

## DID / KMS Integrations

- `vericore-godiddy` – HTTP bridge to DID/VC services exposed by the GoDiddy stack.
- `vericore-waltid` – DID and KMS providers from the walt.id ecosystem.
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

