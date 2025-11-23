# Core Modules

TrustWeave is organised into small, composable modules. The list below explains when to add each module and what responsibility it carries so you can mix and match only what you need in your build.

## Module Overview

- **SPI Interfaces** – Shared plugin/service abstractions and adapter loader utilities are included in `trustweave-common`. SPI functionality is available when you include `trustweave-common` or other TrustWeave modules.
- **TrustWeave-trust** – Trust registry interfaces and runtime helpers. Needed when modelling trust anchors or resolving multi-party provenance.
- **[TrustWeave-common](trustweave-common.md)** – Credential-domain APIs, DSLs, and wallet utilities. Most applications compile against this module.
- **[TrustWeave-json](trustweave-json.md)** – JSON canonicalisation and digest helpers. Pair this with anchoring or signing modules to ensure deterministic hashing.
- **[TrustWeave-kms](trustweave-kms.md)** – Key management abstractions and helpers. Required whenever you integrate HSMs or cloud KMS backends.
- **[TrustWeave-did](trustweave-did.md)** – DID and DID document management with pluggable DID methods. Enables DID creation/resolution across modules.
- **[TrustWeave-anchor](trustweave-anchor.md)** – Blockchain anchoring abstraction with chain-agnostic interfaces. Use it to notarise digests on Algorand, Polygon, etc.
- **[TrustWeave-contract](trustweave-contract.md)** – Smart Contract abstraction for executable agreements with verifiable credentials and blockchain anchoring.
- **[TrustWeave-testkit](trustweave-testkit.md)** – In-memory mocks for every SPI. Import this in unit tests or quick-start prototypes.

## Module Dependencies

```text
TrustWeave-trust → TrustWeave-common (includes SPI)
TrustWeave-common (includes SPI interfaces)
TrustWeave-json → TrustWeave-common
TrustWeave-kms → TrustWeave-common
TrustWeave-did → TrustWeave-common, TrustWeave-kms
TrustWeave-anchor → TrustWeave-common, TrustWeave-json
TrustWeave-contract → TrustWeave-common, TrustWeave-json, TrustWeave-anchor, TrustWeave-did
TrustWeave-testkit → TrustWeave-common, TrustWeave-trust, TrustWeave-did, TrustWeave-kms, TrustWeave-anchor
```

When you need a specific building block, add it to `dependencies` explicitly:

```kotlin
dependencies {
    implementation("com.trustweave:trustweave-common:1.0.0-SNAPSHOT")
    implementation("com.trustweave:trustweave-did:1.0.0-SNAPSHOT")
}
```

**Result:** Gradle resolves only the modules you reference, keeping downstream artefacts slim while still exposing the full API surface you need.

## Next Steps

- Explore individual module documentation linked above for detailed APIs and usage snippets.
- Check out [Integration Modules](../integrations/README.md) when you connect to external providers.

