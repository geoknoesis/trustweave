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

Checkout fulfilments currently fail closed because line-item matching is not implemented. Passing
a payment-side verification does not authorize a checkout. This is a deliberate limitation, not
full VI conformance.

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

- **Multi-pair L2** (one mandate authorizing several distinct purchases) — needs a list-based L3 API.
- **`line_items` deep matching** (acceptable-id + quantity caps) — not implemented; checkout fulfilments are rejected instead of accepted without evaluation.
- Return TrustWeave's core `Result<T>` instead of the local `ChainVerificationResult`.
- Wire as a discoverable plugin (`PluginMetadata`/SPI) once the integration surface is decided.

> Status: draft, tracking VI spec v0.1. Not a conformance-certified implementation.

Budget, recurrence and agent-recurrence constraints fail closed for open mandates.
Checkout verification also remains unsupported pending line-item matching.
