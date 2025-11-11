# Proposed Layout Improvements

## 1. Module-Level Adjustments

### A. Extract Cross-Cutting Infrastructure ✅
- **Module `vericore-trust`**  
  Trust-layer runtime lives here; `vericore-core` now focuses on credential domain APIs.
- **Module `vericore-spi`**  
  Plugin/SPI definitions and adapter loaders now reside here; other modules depend on it.
- **Refined `vericore-core` scope**  
  Contains credential issuance, verification, wallet DSLs, and models only.

### B. Group Integrations
- Consolidate blockchain adapters (`vericore-algorand`, `vericore-polygon`, `vericore-ganache`) into a `vericore-anchor-integrations` composite module using Gradle source sets, or at minimum introduce a shared `anchor-integrations` package to hold common helper code.  
- Apply a similar pattern for DID/KMS provider modules (e.g., `godiddy`, `waltid`) to reduce boilerplate and surface a clear extension point.

## 2. Package-Level Realignment

| Current Package | Proposed Destination | Rationale |
| --- | --- | --- |
| `com.geoknoesis.vericore.core.services` | ✅ `vericore-spi` (`com.geoknoesis.vericore.spi.services`) | Adapter loading and services migrated |
| `com.geoknoesis.vericore.trust` | ✅ `vericore-trust` | Trust DSL runtime relocated |
| `com.geoknoesis.vericore.did.delegation` | `vericore-did.delegation` | Keeps DID-specific flows alongside other DID tooling |
| `com.geoknoesis.vericore.credential.dsl` | Split between `vericore-core.credential.dsl` (domain builders) and `vericore-trust.dsl` (trust-layer wiring) | Clarifies boundary between credential manipulation and runtime configuration |
| `com.geoknoesis.vericore.credential.wallet` | Consider `wallet` submodule if wallet providers grow further | Provides space for wallet registry replacements without polluting credential root package |

## 3. Documentation & Navigation
- Introduce a top-level `docs/modules/overview.md` linking each module to its responsibilities and entry points.
- Update existing module READMEs (e.g., `vericore-core/README.md`, `vericore-did/README.md`) to reflect the refined scope after extraction.
- Provide a migration note in `docs/migration/` describing the SPI module and trust module split for third-party integrators.

