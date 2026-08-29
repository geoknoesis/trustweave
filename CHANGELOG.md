# Changelog

All notable API changes are described here. The project does not yet follow strict semantic versioning in this file; treat entries as migration notes.

## [0.7.0] - 2026-08-29

Covers the 483 commits landed since 0.6.0. Minor bump rather than patch: this release contains
breaking changes, which semver permits within a `0.x` line.

**Read this section before upgrading — it contains five breaking changes.**

### Removed

- **BREAKING — `credentials:plugins:bbs` deleted.** `BbsCryptoSuite` was never BBS+: it keyed an
  HMAC on the first 32 bytes of the *public* key and never used the secret key, so anyone holding
  an issuer's public key — published in its DID document — could forge a proof that verified. There
  is no maintained BBS+ signature library on Maven Central to replace it with, and a placeholder is
  worse than nothing for a signature scheme. `ProofSuiteId.BBS_2023` remains a recognised
  identifier with no engine behind it; such a proof now never verifies. Consumers depending on
  `credentials:plugins:bbs` must remove the dependency. (`d46ec976`)

### Changed

- **BREAKING — `DidResolutionResult.Deactivated` and `Failure.OptionsError` added** per DID
  Resolution 1.0 §4.4. A deactivated DID now resolves to *no document* instead of a `Success`
  carrying `deactivated=true` metadata. Every exhaustive `when` over `DidResolutionResult` must
  handle `Deactivated`, and must treat it as a failure anywhere a signature is verified, a
  presentation validated, or an action authorised — previously callers were silently handed the
  document of a revoked DID. Map-based compatibility constructors and `resolutionMetadataMap`
  accessors are removed. (`ada1d24f`, `d0aa22e9`, `2569cc15`)
- **BREAKING — resolution metadata carries an RFC 9457 error object** instead of a string error
  code. (`6273f2d0`)
- **BREAKING — wallet plugin coordinates changed** from `org.trustweave.core` to
  `org.trustweave.wallet`, matching the per-domain convention every other plugin family follows
  (`did/plugins/*` → `org.trustweave.did`, `kms/plugins/*` → `org.trustweave.kms`,
  `anchors/plugins/*` → `org.trustweave.chains`). Artifacts are now
  `org.trustweave.wallet:wallet-plugins-{cloud,database,file}`.
- DID resolution targets DID Resolution 1.0 CR (2026-08-06), replacing the v0.3-era surface. See
  [docs/releases/did-resolution-1.0-migration.md](docs/releases/did-resolution-1.0-migration.md) —
  several changes interact, so read it in full.

### Security

Remediates the 2026-06-15 security review of the security-critical core. All seven HIGH findings
are closed and verified against source:

- **Verifiable-Intent authorization bypass.** A payment could be authorised with the L2 payment
  mandate omitted entirely; the chain verifier now fails closed when an L3 payment arrives without
  its authorizing L2 mandate.
- **Verifiable-Intent allowlists failed open.** When every `allowed_payees` / `allowed_merchants`
  entry was an SD-reference — the shape used by the reference fixture — the check passed vacuously,
  giving payee allowlists zero enforcement. References are now resolved against the presented
  disclosures. See *Known limitations* below for the remaining scope.
- **did:web SSRF and unbounded response bodies.** Resolution rejects hosts resolving to
  loopback / private / link-local / cloud-metadata addresses before connecting, enforces HTTPS, and
  caps the document body.
- **OID4VP over-disclosure.** Field-level selective disclosure was silently ignored and the full
  credential always disclosed. It now fails closed — see *Known limitations*.
- **OID4VCI had no PKCE.** `code_verifier` is now mandatory on the authorization_code flow
  (OAuth 2.1 / OID4VCI §3.4), and issued credentials are verified rather than trusted.
- **SSRF and ReDoS across the exchange protocols.** OID4VCI / OID4VP / SIOP fetch through an
  SSRF-guarded client with bounded bodies; untrusted Presentation Exchange regexes evaluate through
  a bounded, ReDoS-resistant matcher.
- **secp256k1 low-`s` normalisation wrote a malformed signature** when `n - s` needed fewer than 32
  bytes. See
  [docs/releases/secp256k1-signature-audit.md](docs/releases/secp256k1-signature-audit.md).

Also hardened: DIDComm replay, OID4VP nonce binding, proof-verification binding, JSON-LD
pre-canonicalization bounds, anchor verification integrity, VC API server input limits, and
Verifiable-Intent amount bounds / configuration leakage.

### Fixed

- **`credentials { autoAnchor(true) }` was a silent no-op.** The option was settable and plumbed
  through `CredentialsBuilder` → `TrustWeaveConfig` → `TrustWeaveFactory`, but nothing ever read
  it — `IssuanceBuilder` contained no anchoring code at all, so enabling it anchored nothing and
  reported success. Three existing tests configured it and passed, because none asserted that
  anything reached a chain. Now wired, with two deliberate properties:
  - **It anchors a SHA-256 digest envelope of the canonicalized credential, never the credential
    itself.** The default payload mode for an anchor client is full-payload, so a naive
    implementation would have let one boolean publish every subject's claims to a public ledger
    permanently. Verify by canonicalizing the credential, hashing, and comparing. Use
    `trustWeave.blockchains.anchor(credential, ...)` explicitly if you do want the full document
    on-chain.
  - **It fails closed**, matching the `withRevocation()` precedent in the same builder: a missing
    `defaultChain`, absent anchor layer, or failed write fails the issuance rather than returning
    a credential the caller believes was anchored.
