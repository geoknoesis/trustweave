# OID4VCI — verify issuer-returned credentials before trust (design)

**Date:** 2026-06-16
**Status:** Design — approved decisions, pending spec review.
**Related:** 2026-06-15 security review (H6, second half — `docs/reviews/2026-06-15-security-code-review.md`); H6 first half (PKCE) shipped in `59c68e03`.

## Problem

`Oidc4VciService.issueCredential` fetches the credential from the issuer's credential endpoint into `credentialResponse`, then — for immediate issuance — **ignores it and returns the caller-supplied placeholder credential** ([Oidc4VciService.kt:322-326](../../../credentials/plugins/oidc4vci/src/main/kotlin/org/trustweave/credential/oidc4vci/Oidc4VciService.kt#L322)):

```kotlin
// Immediate issuance — use the credential supplied by the caller
// In production, parse credentialResponse and convert to VerifiableCredential
Oidc4VciIssueResult(issueId = issueId, credential = credential, ...)
```

The wallet therefore stores whatever it was handed, with **no cryptographic verification** of the issuer's response. A malicious or MITM'd issuer endpoint can return forged credential material that the wallet later presents as genuine. This is the wallet's primary inbound trust boundary.

## Goal / non-goals

**Goal:** the immediate-issuance path parses the issuer's *actual* returned credential, verifies it, and returns only a verified credential — failing closed otherwise.

**Non-goals:** adding a VC-JWT proof engine (the default verifier supports VC-LD + SD-JWT-VC; VC-JWT is rejected as unsupported); verifying deferred-issuance credentials beyond routing them through the same check when they arrive; changing the issuer (offer-creation) side.

## Decisions (resolved)

- **Verifier wiring:** optional `didResolver: DidResolver? = null` on `Oidc4VciService` (mirrors `Oidc4VpService`); the verifier is built internally via `credentialService(didResolver)`. Required only on the holder/`issueCredential` path; absent ⇒ that path fails closed.
- **Posture:** cleanest/correct, fail closed. No unverified credential is ever returned. (OID4VCI is not in production use, so no back-compat constraint.)
- **Placeholder param:** **removed** from `issueCredential`; cascades to `WalletHolder.acceptOidc4vciOffer`, `Oidc4VciExchangeProtocol(Provider)`, and their tests.
- **Holder binding:** subject **and** cnf — `credentialSubject.id == holderDid` for VC-LD; for SD-JWT-VC the `cnf` must bind the holder key.

## Design

### Wiring
- `Oidc4VciService(credentialIssuerUrl, kms, httpClient = ssrfGuardedOkHttpClient(), didResolver: DidResolver? = null)`.
- Internal: `private val verifier: CredentialService? = didResolver?.let { credentialService(it) }`.
- `Oidc4VciExchangeProtocolProvider` reads `options["didResolver"] as? DidResolver` and passes it (same pattern as `Oidc4VpExchangeProtocolProvider`).

### Data flow — new `verifyIssuedCredential(response, expectedIssuer, holderDid): VerifiableCredential`
Replaces the placeholder return in the immediate-issuance branch:
1. **Require a verifier** — `verifier ?: throw CredentialVerificationFailed("no DID resolver configured; cannot verify the issued credential")`.
2. **Extract** the credential from `response["credential"]` (OID4VCI v1.0 §7.3). String ⇒ compact (JWT-VC / SD-JWT-VC); JSON object ⇒ VC-LD. Missing ⇒ throw.
3. **Detect format & parse** to `VerifiableCredential`: JSON-LD object → `fromJsonLd`; compact with `~` → SD-JWT-VC; compact `h.p.s` → JWT-VC. Parse via the `credential-api` transformer for that format.
4. **Reject unsupported formats** — if the parsed credential's proof suite is not in `verifier.supportedFormats()` (e.g. VC-JWT), throw. Fail closed.
5. **Verify** — `verifier.verify(parsed, options = VerificationOptions(checkExpiration = true, checkNotBefore = true, resolveIssuerDid = true, revocationFailurePolicy = FAIL_CLOSED))`. Require `VerificationResult.Valid`; any `Invalid.*` ⇒ throw with the reason.
6. **Issuer cross-check** — `parsed.issuer.id` must equal the offer's `issuerDid`/`credentialIssuer`. (`verify` proves the proof matches *its* issuer's key; this proves it is the issuer we expected.)
7. **Holder binding** — `parsed.credentialSubject.id == holderDid`; for SD-JWT-VC additionally require the `cnf` to bind the holder's key.
8. Return the verified `VerifiableCredential`.

The deferred path (`pollDeferredCredential`) routes its returned credential through the same `verifyIssuedCredential` when present.

### Error handling
New `Oidc4VciException.CredentialVerificationFailed(reason, credentialIssuer, cause?)`. Every failure in steps 1–7 throws it (fail closed). The token-exchange / network errors keep their existing typed exceptions.

### Components / units
- `verifyIssuedCredential(...)` — private, single responsibility (parse → verify → bind), unit-testable via the public `issueCredential`.
- A small `detectCredentialFormat(raw)` helper — pure, independently testable.
- Reuses `credential-api` (`credentialService`, `verify`, transformers) — no new crypto.

### Files touched
- `Oidc4VciService.kt` — `didResolver` param, internal verifier, `verifyIssuedCredential`, immediate + deferred branches, remove placeholder param from `issueCredential`.
- `Oidc4VciException.kt` — `CredentialVerificationFailed`.
- `exchange/spi/Oidc4VciExchangeProtocolProvider.kt` — read+pass `didResolver`.
- `exchange/Oidc4VciExchangeProtocol.kt` + `WalletHolder` (acceptOidc4vciOffer) — drop the placeholder credential argument.
- Tests: `Oidc4VciServiceTest.kt` (+ any WalletHolder test).

## Testing (TDD)
Mint a **real** credential in the test so verification is exercised end-to-end:
- Issue a VC-LD from a `did:key` issuer via `credentialService` (testkit KMS + in-memory DID resolver), serve it from MockWebServer as the credential-endpoint response.
- **Accept**: valid credential bound to the holder ⇒ returned & matches the issued one.
- **Reject** (fail closed): tampered credential (bad proof); issuer ≠ offer issuer; `credentialSubject.id` ≠ holder; unsupported format (VC-JWT); no `didResolver` configured.
- Existing pre-auth/token tests updated for the removed placeholder param.

## Blast radius / risk
- Behavior change to `issueCredential` (returns verified issuer credential, not the placeholder) + signature change (param removed) cascading to `WalletHolder` and the provider — acceptable (not in production use).
- SD-JWT-VC compact→model parsing path must be confirmed against the `credential-api` transformer during implementation; if absent, SD-JWT-VC support is a documented follow-up while VC-LD ships now (fail closed for unparseable formats).
