# TrustWeave SDK — full codebase review

**Date:** 11 September 2026
**Scope:** whole SDK working tree on `main` at `e0a4464f`, including 169 uncommitted files (87 modified, 82 untracked). `trustweave-saas` is out of scope.
**Rubric:** the same six equally weighted categories used since 2026-09-05, so the numbers are comparable. Engineering judgement, not certification.

**Overall: 8.6 / 10.**

## How this differs from the last five rounds

The rounds between 2026-09-07 and 2026-09-10 each reassessed **one** category against newly written code and carried the other five forward. That is a reasonable way to track a remediation stream, but it means the 9.3 recorded on 2026-09-10 is a composite of six measurements taken at six different times against six different slices of the repository.

This round re-derives every category from the current tree. A full `./gradlew build` (13m38s, 2188 actionable tasks), `checkKotlinAbi`, the merged Kover report and all seven Python validation gates were executed. Where the picture is worse, it is because the long tail of the library — the parts no recent round examined — reasserts itself, not because previously verified work regressed.

## What is genuinely excellent

This is not a codebase with shallow problems. Several things here are better than most production libraries:

- **Verifiable Intent chain verification** (`ChainVerifier`, 687 lines) fails closed at every branch I could construct: multi-pair L2, duplicate disclosure references, an L3 presented without its authorizing L2 mandate, mismatched agent `cnf.jwk` across mandates, `kid` mismatch, `exp − iat > 1h`, budget or recurrence constraints with no ledger to enforce them. `Constraint.Malformed` fails closed regardless of strictness mode, with a comment explaining exactly why. The `now` parameter defaults to the host clock so that the safe reading is also the lazy one.
- **`PostgresIntentLedger`** forces `synchronous_commit`, locks accounts before reservations in the same order as writers, counts occurrences in an `AFTER INSERT` trigger that cannot fire on an `ON CONFLICT` no-op, and treats an uncertain commit as a denial rather than a retry.
- **Outbound HTTP is SSRF-guarded by default.** `PrivateNetworkGuard` / `SsrfSafeHttp` back `AbstractWebDidMethod` and the federation resolver, and `TrustChainResolver` documents precisely why a bare client there would let a federation peer steer requests at loopback.
- **No crypto anti-patterns.** Every random source in a security path is `SecureRandom`; there is no `TrustAllCerts`, no hostname-verifier bypass, no `GlobalScope`; the XML parsers disable DOCTYPE and external entities.
- **Test discipline.** 3,897 tests, zero failures, zero errors, zero `@Disabled`, and the 15 skips are each named and justified in `config/test-skip-policy.json`. VI is cross-checked against a pinned Python reference implementation.
- **The `observability` module** is the best-tested code in the repository at 97.8% line / 87.2% branch, with a constant-time metrics-token comparison and management traffic exempted from admission limiting so metrics stay reachable under saturation.

## Findings

### F1 — The tree does not pass its own build (verified)

`./gradlew build` **fails**. Two tasks fail, both ktlint on `kms:plugins:hashicorp`: **232 violations across 10 files** (169 main, 63 test), concentrated in `VaultKeyManagementService.kt` (115). This is the Vault Transit routing and response-parsing work that closed finding R15 — it was written and never linted. New and changed code carries no baseline cover by design, so this fails on any push.

Everything else in the build is clean: 3,897 tests pass, `checkKotlinAbi` passes, the documentation gates pass over 371 Markdown files.

### F2 — main carries an unversioned copy of the code being reviewed

`main` is at `e0a4464f` with 169 uncommitted files. The same work **is** committed — on `codex/joint-remediation-20260910` (`dbec32c0`) and `codex/configuration-data-20260910` (`5e8dc04c`), neither merged. The working tree differs from `dbec32c0` in only two tracked files. So every score awarded since 2026-09-07 was given to code reachable from no branch on `main`, and one `git checkout` away from being lost.

Credit where due: **CI on main is green** as of `e0a4464f` (run 34040898083, 2026-09-06). The "red since 2026-08-23" state recorded in earlier rounds no longer holds.

### F3 — There is no publication path

No build file declares a publishing repository. `maven-publish` is applied to every JAR module and signing is enforced on `PublishToMavenRepository` — but that task type is never created, so `publishToMavenLocal` is the only thing that works. Three further blockers:

- The POM has **no `scm` block**. Sonatype Central rejects that outright.
- The POM description points readers at `docs/reference/module-maturity.md`. That path does not exist; the file is `docs/api-reference/module-maturity.md`. Every module published would carry a dead link.
- `v0.7.0` is tagged with no GitHub release and no published artifact. `release-evidence.yml` produces evidence; nothing publishes.

### F4 — Coverage is bimodal

