---
title: Module maturity matrix
nav_order: 310
parent: API Reference
keywords:
  - modules
  - support
  - experimental
  - production
redirect_from:
  - /reference/module-maturity/
  - /api-reference/reference/module-maturity/
---

# Module maturity matrix

TrustWeave is a multi-module repository. **Publishing a JAR to Maven Central does not imply the module is complete or recommended for all production use cases.** This matrix states intended support level for integrators.

| Tier | Meaning |
|------|--------|
| **Supported (GA)** | Intended for production use when configured correctly; breaking API changes follow semver + [Deprecation policy](deprecation-policy.md). |
| **Supported with provider** | Core APIs are stable; you must use a mature KMS/DID/anchor **implementation** you trust. |
| **Experimental** | APIs may change; may contain `TODO` or stub behavior—verify in your environment before production. |

## Core platform (typical SDK dependencies)

| Module / area | Maturity |
|---------------|----------|
| `trust` (facade) | Supported (GA) |
| `credentials:credential-api` | Supported (GA) |
| `did:did-core` | Supported (GA) |
| `kms:kms-core` | Supported (GA) |
| `wallet:wallet-core` | Supported (GA) |
| `anchors:anchor-core` | Supported (GA) |
| `contract` | Supported with provider |
| `common` | Supported (GA) |

## In-memory and test support

| Module | Maturity |
|--------|----------|
| `testkit` | Supported for **tests and local dev**—not a substitute for production KMS or ledger backends. |

## Services, servers and signature formats

These ship in the BOM but are **not** covered by the plugin rule below. Test counts are from the
committed suite and are given so you can calibrate your own review effort — a low count is not a
defect claim, but it is a reason to run your own integration tests before depending on a module.

| Module | Tests | Maturity |
|--------|-------|----------|
| `did:registrar` | 8 | Experimental |
| `did:registrar-server-ktor` | 0 | **Experimental — untested**; verify in your environment |
| `did:registrar-server-spring` | 0 | **Experimental — untested**; verify in your environment |
| `credentials:vc-api-server` | 7 | Experimental |
| `credentials:oidc4vci-server` | 14 | Experimental |
| `trust-registry:trust-registry-core` | 4 | Experimental |
| `trust-registry:trust-registry-server` | 12 | Experimental |
| `wallet:wallet-services` | 0 | **Experimental — untested**; verify in your environment |
| `distribution:all` | n/a | Dependency aggregate only — carries no logic of its own |

### ETSI / eIDAS signature formats

`signatures:*` implements advanced electronic signature formats. Treat every module here as
**Experimental**: the formats are large, conformance is defined by ETSI test suites TrustWeave does
not yet run, and the suites below exercise round-trips rather than full profile conformance. Do not
rely on these for a regulated eIDAS deployment without your own conformance assessment.

| Module | Tests | Notes |
|--------|-------|-------|
| `signatures:jades` | 17 | ETSI TS 119 182-1 (JSON) — also registered as a `ProofEngine` |
| `signatures:trust-lists` | 23 | EU trusted-list handling |
| `signatures:cades` | 8 | ETSI EN 319 122 (CMS) |
| `signatures:tsa-core` | 8 | RFC 3161 timestamping |
| `signatures:etsi-validation` | 7 | Validation helpers |
| `signatures:xades` | 3 | ETSI EN 319 132 (XML) |
| `signatures:pades` | 2 | ETSI EN 319 142 (PDF) |

## Plugins and integrations

Individual plugins under `did/plugins/*`, `kms/plugins/*`, `anchors/plugins/*`, `credentials/plugins/*`, etc., vary widely. Treat each as **Experimental** unless its own README states otherwise and you have run integration tests against your target environment. Many third-party or ledger-specific modules still contain stub or partial implementations (`TODO` in source).

Newer plugin families that ship in `settings.gradle.kts` but are evolving:

- DID methods: `did:plugins:ebsi`
- Credentials formats / proofs: `credentials:plugins:mdl`, `credentials:plugins:eudiw`
- Exchange / discovery: `credentials:plugins:siop`, `credentials:plugins:presentation-exchange`, `credentials:plugins:openid-federation`
- Status lists: `credentials:plugins:status-list:bitstring`, `credentials:plugins:status-list:token`, `credentials:plugins:status-list:publishing`, `credentials:plugins:status-list:server`
- Agent authorization: `credentials:plugins:verifiable-intent`

Until each carries its own GA notice, treat the above as **Experimental**.

**Rule of thumb:** If you did not run your own integration and security review of a plugin, do not treat it as production-ready solely because it is on the classpath.

## Gradle publishing

The root build applies `maven-publish` to Kotlin/JVM library subprojects so artifacts share consistent POM metadata. **Artifact availability does not override the maturity table above.** When in doubt, prefer the core modules plus one well-tested plugin combination.

## Related docs

- [Production integration checklist](../getting-started/production-integration-checklist.md)
- [Result types guide](result-types-guide.md)
- Security policy (repository root `SECURITY.md`)
