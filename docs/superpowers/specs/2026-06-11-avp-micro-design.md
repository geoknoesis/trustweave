# AVP-Micro Implementation Design

**Date:** 2026-06-11
**Spec source:** https://geoknoesis.github.io/avp-micro-spec/
**Status:** Design — approved, pending implementation plan. Securing suite tracks the spec MTI: `ecdsa-jcs-2022` (P-256), via deterministic ECDSA (RFC 6979) with canonical low-s.

## 1. Overview

AVP-Micro (Autonomous Verifiable Payments — Micro) is a draft specification suite for
**delegated, verifiable spending authority for autonomous AI agents**. It answers three
questions cryptographically:

1. **Authorization** — who authorized this agent to spend?
2. **Constraints** — what are the limits, allowed payees, currency, expiry?
3. **Transaction validity** — is this specific purchase authorized?

The suite has three peer specifications:

- **DSA (Delegated Spending Authority)** — the credential and trust foundation.
- **AVP-Micro Payments** — the transaction lifecycle built atop DSA.
- **SD-JWT-VC Interop Profile** — a bridge to Mastercard Verifiable Intent / Google AP2
  (JOSE-signed) payment networks.

This design implements those three specs as **two new TrustWeave plugins** plus a small
additive change to the existing Verifiable Intent plugin.

## 2. Scope

### In scope

- **Issuer role** — issue `SpendingAuthorizationCredential`s (DSA).
- **Merchant/Verifier role** — independently verify a credential + payment authorization,
  including all spending constraints.
- **Agent utilities** — data models for the protocol objects plus a single
  `authorize(...)` signing helper. No agent *workflow* / decision logic.
- **SD-JWT-VC interop bridge** — all three bridge modes (proof-preserving, co-issued,
  attested).

### Out of scope (deliberately deferred)

- **Agent workflow** — "fetch quote → decide → present" is application-level business
  logic above the library. TrustWeave is infrastructure, not an AI-agent runtime. We
  ship the models and a signing helper; the *decision* stays with the caller. (Mirrors
  how the Verifiable Intent plugin does not implement a "holder".)
- **Metered / streaming** — `UsageSession`, `UsageAccrual`, `SessionBudgetAuthorization`.
  These require persistent accrual state the library cannot own. Additive later; pure
  data-class additions with no breaking impact.
- **Settlement** — `PaymentExecution` and value movement. AVP-Micro explicitly layers
  atop any rail (Lightning, Interledger, card, stablecoin) and does not specify
  settlement. Out of scope by definition.
- **`PaymentOffer`** — merchant pricing-model declaration; read-only context for the
  verifier, modeled as `JsonObject` passthrough if needed.

## 3. Architectural Approach (Option C — Hybrid)

Use TrustWeave's existing VC machinery for what **is** a W3C Verifiable Credential, and a
thin standalone layer for what is **not**:

- `SpendingAuthorizationCredential` **is** a W3C VC 2.0 → secured with the spec's
  mandatory `ecdsa-jcs-2022` (P-256) Data Integrity proof from the one audited
  `EcdsaJcs2022` object (§6), and revoked through the existing `BitstringStatusListManager`.
- `PaymentQuote` / `PaymentAuthorization` / `PaymentReceipt` are **protocol messages, not
  credentials** → plain `@Serializable` data classes signed via a thin internal
  `ecdsa-jcs-2022` utility. Forcing these through the VC issuance pipeline would be a
  category error (no `@context` / `credentialSubject` envelope).

The shared primitive behind both paths is *JCS-canonicalize → hash → deterministic P-256
ECDSA sign (RFC 6979, canonical low-s, raw R‖S)*, isolated in one auditable `EcdsaJcs2022`
object.

Rejected alternatives:
- **Route the credential through `VcLdProofEngine`** — that engine secures VCs with
  `Ed25519Signature2020` / `JsonWebSignature2020` over JSON-LD canonicalization, not the
  spec's JCS-based `ecdsa-jcs-2022`; reusing it would mean adding a second cryptosuite
  implementation when one `EcdsaJcs2022` object already secures both paths. It builds
  instead on the P-256 primitives trustweave already ships (KMS `Algorithm.P256`,
  `EcdsaSignatureCodec`, the P-256–capable `did:key` resolver).
