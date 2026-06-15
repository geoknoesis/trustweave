# AVP-Micro Authorization Service — Design

**Date:** 2026-06-14
**Status:** Design — pending user review, then implementation plan.
**Builds on:** [`2026-06-11-avp-micro-design.md`](2026-06-11-avp-micro-design.md) (approved AVP-Micro plugin design), TrustWeave SDK `0.6.0`.
**Spec source:** https://geoknoesis.github.io/avp-micro-spec/ (the `avp-micro-spec` Python harness).

## 1. Context & goal

This is the first of three commercial AVP-Micro products to be built on the TrustWeave
SDK:

1. **Authorization-as-a-Service API** — *this design.*
2. Hosted trust infrastructure (status list + trust registry + payee binding).
3. Certification / conformance-as-a-service.

Product #1 (Authorization API) is the AVP-Micro *runtime enforcement* layer exposed as a
hosted service: a caller submits a signed payment authorization and receives a verified
**allow / reject** verdict with full spending-policy enforcement. It is the AVP-specific IP
— the same engine the conformance product later certifies — and the natural first build,
because the other two products depend on having this verification core.

**This build is a working vertical slice**, not a production deployment: the Kotlin policy
engine + a thin Ktor HTTP endpoint + tests, proving one full happy path plus the key
rejections end-to-end against real TrustWeave verification, with **in-memory** state. No
persistence, API-key auth, billing, or multi-region — those are deferred to a later
"deployable service" iteration.

## 2. Key finding: where TrustWeave fits

TrustWeave provides most of the trust substrate (DID resolution, VC verification, status
lists, KMS P-256 signing with raw `r‖s`, `did:key` P-256). It does **not** ship the spec's
mandatory `ecdsa-jcs-2022` cryptosuite (only `Ed25519Signature2020`,
`JsonWebSignature2020`, `BBS-2023`, `ecdsa-rdfc-2019`), and — by deliberate design — its
AVP-Micro plugin is a **stateless verification library**. The approved
[`avp-micro` plugin design](2026-06-11-avp-micro-design.md) explicitly defers the stateful
enforcement this product is about:

> *"a stateless library cannot enforce a rolling daily cap … Documented as caller
> responsibility by default."* and *"Metered / streaming … require persistent accrual
> state the library cannot own."*

That defines a clean seam: **this service is the stateful "caller" the plugin design
anticipates.** It wraps the plugin's stateless `verifyPayment` and adds replay, rolling
daily cap, and single-use consumption.

## 3. Architectural approach (Approach A — plugin verify-slice + stateful service)

Two new Gradle modules in the **trustweave** repo, both registered in
`settings.gradle.kts`:

- A **minimal slice of the approved `avp-micro` plugin** carrying the one audited
  `EcdsaJcs2022` cryptosuite, the verify-path data models, and the stateless
  `verifyPayment` checklist. Issuance DSL, receipts, and the interop bridge from the
  approved design are **deferred** — not needed to prove the authorization path.
- A new **`avp-authorization-server`** service module that adds the stateful enforcement
  and an HTTP surface.

