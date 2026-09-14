# TrustWeave SDK — technical review

**Date:** 13 September 2026
**Revision:** `7eb96854` — clean working tree on `main`, in sync with `origin`
**Rubric:** the six equally weighted categories used since 2026-09-05, so these numbers sit on the same series as 09-06 (6.6), the 09-10 composite (9.3) and 09-11 (8.6). Engineering judgement, not certification.

**Overall: 8.8 / 10**, up from 8.6 on 11 September.

## A note on evidence

This machine had roughly 1.6 GB of free memory. The Gradle daemon died with a native out-of-memory (`Chunk::new`), and a retry with a smaller heap lost its test executors to socket write failures. A local build here is not a valid signal, so **build, test and coverage evidence in this review comes from CI run 34737199525 at this exact commit**, which is fully green across all 28 steps. Static gates were run locally, where memory is not a factor.

## What has improved since 11 September

Twelve commits landed, and the theme is that the evidence pipeline became trustworthy:

- **Evidence is now a declared Gradle output in a dedicated `qualification/` directory** rather than sharing `reports/` with Kover. That is a better fix than the one I made on 09-11 — it removes the output-overlap risk entirely.
- **`test_workflow_evidence_paths.py`** asserts that workflow evidence paths match what the build writes. This is the right response to a defect class that caused three separate CI failures.
- **Live AWS KMS custody qualification** exists: a read-only, least-privilege test that verifies signatures independently, rejects an altered challenge, proves replacement keys use distinct material, and proves a restarted client still resolves the historical public key. The design is careful.
- **PKCS#11 custody recovery** is tested, and the **host overload boundary** is covered.
- Test skips fell from 15 to 11, each still justified.

CI at HEAD: **4,005 tests, 0 failures, 0 errors, 11 documented skips**, with ktlint, ABI, coverage policy, VI cross-stack interoperability, alert rules and the notification exercise all green.

## Findings

Full detail in [`findings.json`](findings.json). Ten findings; one is high.

### T1 (high) — the AVP authorization server keeps security state in unbounded process memory

`AuthorizationEngine` enforces replay prevention, single-use and daily spend caps through three plain `ConcurrentHashMap`s in [`Stores.kt`](../../../credentials/avp-authorization-server/src/main/kotlin/org/trustweave/credential/avpauth/state/Stores.kt) — 33 lines, no eviction, no persistence.

Two separate failures follow:

- **Availability.** The keys are caller-supplied — `(credentialId, nonce)`, authorization id, `(agent, credential, date)`. Ordinary traffic grows all three without bound; an attacker does it faster. Nothing ever removes an expired nonce or a past date.
- **Correctness.** All three guarantees are process-local and vanish on restart. A restarted instance re-accepts every nonce it has ever seen. **A two-replica deployment enforces none of the three**, because neither replica can see the other's state.

This is the module that authorizes payments. The repository already contains the correct pattern: `PostgresIntentLedger` does exactly this durably, with synchronous commit, `FOR UPDATE` locking and an occurrence trigger. `AvpAuthorizationServer`'s KDoc documents only the bind address and says nothing about any of it.

### T2 (medium) — half the shipped servers still have no authentication hook

`withAuthentication` and its fail-closed default reached the DID registrar, VC API and status-list servers. The **AVP authorization server, OIDC4VCI server and trust-registry server did not.** The AVP route carries a comment — *"unauthenticated by design (it expects a proxy in front)"* — which is honest but is a comment, not a control, on the server that authorizes payments.

### T3 (medium) — the custody qualification records a control it cannot establish

`AwsKmsLiveQualificationTest` asserts only `denied is SignResult.Failure`. That type covers `KeyNotFound`, `UnsupportedAlgorithm` and `Error`, so a mistyped ARN, a wrong region or a transient network fault all satisfy it. The test then writes `"unauthorizedKeyRejected": true` into the evidence as a literal.

