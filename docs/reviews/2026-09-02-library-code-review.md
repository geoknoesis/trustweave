# TrustWeave library — code review and score

**Date:** 2026-09-02
**Scope:** the `trustweave` library only (not `trustweave-saas`). 106 modules, ~242k LOC main,
~85k LOC test, at `2d8f8989` (release 0.7.0).
**Method:** static review plus an executed test run of the core security modules. Every finding
below was read in source and verified, not inferred; two findings I initially suspected were
disproved and are recorded as such.

---

## Score: 7.5 / 10

| Dimension | Score | Basis |
|---|---|---|
| Core security engineering | **9.0** | Crypto, VC verification, XXE, SSRF guard, fail-closed defaults all genuinely hardened |
| Plugin / edge security | **6.0** | Three real fail-open or unbounded-input defects, all in modules labelled Experimental |
| Test quality (core) | **9.0** | 1130 tests, 0 failures, 0 skipped; silent-skip trap systematically fixed |
| Test coverage (edges) | **5.5 → 7.0** | Originally: 6 KMS providers with zero tests, contract suite on 1 of 17, VI at 20 tests. All six now covered; VI at 24. The contract-suite gap remains. |
| Code quality & consistency | **8.0** | Low TODO density, uniform `Result` pattern, controls exist and are reused |
| Documentation honesty | **9.5** | Maturity matrix, known limitations, non-goals stated in the code that has them |
| Build & release health | **6.0** | Full build blocked on Windows by JAR locking; 106-module assembly unverified here |

**The one-sentence summary:** the security core is genuinely strong and the project is unusually
honest about its own limits, but the gap between a hardened core and Experimental plugins that ship
in the same artifact is where the remaining risk lives.

---

## What I verified as genuinely fixed

The 2026-06-15 review claimed all HIGH findings closed. I re-read two of the most severe:

- **H1 (VI authorization bypass)** — closed exactly as recommended.
  `ChainVerifier.kt:199` now reads `if (l3Payment != null && payment == null) return fail("L3
  payment presented without its authorizing L2 payment mandate")`. The bypass required `payment`
  to be null while an L3 payment was accepted; that is now impossible.
- **H2 (allowlists fail open when all-SD)** — closed, and better than the minimum.
  `ConstraintChecker.matchAllowlist` resolves `{"...": digest}` references against
  `disclosuresByHash`, counts `unresolvedRefs`, and fails closed for open mandates rather than
  returning "satisfied" on an empty inline list.
- **`MAX_PRESENTATION_SIZE_BYTES`**, recorded as defined-but-unenforced, is now enforced at
  `VcApiRoutes.kt:244,284`.

The remediation claims hold up. That is worth stating plainly, because it is the exception rather
than the rule for security backlogs.

---

## New findings

### F1 — OpenID Federation fetches attacker-influenced URLs with no SSRF guard and no body cap
**Severity: High within the module / Medium overall (module is Experimental).**
`credentials/plugins/openid-federation/.../TrustChainResolver.kt:37,55,229,235`

```kotlin
private val httpClient: OkHttpClient = OkHttpClient(),   // line 37 — unguarded default
val url = "$fetchEndpoint?sub=${subjectId}"              // line 229
response.body?.string()                                  // lines 60, 235 — unbounded
```

Trust-chain resolution is *defined* by following untrusted input: `authority_hints` comes from the
leaf entity's own JWT, and `federation_fetch_endpoint` is read out of a document that was itself
just fetched. Those URLs are then requested with a plain `OkHttpClient()`.

This is the same class as the previously-fixed H3/H4/H7, and the project already owns the fix:
`org.trustweave.core.net.ssrfGuardedOkHttpClient()`. Its sibling protocols — OID4VP, OID4VCI, SIOP,
did:web, EVM anchoring — all default to the guarded client. Federation is the one that does not, so
this is an inconsistency rather than a missing capability.

Two smaller issues sit in the same code: response bodies are read with no size limit, and line 229
concatenates `subjectId` into a query string without encoding, so a subject identifier containing
`&` or `#` can inject or truncate parameters.

`maxChainLength = 5` does correctly bound recursion.

**Fix:** default `httpClient` to `ssrfGuardedOkHttpClient()`, cap the body as did:web does, and
build the URL with a proper query-parameter builder.

