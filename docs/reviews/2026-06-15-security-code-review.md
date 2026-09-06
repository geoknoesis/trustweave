# TrustWeave — Detailed Code & Security Review (security-critical core)

**Date:** 2026-06-15
**Scope:** Security-critical core only — KMS/crypto primitives, DID resolution (key/web/ethr), VC issuance/verification + proof suites + JSON-LD, SD-JWT + Verifiable-Intent mandate chain, wallet + credential-exchange protocols (DIDComm/OID4VCI/OID4VP/SIOP/PEX), and cross-cutting infra (SPI/plugin loading, facade, error handling, anchors). NOT a whole-repo review.
**Method:** 6 parallel domain reviewers (read-only, evidence-cited) + manual verification of the highest-severity findings against current source.
**Verification key:** ✅ = personally re-verified against code; ◑ = agent-traced with high confidence, not independently re-read.

---

## Executive summary

**Overall posture: strong core, weak edges.** The cryptographic heart of TrustWeave — signing/codec correctness, algorithm/key-type binding, VC signature/issuer/proof-purpose enforcement, JSON-LD safety, SD-JWT holder binding, DIDComm sender authentication, EIP-155 anchoring — is genuinely well-hardened, with the prior P0–R4 rounds clearly visible in the code. **No CRITICAL or HIGH issue was found in the core crypto or VC-verification engines.**

The real risk is concentrated in two places:
1. **The credential-exchange protocol clients** (OID4VP/OID4VCI/SIOP/PEX), which parse untrusted network input and are missing baseline protections (SSRF guards, body caps, PKCE/state, a privacy-critical selective-disclosure step, a ReDoS sink).
2. **One authorization-logic gap in the new Verifiable-Intent mandate verifier**, where constraint enforcement can be skipped by the very party it is meant to bound.

A telling contrast runs through the codebase: the DID `DefaultUniversalResolver` has an exemplary SSRF guard + 1 MB body cap + timeout, but the native `did:web` resolver and every OID4* fetch have none. The fix pattern already exists in-repo; it just isn't applied consistently.

| Severity | Count |
|---|---|
| HIGH | 7 |
| MEDIUM | 16 |
| LOW / hygiene | ~16 |

---

## HIGH findings

### H1. Verifiable-Intent: constraint/authorization bypass when the L2 payment mandate is omitted ✅
- **Location:** `credentials/plugins/verifiable-intent/.../verification/ChainVerifier.kt:116-119, 183-203, 271, 323-324`
- **Issue:** In autonomous mode, `payment`/`checkout` are resolved from the **agent-supplied** L2 presentation (`l2.resolve()`, line 80). `agentJwk` only needs *one* open mandate (line 174). A malicious agent can disclose only the **checkout-open** mandate, **omit the payment-open** mandate (so `payment == null`), and still present a valid `l3Payment` + `l2RoutedForPayment`. Because every payment-side guard is gated on `payment?.`/`l2Payment != null`:
  - constraint enforcement block (`payment?.let { ConstraintChecker.check(...) }`, lines 193-202) **does not run**,
  - pair-identity binding (`expectedPairDisc != null`, line 271) is **skipped**,
  - `paymentInstrumentCrossCheck` returns `null`/pass for a null L2 (line 324).
  The L3a payment mandate is still accepted (`valid=true`).
- **Impact:** A compromised/malicious delegated agent can have an **over-budget / arbitrary-payee / arbitrary-instrument** payment mandate accepted as a valid chain — defeating the module's central guarantee that the network, not the agent, enforces the user's constraints. Authorization bypass / privilege escalation.
- **Fix:** After mode inference, require that a supplied `l3Payment` has a non-null L2 `payment` mandate (and `l3Checkout` ⇒ `checkout`); make `expectedPairDisc`/`l2PaymentMandate` non-nullable on the payment L3 path and fail closed when absent. Add a negative test (checkout-only L2 + L3a payment ⇒ reject).

### H2. Verifiable-Intent: `allowed_payees` / `allowed_merchants` fail OPEN when the allowlist is all SD-references ◑
- **Location:** `credentials/plugins/verifiable-intent/.../verification/ConstraintChecker.kt:89-95` (`matchAllowlist`: `if (inline.isEmpty()) return null`)
- **Issue:** When every allowlist entry is a `{"...": digest}` SD-reference — exactly the shape in the reference fixture (`vi_autonomous_fixture.json:14`) — `inline` is empty and the check returns *satisfied* without resolving the referenced disclosures or comparing the fulfillment payee/merchant. The comment says "resolved out-of-band," but no out-of-band enforcer exists in this path.
- **Impact:** In the normal SD-ref configuration, payee/merchant allowlists provide **zero enforcement** — an agent can pay an arbitrary payee and the constraint passes vacuously. Compounds H1.
- **Fix:** Resolve the SD-ref digests against the presented disclosures (the verifier already holds `discByHash`), match resolved entries against the fulfillment, and fail closed if the referenced disclosures are absent.
- *Confidence: high (agent traced against fixture + code); recommend confirming with a unit test.*