`check-custody-evidence.py` requires that field to be true, so the gate inherits the blind spot instead of catching it. The evidence asserts an IAM control the test never exercised.

### T4 (medium) — the custody qualification has never run

`aws-kms-qualification.yml` is `workflow_dispatch` only, with zero runs. Live custody is unqualified in fact. No provider has a qualified custody profile.

### T5 (medium) — the BOM exports modules whose every method throws

`distribution/bom` exports `platforms:salesforce` and `platforms:servicenow` via `api(project(...))`. Every public method in both throws `TrustWeaveException.Unknown("… requires … implementation")`. They fail closed, which is right — but they are advertised through the BOM, both return `Any`, and neither carries a maturity, while `starknet`, `threebox`, `tezos` and `btcr` are correctly marked `stub`. The catalog has the vocabulary and does not use it here.

### T6 (medium) — coverage is static and the core is the least-covered large module

| | 09-11 | 09-13 |
|---|---|---|
| Line | 58.05% | **58.23%** |
| Branch | 40.72% | **40.82%** |

Floors are 57.0 / 40.0 — set just under the measurement, so the gate ratchets nothing. The recently reviewed modules are excellent (observability 97.5%, verifiable-intent ~90%). `credential-api` — about 4,100 lines, the module that issues and verifies credentials — is under half covered; `did/registrar` near 11%; `didcomm` near 32%.

### T7 (low) — the instrumentation promises more than it emits

Ten call sites use `Telemetry`. Ten of the fifteen declared `Operation` values — the DID lifecycle, all four wallet operations, `KMS_VERIFY`, presentation verification, revocation checks — never fire. A host charting them sees a permanent zero series and cannot distinguish "not instrumented" from "never happened". 33 of 792 main-source files reference a logger, unchanged.

### T8–T10 (low / informational)

Nothing has been published — `v0.7.0` tagged, no release, nothing on Central, publish job never run. Recent CI history on main is success/failure/success/cancelled/failure, green at HEAD but red on nearly half of recent pushes. And the local-build memory problem described above, which is environmental rather than a repository defect.

## Scores

| Category | 09-11 | Now | Why |
|---|---|---|---|
| Security and access control | 8.8 | **8.6** | T1 found this round (pre-existing, not a regression), plus T2, T3, T4 |
| Observability and diagnosability | 9.0 | **9.2** | Telemetry SPI, bridge and correlation landed; ten call sites, logging unchanged |
| Reliability and scale | 9.2 | **9.2** | PKCS#11 recovery tested and evidence now sound, offset by T1 |
| Configuration and data | 8.8 | **8.9** | Catalog and image gates hold; T5 open |
| Deployment and release | 7.0 | **8.0** | CI green with twelve gates, evidence durable; still nothing published |
| Testing and documentation | 8.5 | **8.7** | 4,005 tests, skips 15→11, 135 script tests; coverage static |

Mean 52.6 ÷ 6 = 8.77, rounded to **8.8**.

### Why this differs from the other review dated today

`docs/reviews/2026-09-13-code-review/` scores this same revision **9.4**. That is not a contradiction — it uses a different six-category rubric (security, correctness/protocols, architecture/maintainability, testing/documentation, supply-chain/CI, operations/provider-maturity).

Almost the whole gap is two structural effects:

1. Its **supply-chain and CI** category (9.6) measures pipeline health, which is genuinely excellent. This rubric's **deployment and release** measures whether anything ships, which nothing does.
2. Its rubric has no category where static coverage is the dominant term; here it drives testing and documentation.

Both are defensible readings. This one continues the series the project has tracked since 05 September, which is why I used it. Notably, both rounds independently found the same three open items: the custody qualification has no completed run, several published surfaces are deliberate partial implementations, and green main evidence is not a promoted release.

## Getting above 9.5

Full acceptance criteria in [`tasks.json`](tasks.json). Eleven tasks; projected mean on completion **9.52**.

