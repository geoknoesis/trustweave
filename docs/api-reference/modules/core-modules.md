---
title: Core Modules
nav_order: 1
parent: Reference
redirect_from:
  - /modules/core-modules/

---

# Core Modules

TrustWeave is organized into small, composable modules. The list below explains when to add each module and what responsibility it carries so you can mix and match only what you need in your build.

## Getting Started

For most users, start with `distribution:all` which includes all core modules:

```kotlin
dependencies {
    implementation("org.trustweave:distribution-all:0.6.0")
}
```

For production deployments requiring minimal bundle size, use individual modules as shown below.

## Module Overview

- **SPI Interfaces** – Shared plugin/service abstractions and adapter loader utilities are included in `common`. SPI functionality is available when you include `common` or other TrustWeave modules.
- **[trust](trustweave-trust.md)** – Main facade (`TrustWeave`) and trust registry interfaces. Needed when wiring services together or modelling trust anchors.
- **[common](trustweave-common.md)** – Domain-agnostic core infrastructure. Includes plugin system (registry, metadata, configuration, provider chains), structured error handling, JSON canonicalization, digest computation, and Result utilities. Most applications compile against this module.
- **[kms:kms-core](trustweave-kms.md)** – Key management abstractions and helpers. Required whenever you integrate HSMs or cloud KMS backends.
- **[did:did-core](trustweave-did.md)** – DID and DID document management with pluggable DID methods. Enables DID creation/resolution across modules.
- **[did:registrar](trustweave-did-registrar.md)** – DID Registrar implementations for creating, updating, and deactivating DIDs through Universal Registrar services or local KMS.
- **[did:registrar-server-ktor](trustweave-did-registrar-server.md)** – Universal Registrar HTTP server implementation (Ktor) for hosting your own registrar service.
- **`did:registrar-server-spring`** – Universal Registrar HTTP server implementation (Spring Boot) for hosting your own registrar service.

> **TODO:** A dedicated reference page for `did:registrar-server-spring` is not yet published.
- **[anchors:anchor-core](trustweave-anchor.md)** – Blockchain anchoring abstraction with chain-agnostic interfaces. Use it to notarise digests on Algorand, Polygon, etc.
- **[contract](trustweave-contract.md)** – Smart Contract abstraction for executable agreements with verifiable credentials and blockchain anchoring.
- **[testkit](trustweave-testkit.md)** – In-memory mocks for every SPI. Import this in unit tests or quick-start prototypes.

## Module Dependencies

```text
trust → common (includes SPI)
common (includes SPI interfaces, JSON utilities, plugin infrastructure)
kms:kms-core → common
did:did-core → common, kms:kms-core
did:registrar → did:did-core, kms:kms-core
did:registrar-server-ktor → did:registrar, did:did-core
did:registrar-server-spring → did:registrar, did:did-core
anchors:anchor-core → common
contract → common, anchors:anchor-core, did:did-core
testkit → common, trust, did:did-core, kms:kms-core, anchors:anchor-core
```

When you need a specific building block, add it to `dependencies` explicitly:

```kotlin
dependencies {
    implementation("org.trustweave:common:0.6.0")
    implementation("org.trustweave:did-did-core:0.6.0")
}
```

**Result:** Gradle resolves only the modules you reference, keeping downstream artefacts slim while still exposing the full API surface you need.

## Next Steps

- Explore individual module documentation linked above for detailed APIs and usage snippets.
- Check out [Integration Modules](../../how-to/integrations/README.md) when you connect to external providers.

