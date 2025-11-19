# VeriCore Layout Audit

## 1. Gradle Module Overview

| Module | Responsibility Highlights | Notes |
| --- | --- | --- |
| `vericore-core` | Credential issuance/verification flows, wallet abstractions | Focused on domain logic under `credential.*` |
| `vericore-spi` | Service/adapter interfaces and plugin registry | New home for adapter loader, factories, stubs |
| `vericore-trust` | Trust registry APIs and shared trust-layer contracts | Consumed by DSL/runtime modules |
| `vericore-did` | DID method registry, DID resolution helpers, delegation services | Depends on `vericore-core`, `vericore-spi`, `vericore-kms` |
| `vericore-anchor` | Blockchain anchoring abstractions and default registry | Integrations live in separate modules (`algorand`, `polygon`, `ganache`) with similar structure |
| `vericore-kms` | Key management abstractions & in-memory implementations | Consumed by core credential flows and examples |
| `vericore-json` | JSON helpers and serializers shared across modules | Lightweight but required nearly everywhere |
| `vericore-testkit` | Test doubles (wallets, DID methods), fixture DSLs | Mixes reusable test utilities with demo implementations |
| `vericore-examples` | Scenario-driven sample apps/tests | Currently focused on academic credential workflow |
| `vericore-all` | Aggregated facade combining core + supporting modules | Exposes library entry point (`VeriCore`) |
| `did/plugins/godiddy`, `kms/plugins/waltid`, etc. | Vendor-specific DID/KMS integrations | Each repeats similar adapter plumbing |
| Blockchain adapters (`algorand`, `polygon`, `ganache`) | Provide `BlockchainAnchorClient` implementations | Flat module list; no shared integration base module |

## 2. Key Packages inside `vericore-core`

```
com.geoknoesis.vericore
├── credential.*           # primary issuance, verification, wallet APIs
├── did.delegation         # DID delegation util (still hosted here for now)
└── core                   # shared constants/exceptions
```

Observations:
- `credential.*` combines domain objects with DSL helpers; remaining infrastructure now lives in dedicated modules.
- `did.delegation` is the main non-credential package still residing in `vericore-core`.

## 3. Shared Utilities Living in Feature Modules

- **Adapter loading / Service Locator**: consolidated under `vericore-spi`.
- **Plugin SPI + Registry**: `vericore-spi` now owns metadata/configuration helpers used across modules.
- **Test Helpers**: `vericore-testkit` exports in-memory wallet/DID helpers that some runtime examples rely upon, blurring the boundary between test-code and production usage.
- **Blockchain Integrations**: `vericore-anchor` holds registry abstractions while each chain module duplicates provider boilerplate without a shared base (e.g., `BlockchainIntegrationHelper` sits in `vericore-anchor` but is referenced by multiple adapters).

These findings highlight where responsibilities straddle module boundaries and where additional submodules or clearer package naming could improve discoverability.