- **247 tests had never run.** `fun x() = runBlocking { ... }` infers a non-`Unit` return type from
  its last expression, and JUnit silently ignores `@Test` methods that do not return `Unit` — the
  suite reported green while 247 tests were skipped without appearing in any report. 1,269 `@Test`
  bodies are now explicitly `Unit`. Activating them exposed 29 latent failures, all fixed. Suite:
  3,473 → **3,720 tests, 0 failures**.

### Added

- DID Resolution 1.0 conformance suite in `distribution:conformance` (38 tests across 5 suites,
  enforced by a hard floor).
- OIDC4VCI issued-credential verification.

### Known limitations

- **OID4VP field-level selective disclosure is not implemented.** Supplying `selectedFields` throws
  rather than disclosing the full credential. This closes the over-disclosure hole, but integrators
  expecting PEX-driven field selection will hit it at runtime.
- **Verifiable-Intent allowlist evaluation is scoped to open mandates.** When allowlist entries
  cannot be resolved from the presented disclosures, the check fails closed only for an *open*
  mandate, where unbounded authority makes an unevaluable allowlist dangerous. A bounded mandate
  still passes, on the assumption that it constrains the payee by other means — verify that
  assumption holds for your issuance profile.
- **`wallet:wallet-services` ships with no tests.** It is exported via the BOM; treat it as
  Experimental.

## [0.6.0] - 2025-03-23

### Added

- ktlint plugin for code formatting (`./gradlew ktlintCheck`, `./gradlew ktlintFormat`)
- Experimental/Stub Plugins section in docs/plugins.md documenting plugins not yet fully implemented
- TrustWeaveExtensionsTest: createDidAndIssue and createDidIssueAndStore tests via TrustWeave API

### Changed

- Version set to 0.6.0 for release
- Documentation links in README updated to point to correct docs/ paths
- SECURITY.md supported versions updated to 0.6.x
- CredentialStorage: KDoc for notRevoked(), revoked(), valid() documenting revocation handling limitations
- revoked() filter now returns false (accurate status requires CredentialRevocationManager)
- CloudHSM KMS: Fixed GitHub URL typo in error message
- DelegationDslTest: Marked @Disabled with clear reason (DelegationService not yet implemented)
- Testkit: Removed CredentialServiceRegistry TODOs and dead code
- Removed orphaned distribution/trustweave-bom (referenced non-existent projects)

### Fixed

- README links (GETTING_STARTED.md, API_GUIDE.md, etc.) now point to existing docs
- Documentation artifact coordinates aligned with build (org.trustweave:anchors-plugins-*, kms-plugins-*, did-plugins-*)

---

## Unreleased

- Presentations: use **`presentationResult`** / **`presentationFromWalletResult`** and **`buildResult()`** only; throwing helpers **`TrustWeave.presentation`**, **`presentationFromWallet`**, **`PresentationBuilder.build()`**, and **`WalletPresentationBuilder.build()`** have been removed.
- Configuration: use **`credentialService`** and **`getCredentialService()`**; **`TrustWeaveConfig.issuer`** and **`TrustWeave.getIssuer()`** have been removed.
- Test sources: legacy **`TrustLayer*`** / **`InMemoryTrustLayer*`** class and file names renamed to **`TrustWeave*`** / **`InMemoryTrustWeave*`** for consistent terminology.
- **`docs/README.md`**: examples now use **`TrustWeave`** / **`trustWeave`** and current result-style APIs (aligned with the root **`README.md`** quick start).
- **Documentation**: contributing test templates consolidated as **`trustweave-test-templates.md`** (links updated from **`trust-layer-test-templates.md`** in **`writing-tests.md`** and **`modules/trustweave-testkit.md`**); **`atlas-parametric-architecture-overview.md`** snippets aligned with **`DidCreationResult`**, **`DidResolutionResult`**, **`IssuanceResult.getOrThrow()`**, and **`BlockchainService.anchor(..., serializer, chainId)`**; **`wallet-api.md`**, **`parametric-insurance-mga-implementation-guide.md`**, and example **`println`** copy avoid the deprecated product name “Trust Layer” where **TrustWeave** is meant.
- **Documentation (sweep)**: Source **`docs/**/*.md`** (and mirrored **`docs/_site/**/*.md`**) updated for **`TrustWeave.build { }`** instead of assigning **`trustWeave { }`** to a facade variable; removed **`getDslContext()`** in favor of **`trustWeave.configuration`**, **`resolveDid`**, and **`trustWeave.revocation { }`**; **`org.trustweave.credential.model.vc.*`** imports; **`core-api.md`** quick reference, DID resolve/update, anchoring **`read`**, and advanced sections aligned with current APIs; **`architecture-overview.md`**, **`dsl-guide.md`**, **`trust-registry.md`**, **`mental-model.md`**, and **`STYLE_GUIDE.md`** terminology refreshed.
- **Documentation (follow-up)**: **`api-patterns.md`** DID update/rotate examples match **`DidDocument`** returns; **`mental-model`** / **`architecture-overview`** diagrams drop **`TrustWeaveContext`**; **`blockchain-anchoring.md`** fixes read/anchor examples (**`BlockchainException`**, not **`Result`+`TrustWeaveError`**); **`readAnchor`** renamed to **`blockchains.read`** across scenarios/tutorials; **`api-reference/README.md`**, **`error-handling.md`** (wallet **`WalletCreationResult`**, **`signedBy(did)`**), **`dids.md`**, and **`smart-contracts.md`** error text aligned with current behavior.
- Removed historical / internal audit markdown from **`docs/`** (phase summaries, documentation-improvement trackers, navigation meta, VC API cleanup logs, protocol code-review notes, **`API_SCORE`**, duplicate production-readiness evaluations under **`features/credential-exchange-protocols/`**, etc.); user-facing guides under **`getting-started`**, **`how-to`**, **`introduction`**, **`api-reference`**, and **`scenarios`** are unchanged.
