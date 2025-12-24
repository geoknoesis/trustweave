# TrustWeave

> The Foundation for Decentralized Trust and Identity

A **neutral, reusable trust and identity core** library for Kotlin, designed to be domain-agnostic, chain-agnostic, Decentralized Identifier (DID)-method-agnostic, and Key Management Service (KMS)-agnostic.

## Quick Start (30 Seconds) ⚡

```kotlin
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.types.*
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    // Build and configure TrustWeave
    val trustWeave = TrustWeave.build {
        keys {
            provider("inMemory")
            algorithm("Ed25519")
        }
        did {
            method("key") {
                algorithm("Ed25519")
            }
        }
    }

    // Create a DID
    val issuerDid = trustWeave.createDid {
        method("key")
    }.getOrThrowDid()

    // Issue a credential
    val credential = trustWeave.issue {
        credential {
            type("PersonCredential")
            issuer(issuerDid)
            subject {
                id("did:key:subject")
                "name" to "Alice"
            }
        }
        signedBy(issuerDid = issuerDid, keyId = "key-1")
    }.getOrThrow()

    // Verify the credential
    val verification = trustWeave.verify {
        credential(credential)
    }
    
    when (verification) {
        is VerificationResult.Valid -> println("Credential valid: ✓")
        is VerificationResult.Invalid -> println("Credential invalid: ${verification.errors.joinToString()}")
    }
}
```

## Installation

```kotlin
dependencies {
    implementation("org.trustweave:distribution-all:1.0.0-SNAPSHOT")
    // For testing, use testkit module
    testImplementation("org.trustweave:testkit:1.0.0-SNAPSHOT")
}
```

## Documentation

- **[Getting Started](GETTING_STARTED.md)** - Installation, examples, and tutorials
- **[API Guide](API_GUIDE.md)** - Which API to use and how
- **[Architecture & Modules](ARCHITECTURE.md)** - Module details and design principles
- **[Available Plugins](PLUGINS.md)** - DID methods, blockchain anchors, and KMS plugins
- **[Third-Party Integrations](INTEGRATIONS.md)** - walt.id and godiddy integrations
- **[Development Guide](DEVELOPMENT.md)** - Building, testing, and contributing

## Key Features

- 🎯 **Domain-Agnostic** - Works for any use case
- 🔗 **Chain-Agnostic** - Supports any blockchain via pluggable adapters
- 🆔 **DID-Method-Agnostic** - Works with any DID method
- 🔐 **KMS-Agnostic** - Supports any Key Management Service
- 🛡️ **Type-Safe** - Leverages Kotlin's type system
- ⚡ **Coroutine-Based** - Built for modern async/await patterns

## Comprehensive Documentation

Full documentation is available in the [`docs/`](docs/) directory:

- **[Documentation Index](docs/DOCUMENTATION_INDEX.md)** - Complete documentation index
- **[Core Concepts](docs/core-concepts/README.md)** - Introduction to DIDs, VCs, Wallets, and more
- **[Use Case Scenarios](docs/scenarios/README.md)** - 25+ real-world scenarios with runnable code
- **[API Reference](docs/api-reference/)** - Detailed API documentation
- **[Getting Started](docs/getting-started/)** - Installation and quick start guides

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