Rejected alternatives: a single standalone service that buries the `ecdsa-jcs-2022` core
(loses reuse for products #2/#3 and the full plugin); or building the *entire* approved
plugin first (over-scoped for a vertical slice).

## 4. Module structure

```
credentials/plugins/avp-micro/                       # verify-only slice of the approved plugin
  src/main/kotlin/org/trustweave/credential/avpmicro/
    crypto/EcdsaJcs2022.kt        # internal: JCS(RFC 8785) canon → sha256(proofConfig)‖sha256(payload)
                                  #   → deterministic P-256 ECDSA via KMS (RFC 6979, low-s, raw R‖S); sign + verify
    model/                        # SpendingAuthorizationCredential, PaymentQuote,
                                  #   PaymentAuthorization, Amount, DataIntegrityProof  (verify subset)
    verification/PaymentVerifier.kt   # ordered, short-circuiting stateless checklist
    AvpMicro.kt                   # facade: verifyPayment(...)

credentials/avp-authorization-server/                # the #3 product (stateful service)
  src/main/kotlin/org/trustweave/credential/avpauth/
    state/        NonceStore, DailyBudgetLedger, ConsumptionLedger    # in-memory, thread-safe
    engine/       AuthorizationEngine                                 # stateless verify → stateful checks → verdict
    dto/          AvpAuthorizationDtos                                # request/response JSON
    AvpAuthorizationRoutes.kt                                         # POST /v1/authorizations/verify
    AvpAuthorizationServer.kt                                         # Ktor (Netty) app wiring
```

### Dependencies

| Module | Depends on |
|---|---|
| `avp-micro` | `credential-api` (`BitstringStatusListManager`, VC 2.0 models), `did-core` (P-256 `did:key`), `kms-core` (`Algorithm.P256`, `EcdsaSignatureCodec`), `common`, Bouncy Castle (P-256 ECDSA), kotlinx-serialization-json, kotlinx-datetime |
| `avp-authorization-server` | `avp-micro`, Ktor server (Netty, content-negotiation, kotlinx-json) — mirroring `credentials/vc-api-server`; `kms:plugins:inmemory` + `testkit` (test only) |

## 5. Components

### Component A — `avp-micro` plugin (stateless verify core)

- **`EcdsaJcs2022`** *(internal object)* — the one audited cryptosuite. `verify(payload,
  proof, publicKey)` and `sign(payload, kms, keyRef)` (sign used only for test
  fixtures/round-trips). Per the approved design: strip `proof` → JCS-canonicalize
  (RFC 8785) payload and proof-config → `hashData = sha256(proofConfig) ‖ sha256(payload)`
  → deterministic P-256 ECDSA via KMS (RFC 6979, canonical low-s, raw R‖S) →
  `proofValue = multibase(sig)`. Public keys resolve offline from the `did-core` P-256
  `did:key` resolver (the key is embedded in the DID — no network in the slice).
- **`model/`** — `@Serializable` data classes. `SpendingAuthorizationCredential` (W3C VC
  2.0: subject `agentDid`, `spendingLimits{perTransaction, daily?}`, `allowedPayees?`,
  `allowedCurrencies`, `credentialStatus?`, `expirationDate`); `PaymentQuote`;
  `PaymentAuthorization`; `Amount(value: BigDecimal, currency)`; `DataIntegrityProof`.
- **`PaymentVerifier`** — ordered, short-circuiting **stateless** checklist (the approved
  design's checks, minus the rolling daily cap, which is stateful and belongs to the
  service):
  1. Credential proof valid (issuer DID)
  2. Credential not expired / not revoked (if `statusResolver`)
  3. Quote proof valid (payee DID)
  4. Authorization proof valid (agent DID)
  5. `authorization.credentialDigest` == SHA-256 of canonical credential
  6. `authorization.quoteId` == `quote.quoteId`
  7. `quote.agentDid` == `credential.credentialSubject.agentDid`
  8. `quote.amount` ≤ `perTransaction`
  9. `quote.amount.currency` ∈ `allowedCurrencies`
  10. `quote.payeeDid` ∈ `allowedPayees` (if restricted)
  11. `quote.expiresAt` > now (± `clockSkewSeconds`)

  Returns `PaymentVerificationResult.Valid(credential, quote, authorization, dailyLimit?)`
  or `Invalid(reason, detail)`.
- **`AvpMicro`** *(facade object)* — `verifyPayment(credential, quote, authorization,
  issuerKeyResolver, statusResolver?, now, clockSkewSeconds)`.

### Component B — `avp-authorization-server` (stateful service)

- **`state/`** — in-memory, thread-safe stores (swappable for a DB later):
  - `NonceStore` — seen authorization nonces/ids → replay → `NONCE_REUSE`.
  - `DailyBudgetLedger` — keyed `(agentDid, credentialId, UTC-date)` → accumulated spend;
    checks `prior + quote.amount ≤ daily` → `DAILY_LIMIT_EXCEEDED`; resets on UTC date
    rollover.
  - `ConsumptionLedger` — consumed authorization ids → single-use → `DOUBLE_SPEND`.
- **`AuthorizationEngine`** — one decision:
  1. `AvpMicro.verifyPayment(...)` (stateless gate). `Invalid` → `Reject(code)`.
  2. Stateful checks in order: nonce replay → single-use → daily budget.
  3. If all pass, **atomically commit** (record nonce, mark consumed, add to daily ledger)
     under a per-credential `Mutex`, so check-then-commit cannot race a double-spend
     (TOCTOU). This is the one concurrency-sensitive spot.

  Returns an `AuthorizationVerdict` (allow with echoed amount/agent/payee, or reject with
  code + detail).
- **Ktor surface** (mirrors `vc-api-server`): `AvpAuthorizationServer(engine, port, host)`
  doing `embeddedServer(Netty){ install(ContentNegotiation){ json(...) }; routing{
  configureAuthorizationRoutes(engine) } }`, plus `dto/`. One endpoint:
  **`POST /v1/authorizations/verify`** taking `{credential, quote, authorization}`,
  returning `{decision, reason?, detail?}`.

## 6. Data flow

```
client → POST /v1/authorizations/verify  {credential, quote, authorization}
  │
  ├─ deserialize DTOs                              (malformed → 400 INVALID_REQUEST)
  ├─ AuthorizationEngine.decide(...)
  │     1. AvpMicro.verifyPayment(...)  ── Invalid(reason) ─► Reject(reason)   (stateless)
  │     2. NonceStore.check          ── seen ─► Reject(NONCE_REUSE)
  │     3. ConsumptionLedger.check   ── used ─► Reject(DOUBLE_SPEND)
  │     4. DailyBudgetLedger.check   ── over ─► Reject(DAILY_LIMIT_EXCEEDED)
  │     5. commit atomically (per-credential Mutex) ─► Allow(amount, agentDid, payeeDid)
  └─ respond 200 { decision, reason?, detail? }
```

Decisions — **allow and reject alike** — return HTTP **200**; a reject is a valid
verification outcome, not a client error (mirrors `vc-api-server`'s `verified:false`). Only
malformed input is 4xx.

## 7. Verdict contract

Every outcome is a typed code, never a bare boolean. The vocabulary is the union of the
plugin's stateless `VerificationFailure` and the service's stateful codes, and it maps 1:1
onto the Python harness's `sim.py` `REJECTIONS`, keeping the two stacks behaviourally
aligned.

| Source | Codes | `sim.py` equivalent |
|---|---|---|
| Plugin (stateless) | `CREDENTIAL_PROOF_INVALID` / `QUOTE_PROOF_INVALID` / `AUTHORIZATION_PROOF_INVALID`, `CREDENTIAL_EXPIRED`, `CREDENTIAL_REVOKED`, `DIGEST_MISMATCH`, `QUOTE_MISMATCH`, `SUBJECT_MISMATCH`, `AMOUNT_EXCEEDED`, `CURRENCY_NOT_ALLOWED`, `PAYEE_NOT_ALLOWED`, `QUOTE_EXPIRED` | `badSignature`, `credentialExpired`, `credentialRevoked`, `quoteMismatch`, `holderMismatch`, `overCap`, `currencyMismatch`, `payeeNotAllowed`, `expired` |
| Service (stateful) | `NONCE_REUSE`, `DOUBLE_SPEND`, `DAILY_LIMIT_EXCEEDED` | `nonceReuse`, `doubleSpend`, `dailyLimitExceeded` |

## 8. Error handling

- Malformed / missing fields → `400 INVALID_REQUEST` (with message).
- Unexpected internal error → `500`.
- All policy outcomes are deterministic verdicts at `200`.

## 9. Testing

1. **Known-answer tests for `EcdsaJcs2022`** — *the single most important correctness
   gate.* Use the Python harness's signed `ecdsa-jcs-2022` vectors as fixtures: `verify`
   must accept them byte-exactly, and `sign` must reproduce the identical signature
   (deterministic RFC 6979) for the same input — proving byte-level parity with canonical
   `avp_crypto.py` (exact JCS + `hashData` ordering). The specific vector files are pinned
   from `spec/*/test-vectors/` during planning and copied into test resources.
2. **Plugin verifier matrix** — one test per stateless `VerificationFailure` + the happy
   path, with boundaries: amount at/over `perTransaction`, currency in/out, payee
   allowed/not, quote expiry at ± `clockSkew`, digest mismatch, quote-id mismatch, subject
   mismatch, and tampered proofs on each of credential/quote/authorization.
3. **Service stateful tests** — replay (allow then `NONCE_REUSE`); single-use (resubmit →
   `DOUBLE_SPEND`); daily budget (allow up to the limit, then `DAILY_LIMIT_EXCEEDED`; UTC
   rollover resets); **concurrency** (two simultaneous identical requests → exactly one
   `allow`, one reject — the atomic-commit/TOCTOU guard).
4. **HTTP integration** — Ktor `testApplication`: happy path → `200 allow`; a rejection →
   `200 reject` + code; malformed body → `400`.
5. **Cross-stack parity (nice-to-have)** — drive a handful of `sim-scenarios.json` cases
   through the service and assert the verdict code matches each scenario's declared
   `expect` — wiring the Kotlin service to the Python behavioural suite and pre-staging
   product #3 (conformance).

Fixtures use `testkit` + `kms:plugins:inmemory`, consistent with the Verifiable Intent
plugin's tests.

## 10. Deferred (explicitly out of scope for this slice)

- Persistence (DB-backed stores), API-key auth, usage metering/billing, multi-region.
- Streaming / metered sessions (`UsageSession`, `UsageAccrual`,
  `SessionBudgetAuthorization`).
- Issuance DSL, `PaymentReceipt`, and the SD-JWT-VC interop bridge (covered by the broader
  approved `avp-micro` plugin design).
- The settlement step (value movement) — out of scope by AVP-Micro definition.

## 11. Deliverables summary

| Module | Role | New public surface |
|---|---|---|
| `credentials/plugins/avp-micro` *(slice)* | Stateless AVP-Micro verification core | `AvpMicro.verifyPayment(...)`; `PaymentVerificationResult` |
| `credentials/avp-authorization-server` | Stateful authorization service (#3) | `AvpAuthorizationServer`; `POST /v1/authorizations/verify`; `AuthorizationEngine`, `AuthorizationVerdict` |
