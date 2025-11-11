# vericore-spi

The `vericore-spi` module hosts the shared service and plugin abstractions that
other VeriCore modules implement or consume.

```kotlin
dependencies {
    implementation("com.geoknoesis.vericore:vericore-spi:1.0.0-SNAPSHOT")
}
```

**Result:** Gradle exposes the plugin interfaces so you can register custom DID methods, KMS providers, or blockchain clients without pulling in the higher-level modules.

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
import com.geoknoesis.vericore.spi.services.AdapterLoader
import com.geoknoesis.vericore.spi.PluginRegistry

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

The module has no intra-project dependencies. All other modules that interact
with service abstractions depend on `vericore-spi`.

## Next Steps

- Explore [`vericore-trust`](vericore-trust.md) for trust runtime built on top of
  the SPI layer.
- Review [`vericore-core`](vericore-core.md) to see how credential features
  consume the SPI interfaces.

