# Changelog

All notable API changes are described here. The project does not yet follow strict semantic versioning in this file; treat entries as migration notes.

## [Unreleased]

Remediation of the 11 September 2026 full-codebase review
(`docs/reviews/2026-09-11-full-codebase-review/`) and of the follow-up build, security and
documentation review.

**Read "Breaking and behaviour changes" before upgrading — it lists changes that make previously
working code fail until it is adjusted.**

### Breaking and behaviour changes

- **BREAKING — `DidRegistrarServer`, `VcApiServer`, `StatusListServer`, `Oidc4VciServer`,
  `AvpAuthorizationServer` and `TrustRegistryServer` refuse mutating requests until authentication
  is configured.** These servers create and deactivate DIDs, sign credentials with whatever keys
  their KMS holds or change trust state, and previously carried no authentication
  primitive at all — only a loopback bind default and documentation asking the operator to front
  them with a proxy. POST, PUT, PATCH and DELETE now return 503 `authentication_not_configured`
  until the host calls `withAuthentication(...)`. Reads are unaffected. Three ways to satisfy it:

  ```kotlin
  server.withAuthentication(HostAuthentication.bearerToken(System.getenv("API_TOKEN")))
  server.withAuthentication(HostAuthentication.custom { call -> gateway.authorize(call) })
  server.withAuthentication(HostAuthentication.frontedByProxy("mTLS terminated at the ingress"))
  ```

  `frontedByProxy` admits everything, exactly as before. It exists so that "a proxy handles it" is
  a recorded decision in the host's own code rather than the accidental result of configuring
  nothing.

