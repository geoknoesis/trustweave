# AP2 (Agent Payments Protocol) — TrustWeave Integration Feasibility

**Date:** 2026-06-12
**Status:** Feasibility memo (no code). Decision input only.
**Author:** brainstorming session
**Related:** `credentials/plugins/verifiable-intent` (VI module, shipped 2026-06-11), SaaS design (Issuer Studio / Wallet SDK / Verifier Gateway)

---

## 1. Question & verdict

**Can TrustWeave integrate AP2?**

**Yes — and the credential layer is largely already built.** AP2's reference SDK uses the *same* SD-JWT mandate-chain mechanism that the `verifiable-intent` (VI) module already implements: root SD-JWT, ES256/P-256 signing, `cnf` embedded-JWK key binding, and `sd_hash` cross-hop binding. The realistic work is not "build a mandate engine" — it is **(a)** add AP2's high-level data model (Intent/Cart/Payment mandates built on the W3C Web Payments `PaymentRequest`/`PaymentResponse` structures), **(b)** a small chain-format compatibility layer, and **(c)** the A2A extension transport that carries these mandates between agents. TrustWeave already has every cryptographic and credential primitive AP2 needs; the only genuinely *absent* subsystem is the **A2A agent-to-agent transport**.

---

## 2. What AP2 is (verified from spec + reference SDK)

AP2 is Google's open **Agent Payments Protocol** (announced 2025-09-16, 60+ partners incl. Mastercard, PayPal, Coinbase, AmEx). It represents every agent-driven purchase as **three signed Mandates** forming a non-repudiable audit trail, and ships as an **extension to the A2A (Agent2Agent) protocol** (also MCP-compatible).

Repo: `github.com/google-agentic-commerce/AP2` — SDK under `code/sdk/python/ap2/`.

### The three mandates (actual reference-SDK shapes)

| Mandate | Signed by | Key fields (`ap2/models/mandate.py`) | Proof field & format |
|---|---|---|---|
| **IntentMandate** | User | `natural_language_description`, `merchants[]`, `skus[]`, `requires_refundability`, `intent_expiry`, `user_cart_confirmation_required` | (user-signed; HNP authorization) |
| **CartMandate** | Merchant | `contents: CartContents` (`id`, `payment_request: PaymentRequest`, `cart_expiry`, `merchant_name`) | `merchant_authorization` = **JWT** signing `cart_hash` |
| **PaymentMandate** | User | `payment_mandate_contents` (`payment_details_total: PaymentItem`, `payment_response: PaymentResponse`, `merchant_agent`, `timestamp`) | `user_authorization` = **SD-JWT-VC** (issuer JWT + key-binding JWT w/ `cnf`, plus secure hashes of CartMandate and PaymentMandateContents) |

Notes:
- **Format reality vs. marketing.** AP2 is described as "W3C Verifiable Credentials," but the reference SDK proofs are **SD-JWT-VC** and **JWS/JWT** — i.e. the JOSE/SD-JWT family, **not** JSON-LD Data Integrity. This matters: it maps onto TrustWeave's SD-JWT/JOSE path (the one VI uses), not the titanium LD-proof path.
- Mandates carry the **W3C Web Payments** `PaymentRequest` / `PaymentResponse` / `PaymentItem` objects as their commerce payload.
- `JsonWebKey` type pins `kty=EC`, `crv=P-256`, `alg=ES256` — single-suite, exactly VI's profile.

### Transport & roles (A2A extension)
- Extension URI: `https://github.com/google-agentic-commerce/ap2/tree/v0.1`.
- `AP2ExtensionParameters.roles: list[AP2Role]` — role ids: **`shopper`**, **`credentials-provider`**, **`merchant`**, **`payment-processor`**.
- Mandates ride inside A2A messages as data parts; AP2 is declared as an A2A extension rather than a standalone wire protocol.
- **x402 extension** (Coinbase) adds crypto/stablecoin settlement — optional, out of scope for a first cut.

---

## 3. The decisive finding: AP2's credential layer ≡ the VI chain you already shipped

