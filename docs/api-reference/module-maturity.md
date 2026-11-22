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
| **Supported (GA)** | Intended for production use when configured correctly; breaking API changes follow semver + [Deprecation policy](deprecation-policy.md). Marked `supported` in [assessed capabilities](assessed-capabilities.md). |
| **Supported with provider** | Core APIs are stable; you must use a mature KMS/DID/anchor **implementation** you trust. |
| **Experimental** | APIs may change; may contain `TODO` or partial behavior—verify in your environment before production. Marked `experimental` in [assessed capabilities](assessed-capabilities.md). |
| **Stub (not published)** | Unimplemented scaffolding whose operations throw `UnsupportedOperationException` / `InvalidOperation`. Excluded from `distribution-bom` and Maven Central publishing. Marked `stub` in [assessed capabilities](assessed-capabilities.md). |

API support labels do not certify production deployment or wallet custody. See
[provider deployment and custody profiles](provider-deployment-profiles.md) for
the assessed policy and its enforcement boundaries.

## Core platform (7 GA modules)

These 7 core platform modules are **Supported (GA)** and marked `supported` in `trustweave-capabilities.json` and [assessed capabilities](assessed-capabilities.md):

| Module / area | Artifact ID | Maturity |
|---------------|-------------|----------|
| `trust` (facade) | `org.trustweave:trust` | Supported (GA) |
| `credentials:credential-api` | `org.trustweave:credentials-credential-api` | Supported (GA) |
| `did:did-core` | `org.trustweave:did-did-core` | Supported (GA) |
| `kms:kms-core` | `org.trustweave:kms-kms-core` | Supported (GA) |
| `wallet:wallet-core` | `org.trustweave:wallet-wallet-core` | Supported (GA) |
| `anchors:anchor-core` | `org.trustweave:anchors-anchor-core` | Supported (GA) |
| `common` | `org.trustweave:common` | Supported (GA) |

Additional core-adjacent modules:

| Module / area | Artifact ID | Maturity |
|---------------|-------------|----------|
| `contract` | `org.trustweave:contract` | Supported with provider (experimental) |
| `observability` | `org.trustweave:observability` | Experimental |
| `distribution:all` | `org.trustweave:distribution-all` | Distribution aggregate (bundles the 7 core GA modules, `contract`, `did:plugins:key`, and `kms:plugins:inmemory` for quickstart) |
| `distribution:bom` | `org.trustweave:distribution-bom` | Bill of Materials (`java-platform`) aligning versions of all published modules |

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
| `did:registrar` | 38 | Experimental |
| `did:registrar-server-ktor` | 18 | Experimental |
| `did:registrar-server-spring` | 22 | Experimental |
| `credentials:vc-api-server` | 29 | Experimental |
| `credentials:oidc4vci-server` | 71 | Experimental |
| `credentials:avp-authorization-server` | — | Experimental |
| `trust-registry:trust-registry-core` | 16 | Experimental |
| `trust-registry:plugins:database` | — | Experimental |
| `trust-registry:trust-registry-server` | 28 | Experimental |
| `wallet:wallet-services` | 16 | Experimental |
| `wallet:plugins:database` | — | Experimental |
| `wallet:plugins:file` | — | Experimental |
| `wallet:plugins:cloud` | — | Experimental |
| `observability` | — | Experimental |

### ETSI / eIDAS signature formats

`signatures:*` implements advanced electronic signature formats. Treat every module here as
**Experimental** (with `signatures:pades` currently a **Stub** excluded from publishing): the formats are large, conformance is defined by ETSI test suites TrustWeave does
not yet run, and the suites below exercise round-trips rather than full profile conformance. Do not
rely on these for a regulated eIDAS deployment without your own conformance assessment.

| Module | Tests | Notes |
|--------|-------|-------|
| `signatures:jades` | 34 | ETSI TS 119 182-1 (JSON) — also registered as a `ProofEngine` |
| `signatures:trust-lists` | 49 | EU trusted-list handling |
| `signatures:cades` | 28 | ETSI EN 319 122 (CMS) |
| `signatures:tsa-core` | 40 | RFC 3161 timestamping |
| `signatures:etsi-validation` | 23 | Validation helpers |
| `signatures:xades` | 82 | ETSI EN 319 132 (XML) |
| `signatures:pades` | 2 | **Stub (not published)** — ETSI EN 319 142 (PDF) skeleton |

