---
title: Module maturity matrix
nav_order: 3
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

## Plugins and integrations

Individual plugins under `did/plugins/*`, `kms/plugins/*`, `anchors/plugins/*`, `credentials/plugins/*`, etc., vary widely. Treat each as **Experimental** unless its own README states otherwise and you have run integration tests against your target environment. Many third-party or ledger-specific modules still contain stub or partial implementations (`TODO` in source).

Newer plugin families that ship in `settings.gradle.kts` but are evolving:

- DID methods: `did:plugins:ebsi`
- Credentials formats / proofs: `credentials:plugins:bbs`, `credentials:plugins:mdl`, `credentials:plugins:eudiw`
- Exchange / discovery: `credentials:plugins:siop`, `credentials:plugins:presentation-exchange`, `credentials:plugins:openid-federation`
- Status lists: `credentials:plugins:status-list:bitstring`, `credentials:plugins:status-list:token`, `credentials:plugins:status-list:publishing`, `credentials:plugins:status-list:server`

Until each carries its own GA notice, treat the above as **Experimental**.

**Rule of thumb:** If you did not run your own integration and security review of a plugin, do not treat it as production-ready solely because it is on the classpath.

## Gradle publishing

The root build applies `maven-publish` to Kotlin/JVM library subprojects so artifacts share consistent POM metadata. **Artifact availability does not override the maturity table above.** When in doubt, prefer the core modules plus one well-tested plugin combination.

## Related docs

- [Production integration checklist](../../tutorials/getting-started/production-integration-checklist.md)
- [Result types guide](result-types-guide.md)
- Security policy (repository root `SECURITY.md`)