**Start day one, because they do not compress.** A5 (Sonatype namespace verification) and A3 (an AWS account with the three qualification keys and an IAM deny) are worth 1.5 and 0.9 category points between them and are both blocked on something outside the repository.

**Week 1.** A2 — make the custody denial check assert an `AccessDenied` cause rather than any failure, and derive the evidence from the assertion. A4 — extend `withAuthentication` to the remaining three servers. A7 — mark Salesforce and ServiceNow `stub` and stop the BOM advertising them. A11 — rehearse evidence-path changes on a branch.

**Weeks 1–2.** A1, the one high finding: give the AVP stores durable, shared, bounded state with the same fail-closed semantics as the intent ledger. Two instances against one store must reject the second presentation of a nonce; a restart must not forget.

**Weeks 2–3.** A5, publish something. A9, finish the instrumentation the `Operation` enum promises or trim the enum.

**Weeks 2–5.** A6, coverage. 58.23% / 40.82% to roughly 75% / 60%, top-down from `credential-api`, raising each module's floor as it lands. This is the largest single item and the one that most limits the score.

**Weeks 3–4.** A3, run the custody qualification. A8, classify the remaining 102 modules and qualify the GA core.

**Ongoing.** A10, deployment-scale ledger qualification.

## Evidence

- [`evidence/ci-evidence.json`](evidence/ci-evidence.json) — CI run 34737199525 at `7eb96854`: 4,005 tests, 0 failures, coverage counters
- [`scores.json`](scores.json), [`findings.json`](findings.json), [`tasks.json`](tasks.json)

Local commands run: `python -m unittest discover -s scripts -p 'test_*.py'` (135 pass), and `check-workflow-pinning`, `check-dependency-catalog`, `check-publication`, `check-capability-coverage`, `check-container-images`, `check-cancellation-guards`, `check-documentation` — all pass.

---

# Remediation pass — 13 September 2026

Six of the eleven tasks are done. **8.8 → 9.06.** The five that remain are the five no code change
could have made: two are blocked outside the repository, two are multi-week, and one is a soak.

## A1 (T1, high) — the AVP authorization state is now durable and bounded

The three `ConcurrentHashMap`s are gone. In their place is one interface with one method:

```kotlin
suspend fun admit(request: AdmissionRequest): Admission
```

One method, because replay, single-use and daily spend cannot be checked apart from being
recorded — do that and two concurrent presentations of the same authorization both pass.

[`InMemoryAuthorizationStore`](../../../credentials/avp-authorization-server/src/main/kotlin/org/trustweave/credential/avpauth/state/InMemoryAuthorizationStore.kt)
is the single-process implementation, and it is bounded in two ways that matter:

- Every record carries the instant past which the authorization it describes can no longer be
  validly presented, **taken from the authorization itself**, not from a store-side default. An
  authorization whose lifetime nothing bounds is refused (`UNBOUNDED_LIFETIME`) rather than
  half-remembered.
- At capacity, after a sweep, an admission is **refused**. Evicting a live nonce to make room
  would turn memory pressure into a replay window, so capacity exhaustion is a denial.

The unbounded per-credential `Mutex` map went too — a fixed 64-stripe array gives the same
per-credential serialization and never grows.

[`PostgresAuthorizationStore`](../../../credentials/avp-authorization-server/src/main/kotlin/org/trustweave/credential/avpauth/state/PostgresAuthorizationStore.kt)
is the durable one, following `PostgresIntentLedger`: one READ COMMITTED transaction with
synchronous commit required, unique-key conflicts as the replay and double-spend signals, `FOR
UPDATE` on the payer's row for the day. A refusal rolls the whole transaction back, so a refused
authorization records nothing. A failed transaction is `STORE_UNAVAILABLE` and is never retried as
a fresh authorization.

Proven against a real PostgreSQL, not a fake:

| Test | What it establishes |
|---|---|
| two instances, one database | the second replica sees the first replica's nonce and consumption |
| a fresh instance | a restart does not forget a nonce it has seen |
| four admissions alternating replicas | the daily cap is one budget across both, not one each |
| eight concurrent presentations | exactly one admission |
| unreachable database | a denial, never an admission |