- **Fully integrated** — would force the non-VC protocol objects through the VC pipeline.

## 4. Module Structure

```
credentials/plugins/avp-micro/
  src/main/kotlin/org/trustweave/credential/avpmicro/
    model/          SpendingAuthorizationCredential, PaymentQuote,
                    PaymentAuthorization, PaymentReceipt, Amount, DataIntegrityProof
    crypto/         EcdsaJcs2022 (JCS canon + deterministic P-256 ECDSA sign/verify, internal)
    issuance/       DsaIssuer + SpendingAuthorityBuilder DSL
    verification/   PaymentVerifier (+ PaymentVerificationResult, VerificationFailure)
    agent/          AgentSigning.authorize(...)
    AvpMicro.kt     public facade

credentials/plugins/avp-micro-interop/
  src/main/kotlin/org/trustweave/credential/avpmicro/interop/
    model/          BridgeMode, SdJwtSpendingAuthority
    bridge/         ProofPreservingBridge, CoIssuedBridge, AttestedBridge
    AvpMicroInterop.kt  public facade
```

### Dependencies

| Module | Depends on |
|---|---|
| `avp-micro` | `credential-api` (`BitstringStatusListManager`, VC 2.0 models), `did-core` (P-256 `did:key`), `kms-core` (`Algorithm.P256`, `EcdsaSignatureCodec`), `common`, Bouncy Castle (P-256 ECDSA), kotlinx-serialization-json, kotlinx-datetime |
| `avp-micro-interop` | `avp-micro`, `verifiable-intent` (public facade only), `kms-core`, nimbus-jose-jwt |

Both new modules added to `settings.gradle.kts`. Facades follow the stateless `object`
pattern of `VerifiableIntent` — no hidden globals.

## 5. Data Models

All `@Serializable`, with JSON-LD `@context` constants.

### DSA

```
SpendingAuthorizationCredential (W3C VC 2.0)
  credentialSubject:
    agentDid: String
    spendingLimits:
      perTransaction: Amount
      daily: Amount?
    allowedPayees: List<String>?      // null = unrestricted
    allowedCurrencies: List<String>   // ISO 4217
  credentialStatus: BitstringStatusListEntry
  expirationDate (inherited)

Amount(value: BigDecimal, currency: String)   // validated on construction
```

### Payment protocol objects

```
PaymentQuote
  quoteId, agentDid, payeeDid, amount: Amount, expiresAt: Instant
  proof: DataIntegrityProof   // ecdsa-jcs-2022, merchant key

PaymentAuthorization
  authorizationId, credentialId, credentialDigest (SHA-256 of canonical credential),
  quoteId, authorizedAt: Instant
  proof: DataIntegrityProof   // ecdsa-jcs-2022, agent key

PaymentReceipt
  authorizationId, settledAt: Instant
  proof: DataIntegrityProof   // ecdsa-jcs-2022, merchant key
```

### Interop

```
BridgeMode: PROOF_PRESERVING | CO_ISSUED | ATTESTED

SdJwtSpendingAuthority
  originalCredential: SpendingAuthorizationCredential   // embedded (all modes)
  sdJwtCompact: String
  bridgeMode: BridgeMode
  bridgeIssuerDid: String?                              // ATTESTED only
```

## 6. Crypto Layer

`crypto/EcdsaJcs2022` (internal object) — the single audited implementation of the spec's
mandatory-to-implement securing mechanism:

```
sign(payload, kms, keyRef): DataIntegrityProof
  1. strip existing `proof`
  2. JCS-canonicalize (RFC 8785) the payload
  3. JCS-canonicalize the proof config (without proofValue)
  4. hashData = sha256(proofConfig) || sha256(payload)
  5. deterministic P-256 ECDSA sign hashData via KMS (RFC 6979, canonical low-s; raw R‖S)
  6. DataIntegrityProof{ type=DataIntegrityProof, cryptosuite=ecdsa-jcs-2022,
                         verificationMethod, proofPurpose, proofValue=multibase(sig) }

verify(payload, proof, publicKeyMultibase): Boolean   // reverse
```

