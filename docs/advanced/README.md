---
title: Advanced Topics
nav_order: 80
---

# Advanced Topics

This section is aimed at teams who are moving past the quick starts and now need to harden production deployments. Each guide assumes you already understand the core DID / credential lifecycle and want to fine-tune behaviour through typed options, SPI hooks, or operational playbooks.

## Published Guides

- [Identifier Design Specification](identifier-design.md) — comprehensive design for modeling identifiers and types in TrustWeave. Covers the distinction between identifiers (identity references) and types (classifications), type safety through value classes, validation, and serialization strategy. Essential for understanding the type system architecture.
- [Credential-Agnostic API Design Specification](credential-agnostic-api-design.md) — initial design for a credential-agnostic API that supports multiple credential standards (VC 1.1/2.0, SD-JWT-VC, AnonCreds, mDL, X.509, PassKeys) through a pluggable proof adapter layer. Defines the `CredentialEnvelope` abstraction and `ProofAdapter` SPI for format plugins.
- [Credential-Agnostic API Design Review](credential-agnostic-api-design-review.md) — expert review of the credential-agnostic API design, identifying weaknesses and proposing concrete improvements. Covers type safety, error handling consistency, API ergonomics, missing features, and performance considerations. Essential reading before implementation.
- [Credential-Agnostic API Design v2 - Gorgeous DX Edition](credential-agnostic-api-design-v2.md) — **Recommended**: The ultimate design prioritizing gorgeous developer experience. Features fluent DSL builders, sealed class hierarchies for exhaustive error handling, type-safe everything, and zero backward compatibility compromises. This is the design to implement.
- [Key Rotation](key-rotation.md) — how to plan, automate, and test cryptographic key rollovers using the same registries and KMS interfaces that power the facade. Ideal for security engineers and ops teams.
- [Verification Policies](verification-policies.md) — modelling advanced validation rules (anchors, revocation, domain checks) with `CredentialVerificationOptions` and interpreting the structured `CredentialVerificationResult`.
- [Error Handling](error-handling.md) — structured error handling with `TrustWeaveError` types, `Result<T>` utilities, and input validation. Essential for production applications.
- [Plugin Lifecycle](plugin-lifecycle.md) — initialize, start, stop, and cleanup plugins that implement `PluginLifecycle`. Useful for plugins that need resource management.

## Error Handling

TrustWeave provides structured error handling with rich context:

- **Error Types**: Sealed hierarchy of `TrustWeaveError` types (DID, credential, blockchain, wallet, plugin errors)
- **Result Utilities**: Extension functions for working with `Result<T>`
- **Input Validation**: Validation utilities for DIDs, credentials, and chain IDs
- **Error Context**: Structured context information for debugging

See [Error Handling](error-handling.md) for detailed examples and patterns.

## Plugin Lifecycle

Manage plugin initialization, startup, shutdown, and cleanup:

- **Lifecycle Methods**: `initialize()`, `start()`, `stop()`, `cleanup()`
- **Automatic Discovery**: TrustWeave automatically discovers plugins that implement `PluginLifecycle`
- **Error Handling**: Lifecycle methods return `Result<Unit>` for error handling

See [Plugin Lifecycle](plugin-lifecycle.md) for implementation details and examples.

## Upcoming Topics

Upcoming additions will cover SPI extension points, adapter authoring, test strategies, and performance tuning. If you want to prioritise a topic—or contribute your own guide—open an issue or follow the workflow in the [Contributing Guide](../contributing/README.md).

