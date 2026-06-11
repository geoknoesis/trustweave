---
title: Verifiable Intent Plugin
redirect_from:
  - /features/verifiable-intent/
parent: Feature Reference
grand_parent: API Reference
---

# Verifiable Intent Plugin

TrustWeave implementation of **Verifiable Intent (VI)** — the Mastercard/Google open specification
([`agent-intent/verifiable-intent`](https://github.com/agent-intent/verifiable-intent), draft v0.1)
for cryptographically proving *what a user authorized an AI agent to do* in agentic commerce.

Module: `org.trustweave:credentials-plugins-verifiable-intent`
(`credentials/plugins/verifiable-intent`).

## Overview

OAuth proves identity and grants broad scopes, but it cannot answer the question agent-driven
commerce needs: **exactly which action, within exactly which constraints, did the user approve?**
Verifiable Intent answers that with a layered SD-JWT credential chain, signed end to end with ES256:

```
L1  issuer credential   binds the user's key      (cnf.jwk, ~1 year)      typ: sd+jwt
 └─ L2  user mandate     constraints + agent key   (cnf.jwk, hours–days)   typ: kb-sd-jwt[+kb]
     └─ L3a payment      agent's payment action    (→ network, ~minutes)   typ: kb-sd-jwt
        L3b checkout     agent's checkout action   (→ merchant, ~minutes)  typ: kb-sd-jwt
```

Each layer's `sd_hash` cryptographically binds the previous layer. Selective disclosure routes only
the relevant claims to each party — the payment network sees the payment side, the merchant sees the
checkout side — and the **payment network enforces the constraints**, not the agent itself.

### Modes

- **Immediate** — two layers (L1 + a finalized L2). The user confirmed concrete values; the payment
  mandate's `transaction_id` equals the checkout mandate's `checkout_hash`.
- **Autonomous** — three layers (L1 + a constrained/open L2 + agent-signed L3a/L3b). The user sets
  machine-enforceable constraints and delegates to an agent key; the agent fills in finalized values
  at transaction time within those bounds.

The mode is inferred from the L2 mandate `vct` (open vs. final), never from caller arguments.

## How it composes onto TrustWeave

VI's primitives are exactly what TrustWeave already provides — SD-JWT VC, selective disclosure,
`cnf` key binding, KB-JWT, and ES256 keys (`Algorithm.P256` + `EcdsaSignatureCodec` in `kms-core`).
This plugin adds the VI-specific pieces the existing SD-JWT proof engine does not have:

- **SD-JWT array-element disclosures** (`{"...": digest}`) — load-bearing for `delegate_payload`.
- **Cross-layer `sd_hash`** — an L3's hash binds the *routed L2 presentation* it received, not its
  own credential.
- **Embedded-JWK key resolution** — L1 `cnf.jwk` verifies L2, an L2 open-mandate `cnf.jwk` (+ `kid`)
  verifies L3, and L3 carries no `cnf`. No DID resolution is involved.
- The **mandate/constraint model** and an enforcement engine.

Mapped onto the [SaaS surface](../../introduction/executive-overview.md): an issuer mints L1, a wallet
holds L1 and signs L2/L3, and a verifier gateway runs the chain verification + constraint enforcement.

## Verifying a chain (verifier gateway)

```kotlin
import org.trustweave.credential.vi.VerifiableIntent

val result = VerifiableIntent.verifyChain(
    l1 = l1Compact,
    l2 = l2Compact,
    issuerJwk = issuerPublicJwk,          // issuer EC P-256 public key (JWK)
    l3Payment = l3aCompact,
    l2RoutedForPayment = routedL2ForNetwork,
    now = kotlinx.datetime.Clock.System.now().epochSeconds,
)
if (result.valid) {
    // signatures, cross-layer sd_hash, key binding, reference binding and
    // all constraints checked — the agent acted within the mandate.
}
```

Mode is inferred automatically — for an immediate chain pass only `l1` + `l2`.

## Issuing a chain (KMS-backed)

Signing flows through `KmsEs256Signer`, backed by any P-256 key in a TrustWeave
[KMS](../kms/README.md):

```kotlin
import org.trustweave.credential.vi.crypto.KmsEs256Signer
import org.trustweave.credential.vi.issuance.*

val signer = KmsEs256Signer(kms, keyId)
val l1 = ViIssuer.createLayer1(issuerCredential, signer, issuerKid = "issuer-key-1")
val l2 = ViUser.createLayer2Autonomous(l1, checkoutMandate, paymentMandate, /* ... */ signer, kid)
val l3a = ViAgent.createLayer3Payment(finalPayment, l2.baseJwt, listOf(l2.paymentDiscB64!!), /* ... */)
```

## Constraint types

Open (autonomous) L2 mandates carry machine-enforceable constraints (integer minor units, no decimal
ambiguity):

| Type | Enforces |
|---|---|
| `mandate.checkout.allowed_merchants` | Merchant allowlist |
| `mandate.checkout.line_items` | Item allowlist + quantity caps |
| `mandate.payment.allowed_payees` | Payee allowlist |
| `mandate.payment.amount_range` | Per-transaction min/max + currency |
| `mandate.payment.budget` | Cumulative spend cap (network-enforced) |
| `mandate.payment.recurrence` / `agent_recurrence` | Subscription / recurring terms |
| `mandate.payment.reference` | Binds the payment mandate to the checkout disclosure |

Unknown constraint types are rejected in open mandates (an unevaluable constraint would leave agent
authority unbounded) or under `STRICT` strictness; otherwise skipped under `PERMISSIVE`.

## Status and scope

Draft, tracking VI spec v0.1 — **Experimental** (see the
[Module maturity matrix](../module-maturity.md)). Verified two ways:

- **Cross-stack interop** — verifies tokens minted by the reference Python implementation against a
  committed known-answer fixture.
- **Round trip** — mints L1/L2/L3 through the in-memory KMS + `KmsEs256Signer`, then verifies
  (autonomous, immediate, and a negative over-budget case).

Run the suite: `./gradlew :credentials:plugins:verifiable-intent:test`

Deliberate scope boundaries: multi-pair L2 (one mandate authorizing several purchases),
`line_items` deep matching, returning the core `Result<T>` type, and plugin/SPI discoverability.
See the module [README](https://github.com/geoknoesis/trustweave/tree/main/credentials/plugins/verifiable-intent)
for details.

## Related documentation

- [Supported plugins](../plugins.md) — full plugin catalog
- [Proofs and proof engines](../../core-concepts/proofs-and-proof-engines.md)
- [SIOPv2 plugin](credential-exchange-protocols/siopv2.md) — another SD-JWT-adjacent exchange plugin
