# TrustWeave

> The Foundation for Decentralized Trust and Identity

A **neutral, reusable trust and identity core** library for Kotlin, designed to be domain-agnostic, chain-agnostic, Decentralized Identifier (DID)-method-agnostic, and Key Management Service (KMS)-agnostic.

## Quick Start (30 Seconds) ⚡

```kotlin
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.types.getOrThrowDid
import org.trustweave.trust.types.getOrThrow
import org.trustweave.credential.results.VerificationResult
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val trustWeave = TrustWeave.quickStart()  // In-memory, did:key, ready to go

    val issuerDid = trustWeave.createDid().getOrThrowDid()

    val credential = trustWeave.issue {
        credential {
            type("PersonCredential")
            issuer(issuerDid)
            subject("did:key:subject") { "name" to "Alice" }
        }
        signedBy(issuerDid)  // Key ID auto-extracted
    }.getOrThrow()

    val verification = trustWeave.verify(credential)  // Simple overload

    when (verification) {
        is VerificationResult.Valid -> println("Credential valid: ✓")
        is VerificationResult.Invalid -> println("Credential invalid: ${verification.errors.joinToString()}")
    }
}
```

## Installation

```kotlin
dependencies {
    implementation("org.trustweave:distribution-all:0.6.0")
    // For testing, use testkit module
    testImplementation("org.trustweave:testkit:0.6.0")
}
```

## Documentation

- **[Getting Started](docs/tutorials/getting-started/installation.md)** - Installation, examples, and tutorials
- **[Quick Start](docs/tutorials/getting-started/quick-start.md)** - Get up and running in 5 minutes
- **[Create presentations](docs/how-to/create-presentations.md)** - `presentationFromWalletResult` / `PresentationResult`
- **[Production integration checklist](docs/tutorials/getting-started/production-integration-checklist.md)** - Logging, timeouts, result types
- **[Module maturity matrix](docs/api-reference/module-maturity.md)** - Which modules are GA vs experimental
- **[API Reference](docs/api-reference/)** - Detailed API documentation
- **[Architecture & Modules](docs/core-concepts/introduction/architecture-overview.md)** - Module details and design principles
- **[Available Plugins](docs/api-reference/plugins.md)** - DID methods, blockchain anchors, and KMS plugins
- **[Integrations](docs/how-to/integrations/)** - walt.id, godiddy, and third-party integrations
- **[Contributing](CONTRIBUTING.md)** - Building, testing, and development guide

## Key Features

- 🎯 **Domain-Agnostic** - Works for any use case
- 🔗 **Chain-Agnostic** - Supports any blockchain via pluggable adapters
- 🆔 **DID-Method-Agnostic** - Works with any DID method
- 🔐 **KMS-Agnostic** - Supports any Key Management Service
- 🛡️ **Type-Safe** - Leverages Kotlin's type system
- ⚡ **Coroutine-Based** - Built for modern async/await patterns

## Comprehensive Documentation

Full documentation is available in the [`docs/`](docs/) directory:

- **[Documentation Index](docs/README.md)** - Complete documentation index
- **[Core Concepts](docs/core-concepts/README.md)** - Introduction to DIDs, VCs, Wallets, and more
- **[Use Case Scenarios](docs/scenarios/README.md)** - 25+ real-world scenarios with runnable code
- **[API Reference](docs/api-reference/)** - Detailed API documentation
- **[Getting Started](docs/tutorials/getting-started/)** - Installation and quick start guides

## License

TrustWeave is available under a dual-licensing model:

- **Community License (AGPL v3.0)** - Free for open-source, educational, and non-commercial use
- **Commercial License** - For proprietary and commercial use

See [LICENSE](LICENSE) for the full AGPL v3.0 license text, or [LICENSE-COMMERCIAL.md](LICENSE-COMMERCIAL.md) for commercial license terms.

**Commercial licensing inquiries:** licensing@geoknoesis.com

## Funding & Support

TrustWeave is built and maintained by [Geoknoesis LLC](https://www.geoknoesis.com). If you find TrustWeave useful, please consider supporting its development:

- **GitHub Sponsors** - Support us directly on GitHub
- **Commercial Licensing** - For enterprise use
- **Open Collective** - Transparent financial management

For detailed information, see [Funding Plan](docs/FUNDING_PLAN.md).

## Security

Security vulnerabilities should be reported privately. See [SECURITY.md](SECURITY.md) for our security policy.

**Report security issues to:** security@geoknoesis.com

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines on how to contribute to TrustWeave.

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for detailed information about changes, improvements, and migration guides.

---

**Made with ❤️ by [Geoknoesis LLC](https://www.geoknoesis.com)**