- **DID method:** `did:key` with P-256 Multikey (multicodec `p256-pub`; spec
  mandatory-to-implement), resolved via the existing `did-core` `did:key` resolver, which
  already supports P-256. No new resolver.
- **Conformance:** validated against the spec's signed test vectors (from its Python
  harness, `avp_crypto.py`) as known-answer tests — the same approach as VI's
  `ChainVerifierKnownAnswerTest`. Byte-exact JCS + `hashData` ordering is the single most
  important correctness gate.
- The `SpendingAuthorizationCredential` (a real VC) is secured by this same
  `EcdsaJcs2022` object — one cryptosuite implementation for the credential and the
  protocol objects alike, reused by interop verification of the embedded credential.
  Status/revocation still flows through `BitstringStatusListManager`, which is independent
  of the proof suite.

## 7. Flows

### Issuance (DSA Issuer)

```kotlin
val credential = AvpMicro.issueSpendingAuthority(kms, issuerDid) {
    agent("did:key:zAgent...")
    perTransaction(50.00, "USD")
    daily(200.00, "USD")
    allowPayee("did:key:zMerchant...")   // omit → unrestricted
    currencies("USD", "EUR")
    expires(Instant.parse("2026-12-31T00:00:00Z"))
    status(statusListManager)            // optional revocation wiring
}
```

`SpendingAuthorityBuilder.build()` validates: limits positive, currencies non-empty and
valid ISO 4217, expiry in the future, agent DID well-formed. Returns a signed credential
via `VcLdProofEngine`.

### Verification (Merchant/Verifier) — core value

```kotlin
val result: PaymentVerificationResult = AvpMicro.verifyPayment(
    credential, quote, authorization,
    issuerJwkResolver, statusResolver,   // statusResolver optional
    now, clockSkewSeconds = 300,
    priorDailySpend = null,              // optional stateful daily-cap check
)
```

`PaymentVerifier` runs an ordered, short-circuiting checklist and returns a **structured
result with the specific failure reason** (never a bare boolean — VI philosophy):

1. Credential proof valid (issuer DID)
2. Credential not expired / not revoked (if `statusResolver`)
3. Quote proof valid (payee DID)
4. Authorization proof valid (agent DID)
5. `authorization.credentialDigest` == SHA-256 of canonical credential
6. `authorization.quoteId` == `quote.quoteId`
7. `quote.agentDid` == `credential.credentialSubject.agentDid`
8. `quote.amount` ≤ `perTransaction` limit
9. `quote.amount.currency` ∈ `allowedCurrencies`
10. `quote.payeeDid` ∈ `allowedPayees` (if restricted)
11. `quote.expiresAt` > now (± skew)

```
PaymentVerificationResult
  = Valid(credential, quote, authorization, dailyLimit: Amount?)
  | Invalid(reason: VerificationFailure, detail: String)

VerificationFailure: CREDENTIAL_PROOF_INVALID | CREDENTIAL_EXPIRED | CREDENTIAL_REVOKED |
                     QUOTE_PROOF_INVALID | AUTHORIZATION_PROOF_INVALID | DIGEST_MISMATCH |
                     QUOTE_MISMATCH | SUBJECT_MISMATCH | AMOUNT_EXCEEDED |
                     CURRENCY_NOT_ALLOWED | PAYEE_NOT_ALLOWED | QUOTE_EXPIRED
```

**Daily cap:** a stateless library cannot enforce a rolling daily cap. Default behavior
exposes the limit in `Valid.dailyLimit`; an optional `priorDailySpend: Amount?` parameter
lets the caller opt into a stateful check (verifier compares `priorDailySpend + quote.amount`
against `daily`). Documented as caller responsibility by default.

### Agent signing helper

```kotlin
val authorization = AvpMicro.authorize(credential, quote, kms, agentKeyRef)
```

Computes the credential digest, builds the `PaymentAuthorization`, signs it
`ecdsa-jcs-2022` with the agent's key. **No decision logic** — the caller decides whether
to authorize; this produces the valid signed object once they have.

## 8. Interop Bridge

```kotlin
val bridged = AvpMicroInterop.bridge(credential, mode, kms, bridgeKeyRef)
```

