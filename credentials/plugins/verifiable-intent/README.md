# credentials:plugins:verifiable-intent

A TrustWeave implementation of **Verifiable Intent (VI)** — the Mastercard/Google open spec
([`agent-intent/verifiable-intent`](https://github.com/agent-intent/verifiable-intent), draft v0.1)
for cryptographically proving *what a user authorized an AI agent to do* in agentic commerce.

VI is a layered SD-JWT credential chain that answers what OAuth cannot — not "can this agent act?"
but "exactly what action, within exactly what constraints, did the user approve?":

```
L1  issuer credential   binds the user's key      (cnf.jwk, ~1 year)      sd+jwt
 └─ L2  user mandate     constraints + agent key   (cnf.jwk, hours–days)   kb-sd-jwt[+kb]
     └─ L3a payment      agent's payment action    (→ network, ~minutes)   kb-sd-jwt
        L3b checkout     agent's checkout action   (→ merchant, ~minutes)  kb-sd-jwt
```

Each layer's `sd_hash` binds the previous layer; selective disclosure routes only the relevant
claims to each party (the network sees the payment, the merchant sees the checkout); the **payment
network enforces the constraints**, not the agent. Algorithm: **ES256** throughout.

## Why this composes onto TrustWeave

VI's primitives are exactly what TrustWeave already provides: SD-JWT VC, selective disclosure, `cnf`
key binding, KB-JWT, ES256 keys (`Algorithm.P256` + `EcdsaSignatureCodec` in `kms-core`). This module
adds the VI-specific pieces the existing `SdJwtProofEngine` does not have:

- **SD-JWT array-element disclosures** (`{"...": digest}`) — load-bearing for `delegate_payload`.
- **Cross-layer `sd_hash`** — L3 binds the *routed L2 presentation*, not its own credential.
- **Embedded-JWK key resolution** — L1.cnf→L2, L2.mandate.cnf→L3, L3 carries no `cnf` (no DID).
- **The mandate/constraint model** and an enforcement engine with explicit unsupported-state rejection.

## Layout

| Package | Contents |
|---|---|
| `model` | `Vct`, `MandateMode`, the 8-type `Constraint` sealed hierarchy + parser, mandate data classes |
| `crypto` | `Disclosure` (2- & 3-element), `ViSdJwt` (parse + resolve), `Es256` (Nimbus verify) + `KmsEs256Signer`, `Jws`, `Cnf`, hashing |
| `issuance` | `ViIssuer` (L1), `ViUser` (L2 autonomous + immediate), `ViAgent` (L3a/L3b) |
| `verification` | `ChainVerifier` (full pipeline), `ConstraintChecker`, `IntegrityChecker` |
| (facade) | `VerifiableIntent.verifyChain(...)` — the Verifier Gateway entry point |

## Verify (Verifier Gateway)

```kotlin
val result = VerifiableIntent.verifyChain(
    l1 = l1Compact,
    l2 = l2Compact,
    issuerJwk = issuerPublicJwk,
    l3Payment = l3aCompact,
    l2RoutedForPayment = routedL2ForNetwork,
    expectedL2Aud = trustedExpectedAudience, // verifier configuration, not token claims
    expectedL3PaymentAud = trustedNetworkAudience,
    expectedL3PaymentNonce = issuedPaymentNonce,
    expectedL2Nonce = issuedNonce, // this request's server-issued challenge
)
if (result.valid) { /* constraints satisfied, chain intact */ }
```

Audience and nonce expectations are mandatory by default. Maintain a server-side nonce store and
atomically consume a matching nonce before authorizing an action; this library checks equality,
not single use. `requireReplayProtection = false` is only for offline audits and records a skip.
All layers require numeric `iat` and `exp`, with `exp > iat`. The explicit
`allowMissingTemporalClaims = true` audit policy permits absent L1/L2 timestamps only; L3 always
requires both and a lifetime of at most one hour.

`ConstraintChecker` can match a normalized cart against line-item requirements. It checks
integer quantities, permitted products, disclosure hashes and per-requirement capacity;
`exact` mode assigns at least one unit to every requirement. Overlapping alternatives
cannot reuse the same capacity. Empty acceptable-item arrays explicitly permit any product.
The matcher supports at most 128 requirements, 128 cart entries and 128 alternatives per
requirement; aggregate quantity is bounded to one quarter of Long.MAX_VALUE for safe arithmetic.

Autonomous checkout can be verified with `VerifiableIntent.verifyChainWithCheckout` and a
verifier-owned `CheckoutTrust`. Configure the merchant issuer, public P-256 JWK and merchant
identity from trusted configuration, and supply the expected audience and a fresh nonce.
Never derive that policy from an agent request or the token being verified. The original
`verifyChain` remains fail-closed for autonomous checkout without this policy.

The supported merchant JWT profile requires ES256, `typ: JWT`, a single string `aud`,
`nonce`, integer `iat`/`exp` with a maximum one-hour lifetime, and `cart.items`. The verifier
checks the compact JWT hash and pinned signature before matching the signed cart against
L2 line-item constraints. Conflicting agent-supplied cart or merchant fields are rejected.
The provisioned merchant identity is used for allowlists. This is a narrow integration
profile, not a claim of general merchant interoperability or full VI conformance.

The host must atomically consume the challenge before executing the authorized action.
Signature verification does not itself prevent a second execution of the same purchase.
The stateless APIs continue to reject budget and recurrence constraints. The PostgreSQL-backed
budget API below supports cumulative spending, per-payment minimums and the recurrence profiles below.

## Issue (KMS-backed)

```kotlin
val signer = KmsEs256Signer(kms, keyId)          // any P-256 KMS key
val l1 = ViIssuer.createLayer1(issuerCredential, signer, issuerKid = "issuer-key-1")
val l2 = ViUser.createLayer2Autonomous(l1, checkoutMandate, paymentMandate, /* ... */ signer, kid)
val l3a = ViAgent.createLayer3Payment(finalPayment, l2.baseJwt, listOf(l2.paymentDiscB64!!), /* ... */)
```

## Test status

Two complementary test suites:

- **`ChainVerifierKnownAnswerTest`** — cross-stack fixture: checks tokens minted by the **reference
  Python implementation** (fixture `src/test/resources/vi_autonomous_fixture.json`, self-verified
  valid before commit). Its recurrence constraint now fails closed because external enforcement
  cannot be established; this is not a positive full-conformance test.
- **`IssuanceRoundTripTest`** — mints L1/L2/L3 through the **real in-memory KMS + `KmsEs256Signer`**,
  then verifies. Covers autonomous payment and immediate modes, per-transaction amount rejection,
  checkout constraint rejection, missing L3 timestamps and unauthorized payment instruments.

Run: `./gradlew :credentials:plugins:verifiable-intent:test`

## Implemented

Autonomous + immediate verification; L1/L2/L3 signatures (ES256); cross-layer `sd_hash`; embedded-JWK
resolution + `kid` match; L2 reference binding; L3 pair-identity binding; L3a↔L3b cross-reference;
`card_id` cross-check; mandate-smuggling (duplicate-ref) detection; temporal checks incl. L3
`exp − iat ≤ 1h`; payment required-fields + L2↔L3 `payment_instrument` cross-check; parsing of eight constraint types
with PERMISSIVE/STRICT and open-mandate policies. Enforcement is limited as described below;
parsing a constraint does not mean the verifier can enforce it. Issuance covers all layers.

## Deliberate scope boundaries (TODO)

- **Multi-pair L2** (several mandate pairs in one credential) requires a list-based API.
- **Full interoperability** across merchant checkout formats and multiple recurrence constraints remains unqualified.
- Return TrustWeave's core `Result<T>` instead of the local `ChainVerificationResult`.
- Wire as a discoverable plugin (`PluginMetadata`/SPI) once the integration surface is decided.

> Status: draft, tracking VI spec v0.1. Not a conformance-certified implementation.

Stateless budget and recurrence checks fail closed for open mandates; stateful profiles require the ledger API.
Autonomous checkout requires the explicitly configured merchant trust policy described above.

## Durable budget authorization (PostgreSQL)

Use `VerifiableIntent.verifyAndReserveBudget` with a shared `PostgresIntentLedger` when
an autonomous payment mandate declares one `mandate.payment.budget` constraint. The
supported profile is `currency` plus non-negative integer `max`, with optional positive
`min` applied to each payment and no additional budget fields. This corrects the earlier
description of `min` as cumulative: it is per-transaction. Stateless APIs keep their behavior.

Create the ledger from an application-managed JDBC `DataSource`. Run `initializeSchema()`
once during deployment with DDL privileges. Runtime connections need SELECT/INSERT/UPDATE
on `vi_budget_accounts` and `vi_budget_reservations`; they do not need schema privileges.
All authorizing replicas must share the same database. Configure pool, connection and
socket timeouts; statements have a ten-second timeout. This API performs blocking JDBC
work, so coroutine applications should invoke it on their blocking-I/O dispatcher.

After signatures, disclosures, constraints and cross-references pass, one database
transaction locks the signed L2 budget account, verifies remaining funds, records the
signed payment transaction ID and expected audience/nonce, and increments reserved
spending. Hashes identify mandates, transactions and challenges; raw tokens are not
stored. A duplicate transaction within the mandate or reused audience/nonce in the
ledger is rejected. Issue high-entropy unique verifier nonces bound to the authenticated
requesting account and operation. Changing an L2 selective
presentation does not create a new budget because the account binds to its signed JWT.

`valid = true` with `durable_budget_and_challenge_reserved` means the reservation committed.
It does **not** mean payment executed. Execute downstream payments with the signed
transaction ID as an idempotency key. A database error, including an uncertain commit,
returns failure: do not execute the payment or create a fresh authorization to retry it.
Reservations are conservative and never automatically refunded. Use privileged `reconcile`
only after verifying the authoritative payment journal: `SETTLED` keeps the debit; `RELEASED`
requires confirmed non-execution and returns the amount once. Unknown outcomes and timeouts
must remain reserved. Repeated identical evidence is idempotent; conflicting terminal outcomes
or evidence are rejected. Store the actual evidence durably outside the ledger; its reference
is hashed in the ledger. This method is for a trusted host, never an untrusted presenter API.
Challenge, transaction and occurrence consumption remain after release.

Retain ledger records for every still-valid mandate and challenge, including during
backup/restore. Restoring an older ledger can resurrect spent authority: stop authorization
and reconcile against the authoritative payment journal before resuming. This module does
not authenticate a payment-provider journal, issue refunds or implement multi-region consensus.
Protect reconciliation with the host's authorization, backup and audit controls.

Validation includes real PostgreSQL concurrent writers, restart persistence, duplicate
transactions/challenges, overflow boundaries, injected rollback failures and full signed
chain authorization. These are local integration tests, not production load qualification.

See the compiled [signed-chain budget example](src/test/kotlin/org/trustweave/credential/vi/IssuanceRoundTripTest.kt)
and [PostgreSQL failure/concurrency tests](src/test/kotlin/org/trustweave/credential/vi/PostgresIntentLedgerTest.kt)
for complete executable setups.

## Recurrence profiles

The ledger API supports one recurrence constraint alongside one budget. Agent recurrence also
requires an amount-range constraint. UTC start/end dates are inclusive; the occurrence ceiling
and cumulative budget are checked under the same account lock. Without agent recurrence, the
mandate is single-use even if budget remains. Released reservations still count as occurrences.
Frequency codes describe suggested timing in the draft; they do not impose a strict interval
or run a scheduler. The hosting agent must schedule new purchases and obtain fresh challenges.

Merchant recurrence is one subscription setup, not authorization for later charges through
this SDK. This stricter profile requires bounded `frequency`, `start_date`, `end_date` and
`number` terms, plus a verified checkout JWT containing the same four fields in a `recurrence`
object. Frequency/start must match, and merchant end/count cannot exceed consent. Missing
signed metadata, unknown fields, unsupported codes, mixed recurrence modes and duplicate
recurrence constraints fail closed. Later merchant billing is outside the VI chain.

See the [conformance profile](CONFORMANCE.md) for supported cases and deliberate differences
from the [draft constraint specification](https://www.verifiableintent.dev/spec/constraints/).
`healthSnapshot()` provides aggregate pending/settled/released counts and oldest pending time
for host monitoring, without emitting token or customer identifiers. Run the
[operations exercise](src/test/kotlin/org/trustweave/credential/vi/IntentOperationsExerciseTest.kt)
for a loopback HTTP host, metrics, alerts, database failure and a separate-database restore.