## A4 (T2) — all six servers carry the hook

The AVP authorization server and the OID4VCI issuer now refuse their mutating routes with 503
until the host declares what protects them. The comment saying a proxy was expected in front is
replaced by `HostAuthentication.frontedByProxy(...)` — the same claim, made where the code can see
it.

The trust registry composes rather than stacks: a call the shared gate admitted is authorized, and
its own `apiToken` check stands aside. Demanding both would mean a host using mTLS or a gateway
could never satisfy the route.

OID4VCI needed one new thing. `/token`, `/credential`, `/deferred_credential` and `/notification`
are the protocol surface, authenticated by the pre-authorized code and the access token the spec
defines, and their callers are wallets, which hold no host credential. So `HostAuthentication`
gained a `protocolAuthenticatedPaths` declaration — made by the server about its own routes, not
by the host — and the gate covers `/api/offer`, which mints credential offers.

## A2 (T3) — the custody evidence now reports what the run observed

`assertTrue(denied is SignResult.Failure)` is satisfied by a mistyped ARN. The check now asserts an
AWS `AccessDeniedException` and explicitly refuses `KeyNotFound`, which is exactly what a wrong ARN
or region produces.

Every boolean in the evidence is derived from an assertion that ran, through a recorder that
refuses to emit a file missing any required check. `check-custody-evidence.py` additionally
requires `denialResultType: "Error"` and `denialErrorCode: "AccessDeniedException"`, so the gate
no longer inherits the blind spot it was meant to catch.

## A9 (T7) — the telemetry enum is a contract again

`KMS_VERIFY` is gone: `KeyManagementService` has no verify operation, so the value could only ever
have been a zero series. The other nine are now emitted — `TelemetryDidMethod` and
`TelemetryWallet`, plus presentation verification and revocation checks in `credential-api`.

Both decorators are wired where a host cannot miss them: `DidMethodRegistry.register` instruments
on the way in, and the wallet DSL instruments on the way out, so a host-supplied plugin is covered
without opting in. `DidMethodRegistry.get` therefore returns a `TelemetryDidMethod` wrapping what
was registered; `TelemetryDidMethod.delegate` is the way back to it, and that is documented on
`register`.

`scripts/check-telemetry-operations.py` fails the build if a declared operation has no
main-source emitter, or an emitter has no declaration. A test-source emitter does not count: a host
does not run the test suite.

## A7 (T5) — the BOM stops advertising capability that throws

Salesforce and ServiceNow are marked `stub`, removed from `distribution:bom`, and their KDoc says
so. The unassessed ratchet fell from 102 to 100.

## A11 (T9) — the evidence-path defect is gated by a property

`scripts/check-workflow-evidence-paths.py` asserts the rule rather than a list of paths: every
module-scoped `build/...` path a workflow reads must be backed by a declared Gradle task output.

Writing it surfaced something worse than the original finding. `test_workflow_evidence_paths.py` —
the test written to stop this exact defect — **had never run in CI**: the discovery pattern was
`test_check_*.py`, and the file is not named that way. All three workflows now discover `test_*.py`,
and a test asserts that they do.

## What is left, and why

| | Why it did not move |
|---|---|
| **A5** publish | Sonatype namespace verification. Outside the repository. |
| **A3** run the custody qualification | An AWS account with three keys and an IAM deny. Outside the repository. A2 landed first, so a run will now prove what it records. |
| **A6** coverage | Two to three weeks of test writing against `credential-api`. The ~35 tests added here cover what this pass changed, which is not what T6 measures. |
| **A8** classify 100 modules | A week of assessment, then qualification evidence that does not compress. |
| **A10** ledger at deployment scale | Soak time. |

Deployment cannot pass 8.1 while nothing ships and testing cannot pass 8.8 while `credential-api`
is under half covered. That is where the remaining 0.44 lives.

