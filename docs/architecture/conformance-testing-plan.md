---
title: Conformance Testing and Certification Plan
nav_order: 30
parent: Architecture
status: Draft (proposed)
last_updated: 2026-05
---

# Conformance Testing and Certification Plan

> **Status: Draft / proposed.** This document defines TrustWeave's roadmap for
> *protocol-level* conformance testing and formal certification. It runs
> **in parallel with** the eIDAS Qualified Electronic Signatures (QES) build-out
> tracked in [eIDAS QES Design](./eidas-qes-design.md) — the two efforts share
> resources but have independent acceptance criteria.
>
> The plan covers everything from "self-run W3C test suites with a published
> implementation report" through "EUDI Wallet Large-Scale Pilot (LSP)
> participation" and "Conformity Assessment Body (CAB) engagement for the
> qualified-signature path".
>
> Reviewers: engineering lead, product lead, compliance lead. Approver:
> product lead.

---

## 1. Goals

1. **Publishable conformance claims for every standard TrustWeave implements.**
   Today the codebase ships protocol implementations for the W3C VC Data
   Model 2.0, W3C DID Core 1.1 (and DID Resolution v0.3), W3C Data Integrity
   `Bbs2023`, W3C Bitstring Status List, IETF SD-JWT VC, IETF Token Status
   List, ISO/IEC 18013-5 mdoc/mDL, OpenID4VCI, OpenID4VP (incl. HAIP),
   SIOPv2, DIF Presentation Exchange v2, OpenID Federation 1.0, and the
   EUDI Wallet ARF (EU PID + profile overlays). Each of these has a
   public, standardised conformance method; today, none of them are run on
   our CI. The goal is that **every standard has a self-published
   conformance run accessible from `docs/conformance/`**.

2. **CI-integrate at least one conformance suite per major protocol.**
   Conformance must be a *regression gate*, not a one-shot exercise. Each
   plugin module that implements a protocol gets a dedicated Gradle task
   that runs the appropriate conformance suite (or a downloaded vector
   set), and the matching GitHub Actions workflow blocks PRs that break it.

3. **Pursue formal certifications where they unlock procurement or
   regulatory acceptance.** Specifically: OpenID Foundation (OIDF) cert
   for OID4VCI / OID4VP, EBSI Wallet Conformant status, and — once
   the eIDAS QES work is mature — EUDI LSP participation and a CAB
   engagement for the qualified-signature path.

4. **Build an evidence catalog usable by a Conformity Assessment Body
   (CAB).** Audit-grade artefacts (clause-by-clause coverage docs, dated
   raw test outputs, human-readable summaries) live under
   `docs/conformance/` in a structure designed to be handed straight to
   an assessor.

## 2. Non-goals (MVP)

- **Replacing pilot deployments with synthetic tests.** Conformance
  suites prove protocol-level interoperability; they do not prove product
  fit, UX, or operational readiness. Real-world pilots remain a separate
  track.
- **Formal certification before code stabilizes at 1.0.** Paying for a
  CAB engagement, or attempting OIDF certification, against a moving
  target wastes money and goodwill. Phases 1-2 (self-run + OIDF) are
  acceptable pre-1.0; Phases 3-5 (EBSI / LSP / CAB) wait for 1.0 unless
  product leadership accepts the cost.
- **Bespoke test harness work.** Where an upstream suite exists, we use
  it. We do not fork conformance suites; we contribute upstream if a
  gap blocks us.

---

## 3. Conformance suites — target matrix