Merged coverage is **57.30% line / 39.83% branch**, and the floors in `config/coverage-policy.json` (56.0 / 38.0) sit just under the measurement — they record where the code is, not where it should be.

The split is stark. Recently reviewed modules:

| Module | Line | Branch |
|---|---|---|
| `observability` | 97.8% | 87.2% |
| `credentials/plugins/verifiable-intent` | 90.0% | 69.5% |
| `common` | 87.0% | 66.5% |
| `kms/kms-core` | 85.0% | 69.1% |

Everything else:

| Module | Lines | Line | Branch |
|---|---|---|---|
| `credentials/credential-api` | 4,095 | 48.6% | 38.1% |
| `did/did-core` | 2,491 | 58.1% | 33.4% |
| `wallet/wallet-core` | 216 | 24.5% | 24.0% |
| `did/plugins/web` | 191 | 21.5% | 29.7% |
| `did/registrar` | 768 | 11.5% | 11.7% |
| `did/registrar-server-ktor` | 126 | 9.5% | 4.4% |
| `credentials/credential-models-mp` | 1,214 | 6.8% | 14.3% |
| `did/plugins/ion`, `sol`, `polygon`, `plc`, `cheqd`, `ens` | 162–324 each | 1.2–2.5% | ~2% |
| `common-mp` | 103 | 0% | 0% |

`credential-api` is the core issuance and verification module and it is under half covered. `did:web` is the SSRF-sensitive resolver at 21.5%. Six DID plugins are shipped at 1–3%, which is untested code on a consumer's classpath.

Separately, `check-documentation.py` inventories **2,363 Kotlin blocks** across the docs and only **9** are source-backed and executed.

### F5 — 61 dependency coordinates escape the version catalog, and they have drifted

`gradle/libs.versions.toml` is the declared central catalog, but 61 coordinates are hardcoded as literal strings in module build files. The drift is on libraries that parse untrusted network input:

| Library | Hardcoded in | Catalog |
|---|---|---|
| `org.bitcoinj:bitcoinj-core` | 0.16.2 (`anchors/plugins/bitcoin`, `did/plugins/btcr`) | 0.17.1 |
| `org.web3j:core` | 4.10.0 (`did/plugins/ens`, `ethr`, `polygon`) | 5.0.2 / legacy 4.14.0 |
| `com.google.code.gson:gson` | 2.10.1 (`did/plugins/ion`) | 2.14.0 |
| `org.slf4j:slf4j-api` | 2.0.9 (`did/did-core`) | 2.0.17 |
| `kotlinx-coroutines-test` | 1.8.1 (5 modules) | 1.10.2 |

Dependabot raises PRs against the catalog; these modules never move. The new `observability` module pins OpenTelemetry `1.65.0` inline in four places, repeating the pattern in freshly written code.

### F6 — Three shipped servers have no authentication primitive

`DidRegistrarServer`, `VcApiServer` and the status-list server ship with no authentication or authorization of any kind. `VcApiServer`'s own KDoc says so plainly: *"There is no API key, bearer token, or mTLS anywhere in this module."* The registrar creates, updates and deactivates DIDs.

The mitigation is a loopback bind default plus documentation telling operators to front the server with a proxy. That is guidance, not a control. `trust-registry-server` already shows the right shape — a bearer token required on mutating routes, failing closed — and `DidRegistrarServer` already has a `withObservability` extension point to model a `withAuthentication` hook on.

### F7 — Instrumentation stops at the HTTP boundary

The `observability` module is excellent and is wired into six Ktor hosts. Across the whole tree, **33 of 788** main-source files reference a logger. DID resolution, KMS operations, wallet storage and credential verification emit no structured events, no metrics and no correlation identifier. A host looking at a failed verification span has nothing to join it to.

### F8 — Workflow supply chain is pinned in half the workflows

| Workflow | Actions pinned by SHA | Top-level `permissions` |
|---|---|---|
| `ci.yml` | 10/10 | yes |
| `release-evidence.yml` | 7/7 | yes |
| `docs-check.yml` | 4/4 | yes |
| `deploy.yml` | 0/5 | yes |
| `conformance-nightly.yml` | 0/5 | **no** |
| `conformance-pr.yml` | 0/4 | **no** |

`conformance-nightly.yml` runs `actions/github-script@v7` — which creates issues — on a mutable tag with the repository default token scope.

### F9 — Nothing is declared production-supported

`common/src/main/resources/trustweave-capabilities.json` has **8 entries for 107 modules**, and none is `supported`: four `stub`, four `experimental`. `docs/operations/custody-qualification.md` states no custody profile is qualified. A consumer cannot tell which of 107 modules is production code. This has correctly blocked a production verdict for four rounds running.