## Verification

The box that could not build at review time now can — JDK 21, `-Xmx900m`, `--max-workers=1`, no
daemon. `./gradlew build` is **green in 18m54s**: 2,188 tasks, including `checkKotlinAbi` and
`koverVerify` for every module and the per-source-set ktlint checks.

| | |
|---|---|
| Tests | 500 suites, **3,980 tests, 0 failures, 0 errors**, 17 skipped |
| Script tests | **150 pass**, up from 135 |
| Merged coverage | 58.14% line, 40.83% branch, 56.71% instruction |

The local test count is below CI's 4,005 because several suites are environment-gated — live
custody, PKCS#11 — and do not run here.

**Coverage did not move, and it is worth being precise about the direction.** Against the review's
CI baseline of 58.23 / 40.82, line coverage is **0.09 points lower** and branch 0.01 higher. The
new code is well covered, but it is also about 300 new lines of main source, so the merged line
figure is marginally diluted rather than improved. T6 is untouched: `credential-api` is exactly
where it was, and the testing score rose for the three new gates, not for coverage.

---

# A6 — coverage on the issuance and verification core

`credentials/credential-api` was **48.65%** line covered: the least-tested large module in the
repository, and the one that issues and verifies credentials. It is now **68.0% line, 60.0%
branch**, with 529 tests in the module and none failing.

Five areas were at *literally zero*. Each is a decision point a caller depends on:

| Area | Was | Tests added | What they establish |
|---|---|---|---|
| `InMemoryCredentialRevocationManager` | 0% of 226 lines | 27 | A revoked credential cannot come back valid; suspension and revocation do not leak into one another; one list's state never reaches another; an index is assigned once and two credentials never share one |
| `JsonSchemaValidator` | 0% of 152 | 30 | Every supported keyword, both directions; the paths in error messages; that a type mismatch stops the cascade rather than producing four errors about a value of the wrong shape |
| `ShaclValidator` | 0% of 210 | 24 | What each constraint actually enforces — **including the defect below** |
| `DefaultSchemaRegistry` | 0% of 41 | 8 | That an unregistered schema and a format with no validator both refuse loudly rather than passing |
| Builder DSL | 0% of ~95 | 21 | What the builders refuse and what they fill in |

## The defect this found

`ShaclValidator` extracted the claim name from `sh:path` at three sites, and they disagreed:

```kotlin
// validatePropertyConstraint — datatype, length, pattern, sh:in
path.substringAfterLast("/").substringAfterLast(":").substringAfterLast(".")

// the two sh:minCount checks
path.substringAfterLast("/").substringAfterLast(":")
```

So a shape written the way this class's own KDoc example writes it —

```kotlin
put("sh:path", "credentialSubject.degree")
put("sh:minCount", 1)
```

— looked for a claim named literally `credentialSubject.degree`, **reported a present, correct
property as missing**, and did so while the datatype and length constraints on the same shape
resolved the same path correctly and passed. The documented example did not work.

Now one `fieldNameOf(path)` used by all three, and a test asserting that every constraint agrees
about what a path names, across dotted, prefixed, slash-separated and bare paths.

## What the tests say that is not flattering

Where the implementation does less than the keyword suggests, the test says so rather than
skirting the case. These are recorded, not endorsed:

- **An unknown status list reads as not revoked.** A credential pointing at a list the manager has
  never seen passes its revocation check. Defensible for a manager documented as being for
  testing and development; exactly the property a persistent implementation must not inherit.
- **`additionalProperties: false` with no `properties` constrains nothing.** The check only runs
  inside the per-property loop, which is entered only when `properties` is present.
- **The SHACL datatype checks test parseability, not JSON type.** The string `"42"` satisfies
  `xsd:integer` and `"true"` satisfies `xsd:boolean`, because the underlying accessors read the
  primitive's content whether or not it was quoted.