- **Ethereum mainnet needs an explicit `rpcUrl`.** The anchor client no longer defaults to a free
  public node (and Sepolia no longer defaults to Alchemy's shared demo key); construction fails with
  `ConfigurationFailed` without one, because reads through it are trusted for verification. Sepolia
  defaults to a keyless public node. The StarkNet stub drops its retired testnet URLs and remains
  unregistered for SPI discovery.

- **`FileWallet` needs an encryption key or `FileWallet.unencrypted(...)`.** A `FileWallet` built
  without a key used to store credentials in plaintext with only a log warning; construction now
  fails. Plaintext needs the explicit `unencrypted(...)` factory, or
  `additionalProperties["allowPlaintext"] = true` through `FileWalletFactory`, which also refuses
  before creating any directory. Existing constructor signatures are kept; new `ByteArray` and
  `CharArray` key constructors copy the key and zero their intermediate buffers.

- **`WalletHolder.acceptCredentialOffer` needs a `credentialVerifier`** (a `CredentialService`,
  new optional constructor parameter; the old four-argument constructor is kept). It rejects with
  `CredentialRejectedException`, storing nothing, unless the credential verifies, names the offer's
  issuer and is bound to the holder DID; without a verifier it fails before contacting the issuer.
  did:web derivation from the issuer URL now follows the did:web spec (percent-encoded port, https only).
  An `issuerTrustPolicy` (`IssuerTrustPolicy.allowList(...)` or a registry lookup) decides whether the
  offered issuer is trusted at all and is now **required**: without one the offer fails before the issuer
  is contacted. `IssuerTrustPolicy.acceptAnyIssuer()` is the explicit, documented-unsafe opt-in for tests
  and demos (it logs a warning once per process). `issuerDidFor` works on the raw URL path and rejects
  ambiguous encodings (`%2F`, `%3A`, `%5C`, dot segments, control characters).

- **`fromJwt` rejects unsecured (`alg: none`) JWTs** unless the caller passes `allowUnsecured = true`,
  and there is no raw-JSON fallback. `toJwt` throws instead of silently returning plain JSON.

- **Trust registry `register` throws `ParticipantAlreadyRegisteredException`** for a DID that is
  already registered, in both the in-memory and database registries, and leaves the existing
  (possibly revoked) record untouched. `IssuerRecord`/`VerifierRecord` keep their previous full
  constructors; `copy()` gains the new `revocationReason` parameter (ABI dump updated).

- **did:web no longer follows redirects by default.** Resolution follows at most `maxRedirects`
  hops itself (0 by default); `WebDidConfig.followRedirects` now defaults to `false` and enables 5
  hops. HTTPS and the SSRF guard are re-checked on every hop.

- **did:plc write operations fail with `PLC_NOT_IMPLEMENTED`.** Create, update and deactivate used
  to post to endpoints the PLC directory does not have and fall back to a local store; they now fail
  before any key is generated. Resolution uses the directory's real `GET /{did}` endpoint.

- **The Spring registrar needs a bearer token or a proxy declaration.** `did:registrar-server-spring`
  refuses every POST/PUT/DELETE with 503 until `trustweave.registrar.auth.bearer-token` (32-256
  printable non-space ASCII characters, compared in constant time; 401 on mismatch) or
  `trustweave.registrar.auth.fronted-by-proxy` (a statement of what authenticates callers) is set.
  Job status reads are authenticated as well. The controller's constructor and endpoint
  signatures gain the authentication and `Authorization` header parameters (ABI dump updated).

- **The Ktor registrar gates job status reads.** `GET /1.0/jobs/{jobId}` needs the configured
  credential whenever an authenticator is set, whatever `protect` set was passed to
  `HostAuthentication` (job records can carry DID state). Other reads stay open. New public API:
  `HostAuthentication.protectingPathPrefixes(...)` (ABI dump updated).

- **`DidCommExamples` is no longer part of the public API.** It used `runBlocking` and now lives in
  the plugin's test sources (ABI dump updated).

- **Token status list manager throws instead of reporting "not revoked".** An unknown status list,
  an out-of-range or undeterminable index and an unindexed credential id now fail
  (`STATUS_LIST_UNAVAILABLE`, `RANGE_ERROR`, `STATUS_LIST_INDEX_UNKNOWN`, `NotFound`), and an
  unrecognised stored purpose no longer defaults to revocation. Status list tokens are now signed
  with a configured Ed25519 `issuerKeyId` from the KMS (`trustweave.statuslist.token.issuerKeyId`)
  instead of a throwaway key; building one without it fails with a `ConfigException`.

- **SD-JWT verification requires the issuer JWT `typ`.** The engine stamps `typ: dc+sd-jwt` at
  issuance and refuses an issuer JWT whose `typ` is not `dc+sd-jwt` or `vc+sd-jwt`; an absent `typ`
  is accepted only with `additionalOptions["allowLegacySdJwtTyp"] = true`. (An opt-in single-use
  KB-JWT nonce store, `kbJwtNonceStore`, is additive.)

- **Contract activation and execution are gated.** A contract can only become `ACTIVE` once a credential
  is bound (and an anchor, when an anchor registry is configured). `updateStatus` refuses `EXECUTED`:
  execute through `executeContract` (a `Manual` contract with no conditions executes; one with conditions
  is refused). `verifyAnchorOnVerify = true` now fails closed when no anchor client is registered for the
  anchor's chain. Per-contract locks replace the lock stripes, and `ContractStoreLimits` bounds the number
  of contracts and the status history kept per contract.

- **`FileWallet` records are bound to their wallet.** New writes use format version 2 with AES-GCM
  associated data (wallet id, record kind, SHA-256 of the credential id). Version 1 records are still
  read; a wallet must be reopened with the `walletId` it was written with.

- **Reference wallet issuer trust (web and Android).** An issuer named by an offer is no longer trusted
  or persisted by being named. Trust comes from the configured allow-list, the wallet's own backend
  identity, or an explicit user confirmation; the web `store()` takes `{ confirmedIssuer }` instead of the
  offer-issuer string, and a "Trusted issuers" list lets the user review and remove accepted issuers.

- **`bindContract` is only legal from `DRAFT` or `PENDING`.** Binding an executed or terminated
  contract no longer succeeds. `verifyContract` now requires the credential issuer to be a party,
  the service's own issuing DID, or accepted by an optional `TrustedIssuerPolicy`.

- **EVM anchor reads check the transaction.** A read needs a successful receipt, the expected
  sender and the expected recipient (default: a self-send, which is what the client writes).
  `expectedSender` defaults to the client's own signing account; verify-only clients must set
  `expectedSender` or opt in with `acceptAnySelfSend=true`, otherwise reads fail with a clear error.

- **JSON canonicalization rejects integers beyond 2^53,** literals outside the JSON number grammar
  (`1d`, hex floats, `NaN`, `Infinity`) and lone surrogates, because RFC 8785 would silently round
  them into colliding digests. Carry large integers as strings.

- **Digest anchors hash an RFC 8785 (JCS) envelope.** New digest-mode envelopes carry `canon=JCS`
  and hash the canonical form, so a structurally equal payload verifies regardless of key order or
  number spelling. Legacy envelopes (no `canon` member) are still recognised and verified against
  the bytes the old write path hashed. The `requireCanonicalEnvelope` option (default `false`) makes a
  verifier reject legacy envelopes.

- Other behaviour changes: Ed25519 signatures in did-core are verified and an unchecked digest is
  never passed through; remote JSON-LD contexts are restricted to `https:` (`http:` needs a second
  explicit opt-in; `file:`, `jar:` and other schemes are refused) and cached in a bounded LRU;
  SD-JWT and VC-LD verification honour `VerificationOptions.revocationFailurePolicy` (fail closed
  by default) and process disclosures strictly; DID resolution rejects documents whose `id` differs from the
  requested DID and `verifyDocument` takes an `expectedDid`; did:jwk refuses private or unknown JWKs;
  did:ethr fails fast on a bad private key and no
  longer fakes anchors, did:polygon drops its fake transaction hash, did:ens reports the missing
  lookup as method-not-supported, did:cheqd derives identifiers per spec, did:sol derives its address
  from the Ed25519 public key; the waltid KMS no longer registers placeholder did:key/did:web methods
  through SPI.

### Added

- `HostAuthentication` in `observability`: constant-time bearer tokens, host-supplied authorizers,
  an explicit `frontedByProxy` declaration, and per-caller fixed-window rate limiting with bounded
  caller tracking.

- A real publication path — a declared Maven repository, `scm` and `issueManagement` in every POM,
  and a reviewer-gated publish job on `v*` tags. See `docs/operations/publishing.md`.

- RFC 8785 JSON canonicalization in `common`.

- Remote Bitstring Status List resolution: `BitstringStatusListManager` accepts a
  `RemoteStatusListResolver`. Lists are fetched over HTTPS (public addresses only, no redirects,
  size-capped), verified through an injected `StatusListCredentialVerifier`, checked for type,
  issuer, `statusPurpose` and minimum length, decompressed under a cap and briefly cached; every
  failure throws a specific code, and without a resolver unknown lists still fail closed.

- `closeAsync` on the `trust` facade, with the non-sealed facade contracts documented.

- A pluggable, expiry-based DIDComm replay store that never evicts live ids; `purge` for the Azure KMS.

- `docs/reviews/README.md`, `.github/CODEOWNERS`, recommended branch protection in `CONTRIBUTING.md`,
  CodeQL analysis of the security-critical modules (also on pull requests that touch them), and
  dependency review plus OSV-Scanner over the SBOM and resolved JARs. The OSV job fails for advisories
  outside `config/osv/baseline.json` (`scripts/check-osv-baseline.py`). The documentation check now
  verifies that `org.trustweave.*` imports in docs resolve to main sources.

### Changed

- `BitstringStatusListManager`'s bitstring encode and decode are now `suspend` and check
  cooperative cancellation every 8192 bits. A cancelled status-list refresh previously ran the full
  131072-entry loop to completion. `updateCredentialStatus` became `suspend` with them; all its
  callers were already suspend, so no public signature changed.

- `ChainVerifier.verifyAndReserveBudget` translates ledger `IllegalStateException` and
  `IllegalArgumentException` into an invalid `ChainVerificationResult` instead of propagating them.
  One signed L2 carrying two payment-mandate disclosures with different budgets resolves to the
  same ledger account and previously threw out of a function whose contract is a result.

- Dependency versions all come from `gradle/libs.versions.toml`. 61 coordinates were literals in
  module build files and had drifted from the catalog: web3j 4.10.0 → 4.14.0 (did:ethr, did:polygon and
  did:ens, through a `web3j-legacy` alias; the EVM anchor base uses web3j 5.0.2), gson 2.10.1 → 2.14.0,
  slf4j 2.0.9 → 2.0.17 and kotlinx-coroutines-test 1.8.1 → 1.10.2 are now in effect. bitcoinj stays
  on 0.16.2 through a new `bitcoinj-legacy` alias, because 0.17 is a breaking API change; the drift
  is recorded rather than hidden. `scripts/check-dependency-catalog.py` fails the build on a new
  literal coordinate.

- The cloud SDK BOMs in `kms:plugins:aws`, `kms:plugins:cloudhsm`, `kms:plugins:azure`,
  `kms:plugins:google` and `wallet:plugins:cloud` were still literals the catalog check could not
  see (nested in `platform(...)`). They now come from the catalog: AWS SDK 2.20.0 → 2.43.0, Azure
  SDK BOM 1.2.15 → 1.3.6, Google Cloud libraries-bom 26.22.0/26.38.0 → 26.80.0. Consumers of those
  modules resolve the newer cloud SDKs.

- Security-relevant dependency updates inside the same major: Jackson 2.21.4 → 2.22.3 and Bouncy
  Castle 1.84 → 1.86 (each cleared advisories reported by OSV), SLF4J 2.0.17 → 2.0.20, MongoDB BSON
  4.11.0 → 4.11.5, Azure Identity 1.18.2 → 1.18.7. WireMock moved from `wiremock-jre8` 2.35.2 to
  `org.wiremock` 3.13.2.

- Published `-javadoc` jars now contain Dokka-generated API documentation instead of being empty.

- `docs/reviews` no longer carries raw generated evidence (about 32 MB of JUnit, JaCoCo, SBOM and
  coverage output); it is a CI artifact. Git history still holds the blobs. Stale one-off
  migration scripts and superseded root reports moved to `docs/archive` or were deleted.

- CI is split into parallel jobs; `TRUSTWEAVE_INDY_INTEGRATION` applies to the build job only.

### Fixed

- Four `suspend` functions swallowed `CancellationException` through a broad catch with an empty
  body (`TrustedDomainManager.emitSafely`, `InMemoryDomainTreasury.emitSafely`, and two testkit
  integration helpers). They now rethrow it.

- The published POM pointed every consumer at `docs/reference/module-maturity.md`, which does not
  exist. It is `docs/api-reference/module-maturity.md`.

- `scripts/check-test-evidence.py` and `scripts/check-junit-contract.py` defaulted to the in-repo
  `build/` directory, which on this project's documented Windows layout is not where Gradle writes.
  Run with their defaults they reported 40 evidence failures and 8 invalid JUnit methods that did
  not exist. They now resolve the build root the way the build does and refuse a root with no
  results rather than reporting on it.

- `PrivateNetworkGuard` blocks CGNAT, reserved and IPv4-embedding IPv6 ranges, and did:web checks
  the addresses actually connected to (DNS rebinding).
- Status-list resolver and `close` rethrow `CancellationException` before broad catches;
  `trustweaveCatching` never captures fatal JVM errors; plugin lifecycles run outside the registry monitor.
- XAdES rejects signature wrapping and binds the signer to `SigningCertificateV2`; CAdES signs
  through the KMS without `runBlocking`; PAdES fails with `UnsupportedOperationException`.
- `KmsBasedRegistrar` delegates operations instead of faking them; the trust-registry server maps
  registry failures to the right status codes and the revocation reason survives re-activation.
- OIDC4VP populates `requestedClaims` from what the verifier asked for; `expectedChallenge` and
  `expectedDomain` are honoured without the flag; the Android and web reference wallets verify
  credentials before storing them and fail closed unless the issuer is trusted (an allow-list; the
  web wallet's `requireHolderKeyBinding` option demands `cnf.kid == sub` on plain VC-JWTs, default off); the trust facade keeps the original issuance failure when a status index is
  orphaned and picks the `assertionMethod` key in `getKeyId`.
- DIDComm no longer blocks the caller's dispatcher in secret resolvers and does not rotate on
  invented key ages; Vault public keys are parsed strictly and curve-checked; the Azure KMS honours
  `endpointOverride`; KMS, anchor and Azure configuration redact secrets in `toString`; the
  Salesforce/ServiceNow stubs fail with typed errors; EVM reads reject reverted and foreign
  transactions; contract credentials are verified and contract execution is serialised.
- Verifiable Intent states plainly that multi-pair L2 is unsupported.

### Security

- The fixes above in the "Breaking and behaviour changes" and "Fixed" sections that close
  authentication, SSRF, signature-validation and fail-open paths (registrar servers, Spring
  registrar, did:web, JSON-LD contexts, JWT, SD-JWT, XAdES, wallet storage, offer acceptance).
- Every GitHub Actions reference is pinned to a commit SHA (`setup-gradle` and
  `attest-build-provenance` were repinned) and every workflow declares `permissions`. The nightly
  conformance job's `issues: write` lives in a separate job that runs no repository code.

- Dependency scanning: OSV-Scanner and dependency review run on every push and pull request; the
  `org.didcommx:didcomm` 0.3.2 embedded-Nimbus risk is documented in `SECURITY.md`.


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

### Earlier migration notes

These entries were kept under a second "Unreleased" heading at the end of this file. They were
written before the 0.7.0 release (2026-08-29) and shipped in it.

- Presentations: use **`presentationResult`** / **`presentationFromWalletResult`** and **`buildResult()`** only; throwing helpers **`TrustWeave.presentation`**, **`presentationFromWallet`**, **`PresentationBuilder.build()`**, and **`WalletPresentationBuilder.build()`** have been removed.
- Configuration: use **`credentialService`** and **`getCredentialService()`**; **`TrustWeaveConfig.issuer`** and **`TrustWeave.getIssuer()`** have been removed.
- Test sources: legacy **`TrustLayer*`** / **`InMemoryTrustLayer*`** class and file names renamed to **`TrustWeave*`** / **`InMemoryTrustWeave*`** for consistent terminology.
- **`docs/README.md`**: examples now use **`TrustWeave`** / **`trustWeave`** and current result-style APIs (aligned with the root **`README.md`** quick start).
- **Documentation**: contributing test templates consolidated as **`trustweave-test-templates.md`** (links updated from **`trust-layer-test-templates.md`** in **`writing-tests.md`** and **`modules/trustweave-testkit.md`**); **`atlas-parametric-architecture-overview.md`** snippets aligned with **`DidCreationResult`**, **`DidResolutionResult`**, **`IssuanceResult.getOrThrow()`**, and **`BlockchainService.anchor(..., serializer, chainId)`**; **`wallet-api.md`**, **`parametric-insurance-mga-implementation-guide.md`**, and example **`println`** copy avoid the deprecated product name “Trust Layer” where **TrustWeave** is meant.
- **Documentation (sweep)**: Source **`docs/**/*.md`** (and mirrored **`docs/_site/**/*.md`**) updated for **`TrustWeave.build { }`** instead of assigning **`trustWeave { }`** to a facade variable; removed **`getDslContext()`** in favor of **`trustWeave.configuration`**, **`resolveDid`**, and **`trustWeave.revocation { }`**; **`org.trustweave.credential.model.vc.*`** imports; **`core-api.md`** quick reference, DID resolve/update, anchoring **`read`**, and advanced sections aligned with current APIs; **`architecture-overview.md`**, **`dsl-guide.md`**, **`trust-registry.md`**, **`mental-model.md`**, and **`STYLE_GUIDE.md`** terminology refreshed.
- **Documentation (follow-up)**: **`api-patterns.md`** DID update/rotate examples match **`DidDocument`** returns; **`mental-model`** / **`architecture-overview`** diagrams drop **`TrustWeaveContext`**; **`blockchain-anchoring.md`** fixes read/anchor examples (**`BlockchainException`**, not **`Result`+`TrustWeaveError`**); **`readAnchor`** renamed to **`blockchains.read`** across scenarios/tutorials; **`api-reference/README.md`**, **`error-handling.md`** (wallet **`WalletCreationResult`**, **`signedBy(did)`**), **`dids.md`**, and **`smart-contracts.md`** error text aligned with current behavior.
- Removed historical / internal audit markdown from **`docs/`** (phase summaries, documentation-improvement trackers, navigation meta, VC API cleanup logs, protocol code-review notes, **`API_SCORE`**, duplicate production-readiness evaluations under **`features/credential-exchange-protocols/`**, etc.); user-facing guides under **`getting-started`**, **`how-to`**, **`introduction`**, **`api-reference`**, and **`scenarios`** are unchanged.

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
