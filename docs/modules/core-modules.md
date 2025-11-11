# Core Modules

VeriCore is organised into small, composable modules. The list below explains when to add each module and what responsibility it carries so you can mix and match only what you need in your build.

## Module Overview

- **vericore-spi** – Shared plugin/service abstractions and adapter loader utilities. Add this when you create custom issuers, DID methods, or blockchain connectors.
- **vericore-trust** – Trust registry interfaces and runtime helpers. Needed when modelling trust anchors or resolving multi-party provenance.
- **[vericore-core](vericore-core.md)** – Credential-domain APIs, DSLs, and wallet utilities. Most applications compile against this module.
- **[vericore-json](vericore-json.md)** – JSON canonicalisation and digest helpers. Pair this with anchoring or signing modules to ensure deterministic hashing.
- **[vericore-kms](vericore-kms.md)** – Key management abstractions and helpers. Required whenever you integrate HSMs or cloud KMS backends.
- **[vericore-did](vericore-did.md)** – DID and DID document management with pluggable DID methods. Enables DID creation/resolution across modules.
- **[vericore-anchor](vericore-anchor.md)** – Blockchain anchoring abstraction with chain-agnostic interfaces. Use it to notarise digests on Algorand, Polygon, etc.
- **[vericore-testkit](vericore-testkit.md)** – In-memory mocks for every SPI. Import this in unit tests or quick-start prototypes.

## Module Dependencies

```text
vericore-spi (no dependencies)
vericore-trust → vericore-spi
vericore-core → vericore-spi, vericore-trust
vericore-json → vericore-core
vericore-kms → vericore-core
vericore-did → vericore-core, vericore-spi, vericore-kms
vericore-anchor → vericore-core, vericore-spi, vericore-json
vericore-testkit → vericore-core, vericore-spi, vericore-trust, vericore-did, vericore-kms, vericore-anchor
```

When you need a specific building block, add it to `dependencies` explicitly:

```kotlin
dependencies {
    implementation("com.geoknoesis.vericore:vericore-core:1.0.0-SNAPSHOT")
    implementation("com.geoknoesis.vericore:vericore-did:1.0.0-SNAPSHOT")
}
```

**Result:** Gradle resolves only the modules you reference, keeping downstream artefacts slim while still exposing the full API surface you need.

## Next Steps

- Explore individual module documentation linked above for detailed APIs and usage snippets.
- Check out [Integration Modules](../integrations/README.md) when you connect to external providers.
- See [Examples](../examples/README.md) to understand how the modules compose in real projects.