## Plugins and integrations

Individual plugins under `did/plugins/*`, `kms/plugins/*`, `anchors/plugins/*`, `credentials/plugins/*`, `wallet/plugins/*`, and `trust-registry/plugins/*` vary widely. Treat each published plugin as **Experimental** unless its own README states otherwise and you have run integration tests against your target environment.

Key notes and evolving plugin families:

- DID methods: `did:plugins:ebsi`
- **`did:plugins:ethr` and `did:plugins:polygon` are in-memory only (not mainnet ERC-1056).** They do not read the ERC-1056 registry
  contract and keep no persistent index from a DID to its anchored document. A DID is resolvable only by the
  instance that created or updated it, and only until that process restarts: after a restart, or on any other
  instance, it resolves as not found. DIDs they create are not resolvable by standard ethr resolvers. Use them
  for experiments, not for DIDs that must outlive the process.
- Credentials formats / proofs: `credentials:plugins:mdl`, `credentials:plugins:eudiw`, `credentials:plugins:jades`
- Exchange / discovery: `credentials:plugins:siop`, `credentials:plugins:presentation-exchange`, `credentials:plugins:openid-federation`, `credentials:plugins:oidc4vci`, `credentials:plugins:oidc4vp`, `credentials:plugins:didcomm`, `credentials:plugins:chapi`
- Status lists: `credentials:plugins:status-list:bitstring`, `credentials:plugins:status-list:token`, `credentials:plugins:status-list:publishing`, `credentials:plugins:status-list:database`, `credentials:plugins:status-list:server`
- Agent authorization & micro-protocols: `credentials:plugins:verifiable-intent`, `credentials:plugins:avp-micro`
- Wallet storage plugins: `wallet:plugins:database`, `wallet:plugins:file`, `wallet:plugins:cloud`

Until each carries its own GA notice, treat the above as **Experimental**.

## Pure Stubs (12 modules — not published to Maven Central)

The following **12 pure stub modules** remain in the source tree as scaffolding but are **excluded from `distribution-bom` and Maven Central publishing** and marked `stub` in `trustweave-capabilities.json` and [assessed capabilities](assessed-capabilities.md):

| Module | Domain | Status |
|--------|--------|--------|
| `anchors:plugins:starknet` | Anchors | Stub — transaction operations not implemented |
| `credentials:plugins:platforms:salesforce` | Credentials | Stub — every public method throws `InvalidOperation` |
| `credentials:plugins:platforms:servicenow` | Credentials | Stub — every public method throws `InvalidOperation` |
| `did:plugins:btcr` | DID | Stub — create/resolve not implemented |
| `did:plugins:ens` | DID | Stub — ENS name-to-address lookup not implemented |
| `did:plugins:tezos` | DID | Stub — create/resolve not implemented |
| `did:plugins:threebox` | DID | Stub — create/resolve not implemented |
| `kms:plugins:entrust` | KMS | Stub — vendor nShield SDK/HSM integration not implemented |
| `kms:plugins:thales-luna` | KMS | Stub — vendor Luna SDK/HSM integration not implemented |
| `kms:plugins:utimaco` | KMS | Stub — vendor Utimaco SDK/HSM integration not implemented |
| `kms:plugins:venafi` | KMS | Stub — Venafi integration not implemented |
| `signatures:pades` | Signatures | Stub — PDF signature skeleton not implemented |

**Rule of thumb:** If you did not run your own integration and security review of a plugin, do not treat it as production-ready solely because it is on the classpath.

## Gradle publishing

The root build applies `maven-publish` to Kotlin/JVM library subprojects so artifacts share consistent POM metadata. **Artifact availability does not override the maturity table above.** When in doubt, prefer the core modules plus one well-tested plugin combination.

## Related docs

- [Production integration checklist](../getting-started/production-integration-checklist.md)
- [Result types guide](result-types-guide.md)
- Security policy (repository root `SECURITY.md`)

## Operation-level capability checks

See the [generated assessed capability table](assessed-capabilities.md). The runtime catalog is deliberately conservative: unlisted modules are unassessed. Applications can fail at startup using `ModuleCapabilities.requireOperations(module, required)`. Plugin registry configurations can also specify `requiredCapabilities` to validate feature requirements before lifecycle initialization.