| # | Standard / spec | Suite | Owner module(s) | Type | Setup cost (eng-days) | Priority |
|---|------------------|-------|------------------|------|------------------------|----------|
| 1 | W3C VC Data Model 2.0 | [`w3c/vc-data-model-2.0-test-suite`](https://github.com/w3c/vc-data-model-2.0-test-suite) | `credentials/credential-api` | Automated (self-host) | 3 | P0 |
| 2 | W3C DID Core 1.1 | [`w3c/did-test-suite`](https://github.com/w3c/did-test-suite) | `did/did-core`, all `did/plugins/*` | Automated (self-host) | 5 | P0 |
| 3 | DID Resolution v0.3 | Same suite as #2 (resolver harness) | `did/did-core` resolver | Automated (self-host) | 1 (combined w/ #2) | P0 |
| 4 | DIF Presentation Exchange v2 | [`decentralized-identity/presentation-exchange`](https://github.com/decentralized-identity/presentation-exchange) vectors | `credentials/plugins/presentation-exchange` | Vector-based | 2 | P0 |
| 5 | OpenID4VCI | OIDF [`certification.openid.net`](https://www.certification.openid.net/) — `oid4vci-*` plans (see [list-test-plans](https://www.certification.openid.net/list-test-plans.html)) | `credentials/plugins/oidc4vci` | Automated (hosted by OIDF) | 5 | P0 |
| 6 | OpenID4VP | OIDF `oid4vp-*` plans on `certification.openid.net` | `credentials/plugins/oidc4vp` | Automated (hosted by OIDF) | 4 | P0 |
| 7 | SIOPv2 | OIDF `siopv2-*` plans (if/when published — currently bundled with OID4VP plans) | `credentials/plugins/siop` | Automated (hosted) | 2 | P1 |
| 8 | EBSI Wallet Conformance v3.x | [EBSI Conformance Hub](https://hub.ebsi.eu/conformance/) | `did/plugins/ebsi`, `credentials/plugins/eudiw` | Interop (hosted by EBSI) | 6 | P1 |
| 9 | HAIP profile (high-assurance interop profile) | Internal validators in `credentials/plugins/oidc4vp` cross-checked against [EUDI Ref Wallet](https://github.com/eu-digital-identity-wallet) | `credentials/plugins/oidc4vp`, `credentials/plugins/eudiw` | Interop | 3 | P1 |
| 10 | ISO/IEC 18013-5 mdoc/mDL | Vendor test vectors (ISO does not publish a public open-source suite; we use the ISO Annex D + state-DMV test vectors that have been shared) | `credentials/plugins/mdl` | Manual / vector-based | 4 | P1 |
| 11 | SD-JWT VC + Token Status List (IETF) | [`oauth-selective-disclosure-jwt`](https://github.com/oauth-wg/oauth-selective-disclosure-jwt) test vectors + draft Token Status List vectors | `credentials/credential-api` (sd-jwt engine), `credentials/credential-api` status-list code | Vector-based | 2 | P1 |
| 12 | Data Integrity `Bbs2023` | [`w3c/vc-di-bbs-test-suite`](https://github.com/w3c/vc-di-bbs) | `credentials/plugins/bbs` | Automated (self-host) | 2 | P1 |
| 13 | Bitstring Status List | [`w3c/vc-bitstring-status-list-test-suite`](https://github.com/w3c/vc-bitstring-status-list) | `credentials/credential-api` status-list code | Automated (self-host) | 1 | P1 |
| 14 | OpenID Federation 1.0 | [OIDF federation interop fixtures](https://openid.net/wg/connect/) (no certified suite yet; community fixtures in [`openid/federation`](https://github.com/openid/federation)) | `credentials/plugins/openid-federation` | Vector-based + interop | 3 | P2 |
| 15 | EUDI Wallet ARF — LSP track | POTENTIAL, EWC, NOBID — schedule-driven interop events | `credentials/plugins/eudiw` | Interop (event-based) | n/a (institutional) | P2 |

**Legend.**
- **Type — Automated:** runs unattended in CI; failures cause PR blocks or nightly alerts.
- **Type — Vector-based:** static test vectors in repo; conformance check is a JUnit/Kotest test that asserts our output equals the vector.
- **Type — Interop:** runs against a remote system (hosted by OIDF, EBSI, EU LSP). Requires network credentials.
- **Type — Manual:** human-driven check; cannot fully automate today.
- **Priority — P0:** required before TrustWeave 1.0.
- **Priority — P1:** required for "ecosystem credibility" against walt.id.
- **Priority — P2:** strategic / institutional; tracked but not blocking.

---

## 4. CI integration

### 4.1 Gradle layout

Each plugin module gets a `conformanceTest` task isolated from regular `test`,
so contributors can run normal unit tests without dragging in network calls or
large vector downloads:

```
:credentials:credential-api:conformanceTestVcDataModel20
:credentials:credential-api:conformanceTestSdJwtVc
:credentials:credential-api:conformanceTestBitstringStatusList
:credentials:plugins:bbs:conformanceTestDataIntegrityBbs
:credentials:plugins:oidc4vci:conformanceTestOidf
:credentials:plugins:oidc4vp:conformanceTestOidf
:credentials:plugins:oidc4vp:conformanceTestHaip
:credentials:plugins:siop:conformanceTestOidf
:credentials:plugins:presentation-exchange:conformanceTestPeV2
:credentials:plugins:openid-federation:conformanceTestFederation
:credentials:plugins:mdl:conformanceTestMdoc
:credentials:plugins:eudiw:conformanceTestEbsi
:credentials:plugins:eudiw:conformanceTestArf
:did:did-core:conformanceTestDid11
:did:did-core:conformanceTestDidResolution
```

A root-level aggregator task `./gradlew conformanceTest` runs every leaf task.

Each leaf task is wrapped in a Gradle convention plugin (added to
`buildSrc/`) that:

- Pins the upstream suite revision in `gradle/libs.versions.toml` (alongside
  the existing dependency versions).
- Downloads test vectors to a cached `build/conformance/<suite>/` directory.
- Emits a single JSON report (`build/conformance/<suite>/run.json`) plus a
  short human-readable Markdown summary (`build/conformance/<suite>/report.md`).
- Fails the build on any test failure unless the test is on a known-issue
  allow-list (`docs/conformance/<suite>/known-issues.md`), which is reviewed
  every release.

### 4.2 GitHub Actions workflows

Two new workflows live in `.github/workflows/`:

- **`conformance-pr.yml`** — runs on PRs. Uses `paths` filters to skip
  irrelevant work: a PR touching only `kms/plugins/azure` doesn't need to
  run the OID4VCI suite. Maps:

  | PR path glob | Suites triggered |
  |---|---|
  | `credentials/credential-api/**` | VC 2.0, SD-JWT VC, Bitstring Status List |
  | `credentials/plugins/oidc4vci/**` | OIDF OID4VCI |
  | `credentials/plugins/oidc4vp/**` | OIDF OID4VP, HAIP |
  | `credentials/plugins/siop/**` | OIDF SIOPv2 |
  | `credentials/plugins/presentation-exchange/**` | DIF PE v2 |
  | `credentials/plugins/bbs/**` | DI-Bbs2023 |
  | `credentials/plugins/mdl/**` | ISO 18013-5 vectors |
  | `credentials/plugins/eudiw/**` | EBSI conformance (read-only mode), ARF profile |
  | `credentials/plugins/openid-federation/**` | OpenID Federation |
  | `did/did-core/**`, `did/plugins/**` | W3C DID 1.1 + DID Resolution |

- **`conformance-nightly.yml`** — runs the **full** aggregator nightly
  on `main`. Posts a Slack summary; on failure, opens an issue tagged
  `conformance`. Uploads `build/conformance/**` as a workflow artifact.

For suites that require network credentials (OIDF, EBSI), the workflow
uses GitHub repository secrets and runs only on `main` and on PRs from
branches in this repository (not from forks).

### 4.3 Publication path

Reports are mirrored into the repository under `docs/conformance/<suite>/`
on every successful nightly. This extends the existing pattern already
established by
[`docs/conformance/did-1-1-implementation-report.md`](../conformance/did-1-1-implementation-report.md)
and
[`docs/conformance/W3C-DID-TEST-SUITE.md`](../conformance/W3C-DID-TEST-SUITE.md).
The proposed layout per suite is:

```
docs/conformance/
  <suite-id>/
    report.md           # human summary, regenerated each run
    run-<YYYY-MM-DD>.json
    coverage.md         # clause-by-clause mapping (manually curated)
    known-issues.md     # tracked failures and their allow-list rationale
```

A small auto-generated index file
`docs/conformance/index.md` lists all current suites and their last-known
result. Badge-style shields (red / amber / green) are derived from the
nightly artefact and surfaced in the root `README.md` under a new
"Conformance" section.

### 4.4 What "green" means

A suite is **green** when:

1. Every required (non-optional) test in the upstream suite passes
   against the latest TrustWeave `main`.
2. The allow-list in `docs/conformance/<suite>/known-issues.md` is empty
   *or* every entry on it is tagged with an upstream-tracking issue URL
   and a target-fix date.

A suite is **amber** if there are allow-listed failures but none are
required-to-pass. A suite is **red** if any required-to-pass test fails
and is not allow-listed — this blocks merges into `main`.

---

## 5. Per-suite setup playbook

The playbooks below are ordered roughly by priority. Each playbook is
intentionally compact; the full how-to lives in the relevant
`docs/conformance/<suite>/` directory once the suite is wired up.

### 5.1 W3C VC Data Model 2.0 test suite — P0

- **Description.** Reference JavaScript suite that validates VC 2.0
  documents (data model, contexts, status, proof handling). Runs against
  a small HTTP shim that we provide.
- **Setup.** Clone `w3c/vc-data-model-2.0-test-suite`. Add TrustWeave as
  an implementation: write a tiny Ktor server in `testkit/` exposing
  `/issue`, `/verify`, `/derive` endpoints. Register the implementation
  in the suite's `config.cjs`.
- **Gradle/CI.**
  `./gradlew :credentials:credential-api:conformanceTestVcDataModel20`
  spawns the shim on a random port, invokes the upstream suite via
  `node`, parses the Mocha JSON output into our `run.json` shape.
- **Frequency.** Per PR (when `credentials/credential-api/**` is
  touched); nightly always.
- **Report destination.** `docs/conformance/vc-data-model-2.0/`.
- **Estimated wire-up cost.** 3 engineer-days (Ktor shim is the bulk;
  Gradle wrapper is trivial).

### 5.2 W3C DID 1.1 test suite — P0

- **Description.** Validates DID document syntax (§3.1 ABNF), parsing,
  serialization, verification relationships, services, and resolver
  outputs. Already triaged for TrustWeave in
  [`docs/reference/did-core-1-1-compliance-and-gaps.md`](../reference/did-core-1-1-compliance-and-gaps.md).
- **Setup.** Clone `w3c/did-test-suite`. For each DID method plugin
  under `did/plugins/`, supply a fixture JSON and either (a) an in-process
  resolver hook or (b) an HTTP resolver endpoint conforming to DID
  Resolution v0.3. The shared `DidDocumentJsonProducer` and
  `DidDocumentJsonParser` (referenced in the existing
  [DID 1.1 implementation report](../conformance/did-1-1-implementation-report.md))
  give us a single point of integration.
- **Gradle/CI.**
  `./gradlew :did:did-core:conformanceTestDid11` and
  `:did:did-core:conformanceTestDidResolution`. The CI runner installs
  Node 22, runs `npm install && npm run test-and-generate-report`,
  uploads the report.
- **Frequency.** Per PR touching `did/**`; nightly always.
- **Report destination.** `docs/conformance/did-1-1/` (replaces the
  current placeholder report).
- **Estimated wire-up cost.** 5 engineer-days (resolver HTTP shim per
  plugin is the longest single piece).

### 5.3 DIF Presentation Exchange v2 — P0

- **Description.** Validates Presentation Definition / Presentation
  Submission handling. Conformance is via static JSON vectors in the
  DIF repository.
- **Setup.** Vendor the upstream `test/` directory of
  `decentralized-identity/presentation-exchange` into
  `credentials/plugins/presentation-exchange/src/test/resources/dif-pe-v2-vectors/`
  via a Gradle download task that pins to a git ref.
- **Gradle/CI.** A standard JUnit/Kotest test (`PeV2VectorsTest`) loads
  every vector and asserts our matcher's output matches.
- **Frequency.** Per PR when
  `credentials/plugins/presentation-exchange/**` changes; nightly always.
- **Report destination.** `docs/conformance/dif-pe-v2/`.
- **Estimated wire-up cost.** 2 engineer-days.

### 5.4 OIDF OpenID4VCI conformance suite — P0

- **Description.** OIDF's hosted suite at `certification.openid.net`.
  Stands up a synthetic client/wallet that drives our issuer through
  the OID4VCI flows defined in the
  [test-plans list](https://www.certification.openid.net/list-test-plans.html).
- **Setup.**
  1. Create an OIDF certification account.
  2. Register a TrustWeave OID4VCI issuer endpoint, deployed in a
     dedicated CI environment (Ktor server backed by `testkit` in-memory
     services).
  3. Provide JWKS and metadata; configure a test-only KMS key.
  4. Pick the `oid4vci-*` test plan(s) matching the profile(s) we claim
     (e.g. `oid4vci-pre-authorized-code`, `oid4vci-authorization-code`,
     `oid4vci-sd-jwt`).
- **Gradle/CI.**
  `:credentials:plugins:oidc4vci:conformanceTestOidf` invokes the
  certification API via REST (the OIDF suite exposes a JSON API for
  programmatic invocation). The task uploads logs to artifacts and
  parses pass/fail counts.
- **Frequency.** Nightly on `main`; per PR only when
  `credentials/plugins/oidc4vci/**` changes (because runs cost network
  time).
- **Report destination.** `docs/conformance/oidf-oid4vci/`.
- **Estimated wire-up cost.** 5 engineer-days (most of which is hosting
  the issuer endpoint in CI and managing OIDF credentials safely).

### 5.5 OIDF OpenID4VP conformance suite — P0

- **Description.** OIDF's hosted suite for OpenID4VP, covering verifier
  and wallet roles. We target both roles.
- **Setup.** Same workflow as 5.4 with a TrustWeave verifier endpoint
  *and* a wallet simulator (the wallet simulator uses the in-memory
  wallet from `wallet:wallet-core` + the OIDC4VP plugin).
- **Gradle/CI.**
  `:credentials:plugins:oidc4vp:conformanceTestOidf`.
- **Frequency.** Nightly + per PR (path-filtered).
- **Report destination.** `docs/conformance/oidf-oid4vp/`.
- **Estimated wire-up cost.** 4 engineer-days.

### 5.6 OIDF SIOPv2 conformance — P1

- **Description.** SIOPv2 conformance tests are currently delivered as
  part of the OID4VP test plan set. Where a standalone SIOPv2 plan is
  available we run it directly; otherwise this suite is parasitic on
  5.5 and we only report SIOPv2-specific sub-results.
- **Setup.** Reuse the verifier endpoint from 5.5; add `siop` plugin
  endpoints.
- **Gradle/CI.**
  `:credentials:plugins:siop:conformanceTestOidf`.
- **Frequency.** Nightly only (saves OIDF capacity).
- **Report destination.** `docs/conformance/oidf-siopv2/`.
- **Estimated wire-up cost.** 2 engineer-days.

### 5.7 EBSI Wallet Conformance v3.x — P1

- **Description.** EBSI's hosted suite at
  [`hub.ebsi.eu/conformance`](https://hub.ebsi.eu/conformance/) runs
  protocol-level interop tests against `did:ebsi` + EBSI-flavoured
  OID4VCI/OID4VP, including trust registry interactions.
- **Setup.**
  1. Request EBSI test-network access (free, requires a verified
     organisation identity).
  2. Onboard a TrustWeave wallet built from `did/plugins/ebsi` +
     `credentials/plugins/eudiw`.
  3. Configure credentials & VCs aligned with EBSI's test issuers.
- **Gradle/CI.**
  `:credentials:plugins:eudiw:conformanceTestEbsi`. In PR mode it runs
  only the offline subset (vector-based); the full hosted suite runs
  nightly to respect EBSI rate limits.
- **Frequency.** Per PR (offline subset); nightly (full).
- **Report destination.** `docs/conformance/ebsi-wcc-v3/`.
- **Estimated wire-up cost.** 6 engineer-days, plus institutional time
  to onboard.

### 5.8 HAIP profile validation — P1

- **Description.** The High-Assurance Interoperability Profile (HAIP)
  for OpenID4VP narrows the OID4VP search space (formats, signature
  suites, cryptosuites, presentation rules) for use by the EUDI Wallet.
  We already have internal HAIP validators in
  `credentials/plugins/oidc4vp`; we extend with cross-validation
  against the EUDI Reference Wallet.
- **Setup.** Pin a known EUDI Ref Wallet commit; run a Dockerised
  reference issuer + wallet; drive against TrustWeave's verifier.
- **Gradle/CI.**
  `:credentials:plugins:oidc4vp:conformanceTestHaip` uses Testcontainers
  to bring up the reference wallet.
- **Frequency.** Per PR + nightly.
- **Report destination.** `docs/conformance/haip/`.
- **Estimated wire-up cost.** 3 engineer-days.

### 5.9 ISO/IEC 18013-5 mdoc/mDL — P1

- **Description.** ISO has no open conformance suite; instead, a small
  set of vendor test vectors (Annex D + state-DMV-shared sets) is the
  de-facto baseline. We assert encoding, COSE signing, device
  authentication, and selective disclosure against these vectors.
- **Setup.** Vendor the vectors we have a licence to use into
  `credentials/plugins/mdl/src/test/resources/iso-18013-5-vectors/`.
  Engage with state DMV pilot programs for further vector access (this
  is a partnerships task, not a coding task).
- **Gradle/CI.**
  `:credentials:plugins:mdl:conformanceTestMdoc`.
- **Frequency.** Per PR + nightly.
- **Report destination.** `docs/conformance/iso-18013-5/`.
- **Estimated wire-up cost.** 4 engineer-days for in-repo vectors;
  partnerships unbounded.

### 5.10 SD-JWT VC + Token Status List — P1

- **Description.** IETF SD-JWT VC has draft test vectors in the
  oauth-wg repo and the Token Status List draft ships with example
  vectors. We assert round-trip encode/decode/verify.
- **Setup.** Pin draft revisions; vendor vectors under
  `credentials/credential-api/src/test/resources/sd-jwt-vc-vectors/`
  and `…/token-status-list-vectors/`.
- **Gradle/CI.**
  `:credentials:credential-api:conformanceTestSdJwtVc`.
- **Frequency.** Per PR + nightly.
- **Report destination.** `docs/conformance/sd-jwt-vc/` and
  `docs/conformance/token-status-list/`.
- **Estimated wire-up cost.** 2 engineer-days combined.

### 5.11 Data Integrity `Bbs2023` — P1

- **Description.** W3C VC-WG test suite for the BBS+ Data Integrity
  cryptosuite (`Bbs2023`).
- **Setup.** Clone `w3c/vc-di-bbs-test-suite`; provide a TrustWeave
  implementation entry pointing at a local Ktor server backed by
  `credentials/plugins/bbs`.
- **Gradle/CI.**
  `:credentials:plugins:bbs:conformanceTestDataIntegrityBbs`.
- **Frequency.** Per PR + nightly.
- **Report destination.** `docs/conformance/vc-di-bbs2023/`.
- **Estimated wire-up cost.** 2 engineer-days.

### 5.12 Bitstring Status List — P1

- **Description.** W3C test suite covering bitstring-based status
  list encoding, compression, and lookup.
- **Setup.** Mirror 5.11 with a different shim.
- **Gradle/CI.**
  `:credentials:credential-api:conformanceTestBitstringStatusList`.
- **Frequency.** Per PR + nightly.
- **Report destination.** `docs/conformance/bitstring-status-list/`.
- **Estimated wire-up cost.** 1 engineer-day.

### 5.13 OpenID Federation 1.0 — P2

- **Description.** No certified suite yet; community fixtures and a
  small set of OIDF federation interop vectors exist. We run the
  vectors and participate in OIDF federation interop calls when they
  happen.
- **Setup.** Vendor vectors; subscribe to OIDF federation WG calendar.
- **Gradle/CI.**
  `:credentials:plugins:openid-federation:conformanceTestFederation`.
- **Frequency.** Per PR + nightly (vectors); quarterly (interop call).
- **Report destination.** `docs/conformance/openid-federation/`.
- **Estimated wire-up cost.** 3 engineer-days.

### 5.14 EUDI Wallet ARF — LSP track — P2

- **Description.** The EUDI Wallet ARF is conformance-tested *de
  facto* by participation in Large-Scale Pilots — POTENTIAL, EWC, and
  NOBID. These are quarterly/biannual interop events.
- **Setup.** Institutional sponsorship required (see §10). The
  engineering deliverable is a hardened deployment of
  `credentials/plugins/eudiw` plus an issuer/verifier instance.
- **Gradle/CI.** Not directly. Each LSP event produces a written
  report which we cite in our evidence catalog.
- **Frequency.** Event-driven (next POTENTIAL plenary date drives
  scheduling).
- **Report destination.** `docs/conformance/eudi-lsp/`.
- **Estimated wire-up cost.** 0 engineer-days for CI; substantial
  product-management time.

---

## 6. Formal certification roadmap

Phasing is calendar-quarter relative to "now" (today is 2026-05).
"Now" means *the current quarter*; downstream phases land in the listed
quarter. Each phase is gated by the prior phase landing successfully.

### Phase 1 — Self-run W3C and DIF suites (now → end of current quarter)

- Wire up suites 1, 2, 3, 4, 11, 12, 13 from §3 into CI.
- Publish implementation reports under `docs/conformance/<suite>/` with
  the structure defined in §4.3.
- Extend the existing
  [DID 1.1 implementation report](../conformance/did-1-1-implementation-report.md)
  to reference real upstream-suite runs (currently it cites only the
  self-assessment in
  [DID Core 1.0 vs 1.1 and TrustWeave compliance gaps](../reference/did-core-1-1-compliance-and-gaps.md)).
- Add `docs/conformance/index.md` and the README badge section.

**Exit criteria.** Every P0 self-run suite green on `main` for ten
consecutive nightlies.

### Phase 2 — OIDF certification (next quarter, ~Q3 2026)

- Submit OIDF OID4VCI and OID4VP conformance test runs against a
  hosted TrustWeave issuer/verifier.
- Pick the OIDF "Certified" status (paid) once the unpaid public-list
  run is clean. The
  [OIDF test plans list](https://www.certification.openid.net/list-test-plans.html)
  is the authoritative source for what's available at submission time.
- Publish badges, list TrustWeave on the OIDF certified deployments
  page.
- SIOPv2 follows when a dedicated test plan is published; until then,
  cite the SIOPv2-relevant sub-results from the OID4VP run.

**Exit criteria.** TrustWeave listed on `openid.net/certification` for
OID4VCI and OID4VP.

### Phase 3 — EBSI Conformant Wallet status (~Q4 2026)

- Apply for EBSI Conformant Wallet via `hub.ebsi.eu/conformance`
  against `did:ebsi` (`did/plugins/ebsi`) + the EUDIW plugin
  (`credentials/plugins/eudiw`).
- Maintain green-on-nightly for the EBSI suite for at least the
  preceding month before submitting.
- Coordinate with EBSI on credential profile alignment — this is the
  step most likely to surface protocol gaps.

**Exit criteria.** Listed in EBSI's wallet conformance registry.

### Phase 4 — EUDI LSP participation (~2027)

- Apply for EWC or POTENTIAL membership. Requires institutional
  sponsorship + a hardened operational deployment.
- Depends on Phase 3 being complete (EBSI Conformant status is
  effectively a prerequisite, or at minimum a positive signal).
- Depends on the [eIDAS QES Design](./eidas-qes-design.md) being shipped
  so that LSP testing of the qualified-signature path has something to
  test.

**Exit criteria.** TrustWeave listed as an LSP participant in at least
one of POTENTIAL / EWC / NOBID.

### Phase 5 — CAB engagement (~2027-2028)

- Engage a notified Conformity Assessment Body for the
  qualified-signature path produced by the eIDAS QES work.
- Hand over the evidence catalog assembled across Phases 1-4 (see §7).
- Pursue conformity attestation per eIDAS 2 / EU 910/2014.
- This phase is the most expensive and most time-consuming. We do not
  start it until product leadership commits the budget and the QES
  path is operationally hardened.

**Exit criteria.** Out of scope for this document; will be defined in
the QES design once Phase 4 lands.

---

## 7. Evidence catalog

A CAB or a procuring government will not read our source code first.
They will ask: *"show me a binder."* The directory below is that
binder.

```
docs/conformance/
  index.md                                    # auto-generated landing page
  <suite-id>/
    report.md                                 # human-readable summary
    coverage.md                               # clause-by-clause mapping
    known-issues.md                           # allow-listed failures + rationale
    run-2026-05-28.json                       # raw test output, dated
    run-2026-06-15.json
    run-<latest>.json
```

**`report.md`** is regenerated on every nightly. It contains:
- Date of run, commit SHA, test-suite version pinned.
- Pass / fail / skipped counts.
- Plain-English claim ("TrustWeave conforms to W3C VC Data Model 2.0
  §4.2 (issuance), §4.4 (status), §5.1 (data integrity)…").

**`coverage.md`** is hand-curated. It maps every normative clause of the
spec to (a) the TrustWeave module that implements it and (b) the
specific test name in the upstream suite that exercises it. Format is
modelled on
[`docs/reference/did-core-1-1-compliance-and-gaps.md`](../reference/did-core-1-1-compliance-and-gaps.md):
a markdown table per major spec section, with the clause text on the
left and TrustWeave evidence on the right.

**`known-issues.md`** lists every test that is currently failing or
skipped, with:
- the test name,
- the upstream issue URL (if filed),
- the TrustWeave issue URL,
- a planned-fix release.

**`run-*.json`** is the raw output. We keep at least the latest run plus
one per quarter for historical audit purposes; older runs are pruned to
keep the repo small.

---

## 8. Comparison with walt.id

walt.id is the current reference for "what good looks like" on the
protocol-conformance dimension. The matrix below makes the gap
explicit.

| Dimension | walt.id today | TrustWeave today | TrustWeave gap-closing target |
|---|---|---|---|
| OIDF certification (OID4VCI) | Certified | Code in `credentials/plugins/oidc4vci`, no run | Phase 2 (~Q3 2026) |
| OIDF certification (OID4VP) | Certified | Code in `credentials/plugins/oidc4vp`, no run | Phase 2 (~Q3 2026) |
| EBSI Conformant Wallet | Yes | `did/plugins/ebsi` + `credentials/plugins/eudiw` present, no application | Phase 3 (~Q4 2026) |
| EU LSP participation (POTENTIAL) | Member | Not a member | Phase 4 (~2027) |
| EU LSP participation (EWC) | Member | Not a member | Phase 4 (~2027) |
| EU LSP participation (NOBID) | Member | Not a member | Phase 4 (~2027) |
| Published implementation reports (W3C VC 2.0, DID 1.1, DIF PE) | Yes | Placeholder for DID 1.1 only — see [DID 1.1 implementation report](../conformance/did-1-1-implementation-report.md) | Phase 1 (now) |
| Public conformance badges | Yes (README) | None | Phase 1 (now) |
| CAB engagement for qualified-signature path | In progress | Not started — depends on QES design | Phase 5 (~2027-2028) |

The phase plan in §6 is deliberately structured to **close one row of
this table per phase**, in the order that maximizes commercial
credibility per engineer-month spent. Phase 1 alone closes three rows
(implementation reports + badges + most W3C-suite gaps) at very low
cost, which is why it's the immediate priority.

---

## 9. Risks and dependencies

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| OIDF conformance suite changes underneath us. | High | Medium | Pin suite revisions in `libs.versions.toml`; track OIDF working-group changelog; subscribe to OIDF announcement list. |
| OIDF certification requires a publicly reachable endpoint with TLS. | High | Low (cost) | Stand up a small CI-only endpoint with a free TLS cert; budget for hosting. |
| EBSI conformance requires test-network credentials, free but rate-limited. | High | Medium | Run EBSI full suite only nightly; offline subset on PRs. Escalate to EBSI support if rate-limit blocks releases. |
| W3C VC 2.0 + DI Bbs2023 suites are still moving targets (REC not yet ratified at time of writing). | High | Medium | Pin suite revisions; treat failures introduced by upstream churn as amber, not red, until the upstream suite stabilizes. |
| ISO 18013-5 conformance has no public open-source suite — partner access required. | Medium | High | Engage state DMV programs and ISO-aligned vendors for vector access; document what we cannot run. |
| LSP participation requires institutional sponsorship, not just engineering. | Certain | High | Flag as non-engineering blocker for product leadership; do not attempt to absorb into the engineering plan. |
| HAIP profile drift — the EUDI Reference Wallet updates faster than HAIP itself. | Medium | Medium | Pin Ref Wallet commit; review monthly. |
| OpenID Federation 1.0 has no certified suite; relying on community fixtures is fragile. | Medium | Low | Track OIDF federation WG; revisit when a certified suite appears. |
| Conformance failures bottleneck releases if treated as hard blockers prematurely. | Medium | Medium | Allow-list mechanism in `known-issues.md` lets us release with documented gaps; amber state is acceptable for non-required tests. |
| CAB engagement (Phase 5) drifts in scope as eIDAS 2 implementing acts evolve. | High | High | Defer Phase 5 until eIDAS 2 implementing acts are stable and the QES design is hardened. |

---

## 10. Resource requirements

### Engineering

- **One engineer, two weeks (~10 engineer-days), to wire all P0
  automated suites into CI** (suites 1, 2, 3, 4, 5, 6, 11, 12, 13 from
  §3 — total estimated cost is roughly 25 eng-days, but several share
  scaffolding so the realistic figure is closer to two weeks of
  focused work for one engineer with build-tooling experience).
- **Half an engineer, ongoing, to maintain + triage failures.** Most
  weeks this is ~3-4 hours; release weeks closer to a full day. Owner:
  the conformance lead, rotating per release.
- **One engineer, three additional weeks**, spread across the
  remainder of the current and next quarter, to land suites 7, 8, 9,
  10, 14 (P1 suites — EBSI, HAIP, ISO 18013-5, SD-JWT VC, OpenID
  Federation). These are harder because of hosted-suite credentials,
  Testcontainers reference-wallet setup, and ISO-vector access.

### Non-engineering (flag for product leadership)

- **OIDF certification fee.** Public listing as a Certified
  implementation involves OIDF certification fees per profile; check
  the current OIDF schedule before Phase 2 kickoff.
- **EBSI test-network onboarding.** Free, but requires an organisation
  identity verification step that needs a director-level signatory.
- **LSP institutional sponsorship.** Membership in POTENTIAL / EWC /
  NOBID is the single largest non-engineering lift in this plan.
  Membership requires national / EU-level sponsorship and a formal
  application. Engineering cannot resolve this; it is a board-level
  decision.
- **CAB engagement.** Multi-month, paid. Budget at least the cost of a
  senior-consultant-quarter, plus the assessor's fee. Out of scope
  until Phase 5.

### Hosting

- A small always-on CI environment is needed for the OIDF and EBSI
  hosted suites to call into. A single small VM (1 vCPU, 2 GB RAM, public
  TLS endpoint) is sufficient. Estimated cost: ~$10-20/month.

---

## 11. References

### Upstream test suites and certification bodies

- OpenID Foundation certification: <https://www.certification.openid.net/>
- OIDF current test plans:
  <https://www.certification.openid.net/list-test-plans.html>
- EBSI Conformance Hub: <https://hub.ebsi.eu/conformance/>
- W3C VC Data Model 2.0 test suite:
  <https://github.com/w3c/vc-data-model-2.0-test-suite>
- W3C DID test suite: <https://github.com/w3c/did-test-suite>
- W3C VC-DI BBS test suite: <https://github.com/w3c/vc-di-bbs>
- W3C VC Bitstring Status List test suite:
  <https://github.com/w3c/vc-bitstring-status-list>
- DIF Presentation Exchange:
  <https://github.com/decentralized-identity/presentation-exchange>
- IETF SD-JWT VC working group:
  <https://github.com/oauth-wg/oauth-selective-disclosure-jwt>
- EUDI Reference Wallet:
  <https://github.com/eu-digital-identity-wallet>

### Comparator

- walt.id GitHub: <https://github.com/walt-id>
- walt.id community edition (for comparison of published conformance
  results): <https://github.com/walt-id/waltid-identity>

### Internal TrustWeave references

- [eIDAS QES Design](./eidas-qes-design.md) — parallel track, drives
  Phases 4 and 5.
- [CLEAN_ARCHITECTURE.md](./CLEAN_ARCHITECTURE.md) — explains the
  layered structure that conformance suites slot into.
- [credential-api architecture](./credential-api-architecture.md) —
  proof-engine and credential-service layering, relevant for the
  VC 2.0 and SD-JWT VC suites.
- [DID 1.1 implementation report](../conformance/did-1-1-implementation-report.md)
  — current placeholder; to be expanded under Phase 1.
- [W3C DID Test Suite — how to run](../conformance/W3C-DID-TEST-SUITE.md)
  — step-by-step that informs §5.2 above.
- [DID Core 1.0 vs 1.1 and TrustWeave compliance gaps](../reference/did-core-1-1-compliance-and-gaps.md)
  — template for the per-suite `coverage.md` artefact.
- [Module maturity](../reference/module-maturity.md) — used to decide
  which plugins are ready for a paid conformance run.
- [Version compatibility](../reference/version-compatibility.md) —
  tracks which spec versions are wired into which TrustWeave releases.

---

*End of document.*
