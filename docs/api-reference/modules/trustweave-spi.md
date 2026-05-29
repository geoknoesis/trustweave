---
title: SPI Interfaces
redirect_from:
  - /modules/trustweave-spi/

---

# SPI Interfaces

The SPI (Service Provider Interface) interfaces are shared service and plugin abstractions that
other TrustWeave modules implement or consume. These interfaces are included in `common`.

**Note:** SPI interfaces are included in `common`. You don't need a separate dependency for SPI functionality. The interfaces are automatically available when you include `common` or other TrustWeave modules.

**Result:** SPI interfaces are available through `common` so you can register custom DID methods, KMS providers, or blockchain clients without pulling in the higher-level modules.

## Responsibilities

- **DID methods**: `DidMethodProvider` implementations can be discovered with `ServiceLoader` and registered on `DidMethodRegistry` (see `did-core`).
- **Shared plugin helpers** in `common`: metadata, configuration loading, provider chaining (`org.trustweave.core.plugin`).
- **Factories** wired through `TrustWeave.build { factories(...) }` (for example `WalletFactory` in `org.trustweave.wallet.services`).

## Typical Usage

```kotlin
import kotlinx.coroutines.runBlocking
import org.trustweave.did.registry.DidMethodRegistry
import org.trustweave.testkit.kms.InMemoryKeyManagementService

// Register every DidMethodProvider on the classpath (META-INF/services)
val registry = runBlocking {
    DidMethodRegistry.autoRegister(kms = InMemoryKeyManagementService()).registry
}
```

**What this does:** Uses `ServiceLoader` internally, calls each provider’s `create` for its `supportedMethods`, and registers the resulting `DidMethod` instances (skipping providers whose required environment variables are missing).

**Outcome:** DID method JARs with SPI metadata are picked up automatically; in apps you normally still obtain a registry through `TrustWeave.build { }` rather than constructing this by hand.

## Dependencies

SPI interfaces are included in `common`. All other modules that interact
with service abstractions depend on `common` (which includes the SPI interfaces).

## Next Steps

- Explore [`trust`](trustweave-trust.md) for trust runtime built on top of
  the SPI layer.
- Review [`common`](trustweave-common.md) to see how credential features
  consume the SPI interfaces.

