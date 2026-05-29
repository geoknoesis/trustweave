---
title: Production integration checklist
nav_order: 9
parent: Getting Started
keywords:
  - production
  - operations
  - logging
  - security
---

# Production integration checklist

Use this checklist when wiring TrustWeave into a production or pre-production service.

## Configuration and errors

- Configure `CredentialService` via `TrustWeave.build { ... }` (or `quickStart()` / `inMemory()`). If it is missing, `issue` / `verify` / `presentationResult` return **`AdapterNotReady`** variants—handle them explicitly; do not assume success.
- For DSL **`verify { }`** when misconfigured, **`VerificationResult.Invalid.AdapterNotReady`** may reference an **internal placeholder credential**. Never log it or show it to end users as if it were a real VC; branch on `AdapterNotReady` first.
- Use **`presentationResult`** / **`presentationFromWalletResult`** so configuration and validation errors are modeled as `PresentationResult.Failure`.
- With **`presentationFromWalletResult`**, every **`fromWallet` id** must exist; a missing id yields **`InvalidRequest`** (no silent partial resolution).
- If you call **`PresentationResult.getOrThrow()`**, failures throw **`TrustWeaveException.InvalidState`** with codes **`PRESENTATION_ADAPTER_NOT_READY`**, **`PRESENTATION_INVALID_REQUEST`**, or **`PRESENTATION_ADAPTER_ERROR`**—not raw **`IllegalStateException`**.

## Timeouts and cancellation

- `issue` and `verify` run under **`withTimeout`** in the trust-layer services (defaults: issuance 30s, verification 10s). Ensure your outer coroutine scope respects cancellation and choose timeouts appropriate for your KMS and network latency.
- Load tests: tune `Dispatchers.IO` usage and concurrency (`issueBatch` / `verifyBatch` `maxConcurrency`) to avoid exhausting connection pools.

## Logging and privacy

- Avoid logging full **VerifiableCredentials**, **presentations**, raw **proof** material, or **raw signatures**.
- Logging **DIDs** may be acceptable for audit trails depending on policy; treat as personal data where applicable.
- Surface **user-facing** messages that are generic (“Verification failed”); keep detailed errors in operator logs only. `AdapterNotReady` messages are operator-oriented (configuration hints).

## API stability

- Follow the [Deprecation policy](../../api-reference/reference/deprecation-policy.md). Replace deprecated APIs when warnings appear in your build.
- Consult the [Module maturity matrix](../../api-reference/reference/module-maturity.md) before depending on optional plugins in production.
