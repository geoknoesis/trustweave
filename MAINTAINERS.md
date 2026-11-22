# Maintainers

This file lists the maintainers of TrustWeave, their responsibilities, and how project stewardship works.

## Active Maintainers

| Maintainer | GitHub | Organization | Scope |
|------------|--------|--------------|-------|
| Stephane Fellah | [@fellahst](https://github.com/fellahst) | [Geoknoesis LLC](https://www.geoknoesis.com) | Core architecture, security, supply chain, CI/release provenance, and all modules |

> **Governance note:** TrustWeave currently has a single code owner across all paths in [`.github/CODEOWNERS`](.github/CODEOWNERS). See [CONTRIBUTING.md](CONTRIBUTING.md#recommended-branch-protection-maintainers) for the single-maintainer branch-protection configuration and [ROADMAP.md](ROADMAP.md) for the plan to expand maintainer teams as the community grows.

## Maintainer Responsibilities

Maintainers are responsible for:

- **Code Review & Quality:** Reviewing pull requests for correctness, Kotlin idioms, sealed result API consistency, ktlint compliance, and test coverage.
- **Security & Supply Chain:** Responding to private vulnerability disclosures per [SECURITY.md](SECURITY.md), triaging OSV baseline entries (`config/osv/baseline.json`), and maintaining Dependabot/CodeQL/dependency-review gates.
- **Release Engineering:** Executing and verifying tagged `v*` releases from `main` via `.github/workflows/release-evidence.yml` (staging, SHA-256 verification, SLSA build provenance attestation, and Maven Central publication; see [Publishing](docs/operations/publishing.md)).
- **Documentation & Maturity Accuracy:** Keeping `docs/`, [Module Maturity Matrix](docs/api-reference/module-maturity.md), and [CHANGELOG.md](CHANGELOG.md) accurate and synchronized with shipped code.
- **Community Health:** Upholding the [Code of Conduct](CODE_OF_CONDUCT.md) across issues, pull requests, and discussions.

## Becoming a Maintainer

We welcome sustained contributors who want to co-maintain core modules or plugin families (DID methods, KMS providers, blockchain anchors, credential exchange protocols, or wallet backends).

Maintainer status is invited based on:

1. Consistent, high-quality pull requests following [CONTRIBUTING.md](CONTRIBUTING.md).
2. Constructive participation in code reviews, issue triage, and [GitHub Discussions](https://github.com/geoknoesis/trustweave/discussions).
3. Demonstrated security awareness and commitment to [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).

## Contact

- **Security disclosures (private):** security@geoknoesis.com (see [SECURITY.md](SECURITY.md))
- **Commercial licensing & enterprise inquiries:** licensing@geoknoesis.com (see [LICENSE-COMMERCIAL.md](LICENSE-COMMERCIAL.md))
- **General support & questions:** See [SUPPORT.md](SUPPORT.md)
