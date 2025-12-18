---
title: Proposed Layout Improvements
---

# Proposed Layout Improvements

## 1. Module-Level Adjustments

### A. Extract Cross-Cutting Infrastructure ✅
- **Module `TrustWeave-trust`**
  Trust-layer runtime lives here; `TrustWeave-core` now focuses on credential domain APIs.
- **Module `TrustWeave-spi`**
  Plugin/SPI definitions and adapter loaders now reside here; other modules depend on it.
- **Refined `TrustWeave-core` scope**
  Contains credential issuance, verification, wallet DSLs, and models only.

### B. Group Integrations
- Consolidate blockchain adapters (`chains/plugins/algorand`, `chains/plugins/polygon`, `chains/plugins/ganache`) into a `TrustWeave-anchor-integrations` composite module using Gradle source sets, or at minimum introduce a shared `anchor-integrations` package to hold common helper code.
- Apply a similar pattern for DID/KMS provider modules (e.g., `godiddy`, `waltid`) to reduce boilerplate and surface a clear extension point.

## 2. Package-Level Realignment

| Current Package | Proposed Destination | Rationale |
| --- | --- | --- |
| `com.trustweave.core.services` | ✅ `TrustWeave-spi` (`com.trustweave.spi.services`) | Adapter loading and services migrated |
| `com.trustweave.trust` | ✅ `TrustWeave-trust` | Trust DSL runtime relocated |
| `com.trustweave.did.delegation` | `TrustWeave-did.delegation` | Keeps DID-specific flows alongside other DID tooling |
| `com.trustweave.credential.dsl` | Split between `TrustWeave-core.credential.dsl` (domain builders) and `TrustWeave-trust.dsl` (trust-layer wiring) | Clarifies boundary between credential manipulation and runtime configuration |
| `com.trustweave.credential.wallet` | Consider `wallet` submodule if wallet providers grow further | Provides space for wallet registry replacements without polluting credential root package |

## 3. Documentation & Navigation
- Introduce a top-level `docs/modules/overview.md` linking each module to its responsibilities and entry points.
- Update existing module READMEs (e.g., `TrustWeave-core/README.md`, `TrustWeave-did/README.md`) to reflect the refined scope after extraction.
- Provide a migration note in `docs/migration/` describing the SPI module and trust module split for third-party integrators.

