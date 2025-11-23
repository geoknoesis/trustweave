# SPI Interfaces

The SPI (Service Provider Interface) interfaces are shared service and plugin abstractions that
other TrustWeave modules implement or consume. These interfaces are included in `trustweave-common`.

**Note:** SPI interfaces are included in `trustweave-common`. You don't need a separate dependency for SPI functionality. The interfaces are automatically available when you include `trustweave-common` or other TrustWeave modules.

**Result:** SPI interfaces are available through `trustweave-common` so you can register custom DID methods, KMS providers, or blockchain clients without pulling in the higher-level modules.

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
import com.trustweave.spi.services.AdapterLoader
import com.trustweave.spi.PluginRegistry

// Locate DID method service at runtime
val didMethodService = AdapterLoader.didMethodService()

// Register custom plugin
PluginRegistry.register(
    metadata = myPluginMetadata,
    instance = myPlugin
)
```

**What this does:** Loads a DID method provider discovered via the SPI and registers your own plugin instance programmatically.

**Outcome:** Your application can resolve custom adapters at runtime while keeping transitive dependencies clean.

## Dependencies

SPI interfaces are included in `trustweave-common`. All other modules that interact
with service abstractions depend on `trustweave-common` (which includes the SPI interfaces).

## Next Steps

- Explore [`TrustWeave-trust`](trustweave-trust.md) for trust runtime built on top of
  the SPI layer.
- Review [`TrustWeave-common`](trustweave-common.md) to see how credential features
  consume the SPI interfaces.

