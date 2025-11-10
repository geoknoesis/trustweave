# Integration Modules

VeriCore ships optional integration modules that plug providers into the SPI
and trust/runtime layers. They can now share infrastructure exposed by
`vericore-spi` and `vericore-trust`.

## Blockchain Anchor Integrations

All blockchain clients adopt the shared SPI abstractions:

- `vericore-algorand`
- `vericore-polygon`
- `vericore-ganache`

These modules will be consolidated over time into a single
`anchor-integrations` family that reuses common adapter plumbing.

## DID / KMS Integrations

- `vericore-godiddy`
- `vericore-waltid`
- `vericore-indy`

Each module implements the SPI interfaces (`DidMethodService`, `KmsService`,
etc.) supplied by `vericore-spi`.

## Next Steps

- When authoring a new integration, depend on `vericore-spi` instead of
  `vericore-core` and expose providers via the adapter loader.
- Trust registries should target `vericore-trust` and optionally wrap
  themselves using `TrustRegistryServiceAdapter`.
- Future work will introduce a shared `vericore-anchor-integrations` module to
  reduce duplicate registration logic across blockchain clients.

