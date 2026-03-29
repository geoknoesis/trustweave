# Error and result handling

This document describes how TrustWeave surfaces failures across layers. Follow it for new APIs and refactors.

## Principles

1. **Expected domain failures** — Use a **sealed result type** (`DidCreationResult`, `IssuanceResult`, `DidResolutionResult`, KMS `*Result`, etc.) at public boundaries so callers can handle failures exhaustively without catching exceptions.

2. **Programming errors** — Use `require`, `check`, or `IllegalArgumentException` / `IllegalStateException` only for bugs or impossible states the **caller** is responsible for (e.g. missing mandatory DSL field before `build()`). Document when the DSL throws.

3. **Invalid user or configuration input** — Prefer structured failures on the result type (e.g. `DidCreationResult.Failure.InvalidConfiguration`) or [`TrustWeaveException.ValidationFailed`](../../common/src/main/kotlin/org/trustweave/core/exception/TrustWeaveException.kt) when an exception-based API is required.

4. **Infrastructure / I/O faults** — Use [`TrustWeaveException`](../../common/src/main/kotlin/org/trustweave/core/exception/TrustWeaveException.kt) or a **domain-specific** subclass (e.g. [`BlockchainException`](../../anchors/anchor-core/src/main/kotlin/org/trustweave/anchor/exceptions/BlockchainExceptions.kt)) with a **stable `code`** and structured `context`. Preserve `cause` when wrapping third-party errors.

5. **KMS operations** — Implementations return sealed `org.trustweave.kms.results.*` types. Use `getOrThrow()` only at boundaries that intentionally translate to exceptions.

## DID creation

- **`createDid`** returns [`DidCreationResult`](../../trust/src/main/kotlin/org/trustweave/trust/types/DidResult.kt).
- **`createDidWithKey`** returns [`DidCreationWithKeyResult`](../../trust/src/main/kotlin/org/trustweave/trust/types/DidResult.kt): success includes `(Did, keyId)`; failures either wrap `DidCreationResult.Failure` or describe key extraction errors.

## TrustWeave facade vs services

- [**TrustWeave**](../../trust/src/main/kotlin/org/trustweave/trust/TrustWeave.kt) composes domain services; DID operations (`createDid`, `resolveDid`, etc.) are **members** on the facade. DID DSL builders and [`DidManagementService`](../../trust/src/main/kotlin/org/trustweave/trust/services/DidManagementService.kt) depend on [`DidDslContext`](../../trust/src/main/kotlin/org/trustweave/trust/context/DidDslContext.kt), not the full facade type.

## Credential API layout

- **SPI** for format conversion: `org.trustweave.credential.spi.transform` (`CredentialFormatConverter`).
- **Default Jackson/Nimbus/CBOR implementation**: `org.trustweave.credential.transform` (`CredentialTransformer` and extension helpers). Callers normally use **`CredentialService`** extensions in `CredentialServiceExtensions.kt` rather than constructing a transformer directly.

## Backlog / hotspots

When adding features, prefer aligning with the above over introducing new ad-hoc exception types for expected failures. Document any remaining `kotlin.Result` or exception-heavy surfaces in PR descriptions until migrated.
