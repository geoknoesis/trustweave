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
- **The mandate/constraint model** and a stateful-aware enforcement engine.

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
    now = Clock.System.now().epochSeconds,
)
if (result.valid) { /* constraints satisfied, chain intact */ }
```

## Issue (KMS-backed)

```kotlin
val signer = KmsEs256Signer(kms, keyId)          // any P-256 KMS key
val l1 = ViIssuer.createLayer1(issuerCredential, signer, issuerKid = "issuer-key-1")
val l2 = ViUser.createLayer2Autonomous(l1, checkoutMandate, paymentMandate, /* ... */ signer, kid)
val l3a = ViAgent.createLayer3Payment(finalPayment, l2.baseJwt, listOf(l2.paymentDiscB64!!), /* ... */)
```

## Test status

Two independent angles, both green:

- **`ChainVerifierKnownAnswerTest`** — cross-stack interop: verifies tokens minted by the **reference
  Python implementation** (fixture `src/test/resources/vi_autonomous_fixture.json`, self-verified
  valid before commit). Proves byte-for-byte agreement on the novel mechanisms.
- **`IssuanceRoundTripTest`** — mints L1/L2/L3 through the **real in-memory KMS + `KmsEs256Signer`**,
  then verifies. Covers autonomous (L3a+L3b + cross-reference, `card_id`, constraint enforcement) and
  immediate modes, plus a negative over-budget case.

Run: `./gradlew :credentials:plugins:verifiable-intent:test`

## Implemented

Autonomous + immediate verification; L1/L2/L3 signatures (ES256); cross-layer `sd_hash`; embedded-JWK
resolution + `kid` match; L2 reference binding; L3 pair-identity binding; L3a↔L3b cross-reference;
`card_id` cross-check; mandate-smuggling (duplicate-ref) detection; temporal checks incl. L3
`exp − iat ≤ 1h`; payment required-fields + L2↔L3 `payment_instrument` cross-check; 8-type constraint
enforcement with PERMISSIVE/STRICT + open-mandate strictness; full issuance for all layers.

## Deliberate scope boundaries (TODO)

- **Multi-pair L2** (one mandate authorizing several distinct purchases) — needs a list-based L3 API.
- **`line_items` deep matching** (acceptable-id + quantity caps) — currently acknowledged as checked.
- Return TrustWeave's core `Result<T>` instead of the local `ChainVerificationResult`.
- Wire as a discoverable plugin (`PluginMetadata`/SPI) once the integration surface is decided.

> Status: draft, tracking VI spec v0.1. Not a conformance-certified implementation.