### F10 — A presenter-reachable exception escapes the verification contract

`ChainVerifier.verify` returns a `ChainVerificationResult` and translates only `java.sql.SQLException` from the budget reservation. `PostgresIntentLedger.reserveImpl` also throws `IllegalStateException` from `check(rows.getString(1) == budget.currency && rows.getLong(2) == budget.maximum)`.

That is reachable. The ledger scope is `sha256(l2.jwt)` — the JWT compact part **without** disclosures. One signed L2 carrying two payment-mandate disclosures with different budgets yields the same scope under different presentations; the second presentation raises an unchecked exception out of a function whose documented contract is a result object. Not an authorization bypass — it fails hard rather than open — but it breaks the API contract on presenter-controlled input.

I checked the adjacent variant and it is **not** a defect: a negative `budget.max` is caught by `unreadableBound`, becomes `Constraint.Malformed`, and fails closed cleanly.

### F11 — Two documented cancellation gaps

`BitstringStatusListManager` lines 1211 and 1263 carry TODOs marking loops over the full status list (131,072 entries by default) with no cooperative cancellation check. Both are plain non-suspend functions that cannot reach `coroutineContext`. A cancelled refresh burns CPU to completion. The TODOs prescribe the fix.

### F12 — The validation gates read the wrong directory on Windows

`check-test-evidence.py` and `check-junit-contract.py` default `--build-root` to the in-repo `build/`. On this project's documented Windows default, outputs go to `%LOCALAPPDATA%/TrustWeave/gradle-build/`, and the in-repo `build/` tree is a stale 2026-09-06 snapshot.

Run with their defaults today they report **40 evidence failures and 8 invalid JUnit methods**. Pointed at the real build root, both exit 0 with zero failures. A developer running the gates locally gets an entirely fictional result — and the failure mode works in the other direction too. (This caught me mid-review: the 8 "dead tests" looked like the known Kotlin non-void `@Test` trap until I confirmed all six named OID4VP tests do appear in the current JUnit XML and did run.)

### F13 — 550 broad catches, 21 with a comment for a body

Every security path I read fails closed correctly. But `catch (Exception)` / `catch (Throwable)` appears 550 times across 790 main-source files, and that surface has not been audited. A swallowed `CancellationException` or a masked verification failure is exactly the class of defect these rounds keep finding one at a time.

A balanced-brace scan finds **zero** genuinely empty catch bodies and 21 whose body is only a comment. (An earlier line-counting grep in this review reported 12 empty ones; that number was wrong.) Four of the 21 sat in `suspend` functions and swallowed `CancellationException` — `TrustedDomainManager.emitSafely`, `InMemoryDomainTreasury.emitSafely`, and two testkit integration helpers. The rest are best-effort `close()` and logging fallbacks in non-suspend code, each already carrying a comment saying so.

The wider surface: **187** broad catches inside `suspend` functions that neither rethrow nor mention cancellation. Most convert to a sealed failure and are fine; separating those from the real swallows is the week of work T15 describes.

## Scores

| Category | 2026-09-10 | Now | Why it moved |
|---|---|---|---|
| Security and access control | 9.0 | **8.8** | F6, F8, F9, F10 |
| Observability and diagnosability | 9.6 | **9.0** | F7 — the 9.6 was scored against the six HTTP hosts, not the library |
| Reliability and scale | 9.5 | **9.2** | F11, plus no deployment-scale evidence |
| Configuration and data | 9.5 | **8.8** | F5 |
| Deployment and release | 8.5 | **7.0** | F1, F2, F3, F12 |
| Testing and documentation | 9.5 | **8.5** | F4 |

Equal-weight mean: 51.3 ÷ 6 = 8.55, rounded to **8.6**.

## Getting above 9.5

The full task list with acceptance criteria is in [`tasks.json`](tasks.json). In short:

**Week 1 — stop the bleeding.** `ktlintFormat` the Vault module (T01, under an hour). Land one candidate on `main` and push (T02, half a day). SHA-pin the six unpinned workflow actions and add the two missing `permissions` blocks (T08, under an hour). Fix the bitstring cancellation gaps (T10) and the ledger exception contract (T12). Fix the Windows build-root default in the validation scripts (T13).

**Weeks 2–3 — make it shippable.** Build a real publication path: Central repository, `scm` in the POM, correct doc URL, a tag-triggered publish workflow, signed artifacts with SBOM and provenance (T03). Pull all 61 coordinates into the catalog and add a gate that rejects literals (T05). Add the `withAuthentication` hook and rate limiting to the three open servers (T07).

