# TrustWeave Roadmap

This roadmap outlines TrustWeave's current baseline and planned milestones toward `1.0.0`. Dates and scope are directional and may adjust based on security priorities, standards evolution, and community feedback.

For completed changes and migration notes, see [CHANGELOG.md](CHANGELOG.md). For current production readiness by module, see the [Module Maturity Matrix](docs/api-reference/module-maturity.md).

## Current Baseline — `0.7.x` (Pre-1.0)

TrustWeave `0.7.0` establishes the hardened pre-1.0 foundation on **Java 21+**, **Kotlin 2.4.21**, and **Gradle 9.8.1**:

- **7 Supported (GA) core modules:** `trust`, `credentials:credential-api`, `did:did-core`, `kms:kms-core`, `wallet:wallet-core`, `anchors:anchor-core`, and `common` (plus `distribution-bom` and `distribution-all`).
- **Type-safe sealed result APIs:** Exhaustive result hierarchies across issuance, verification, DID creation/resolution, revocation, trust evaluation, key lookup, and wallet operations.
- **Security & protocol hardening:** Fail-closed SSRF guards, composite-key `DatabaseWallet` v2 and per-wallet `CloudWalletFactory` isolation, OID4VCI/OID4VP/VC-API/SIOP hardening, ETSI XAdES/CAdES/JAdES/trust-list verification hardening, and KMS contract alignment.
- **Release provenance & supply-chain gates:** Single-staged builds, `SHA256SUMS`, SLSA build provenance attestations (`release-evidence.yml`), CycloneDX SBOMs, and time-bounded OSV baseline triage.
- **Honest publishing scope:** 12 pure stub modules are excluded from `distribution-bom` and Maven Central publishing until real implementations ship.

## Near-Term — `0.8.x` (Ecosystem Maturity & Supply-Chain Hardening)

- **Plugin & server graduation:** Advance high-demand experimental modules toward GA backed by live/containerized integration suites and conformance checks:
  - Production KMS plugins (`kms:plugins:aws`, `kms:plugins:azure`, `kms:plugins:google`, `kms:plugins:hashicorp`)
  - Persistent wallet backends (`wallet:plugins:database`, `wallet:plugins:cloud`, `wallet:plugins:file`)
  - Core DID methods (`did:plugins:key`, `did:plugins:web`, `did:plugins:jwk`, `did:plugins:peer`) and registrar servers
  - Credential exchange servers (`credentials:oidc4vci-server`, `credentials:vc-api-server`, `trust-registry:trust-registry-server`)
- **Gradle dependency verification:** Generate and maintain a complete SHA-256 `gradle/verification-metadata.xml` across all modules and report tasks (tracked in [CONTRIBUTING.md](CONTRIBUTING.md#gradle-dependency-verification-planned)).
- **OSV backlog & DIDComm dependency modernization:** Continue burning down triaged OSV baseline entries in `config/osv/baseline.json` and move `credentials:plugins:didcomm` off the shaded `org.didcommx:didcomm` 0.3.2 artifact (tracked in [SECURITY.md](SECURITY.md#known-dependency-risks) and [Dependency Upgrade Plan](docs/contributing/dependency-upgrade-plan.md)).
- **Deprecated `0.7.0` API cleanup:** Remove pre-1.0 facade methods deprecated in `0.7.0` (`trustWeave.revoke`, `trustWeave.trust`, `trustWeave.getKeyId`, and `VerificationBuilder.requireTrustPath`) in favor of their sealed-result replacements.
- **Contributor licensing policy:** Finalize DCO vs. CLA policy for dual-licensed (AGPL-3.0 / Commercial) contributions (tracked in [CONTRIBUTING.md](CONTRIBUTING.md#contributor-licensing-todo-for-maintainers)).
- **Documentation & KDoc excellence:** Expand browsable Dokka KDoc coverage at [trustweave.org/api/](https://trustweave.org/api/) and keep every active guide continuously verified in CI (`scripts/check-documentation.py`).

## Medium-Term — `1.0.0` (API Stability Milestone)

- **Strict Semantic Versioning:** Lock binary- and source-compatible APIs across all GA modules with formal ABI verification and full enforcement of the [Deprecation Policy](docs/api-reference/deprecation-policy.md).
- **Standards conformance:** Expand automated interoperability test vectors for W3C Verifiable Credentials 2.0, W3C DID Resolution 1.0, SD-JWT-VC, ISO/IEC 18013-5 (mDoc/mDL), OpenID4VCI/OID4VP, and ETSI/eIDAS signature profiles.
- **Multi-maintainer governance:** Expand `.github/CODEOWNERS` and [MAINTAINERS.md](MAINTAINERS.md) from a single owner to area-specific maintainer teams.

## Unpublished Stub Modules

The 12 pure stub modules listed in [Module Maturity Matrix](docs/api-reference/module-maturity.md#pure-stubs-12-modules--not-published-to-maven-central) (including `signatures:pades`, `anchors:plugins:starknet`, `did:plugins:{btcr,ens,tezos,threebox}`, `kms:plugins:{entrust,thales-luna,utimaco,venafi}`, and `credentials:plugins:platforms:{salesforce,servicenow}`) remain outside the published BOM. They will only be promoted when complete, tested implementations are contributed or sponsored.

## How to Influence the Roadmap

- **Vote or propose ideas:** Open a [Feature Request](.github/ISSUE_TEMPLATE/feature_request.yml) or join [GitHub Discussions](https://github.com/geoknoesis/trustweave/discussions).
- **Contribute implementations:** See [CONTRIBUTING.md](CONTRIBUTING.md) and [Creating Plugins](docs/contributing/creating-plugins.md).
- **Sponsor priority plugins or enterprise features:** Contact Geoknoesis LLC at **licensing@geoknoesis.com** (see [SUPPORT.md](SUPPORT.md)).