The AP2 reference SDK (`ap2/sdk/mandate.py`, `MandateClient`) and the VI module are **mechanically the same SD-JWT mandate chain**:

| Mechanism | AP2 reference SDK | VI module (already in `main`) |
|---|---|---|
| Root credential | `MandateClient.create()` → **root SD-JWT** | `ViIssuer.createLayer1` → L1 SD-JWT |
| Chain verification | `verify_chain()` over hops (`~~` hop delimiter) | `ChainVerifier.verify` (L1→L2→L3) |
| Signature suite | **ES256 / P-256**, `jwcrypto` JWK | `KmsEs256Signer` + `Algorithm.P256` + `EcdsaSignatureCodec` |
| Key binding | **`cnf`** claim, embedded JWK | `Cnf.kt` — `cnf.jwk` per RFC 7800 |
| Hop binding | **`sd_hash`** (default) / `issuer_jwt_hash` | cross-layer `sd_hash` in `IntegrityChecker` |
| Mandate vocabulary | `open_checkout_mandate`, `checkout_mandate`, `open_payment_mandate`, `payment_mandate` | `Vct.CHECKOUT_OPEN/FINAL`, `PAYMENT_OPEN/FINAL` |
| Selective disclosure | SD-JWT disclosures, `cnf` always disclosed | `Disclosure.kt` (2- and 3-element), `ViSdJwt` |

**Conclusion:** VI is not a "similar" project — it is the **same credential profile** AP2's SDK converged on. The ~80% of AP2 that is cryptography and credential-chaining is **done**.

---

## 4. Substrate fit — TrustWeave capabilities AP2 needs

| AP2 requirement | TrustWeave today | Verdict |
|---|---|---|
| SD-JWT-VC issuance/verification | `SD_JWT_VC` proof format; VI module's `ViSdJwt` codec | ✅ ready |
| ES256 / P-256 signing | KMS `Algorithm.P256`, 15+ backends, `EcdsaSignatureCodec` | ✅ ready |
| `cnf` embedded-JWK binding | VI `Cnf.kt` | ✅ ready |
| `sd_hash` cross-hop binding | VI `IntegrityChecker` | ✅ ready |
| Constraint / policy enforcement | VI `ConstraintChecker` (8 types, generic over `JsonObject`) | ✅ reusable |
| Merchant identity (`did:web`) | 19 DID methods incl. `did:web` | ✅ ready |
| Wallet storage + VP / holder binding + SD | `wallet-core` `CredentialPresentation`, selective disclosure | ✅ ready |
| W3C `PaymentRequest`/`PaymentResponse` data types | — | ❌ absent (small) |
| `issuer_jwt_hash` hop mode (downstream redaction) | only `sd_hash` | ⚠️ partial |
| AP2 chain framing (`~~` delimiter, `MandateClient` conventions) | VI uses its own L1/L2/L3 framing | ⚠️ compat shim |
| **A2A transport** (extension URI, roles, message data parts) | DIDComm v2, OID4VCI/VP, CHAPI — **no A2A, no MCP** | ❌ absent (largest gap) |
| x402 crypto settlement | `anchors:plugins:*` (ethereum/algorand/…) could back it | ➖ optional / later |

---

## 5. Gaps, in priority order

1. **A2A extension transport (the only structural gap).** TrustWeave has rich credential-exchange protocols (DIDComm, OID4VP) but no A2A. Options: (a) implement a minimal AP2-over-A2A binding as a new exchange plugin; (b) tunnel mandates over existing DIDComm for TrustWeave-internal use and add A2A only for external interop. Interop with Google's reference requires real A2A.
2. **AP2 data model + W3C Web Payments types.** Add `IntentMandate`, `CartMandate`/`CartContents`, `PaymentMandate`/`PaymentMandateContents`, and the `PaymentRequest`/`PaymentResponse`/`PaymentItem` value types. Mechanical.
3. **Chain-format compatibility.** AP2's `verify_chain` hop framing and `issuer_jwt_hash` mode differ from VI's L1/L2/L3 framing. Either (a) generalize VI's verifier to accept AP2 hop framing, or (b) write a thin AP2 adapter over VI's crypto. The `issuer_jwt_hash` redaction mode is net-new logic.
4. **IntentMandate is natural-language + allowlists**, whereas VI uses 8 structured constraint types. A small mapping layer translates AP2 IntentMandate constraints (`merchants`, `skus`, `requires_refundability`, expiry) into VI-style `ConstraintChecker` predicates — or AP2 constraints get their own checker reusing the same engine.
5. **x402 / on-chain settlement** — optional; defer.