**Weeks 2–6 — the big one.** Coverage (T04). Merged 57.30% / 39.83% needs to reach roughly 75% / 60%, working top-down by untested line count: `credential-api` first, then `did-core`, then `wallet-core` and `did:web`. Decide explicitly whether the 1–3% DID plugins are shipped or experimental. This is the largest single item and the one that most limits the score.

**Weeks 4–5.** Instrument the library itself behind the observability SPI (T06). Classify all 107 modules in the capability catalog and qualify the GA core (T09). Compile the documentation blocks consumers copy (T14).

**Ongoing.** Deployment-scale ledger qualification (T11) and the broad-catch audit (T15).

Projected on completion: Security 9.5, Observability 9.5, Reliability 9.5, Configuration 9.6, Deployment 9.5, Testing 9.5 — **9.52 overall**.

Two of these do not compress no matter how fast the code goes: Sonatype namespace verification (T03) and real custody/hardware qualification (T09). Start them first even though they finish last.


## Remediation, same day

Nine of fifteen tasks are closed with local evidence; the full record with acceptance detail is in [`remediation.json`](remediation.json).

**Closed:** F1 (build passes — all 232 ktlint violations cleared, module baseline untouched), F3 (publishing repository, `scm`, corrected doc URL, reviewer-gated publish job — see [the runbook](../../operations/publishing.md)), F5 (all 61 coordinates in the catalog, four libraries converged, bitcoinj recorded as a deliberate `-legacy` pin), F6 (`HostAuthentication` and fail-closed mutations on the three servers), F8 (14 actions SHA-pinned, both `permissions` blocks added, `issues: write` isolated), F10 (ledger exceptions become results), F11 (both loops suspend and cancellable), F12 (build root resolved the way the build resolves it).

**Also closed after the push:** F2 — committed as `5128031f`, then `codex/joint-remediation-20260910` merged with `-s ours` (main already held every one of the 319 files that branch touches; a content merge would have blended the unformatted Vault sources and literal coordinates back in), and pushed. F13 — the full sweep landed: 212 clauses across 81 files rethrow `CancellationException` first.

**Two defects the push itself surfaced**, both invisible to local runs because the tasks had been coming from the build cache:

- **F14 — a container image tag had vanished.** `minio/minio`'s Docker Hub copy of the pinned release no longer resolves, so `:wallet:plugins:cloud:test` could not pull it on a cold runner. Repointed at quay.io by digest. `localstack`, `vault` and `ganache-cli` were on `:latest`; all now name versions, and a gate rejects `:latest`.
- **F15 — gate evidence did not survive its own build cache.** `:verifiable-intent:test` came back `FROM-CACHE`: the JUnit XML is a declared task output so it was restored, but the reliability JSON the gate reads was written outside the declared outputs and was not. The evidence directory is now a declared test output, verified by deleting it, re-running to a cache hit, and watching it return.

**Partly closed:** F9 — a ratchet now forces every new module to declare a maturity; classifying the 102 recorded ones is a product decision.

**A correction to my own fix:** `CancellationException` is an `IllegalStateException`, so a guard placed after such a clause is unreachable. My first sweep did exactly that in two files. `check-cancellation-guards.py` now enforces the ordering as well as the existence, which is how those two were found.

**Open:** F4 (coverage), F7 (library instrumentation), T11 (deployment-scale qualification), T14 (compiled documentation). Each is a week or more of work and none was attempted here.

Five new gates keep the closed findings closed — `check-workflow-pinning`, `check-dependency-catalog`, `check-publication`, `check-capability-coverage`, and the shared `build_root` resolver. All are wired into CI and the release workflow, and each ships with unit tests.

## Evidence

- [`evidence/tests.json`](evidence/tests.json) — 3,897 tests, 0 failures, 0 errors, 15 skips, 489 suite XMLs
- [`evidence/coverage.json`](evidence/coverage.json) — merged and per-module Kover figures for 83 modules
- [`evidence/build-tail.log`](evidence/build-tail.log) — the failing build result
- [`evidence/ktlintMainSourceSetCheck.txt`](evidence/ktlintMainSourceSetCheck.txt), [`evidence/ktlintTestSourceSetCheck.txt`](evidence/ktlintTestSourceSetCheck.txt) — the 232 violations
- [`scores.json`](scores.json), [`tasks.json`](tasks.json)

Commands: `./gradlew build --max-workers=3 --continue`, `./gradlew checkKotlinAbi --max-workers=3`, `python -m unittest discover -s scripts -p 'test_check_*.py'`, `python scripts/check-documentation.py`, `python scripts/generate-capability-docs.py --check`, `python scripts/check-coverage-policy.py`, `python scripts/check-junit-contract.py --build-root <real>`, `python scripts/check-test-evidence.py --build-root <real>`, `python scripts/check-reliability-evidence.py`, `python scripts/check-vi-matrix.py`.