- **`expiresIn` reads `issuedAt` at the moment it runs**, so ordering inside the builder block
  changes the result.
- **`CredentialValidator`'s `MISSING_ISSUER` and `MISSING_PROOF_FORMAT` branches are unreachable**
  through the public model — `Iri` refuses a blank value and `getFormatId` is exhaustive. Recorded
  so a reader knows they are belt-and-braces rather than load-bearing.

## What is still uncovered

`DefaultExchangeService` (157 lines, 0%), `DefaultCredentialService`'s remaining 163, the DID
convenience extensions (71, 0%), and the proof engines' remaining branches. Reaching the 75% / 60%
that A6's acceptance asks for means continuing through those; branch coverage is already at 60%.

---

# A8 — classifying the 100 unassessed modules

Every one of the 110 modules now carries a maturity. The unassessed ratchet is **102 → 0**, so the
gate is closed: a new module has to be classified in the commit that adds it.

**99 experimental, 11 stub, 0 supported.**

The bar itself is the part that was missing. There was no written standard for what `supported`
means, which is why nothing could be measured against it. It is now
[`docs/api-reference/module-maturity-bar.md`](../../api-reference/module-maturity-bar.md), and the
classification is *derived* from per-module coverage and test evidence by that published rule
rather than assigned by hand — so a later reviewer can re-run it and disagree on the evidence.

## Nothing is `supported`, and that is the finding rather than a gap in the work

Thirty-two modules meet the coverage floor. **None meets all four requirements**, because
requirements 2–4 — a dated security review, a retained interoperability artifact, a documented
operational-limits section — are evidence that mostly does not exist yet. One module has the
interoperability half (verifiable-intent). None has documented operational limits of its own.

Marking a module `supported` on coverage alone would be precisely the error this review has spent
its other findings correcting: asserting a control nobody established. So the catalog says
`experimental`, and the bar document says, per module, exactly what would change it.

The GA core's remaining distance is mostly coverage, and one item that does not compress:

| Module | Line | Gap |
|---|---|---|
| `credentials:plugins:verifiable-intent` | 90.0% | security review, documented limits |
| `kms:kms-core` | 82.9% | security review, interop evidence, documented limits |
| `credentials:credential-api` | 68.0% | +2.0 coverage, then the other three |
| `did:plugins:key` | 68.2% | +1.8 coverage, then the other three |
| `did:did-core` | 58.4% | +11.6 coverage, then the other three |
| `wallet:wallet-core` | 30.4% | +39.6 coverage, then the other three |
| `did:plugins:web` | 21.5% | +48.5 coverage, then the other three |
| `kms:plugins:aws` | 26.6% | +43.4 coverage, and **a completed custody qualification run** |

That last row is A3 again, from a different direction: no KMS provider can be `supported` until the
live custody qualification has actually run, and that needs an AWS account.

---

# The ceiling

**9.5 is not reachable from inside this repository, and it is worth being exact about why.**

| Category | Now | In-repo ceiling | What caps it |
|---|---|---|---|
| Security and access control | 9.25 | ~9.4 | **A3** — an AWS account with three keys and an IAM deny |
| Observability and diagnosability | 9.5 | 9.5 | — |
| Reliability and scale | 9.4 | 9.4 | A10 — soak time, not code |
| Configuration and data | 9.3 | **9.6** | A8, now largely done |
| Deployment and release | 8.1 | **8.1** | **A5** — Sonatype namespace verification |
| Testing and documentation | 8.8 | **9.5** | A6, in progress |

Best in-repo case is **55.6 ÷ 6 = 9.27**. With deployment pinned at 8.1, the other five would have
to average **9.78** to clear a 9.5 mean — a level this rubric only grants to qualified,
production-proven states, which themselves rest on exactly the evidence A3 and A5 would produce.

So the last ~0.25 is not a matter of writing more tests. **A5 alone is worth about 1.4 category
points and no code change moves it.** Both blockers are a day of account setup each, and both have
been the critical path since the review was written.
