---
title: Core Modules
nav_order: 1
parent: Reference
---

# Core Modules

TrustWeave is organised into small, composable modules. The list below explains when to add each module and what responsibility it carries so you can mix and match only what you need in your build.

## Module Overview

- **SPI Interfaces** – Shared plugin/service abstractions and adapter loader utilities are included in `trustweave-common`. SPI functionality is available when you include `trustweave-common` or other TrustWeave modules.
- **trustweave-trust** – Trust registry interfaces and runtime helpers. Needed when modelling trust anchors or resolving multi-party provenance.
- **[trustweave-common](trustweave-common.md)** – Domain-agnostic core infrastructure. Includes plugin system (registry, metadata, configuration, provider chains), structured error handling (13+ granular error types), JSON canonicalization, digest computation, and Result utilities. Most applications compile against this module.
- **[trustweave-kms](trustweave-kms.md)** – Key management abstractions and helpers. Required whenever you integrate HSMs or cloud KMS backends.
- **[trustweave-did](trustweave-did.md)** – DID and DID document management with pluggable DID methods. Enables DID creation/resolution across modules.
- **[trustweave-did-registrar](trustweave-did-registrar.md)** – DID Registrar implementations for creating, updating, and deactivating DIDs through Universal Registrar services or local KMS.
- **[trustweave-did-registrar-server-ktor](trustweave-did-registrar-server.md)** – Universal Registrar HTTP server implementation (Ktor) for hosting your own registrar service.
- **[trustweave-did-registrar-server-spring](trustweave-did-registrar-server-spring.md)** – Universal Registrar HTTP server implementation (Spring Boot) for hosting your own registrar service.
- **[trustweave-anchor](trustweave-anchor.md)** – Blockchain anchoring abstraction with chain-agnostic interfaces. Use it to notarise digests on Algorand, Polygon, etc.
- **[trustweave-contract](trustweave-contract.md)** – Smart Contract abstraction for executable agreements with verifiable credentials and blockchain anchoring.
- **[trustweave-testkit](trustweave-testkit.md)** – In-memory mocks for every SPI. Import this in unit tests or quick-start prototypes.

## Module Dependencies

```text
trustweave-trust → trustweave-common (includes SPI)
trustweave-common (includes SPI interfaces, JSON utilities, plugin infrastructure)
trustweave-kms → trustweave-common
trustweave-did → trustweave-common, trustweave-kms
trustweave-did-registrar → trustweave-did, trustweave-kms
trustweave-did-registrar-server-ktor → trustweave-did-registrar, trustweave-did
trustweave-did-registrar-server-spring → trustweave-did-registrar, trustweave-did
trustweave-anchor → trustweave-common
trustweave-contract → trustweave-common, trustweave-anchor, trustweave-did
trustweave-testkit → trustweave-common, trustweave-trust, trustweave-did, trustweave-kms, trustweave-anchor
```

When you need a specific building block, add it to `dependencies` explicitly:

```kotlin
dependencies {
    implementation("org.trustweave:trustweave-common:1.0.0-SNAPSHOT")
    implementation("org.trustweave:trustweave-did:1.0.0-SNAPSHOT")
}
```

**Result:** Gradle resolves only the modules you reference, keeping downstream artefacts slim while still exposing the full API surface you need.

## Next Steps

- Explore individual module documentation linked above for detailed APIs and usage snippets.
- Check out [Integration Modules](../integrations/README.md) when you connect to external providers.