### H3. did:web resolution has no SSRF protection ✅
- **Location:** `did/plugins/base/.../AbstractWebDidMethod.kt:114-148`; URL built in `did/plugins/web/.../WebDidMethod.kt` (`getDocumentUrl`)
- **Issue:** `resolveFromHttp` calls only `validateHttps(url)` (scheme check) then `httpClient.newCall(request).execute()`. No check that the resolved host is public — loopback/`localhost`/`169.254.169.254`/RFC-1918/link-local are all reachable. Reachable from the normal `resolve(did)` API with an attacker-controlled DID string (issuer DID, holder DID, controller reference).
- **Impact:** Classic SSRF — cloud metadata/credential theft (IMDS), internal port scanning, hitting internal admin endpoints.
- **Fix:** Add the SSRF guard already implemented in `DefaultUniversalResolver` (resolve host→IP, reject private/loopback/link-local; re-check after redirects via a DNS/interceptor to defeat rebinding + redirect-to-internal). Apply to resolve/publish/update/deactivate.

### H4. did:web response body read with no size limit ✅
- **Location:** `did/plugins/base/.../AbstractWebDidMethod.kt:143-147` (`body.string()`)
- **Issue:** Entire response buffered into a String with no cap (the Universal Resolver caps at 1 MB; this path does not).
- **Impact:** Memory-exhaustion DoS; amplified by H3 (attacker picks the host).
- **Fix:** `byteStream().readNBytes(MAX+1)` and reject over limit.

### H5. OID4VP: selective disclosure is silently ignored — full credential always disclosed ◑
- **Location:** `credentials/plugins/oidc4vp/.../Oidc4VpService.kt:322-346` (`selectedFields` accepted but never read; VP built from `selectedCredentials.map { it.credential }`, full subject embedded at `:967-973`)
- **Issue:** The exchange layer computes and passes `selectedFields`, so callers believe minimization happens, but the body ignores it and transmits the whole credential.
- **Impact:** Privacy/over-disclosure — a holder selecting "name + email only" still sends DOB, address, government IDs, etc. Shipping today.
- **Fix:** Honor `selectedFields` (redact `credentialSubject` to selected paths; prefer SD-JWT/BBS for unlinkable disclosure), or remove the parameter and fail loudly.

### H6. OID4VCI: no PKCE/`state`, and issued credential never verified ◑
- **Location:** `credentials/plugins/oidc4vci/.../Oidc4VciService.kt:641-653` (token exchange, no `code_verifier`), `:254-317` (returns caller-supplied placeholder, ignores issuer response, no signature/issuer/holder-binding verification)
- **Issue:** Authorization-code flow has no PKCE and no `state` (repo-wide grep confirms absence); the wallet stores whatever the issuer endpoint returns without cryptographic verification.
- **Impact:** An intercepted authorization code is replayable to mint the holder's credential (no CSRF/code-injection defense); a malicious/MITM issuer can plant forged credentials the wallet later presents as genuine. This is the wallet's primary inbound trust boundary.
- **Fix:** Add PKCE (S256, ≥256-bit verifier) + `state` generate/verify; parse and verify the returned credential (signature vs resolved issuer keys, issuer match, `cnf` holder binding) before storing.

### H7. Untrusted-fetch SSRF + unbounded bodies across OID4VP / OID4VCI / SIOP, and ReDoS in PEX ◑
- **Locations:** `Oidc4VpService.kt:424-461` (`request_uri`), `:853-860` (metadata); `Oidc4VciService.kt:357,476,588,687,826,1006,1126` (https-or-loopback check at `:1062-1081` still permits `https://10.x`/`192.168.x` and redirect bypass); `SiopV2Service.kt:139-149`; **PEX ReDoS** at `credentials/plugins/presentation-exchange/.../PresentationDefinitionMatcher.kt:229-232` (`Regex(pattern)` from the untrusted `PresentationDefinition`, backtracking engine, no timeout)
- **Issue:** `request_uri`/`credential_offer_uri`/`response_uri`/metadata URLs are attacker-steerable (QR/offer) and fetched with a default `OkHttpClient()` (follows redirects, no call timeout, no body cap). The PEX matcher compiles an attacker-supplied regex with no guard.
- **Impact:** SSRF to internal services/cloud metadata (esp. server-hosted wallets), OOM/slow-loris DoS, exfiltration of disclosed VP claims to an attacker-chosen `response_uri`; a malicious verifier hangs the wallet thread with a catastrophic-backtracking pattern.
- **Fix:** Centralize a hardened HTTP client (https-only, reject private/loopback/link-local on every redirect hop, body-size cap, explicit timeouts); pin `response_uri`/`request_uri` host to the authenticated `client_id`. For PEX, use RE2 (`com.google.re2j`) or a watchdog timeout + pattern-length cap.