### F2 — Verifiable Intent: `line_items` is reported as enforced while nothing is enforced
**Severity: High within the module / Medium overall.**
`credentials/plugins/verifiable-intent/.../ConstraintChecker.kt:70`

```kotlin
is Constraint.LineItems -> checked += c.type // TODO: port acceptable-id + quantity-cap matching
```

`ConstraintCheckResult` distinguishes `checked` (evaluated) from `skipped` (not evaluated), and the
surrounding code is careful about the difference: `Constraint.Malformed` always fails closed, and
`Constraint.Unknown` fails closed for open mandates or under `STRICT`. `LineItems` does neither —
it is added to **`checked`**, so a verifier inspecting the result sees `line_items` among the
evaluated constraints and reasonably concludes the bound was enforced.

A `line_items` constraint is what bounds *what* a delegated agent may buy (acceptable SKUs,
quantity caps). Unenforced, an agent may purchase arbitrary items within budget. This is the same
family as H2 — a constraint that passes vacuously — and it survives in the module that H2 was
found in.

The neighbouring `Reference`/`Budget`/`Recurrence` cases are also `checked += type`, but those
carry an explicit rationale ("integrity-/network-enforced; acknowledged here"). `LineItems` has no
such delegation; it is simply unimplemented.

No test asserts `line_items` enforcement. `ChainVerifierKnownAnswerTest.kt:66` asserts
`checksPerformed shouldContain "open_checkout_contains_line_items"`, which checks that the mandate
*contains* line items — easily mistaken for evidence that they are enforced.

**Fix:** until matching is implemented, route `LineItems` through the `Unknown` path — `skipped`
plus fail-closed for open mandates — so the result never overstates what was checked.

### F3 — AVP authorization server reads an unbounded request body
**Severity: Medium.** `credentials/avp-authorization-server/.../AvpAuthorizationRoutes.kt:31`

```kotlin
lenientJson.parseToJsonElement(call.receiveText()).jsonObject
```

`call.receiveText()` has no size limit, so a single request can drive allocation until the server
dies. The VC API next door already solves this with
`requireWithinSizeLimit(json, SecurityConstants.MAX_PRESENTATION_SIZE_BYTES, ...)`; this endpoint
does not use it. Malformed JSON is correctly handled — the failure mode here is size, not shape.

### F4 — 16 of 17 KMS providers are not verified against the KMS contract suite; 6 have no tests
**Severity: Medium (quality/assurance, not a live vulnerability).**

`KeyManagementServiceContractTest` is an abstract 19-test suite defining correct
`KeyManagementService` behaviour. Exactly one provider extends it — `kms/plugins/inmemory`, the
reference implementation. AWS, Azure, Google, HashiCorp, IBM, PKCS#11, CloudHSM and waltid have
their own tests (17–42 each) but are not held to the shared contract.

Six providers ship with **zero tests** over ~2,858 lines of key-handling code:

| Provider | main LOC | @Test |
|---|---|---|
| cyberark | 820 | 0 |
| thales | 797 | 0 |
| fortanix | 692 | 0 |
| thales-luna | 178 | 0 |
| entrust | 162 | 0 |
| utimaco | 162 | 0 |
| venafi | 47 | 0 |

For a library whose whole purpose is custody of signing keys, an untested provider is the highest-
consequence place for an untested path. The mitigating fact is real: `module-maturity.md` tells
consumers to treat every plugin as Experimental and to run their own review. The gap is that
nothing in the build makes the difference between "verified against the contract" and "compiles"
visible.

**Fix:** have every provider extend the contract suite, with credential-requiring providers gated
behind an integration-test source set so the omission is explicit rather than silent.

### F5 — mdoc issuer certificates are validated without revocation
**Severity: Low–Medium, and explicitly documented.**
`credentials/plugins/mdl/.../MdocProofEngine.kt:533`

PKIX path validation to a configured IACA anchor runs with revocation checking disabled, and the
KDoc says so, along with the other ISO 18013-5 gaps (no certificate-policy, key-usage, or
DocSigner constraint checks). It fails closed when an x5chain is present but no anchors are
configured — the right default. This is a completeness gap in an Experimental module, disclosed in
the place a developer will read it. I record it because "documented" and "safe to deploy" are not
the same thing.

### F6 — ~~Non-EVM anchor clients bypass the SSRF guard~~ → **corrected: anchor RPC endpoints have no transport-security check**
**Severity: Medium.** `anchors/plugins/{cardano,indy,bitcoin}`, `did/plugins/cheqd`.