The spec is explicit: **JCS and JOSE serialize differently → signature migration is
impossible**. Even though both sides now sign with the same P-256 curve, the signed bytes
differ (JCS `hashData` vs the JWS signing input), so you cannot reuse one signature as the
other. This drives the three modes:

| Mode | Behavior | Trust cost |
|---|---|---|
| **PROOF_PRESERVING** (default) | Wrap claims into an SD-JWT body and embed the original Data Integrity credential verbatim. Verifier checks both envelopes. | None — original issuer remains trust root |
| **CO_ISSUED** | Issuer natively signed both forms at creation **with the same P-256 key** (the `ecdsa-jcs-2022` Data Integrity proof and the `ES256` SD-JWT); the bridge assembles the pre-existing JOSE signature alongside the VC and validates they describe the same claims. | None — issuer signed both |
| **ATTESTED** | A named bridge entity re-signs the claims in ES256 SD-JWT; its DID becomes the JOSE trust anchor. Records `bridgeIssuerDid`. | Bridge becomes trust root |

```kotlin
val result = AvpMicroInterop.verifyBridged(
    bridged, expectedIssuerJwk, bridgeJwkResolver, now,
)
```

- PROOF_PRESERVING / CO_ISSUED → verify the embedded `ecdsa-jcs-2022` credential (reuse
  `avp-micro`'s `EcdsaJcs2022.verify`) and that the SD-JWT claims match. Trust → original
  issuer.
- ATTESTED → verify the ES256 SD-JWT against the bridge DID. Trust → bridge.

### SD-JWT/ES256 reuse decision

The bridge needs SD-JWT / ES256 crypto that the Verifiable Intent plugin already
implements. **Decision: `avp-micro-interop` depends on `verifiable-intent` directly.**

Precondition: VI's workhorse crypto (`Es256`, `ViSdJwt`, `Disclosure`/`Disclosures`) is
currently `internal`; only `Es256Signer` and `KmsEs256Signer` are public. So VI gains a
**thin public facade** — keeping internals internal — exposing exactly what interop needs:

```kotlin
// new public surface in verifiable-intent
public object ViSdJwtFacade {
    public fun build(claims: JsonObject, selectivelyDisclosable: Set<String>,
                     signer: Es256Signer, holderJwk: JsonObject?): String  // compact SD-JWT
    public fun verify(compact: String, issuerJwk: JsonObject, now: Long): SdJwtVerification
}
```

Interop depends on VI's *public API*, not its guts. `KmsEs256Signer` (already public)
covers ATTESTED-mode ES256 signing. This avoids duplicating audited crypto and keeps one
ES256 implementation in the codebase.

## 9. Testing

- **Known-answer tests** against the spec's signed test vectors for `EcdsaJcs2022`
  (byte-exact JCS + `hashData` ordering, deterministic RFC 6979 nonces, canonical low-s) —
  primary correctness gate.
- **Issuance round-trip** — issue → verify a `SpendingAuthorizationCredential`.
- **Verifier matrix** — one test per `VerificationFailure` case plus the happy path.
- **Constraint tests** — amount/currency/payee/expiry boundaries; optional daily-cap with
  `priorDailySpend`.
- **Interop** — round-trip each of the three bridge modes through `bridge` →
  `verifyBridged`; assert correct trust anchor per mode.
- testkit + `kms:plugins:inmemory` for fixtures (consistent with VI tests).

## 10. Documentation

Per project instruction, documentation is updated **as part of implementation**, not
after:

- Per-plugin `README` / KDoc on the `AvpMicro` and `AvpMicroInterop` facades.
- A usage example under `distribution:examples` (issue → authorize → verify, plus one
  bridge round-trip).
- Note the VI `ViSdJwtFacade` addition in the Verifiable Intent docs.

## 11. Deliverables Summary

| Module | Role | New public surface |
|---|---|---|
| `verifiable-intent` *(modified)* | Expose SD-JWT to siblings | `ViSdJwtFacade` |
| `avp-micro` | DSA + Payments verifier + agent helper | `AvpMicro`: `issueSpendingAuthority`, `verifyPayment`, `authorize` |
| `avp-micro-interop` | SD-JWT-VC bridge (3 modes) | `AvpMicroInterop`: `bridge`, `verifyBridged` |
