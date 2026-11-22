# Support

Thank you for using TrustWeave! This document explains where to find help, report bugs, ask questions, and obtain commercial support.

## Self-Service Documentation

Before opening a discussion or issue, check these resources:

- **[Documentation Index](docs/README.md)** — Full developer documentation (also published at [trustweave.org](https://trustweave.org))
- **[Quick Start](docs/getting-started/quick-start.md)** — Run your first local issuance and verification flow in minutes
- **[Installation Guide](docs/getting-started/installation.md)** — Gradle coordinates, BOM usage, and production KMS selection
- **[Troubleshooting Guide](docs/getting-started/troubleshooting.md)** — Common build, configuration, and runtime issues
- **[FAQ](docs/faq.md)** — Frequently asked questions
- **[Browsable KDoc API Reference](https://trustweave.org/api/)** — Generated Kotlin API docs (Dokka)
- **[Module Maturity Matrix](docs/api-reference/module-maturity.md)** — Which modules are GA (`supported`), `experimental`, or unpublished `stub`s

## Community Support Channels

- **Questions & Usage Help:** Use [GitHub Discussions](https://github.com/geoknoesis/trustweave/discussions) for architecture questions, integration advice, and general usage help.
- **Bug Reports:** Open an issue using the [Bug Report](.github/ISSUE_TEMPLATE/bug_report.yml) template with your TrustWeave version, JDK/Kotlin/Gradle versions, and a minimal reproduction.
- **Feature Requests:** Propose new capabilities or plugin integrations via the [Feature Request](.github/ISSUE_TEMPLATE/feature_request.yml) template.
- **Documentation Improvements:** Report broken links, unclear guides, or outdated snippets via the [Documentation Improvement](.github/ISSUE_TEMPLATE/doc_improvement.yml) template.

## Security Vulnerabilities

**Do not open a public GitHub issue for security vulnerabilities.**

Follow [SECURITY.md](SECURITY.md) and report vulnerabilities privately to **security@geoknoesis.com**. Initial acknowledgment is targeted within 48 hours and initial assessment within 7 days.

## Supported Versions & Scope

While TrustWeave is pre-1.0, only the latest minor release line receives fixes and security updates (see [SECURITY.md](SECURITY.md)):

| Version | Status |
|---------|--------|
| `0.7.x` | Supported (active development and security fixes) |
| `0.6.x` and earlier | Unsupported — upgrade to `0.7.x` (see [Migrating to 0.7.0](docs/how-to/migration/migrating-to-0.7.0.md)) |

Support expectations also depend on module maturity (see [Module Maturity Matrix](docs/api-reference/module-maturity.md)):

- **Core platform (7 GA modules):** `trust`, `credentials:credential-api`, `did:did-core`, `kms:kms-core`, `wallet:wallet-core`, `anchors:anchor-core`, and `common` are supported for production use with a mature KMS/DID/anchor provider.
- **Experimental modules & plugins:** Published plugins and embedded servers are experimental unless noted otherwise; verify them against your target environment before production deployment.
- **Pure stubs (12 modules):** Excluded from `distribution-bom` and Maven Central publishing and not supported for runtime use.

## Commercial Licensing & Enterprise Support

TrustWeave is built and maintained by [Geoknoesis LLC](https://www.geoknoesis.com) and is dual-licensed under [AGPL-3.0](LICENSE) and a [Commercial License](LICENSE-COMMERCIAL.md).

For commercial licensing, dedicated SLAs, custom plugin engineering, or architecture consulting:

- **Email:** licensing@geoknoesis.com
- **Website:** [https://www.geoknoesis.com](https://www.geoknoesis.com)