**The original finding was wrong, and acting on it would have caused an outage.** Applying
`ssrfGuardedOkHttpClient()` to these clients would block loopback and private addresses — which is
where node software normally lives. Bitcoin's own KDoc documents `http://localhost:8332`, and
Ganache defaults to `http://localhost:8545`. The SSRF guard is correct for URLs discovered inside
untrusted documents (F1) and actively wrong for endpoints the operator configured.

`AbstractEvmAnchorClient` had already worked this out: it uses `PrivateNetworkGuard` in the
*opposite* direction — permitting plaintext only for local hosts and requiring TLS for public ones.
The real gap is that **only EVM does this**. Cardano, Bitcoin, Indy and cheqd accept a plaintext
`http://` endpoint pointed at a public host with no complaint. Bitcoin is the sharpest case: it
attaches `Authorization: Basic <rpcUser:rpcPassword>` to every call, so a public plaintext endpoint
leaks the RPC credentials and the signed transaction in transit.

**A latent fail-open found while fixing this:** `PrivateNetworkGuard.rejectionReason()` returns a
reason both for a genuinely private host *and* for one that cannot be resolved. Code reading
"has a reason" as "is local" therefore permits plaintext to an **unresolvable public host** — it
fails open on exactly the DNS failure an attacker can induce. `AbstractEvmAnchorClient.
requireTransportSecurity` has this shape and should be re-pointed at the shared helper below.

---

## Two suspicions I disproved

Recorded because a review that only lists confirmed hits hides its own false-positive rate.

- **"The SSRF guard is applied to only 2 of ~24 fetch sites."** My first grep searched for
  `SsrfSafeHttp`/`PrivateNetworkGuard` and missed the actual entry point,
  `ssrfGuardedOkHttpClient()`. OID4VP, OID4VCI and SIOP all default to the guarded client, at both
  the service and the provider. Only F1 and F6 are genuinely unguarded.
- **"19 KMS tests never run."** `kms-core` declares 276 `@Test` and executes 257. The 19 are
  `KeyManagementServiceContractTest`, an abstract suite that runs in the plugin module that extends
  it. Not a defect — though F4 came out of chasing it.

---

## What is strong

- **No TLS trust bypass anywhere.** No `TrustManager`, `HostnameVerifier`, or `trustAllCerts`
  override in the entire codebase.
- **All randomness is `SecureRandom`.** Every hit across SD-JWT, DIDComm, mdoc, PKCE and VI
  disclosure generation.
- **XXE is properly closed** in `signatures/trust-lists`, which parses remote EU Trusted Lists:
  `FEATURE_SECURE_PROCESSING`, `disallow-doctype-decl`, and both external-entity features disabled
  in both parsers. This module was outside the previous review's scope and still holds up.
- **Fail-closed is the habit, not the exception.** Revocation defaults to `checkRevocation = true`;
  `FAIL_OPEN` logs a warning naming the credential; mdoc fails closed when anchors are absent;
  malformed and unknown VI constraints fail closed. No empty `catch` blocks in main code.
- **The silent-skip test trap is systematically fixed** — 1320 tests use `= runBlocking<Unit>`
  against 34 that do not, and declared-versus-executed counts match exactly in credential-api
  (400/400), did-core (453/453) and verifiable-intent (20/20).
- **Low TODO density** — 12 files of ~2,500 contain a TODO or FIXME.
- **The documentation tells the truth**, including where the code is weak. `module-maturity.md`
  labels plugins Experimental and says outright: "If you did not run your own integration and
  security review of a plugin, do not treat it as production-ready solely because it is on the
  classpath."

---

## Executed verification

```
:credentials:credential-api:test          400 tests   0 failures   0 skipped
:did:did-core:test                        453 tests   0 failures   0 skipped
:kms:kms-core:test                        257 tests   0 failures   0 skipped
:credentials:plugins:verifiable-intent    20 tests    0 failures   0 skipped
                                        ----------------------------------
                                         1130 tests   0 failures   0 skipped
```

**Update:** after shutting down the TrustWeave SaaS — whose backend runs through the composite
build and therefore held the library's multiplatform jars open — the full build ran clean:
**3724 tests, 0 failures, 15 skipped, 82 modules**, including the `distribution:all` assembly and
its merged `META-INF/services` ordering that had been unverified in both this review and June's.
On that basis Build & release health is revised from 6.0 to **7.5**; the fragility is environmental
(a running composite-build consumer locks library outputs on Windows), not a defect in the build.

