---
title: Advanced Topics
nav_order: 80
redirect_from:
  - /advanced/README/
parent: API Reference
has_children: true
---

# Advanced Topics

This section is aimed at teams who are moving past the quick starts and now need to harden production deployments. Each guide assumes you already understand the core DID / credential lifecycle and want to fine-tune behaviour through typed options, SPI hooks, or operational playbooks.

## Published Guides

- [Identifier Design Specification](identifier-design.md) — design for modeling identifiers and types in TrustWeave. Covers the distinction between identifiers (identity references) and types (classifications), type safety through value classes / `Iri`-based inheritance, validation, and serialization strategy.
- [VC-Only API Design](vc-only-api-design.md) — historical design that produced the current W3C-VC-aligned credential model (`VerifiableCredential`, `Issuer`, `CredentialSubject`, `CredentialProof`). See the status note at the top of the document for what shipped vs. what was proposed.
- [Proof Engine Implementation Guide](proof-engine-implementation-guide.md) — current authoring guide for the `ProofEngine` / `ProofEngineProvider` SPI used by both built-in (VC-LD, SD-JWT-VC) and plugin (BBS-2023, mDoc) proof engines.
- [Key Rotation](key-rotation.md) — how to plan, automate, and test cryptographic key rollovers using the same registries and KMS interfaces that power the facade.
- [Verification Policies](verification-policies.md) — advanced validation rules (expiration, revocation, schema, trust) via **`trustWeave.verify { }`** / **`VerificationOptions`** and interpreting the sealed **`VerificationResult`**.
- [Error Handling](error-handling.md) — sealed **result types**, `TrustWeaveException` (and domain subclasses), `Result<T>` utilities, and input validation.
- [Plugin Lifecycle](plugin-lifecycle.md) — initialize, start, stop, and cleanup plugins that implement `PluginLifecycle`.

## Error Handling

TrustWeave provides structured error handling with rich context:

- **Errors**: Sealed **results** for many facade APIs; **`TrustWeaveException`** hierarchy for throws (`DidException`, `BlockchainException`, `WalletException`, `PluginException`, …)
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

Upcoming additions will cover SPI extension points, adapter authoring, test strategies, and performance tuning. If you want to prioritise a topic—or contribute your own guide—open an issue or follow the workflow in the [Contributing Guide](../../contributing/README.md).

