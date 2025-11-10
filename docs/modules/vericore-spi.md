# vericore-spi

The `vericore-spi` module hosts the shared service and plugin abstractions that
other VeriCore modules implement or consume.

## Responsibilities

- Adapter loading utilities (`AdapterLoader`) for discovering runtime providers
  without hard dependencies.
- Service interfaces for DID methods, KMS, wallet factories, and blockchain
  registries.
- Default stub implementations used in documentation snippets and low-footprint
  environments.
- Plugin registry infrastructure (metadata, configuration loader, provider chain).

## Typical Usage

```kotlin
import io.geoknoesis.vericore.spi.services.AdapterLoader
import io.geoknoesis.vericore.spi.PluginRegistry

// Locate DID method service at runtime
val didMethodService = AdapterLoader.didMethodService()

// Register custom plugin
PluginRegistry.register(
    metadata = myPluginMetadata,
    instance = myPlugin
)
```

## Dependencies

The module has no intra-project dependencies. All other modules that interact
with service abstractions depend on `vericore-spi`.

## Next Steps

- Explore [`vericore-trust`](vericore-trust.md) for trust runtime built on top of
  the SPI layer.
- Review [`vericore-core`](vericore-core.md) to see how credential features
  consume the SPI interfaces.