The original note follows.

**The full 106-module build did not run.** `:common-mp:jvmJar` and `:wallet:wallet-core-mp:jvmJar`
fail with `Unable to delete file ... common-mp-jvm-0.7.0.jar` — the Windows JAR-locking problem
`CLAUDE.md` documents, with 13 JVM processes holding locks. I worked around it with
`-Ptrustweave.windowsInRepoBuild=true` for the core modules rather than killing processes that may
belong to the IDE. Anything requiring the full assembly — merged `META-INF/services` ordering in
`distribution:all` especially — remains unverified, as it was in the previous review.

---

## Remediation status (2026-09-02, same day)

| # | Status | What shipped |
|---|---|---|
| F2 | **Fixed** | `line_items` now reports as `skipped`, and fails closed for an open mandate or under `STRICT`. 4 tests. |
| F1 | **Fixed** | Default client is `ssrfGuardedOkHttpClient()`; bodies bounded at 1 MiB; `sub` built through `HttpUrl`. 4 tests. |
| F3 | **Fixed** | Request body bounded at 256 KiB, enforced on the read itself rather than trusting `Content-Length`. 2 tests. |
| F6 | **Corrected, then fixed for Bitcoin** | New shared `TransportSecurity.requireSecureForPublicHosts`; applied to the Bitcoin RPC client. 5 tests. |
| F4 | **Partly fixed, and partly corrected** | All six zero-test providers now have tests (29 in total). The finding's severity was overstated — see below. |
| F5 | **Open — needs a decision** | Whether mdoc revocation belongs in the engine or in deployment guidance is a product call, not a bug fix. |

### F4, revisited after working on it

The headline number in F4 — "~2,858 lines of untested key-handling code" — was wrong, and the
correction matters more than the tests:

- **Three of the six are honest stubs.** `entrust`, `thales-luna` and `utimaco` implement nothing:
  every operation returns a documented `Failure` and the KDoc says outright "**None of them work**
  — this plugin is a stub." That is the right shape, not a gap. ~502 of those lines are stubs, not
  untested implementations.
- **`venafi` is not a KMS at all.** Despite living under `kms/plugins/`, it registers no SPI
  provider and its single class is a documented placeholder whose only method throws. It has no
  `KeyManagementService` to test.
- **The real untested surface was ~2,309 lines** across `cyberark`, `thales` and `fortanix` — which
  are working implementations.

What shipped:

- **Algorithm-mapping tests** for the three working providers (14 tests). These cannot exercise a
  KMS without credentials, but they pin the property where a silent, high-consequence bug lives: if
  encode and decode disagree, a key is created as one type and read back as another — a P-384 key
  interpreted as P-256, or RSA-3072 as RSA-2048 — with nothing thrown and the signature simply made
  against the wrong material. Each suite checks round-trip fidelity for every advertised algorithm,
  uniqueness of the encoding, and that unknown input decodes to null rather than a guess. Fortanix
  gets extra attention because it encodes across three separate values (type, curve, size), which
  has more room to disagree.
- **Fail-closed tests** for the three stubs (15 tests). A stub's danger is not that it fails, but
  that a later partial implementation makes one operation return `Success` while the rest do
  nothing — callers would believe keys were generated and signatures produced. These fail the
  moment any operation claims success.

Still open: the contract suite itself remains applied to one provider. Running it requires a live
KMS, so cloud and HSM providers need an integration source set with credentials — a CI decision, not
a code change.

Still to do on F6: apply the shared helper to Cardano, Indy and cheqd, and re-point
`AbstractEvmAnchorClient.requireTransportSecurity` at it so the unresolvable-host fail-open is fixed
in one place rather than four.

---

## Caveats

- Static review plus an executed core test run. F1–F4 were each read in source and confirmed; F5
  and F6 are read from code and documentation.
- Not covered: BBS and JAdES proof engines, the EUDIW plugin, most DID method plugins, the
  reference wallet, and the full-assembly service-loader ordering.
- No dynamic testing, fuzzing, or dependency CVE scan was performed. Dependency versions are
  current at the time of review (Bouncy Castle 1.84, Nimbus JOSE 9.48, OkHttp 4.12.0,
  Jackson 2.21.3).