**What does *not* compress** (per AI-accelerated estimate calibration, these stay slow regardless of tooling): A2A wire-conformance testing against Google's evolving v0.1 reference; the spec is still a draft and will move; on-device hardware-key + biometric user signing is a client concern outside this library; payment-network/issuer integration is partner work, not library work.

---

## 6. Approaches & recommendation

**Approach A — AP2 profile that composes the VI crypto (recommended first cut).**
New module `credentials/plugins/ap2` that *reuses* VI's `Es256`, `ViSdJwt`, `Cnf`, `IntegrityChecker`, `ConstraintChecker` and adds AP2's data model + chain framing + (later) A2A transport. Mirrors how VI itself composed core rather than forking. Lowest risk; ships a verifier + issuer for the three mandates quickly. *Trade-off:* two modules share crypto by dependency, not by a shared abstraction.

**Approach B — Extract a shared `mandate-chain` core, layer VI and AP2 as profiles.**
Refactor the SD-JWT chain crypto out of VI into a reusable core, then express both VI and AP2 as thin profiles. Cleanest long-term if both specs are first-class; *trade-off:* touches the just-shipped VI module and is more up-front work for a still-draft spec.

**Approach C — Feasibility only / wait.** AP2 v0.1 is a moving draft; defer build until the data model stabilizes or a concrete demo need appears.

**Recommendation:** **A**, phased:
- **Phase 1 — AP2 credential profile (small).** Data model + `PaymentRequest`/`PaymentResponse` + issue/verify the three mandates over VI crypto + IntentMandate→constraint mapping. Cross-stack tested against the AP2 reference SDK's vectors (same approach VI used with `vi_vectors.json`).
- **Phase 2 — A2A extension transport (the real new build).** Extension URI, the four roles, mandate-carrying message binding. Decide DIDComm-tunnel vs. native A2A based on whether external Google interop is required.
- **Phase 3 — x402 settlement (optional)** via existing `anchors` plugins.

Promote to Approach **B** only if/when AP2 commitment is firm and we want VI + AP2 to share one engine.

---

## 7. Rough effort (AI-accelerated cadence; build-only, excludes spec-churn & interop hardening)

- Phase 1 credential profile: **~3–5 days** (data model + Payment* types ~1d; AP2 chain adapter over VI ~1–2d; `issuer_jwt_hash` mode ~0.5d; IntentMandate→constraint map ~0.5d; cross-stack vectors + tests ~1d).
- Phase 2 A2A transport: **~1–2 weeks**, dominated by A2A conformance and interop testing (does not compress).
- Phase 3 x402: separate, demand-driven.

---

## 8. Open decisions for the user

1. **Goal of integration** — internal TrustWeave agentic-commerce capability, or **external interoperability** with Google's AP2 ecosystem? (Determines whether real A2A is mandatory or DIDComm-tunnel suffices.)
2. **Module strategy** — sibling `ap2` plugin (Approach A) vs. shared mandate-chain core (Approach B)?
3. **Scope of first cut** — credential profile only (Phase 1), or include A2A transport (Phase 2)?
4. **x402 / on-chain settlement** — in or out for now?

---

## 9. Ties to existing work

- **VI module** — the crypto substrate AP2 reuses; this memo's central finding.
- **SaaS design** — maps cleanly: Issuer Studio mints CartMandates / credential-provider creds; Wallet SDK signs Intent/Payment mandates; Verifier Gateway runs the chain verifier + constraint checker.
- **Anchors plugins** — candidate backing for x402 settlement.
- **Estimate calibration** — A2A interop and draft-spec churn are the non-compressible costs.