---

## MEDIUM findings (condensed)

| # | Finding | Location |
|---|---|---|
| M1 | VI `amount_range` is `Int`-typed; an over-`Int` `max` parses to `null` → **no upper bound enforced**; negatives not rejected | `vi/.../ConstraintChecker.kt:79-82,108`; `model/Constraint.kt:102` |
| M2 | PKCS#11 secp256k1 signatures not low-s normalized (contract violation; null-algorithm path) → rejected by Ethereum/Bitcoin + malleable | `kms/plugins/pkcs11/.../Pkcs11KeyManagementService.kt:220-231,411-415` |
| M3 | JsonWebSignature2020 ECDSA verifier picks curve from header `alg` without cross-checking the JWK `crv`/`kty` | `credentials/credential-api/.../DefaultJsonWebSignature2020Adapter.kt:125-159` |
| M4 | Proof `verificationMethod` DID not bound to issuer DID; fragment-only fallback match (key still issuer's, so not forgery) | `credentials/credential-api/.../ProofEngineUtils.kt:245-271` |
| M5 | No pre-canonicalization size/blank-node bound before super-linear RDFC-1.0 (claim-count cap only) → verifier DoS | `credentials/credential-api/.../JsonLdUtils.kt:90-131` |
| M6 | did:key DID length unbounded → O(n²) base58 decode CPU DoS | `did/.../validation/DidValidator.kt:121-128`; `core/util/Base58.kt:48-59` |
| M7 | did:ethr ignores the DID `network` segment (uses configured chain) and doesn't assert returned `document.id == requested DID` | `did/plugins/ethr/.../EthrDidMethod.kt:190-228`; `AbstractBlockchainDidMethod.kt:176-181` |
| M8 | did:key accepts wrong-length Ed25519/X25519 keys (no 32-byte check) | `did/plugins/key/.../KeyDidMethod.kt:334-346` |
| M9 | DIDComm: no replay/`id`/`thid` dedup; `expires_time` parsed but never enforced | `credentials/plugins/didcomm/.../DidCommService.kt:136-156` |
| M10 | `EncryptedFileLocalKeyStore` is non-functional — `Secret` (de)serialization throws "not implemented" (fails closed, but advertised prod store can't persist keys) | `credentials/plugins/didcomm/.../EncryptedFileLocalKeyStore.kt:164-212` |
| M11 | SIOP/OID4VP non-DID `client_id` schemes accept request-object claims with only self-attested `jwks` (no x509/pre-registered validation) | `SiopV2Service.kt:362-381`; `Oidc4VpService.kt:537-545` |
| M12 | Config JSON (may hold provider secrets) captured untruncated in exception context (sibling `SerializationException` caps at 500) | `common/.../exception/ConfigException.kt:46` |
| M13 | Anchor verify trusts a single RPC node; no confirmation-depth / re-org protection | `anchors/plugins/evm-base/.../AbstractEvmAnchorClient.kt:272-305` |
| M14 | Anchor read/verify never asserts returned tx id == requested `txHash` nor checks sender (anyone can re-anchor a public digest) | Algorand `:482-538`, Bitcoin `:209-210`, EVM `:122-150` |
| M15 | Algorand sponsor-key parse failure fails OPEN to `SponsorNotAllowed` (inverse of the fail-closed main key path) | `anchors/plugins/algorand/.../SponsorRegistry.kt:53-57` |
| M16 | OID4VP `nonce` optional outside HAIP; nonce-less VP tokens minted (no replay binding) | `Oidc4VpService.kt:1018-1021` |

---

## LOW / hygiene (selected)

- did:web allows `.`/`..` path segments (same-host path escape) — `WebDidMethod.kt`.
- NIST-curve ECDSA not low-s normalized (deliberate; document if any caller treats signature bytes as identity).
- VC revocation `FAIL_OPEN` accepts silently; `checkRevocation=false` skips a present `credentialStatus` (caller config; defaults safe).
- `hasValidContext()` doesn't enforce base-context-first (conformance only); `status()` doesn't consult the engine `CredentialStatusChecker` SPI (verify path is authoritative).
- DatabaseWallet stores credential JSON in a plaintext column (relies on DB-level encryption; FileWallet uses AES-GCM).
- OID4VCI interpolates upstream response bodies (possible `c_nonce`/token) into exception messages.
- `Es256.verify` relies on Nimbus implicit alg-pinning (confirmed safe in 9.48) — assert ES256 explicitly for defense-in-depth; at L2 the explicit `header()` alg check runs *after* `Es256.verify`.
- VI verification `now` is caller-supplied with no trusted-clock fallback; `expectedL2Nonce`/`expectedL2Aud` default to skipped.
- Anchors accept plaintext `http://` RPC on override (defaults are https; Indy plugin already validates scheme).
- Stale Ethereum SPI registration file referencing a non-existent class/package; `bin/` build-output copies and a `.claude/worktrees/*` full-repo copy inflate audit/grep surface.
- `slf4j-api` pinned to 1.7.36 (EOL line); risky only if paired with old Logback 1.2.x (backend not pinned in the catalog). BC 1.84 / Nimbus 9.48 / Titanium 1.7.0 are current.

---

## What's already strong (verified)

- **KMS/crypto:** DER↔P1363 transcoding correct per curve (32/48/66B, sign-byte stripping, minimal-length DER parsing); secp256k1 low-s with the real group order; `SecureRandom` only; `sign()` validates requested algorithm against key type (`isCompatibleWith`), blocking confusion/downgrade; private keys never logged/serialized/returned.
- **VC verification:** verification method resolved from the **issuer** DID doc and must be under `assertionMethod`; proof-purpose enforced; `alg:none` impossible and VC-JWT engine not registered (no alg-confusion); Data-Integrity payload binds proof options; remote `@context` fetching disabled (bundled W3C contexts); fail-closed canonicalization with a dropped-claim detector; SD-JWT signed-claim authority + `cnf`/KB-JWT holder binding + replay window; version-aware temporal checks on by default.
- **DID:** `DefaultUniversalResolver` SSRF guard + 1 MB cap + timeout; did:key EC on-curve validation (invalid-curve safe); strict key-document signature verification; fallback only on `MethodNotRegistered`; cache keyed per-DID, success-only.
- **VI:** ES256/`crit`/blank-signature/`alg:none` all rejected (confirmed against Nimbus 9.48 bytecode); disclosure digests recomputed against the *signed* `_sd` set; cross-layer `sd_hash` over literal token bytes; mandate-smuggling/duplicate-ref detection; agent-key consistency; unknown constraint *types* fail closed; real negative tests.
- **Wallet/exchange:** DIDComm sender authenticated from the actual ECDH-1PU key (never plaintext `from`); JWS rigorous (`alg=EdDSA`, `crit`/`b64:false` rejected, `kid` bound); insecure placeholder crypto removed (fail-closed); FileWallet AES-GCM at rest with random IV + path-traversal containment; OID4VP/SIOP request-object DID pinning robust and tested; OID4VCI mix-up defenses (RFC 8414, offer pinning, scoped retry); PEX fails closed on bad JSONPath; no plaintext secret logging.
- **Infra/anchors:** EIP-155 chain-id signing (not the legacy replayable overload); fail-closed verify with constant-time digest compare; plugin registry rolls back partial registration; SPI discovery error-isolated and deterministic; no dev backdoors (no trust-all/TLS-disable/fake-auth in `src/main`); no production secrets (all key-looking hits are public constants / vendor examples / test dummies); safe `quickStart` defaults.

---

## Coverage & caveats

- Scope was the **security-critical core**; out of scope: BBS/mdoc/JAdES proof plugins, `vc-api-server` HTTP layer, most non-EVM anchor chains (Cardano/Indy/zkSync/etc. spot-checked), and the full 86-module assembly's merged `META-INF/services` ordering (no build run).
- All findings are static (read-only; no builds/tests). H1, H3, H4 personally re-verified against source; H2/H5/H6/H7 and the MEDIUMs are agent-traced with cited line numbers and high confidence but were not all independently re-read.
- Recommended deeper pass for full coverage: the cloud `/code-review ultra` (user-triggered) over the whole branch, plus targeted unit tests/PoCs for H1 and H2.

---

## Recommended remediation order

1. **H1 + H2** (VI constraint/authorization bypass + vacuous allowlists) — shipped on `main`; smallest, highest-impact fix; add negative tests.
2. **H3 + H4 + H7** (SSRF + body caps) — implement one hardened HTTP client and apply to did:web and all OID4*/SIOP fetches; reuse the `DefaultUniversalResolver` guard.
3. **H5** (OID4VP over-disclosure) — privacy bug shipping today.
4. **H6** (OID4VCI PKCE/state + issued-credential verification).
5. MEDIUMs, then LOW/hygiene.

---

## Remediation status (updated 2026-08-20)

All HIGH and all MEDIUM findings are closed on local `main`. Each fix was driven by a test that
failed first; where a finding turned out to be different from its description, that is recorded
below rather than quietly reinterpreted.

### Findings that were worse than described

- **M1** was not only an over-`Int` overflow. *Any* `amount_range` bound the parser could not read
  became "no ceiling", so a plain decimal (`1000.0`) deleted a declared cap at ordinary amounts. A
  5000 payment passed a 1000 cap.
- **M3** was not merely missing defence in depth. `buildEcdsaVerifier` chose the curve from the
  attacker-controlled header and never read the JWK, so a key declaring `crv: P-384` verified an
  ES256 signature and a `kty: OKP` key was used on the ECDSA path — both accepted outright.
- **M15**'s private-key path was worse than a swallowed error: the Algorand SDK's base64 decoder
  skips characters outside the alphabet, so a mistyped key decoded to different bytes and produced
  a real, working, unintended sponsor account rather than any error.
- **Es256.verify** (listed as LOW) accepted an ES512 token with a P-521 `cnf.jwk`, in a module whose
  contract is that ES256 is the only permitted algorithm.

### Findings that were already closed

- **M4** — a proof naming a verification method under another DID never resolved: lookup only ever
  searches the issuer's own document. The flagged fragment fallback was dead code, comparing
  against `"#${keyId.value}"` while `KeyId` already carries its `#`. Removed; regression tests
  added. The adjacent string-equality clause is load-bearing and was kept.
- **M2** — PKCS#11 now rejects `Secp256k1` outright, so the non-normalized path cannot be reached.

### Accepted as-is, with reasons

- **NIST-curve ECDSA not low-s normalized** — deliberate. Low-s matters where signature bytes are
  treated as an identity (Ethereum, Bitcoin); secp256k1 *is* normalized. No caller treats P-256/384/521
  signature bytes as identity.
- **`checkRevocation=false` skips a present `credentialStatus`** — caller configuration, and the
  default is `true` with `FAIL_CLOSED`. `FAIL_OPEN` no longer accepts silently: it logs a warning
  naming the credential, so accepting with unknown status is visible afterwards.
- **`hasValidContext()` does not enforce base-context-first** — conformance only; the signature
  covers the context regardless of ordering.
- **`status()` does not consult the engine `CredentialStatusChecker` SPI** — the verify path is
  authoritative and does consult it; `status()` is a convenience reader.
- **DatabaseWallet stores credential JSON in a plaintext column** — deliberate: it delegates to
  database-level encryption, which is how deployments key and rotate at rest. `FileWallet` uses
  AES-GCM because it has no such layer beneath it. Changing this is a storage-architecture decision,
  not a bug fix.
- **`slf4j-api` pinned to 1.7.36 (EOL)** — deliberately not bumped. Moving a *library's* facade to
  2.x silently breaks every consumer still on Logback 1.2.x: they get "no providers found" and lose
  logging entirely. No backend is pinned in the catalog, so consumers choose, and 1.7.x is the
  maximally compatible choice. This is a release-policy call for the maintainer, not a security fix.

### Out of scope but observed

- `MAX_CREDENTIAL_SIZE_BYTES` and `MAX_PRESENTATION_SIZE_BYTES` were defined but enforced nowhere —
  the only references were assertions that the constants are positive. The credential one now bounds
  `JsonObject.toCredential()`. The presentation one is still unenforced: presentations reach the
  service already parsed, so there is no boundary at which its size is a property of the input.
- The ktlint baselines were stale repo-wide — earlier commits reformatted files without regenerating
  them, so they carried entries for violations that no longer existed. Every module touched during
  remediation was re-anchored; `did/plugins/base` alone fell from 321 entries to 66.
- Seven git worktrees under `.claude/worktrees/` are full repo copies that inflate grep and audit
  surface. **Six of the seven hold uncommitted or untracked files** and were left alone: removing
  them would destroy work that exists nowhere else.
