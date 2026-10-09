# Changelog

All notable API changes are described here. The project does not yet follow strict semantic versioning in this file; treat entries as migration notes.

## [Unreleased]

Remediation of the 11 September 2026 full-codebase review
(`docs/reviews/2026-09-11-full-codebase-review/`) and of the follow-up build, security and
documentation review.

**Read "Breaking and behaviour changes" before upgrading — it lists changes that make previously
working code fail until it is adjusted.**

### Release provenance

- **Provenance now covers the published files.** The release `publish` job publishes to Maven Central and to a
  local `build/release-staging` repository in one Gradle invocation, writes `SHA256SUMS` over every staged file
  plus the aggregate CycloneDX SBOM, and attests all of them (and `SHA256SUMS`) with
  `actions/attest-build-provenance`. A new `release-assets` job attaches `SHA256SUMS` and the SBOM to the GitHub
  release. The earlier attestation of separately rebuilt JARs in the `evidence` job is removed, and that job no
  longer holds `id-token`/`attestations` write. `SECURITY.md` ("Verifying a Release") shows `gh attestation verify`.

- **Release ordering fixed.** The `publish` job now builds and signs once into `build/release-staging` (no Central
  credentials), verifies every staged jar is byte-identical to the jar the evidence job validated
  (`scripts/verify-staged-jars.py`, using the new `jar_sha256` section of `validation-manifest.json`), writes
  `SHA256SUMS`, attests, and only as its last step uploads that directory to the Central Portal
  (`scripts/upload-to-central.py`, `USER_MANAGED`, with `--dry-run`). Nothing reaches Central before the checksums
  and attestations succeed, and the attested bytes are the uploaded bytes. The BOM now publishes to `releaseStaging`
  too (it was missing). The Central HTTP exchange is unit-tested only up to request construction; a real tag run is
  the proof.
- **Tag must be on main.** The evidence job fails a `v*` tag whose commit is not an ancestor of `origin/main`.
- **One contract-check list.** `scripts/run-contract-checks.sh` is run by both `ci.yml` and `release-evidence.yml`;
  the release gate now also runs `check-telemetry-operations.py` and `check-workflow-evidence-paths.py`.
- **OSV baseline.** Triage expiries are staggered (needs-review 1/8/15 Nov 2026, affected 10/17/24/31 Dec 2026,
  none later than before); package matching uses `group:name` instead of the artifact name alone; the weekly run
  opens or updates one issue for entries expiring within 30 days (`issues: write` on that job only).

### Breaking and behaviour changes

- **Behaviour (security) — SD-JWT-VC revocation is evaluated on the signed `credentialStatus`.**
  `SdJwtProofEngine.verify` reads `vc.credentialStatus` from the issuer-signed JWT, rejects an envelope
  `credentialStatus` that differs from it (an absent envelope value is allowed), and runs the status check on the signed
  value; `VerificationResult.Valid.credential` now carries that signed status, and `DefaultCredentialService` runs its
  revocation check on `Valid.credential`. Stripping or repointing the unsigned envelope no longer hides a revoked
  credential.
- **Behaviour (security) — DID client_ids need a signed request object (`oidc4vp`, `siop`).** An unsigned request
  (plain JSON from `request_uri`, or bare URL parameters) whose `client_id` is a DID or whose scheme is `did` is
  refused (`Oidc4VpException.AuthorizationRequestFetchFailed` / `UrlParseFailed`, `SiopV2Exception` code
  `UNSIGNED_REQUEST_OBJECT`; SIOP's model also defaults an absent scheme to `did`). `submitPermissionResponse` and
  `SiopV2Service.submitResponse` refuse a `response_uri` that is not https (plain http only for loopback)
  (`INSECURE_RESPONSE_URI` for SIOP).
- **Behaviour — OID4VCI issuer (`oidc4vci-server`).** A credential is now issued for one configuration of the offer
  (named by `credential_configuration_id`, else the first of the request's types still open, else the next in offer
  order) instead of every configuration in each credential; `Oidc4VciIssuerService.issueCredential` has an overload taking
  `credentialConfigurationId`, and `TokenEntry` gained `remainingConfigurations` (constructor/`copy` signature changed;
  the 4- and 5-argument constructors remain). A failed credential build gives the consumed `c_nonce` back so the wallet
  can retry with the nonce it holds; malformed proof JWT members (array `aud`, object `alg`, ...) are `invalid_proof`
  rather than a 500. `Oidc4VciProtocolRateLimit` sweeps expired windows at most once per tenth of a window and spreads
  overflow callers over 64 hashed buckets per endpoint instead of one shared window.
- **Behaviour (security) — DIDComm replay ids are scoped by the authenticated sender.** `DidCommService` and
  `DatabaseDidCommService` key replay state on the sender the unpacking step authenticated (authcrypt sender or verified
  signer), not the attacker-controlled `from` header; messages with no authenticated sender share one bounded bucket and
  are remembered for ten minutes at most. Custom `DidCommReplayStore`s now see the scope `unauthenticated` for such
  messages.
- **Behaviour (security) — SD-JWT key binding.** The default maximum KB-JWT age is now 2 minutes (was 10; the
  verifier's clock-skew tolerance is still added; override with `kbJwtMaxAge`). Presentations whose KB-JWT `sd_hash`
  does not cover the credential's `disclosures` are rejected, as are additional SD-JWT credentials without an
  issuer-signed `cnf` (the KB-JWT only covers the first credential).
- **Behaviour — remote status lists.** `RemoteStatusListResolver` stops serving a cached list at the credential's
  `validUntil`/`expirationDate`, and one host may hold at most an eighth of the cache (and of the failure cache).

- **Behaviour (security) — trust-list signature verification is stricter (`signatures:trust-lists`).**
  `DefaultLotlSignatureVerifier` now requires exactly one `ds:Signature` (a direct child of the document
  element), exactly one whole-document `URI=""` reference with the enveloped-signature transform plus at most one
  `xades:SignedProperties` reference, and no duplicate `Id`/`ID`/`id`; a signature covering only a fragment is
  rejected as `Malformed`. The signer certificate must be valid at validation time (new
  `Invalid.SignerCertificateNotValid`, which also reports a `SigningTime` in the future; add a branch if you
  `when` over `LotlSignatureValidationResult` exhaustively), and the PKIX fallback uses the intermediates carried in
  `ds:KeyInfo`. `DefaultLotlSignatureVerifier(clock)` is optional (`@JvmOverloads`). `EtsiTrustListParser` now
  matches the ETSI namespace and the `TrustServiceStatusList` root instead of local names only. `TrustList` and
  `MemberStateTsl` gained trailing defaulted properties (`tslPointers`, `nextUpdateAt`, `schemeTerritory`), so the
  old `copy(...)` signatures are gone. New: `DefaultTslSignatureVerifier` and `VerifiedTrustListLoader`, which
  verifies the LoTL and every TSL against the certificates of its LoTL pointer, enforces `NextUpdate` and
  sequence-number rollback protection, and accepts stale lists only with `TrustListLoadOptions(allowStale = true)`.
- **BREAKING (security) — ETSI verifier hardening (XAdES, CAdES, JAdES, `etsi-validation`).**
  - XAdES: embedded `RevocationValues` earn B-LT/B-LTA credit only when a verified `ArchiveTimeStamp` covers them
    (they precede it); values appended after the last archive stamp no longer grant B-LTA.
  - CAdES: the signed `signing-certificate-v2` / `signing-certificate` attribute is now required and must match the
    chosen signer certificate (digest and issuer/serial, EN 319 122-1 5.2.2); an absent attribute is `Malformed`, a
    mismatch is `BadSignature`. Signatures made without it are no longer accepted.
  - XAdES, CAdES and JAdES judge the signer certificate's validity window at the authenticated time (trusted
    time-stamp) else now, never at the claimed `SigningTime` / `signing-time` / `sigT`; CAdES no longer skips the
    check when `signing-time` is absent. A since-expired certificate therefore needs a trusted time-stamp (or
    `allowExpiredCertificateAtSigningTime`). A not-yet-valid CAdES/JAdES signer certificate is reported as
    `SignerCertificateInvalid`.
  - JAdES: an `arcTst` is never credited as B-LTA (the EN 319 182-1 5.3.6 imprint is not implemented and the
    signer's private imprint is not the standard one), so `DefaultJadesVerifier` reports at most B-LT and
    `archivalTimeStamp` is always null; every `arcTst` token is checked, not only the first.
  - `etsi-validation`: the step outcomes over JAdES results are exhaustive `when`s with no fail-open `else`.

- **BREAKING (security) — VC API `POST /presentations/verify` enforces holder binding and one-time challenges.**
  The route now sets `enforceHolderBinding` (credential subjects must be the presentation holder and the proof key
  must belong to the holder) and requires `options.challenge` to be a challenge this server issued with the new
  `POST /presentations/challenge`; the challenge is consumed by the verification, so a signed presentation cannot be
  replayed. A missing, unknown, expired or already-used challenge answers `200 {"verified": false}` without
  verifying. `VcApiVerificationPolicy` (`VcApiServer.withVerificationPolicy`, or the new third parameter of
  `configureVcApiRoutes`) relaxes it: `enforceHolderBinding = false`, `requireChallenge = false`, or
  `challengeStore = null` to restore caller-chosen challenges. `InMemoryVcApiChallengeStore` is bounded (10,000
  challenges, 5 minute TTL by default) and per process; implement `VcApiChallengeStore` to share it across instances.
- **BREAKING (security) — OID4VCI access tokens are limited and offers are validated.** An access token obtains at
  most as many credentials as its offer has `credentialTypes` (`TokenEntry.remainingCredentials`; the next request
  is `400 invalid_request`). `createOffer` / `POST /api/offer` now reject an empty `credentialTypes`, a type that
  is not a key of `supportedConfigurations` (an issuer that advertises no configurations can no longer mint
  offers), and a `txCode` without `txCodeValue` or the reverse; the route answers `400 invalid_request`. `/token`,
  `/credential`, `/deferred_credential` and `/notification` get their own per-caller, per-endpoint rate limit
  (`Oidc4VciProtocolRateLimit`, 120 requests per minute by default; `Oidc4VciServer.withProtocolRateLimit(null)` to
  disable it behind a proxy that limits already). `TokenEntry` gained a field, so its 4-argument `copy` is gone.
- Wrong-typed fields on `/credential`, `/deferred_credential`, `/notification` and `/token` now answer
  `400 invalid_request` instead of 500. `/token` answers `invalid_grant` with no description for an unknown, expired
  or used code and for a wrong `tx_code`, and an unknown and an expired access token share one message.
- **`DefaultCredentialService.verify` verifies the proof before anything is fetched.** Revocation (status-list
  fetches) and trust evaluation now run after the proof engine accepts the credential, so an unverified credential
  cannot make the service call out. A credential that is both revoked and badly signed now reports the proof failure.
- **Status lists.** `RemoteStatusListResolver.DEFAULT_MAX_DECODED_BYTES` drops from 16 MiB to 2 MiB (16,777,216
  entries) and a new `maxCacheBytes` (default 32 MiB) bounds the decoded bytes cached across lists. The status-list
  server answers with `Cache-Control: public, max-age=60` and an `ETag` (`If-None-Match` gives 304;
  `cacheMaxAge` on `configureStatusListRoutes`), and `BitstringStatusListManager.buildStatusListVc` reuses the
  signed credential until the list changes or `signedStatusListCacheTtl` (5 minutes) passes instead of re-signing
  and rewriting it on every request.
- **BREAKING (security) — the OID4VCI issuer no longer emits unsigned credentials.**
  `Oidc4VciIssuerService` takes an `Oidc4VciCredentialBuilder` (`credentialBuilder`); without one every
  credential request is refused with `unsupported_credential_format`, and so is any format the builder does not
  list in `supportedFormats` (the requested `format` is no longer echoed back). `CredentialServiceCredentialBuilder`
  signs through a `CredentialService`/KMS (`ldp_vc` by default; map further formats with `formats`).
  `issueCredential` is now `suspend`, `createOffer` / `POST /api/offer` accept `claims`, which are carried into
  `credentialSubject`, and the proof JWT's `typ` header must be `openid4vci-proof+jwt` (previously only checked when
  present). `registerDeferredCredential` takes an optional `ownerAccessToken`; a credential registered with one can
  only be collected with that access token (`Oidc4VciIssuerStateStore.consumeDeferredOwnedBy`).
  `credential-api` and `did-core` are now `api` dependencies of the module.
- **BREAKING (security) — embedded servers refuse oversized request bodies.** `vc-api-server`, `oidc4vci-server`,
  `trust-registry-server` and `registrar-server-ktor` answer `413` for a body over 1 MiB (declared `Content-Length`
  or, for chunked requests, the bounded read), before `ContentNegotiation` parses it. Raise or lower it with
  `withMaxRequestBytes(...)`; the shared guard is `org.trustweave.observability.RequestBodyLimit`.
- **VC API `verified: true` is explicit about trust.** The verify endpoints accept an optional `TrustEvaluator`
  (`VcApiServer.withTrustEvaluator`); every response carries a `trust:evaluated` or `trust:not-evaluated` check and,
  in the latter case, a warning, because without an evaluator `verified` covers the proof and enabled checks only.
  Error responses carry a fixed message instead of the exception text (details are logged), and
  `/presentations/prove` answers `400` when any listed credential cannot be parsed instead of dropping it.
- **BREAKING (security) — trust registry host gates must cover writes.** `TrustRegistryServer` only stands its
  `apiToken` check down when the `HostAuthentication` gate covers every mutating method
  (`HostAuthentication.coversMutations()`); a gate that protects only `GET` no longer authorizes `POST`/`PUT`.
  `apiToken` must be at least 32 characters, as for `HostAuthentication.bearerToken`.
- **BREAKING (security) — mDoc status checks follow `RevocationFailurePolicy`.** `MdocProofEngine` treated
  `CheckFailed` as success; it now fails closed by default like the SD-JWT and VC-LD engines (new public
  `org.trustweave.credential.status.StatusCheckPolicy` for engines outside `credential-api`).
- **DIDComm replay ids are scoped to the sender and bounded per sender.** `DidCommReplayStore` gains a
  `recordIfAbsent(sender, ...)` overload (default: key on sender and id); `InMemoryDidCommReplayStore` takes
  `maxPerSender` (default 10,000) and refuses a sender's new messages past it, so one sender can neither fill the
  store nor make another sender's reused id look like a replay.
- **BREAKING (security) — JAdES time-stamps are only trusted against configured TSA anchors.**
  `JadesVerificationOptions` gains `timestampTrustAnchors` (empty by default). A `sigTst` / `arcTst` is
  authenticated only when its TSA signature and certificate verify against those anchors
  (`org.trustweave.signatures.revocation.TimeStampTokenVerifier`); a token from another TSA is rejected as
  `TimeStampMismatch`, and without anchors the token is only structurally checked, its time is the signer's
  claim, and the signature is reported as B-B. Callers that required `B_T` or higher must now pass their TSA
  certificates. `JadesValidationResult.Valid.foundProfile` now means the profile that was *proved*: B-LT also
  needs embedded revocation evidence that verified and covers the chain, B-LTA also a trusted `arcTst`, and
  requiring B-LT or above implies `RevocationPolicy.REQUIRED`. Garbage in `rVals` no longer yields B-LT.
- **BREAKING (security) — JAdES refuses `QualifiedWithdrawn` trust unless a trusted time-stamp predates the
  withdrawal** (new `Invalid.TrustWithdrawn`, option `allowWithdrawnTrustWithoutAuthenticatedTime`), as XAdES
  already did. `EtsiSignaturePolicy` gains `timestampTrustAnchors`; the TIME_STAMP_TOKEN step reports a `sigTst`
  as validated only when an anchor accepted it, and a policy that lists the WITHDRAWN status URI is what permits
  a withdrawn service.
- **BREAKING (security) — CAdES gets the same trust rules as XAdES and JAdES.** `CadesVerificationOptions`
  gains `timestampTrustAnchors`, `allowWithdrawnTrustWithoutAuthenticatedTime` and the revocation options.
  A signature time-stamp is trusted only when its TSA signature and certificate verify against the anchors
  (a forged token no longer yields B-T; without anchors the result is B-B, so callers requiring `B_T` must pass
  their TSA certificates); `QualifiedWithdrawn` trust is refused unless a trusted time-stamp predates the
  withdrawal (`Invalid.TrustWithdrawn`); the path is validated as of the trusted time over an ordered chain;
  a CA or non-signing certificate is refused (`Invalid.SignerCertificateInvalid`, also new in JAdES); caller
  supplied CRL / OCSP evidence is enforced (`Invalid.CertificateRevoked`, `Invalid.RevocationUnavailable`,
  `Valid.revocationChecked`). Embedded CMS revocation values are read (see the entry below).
- Time-stamp handling is aligned across formats: every token is checked (not only the first), SHA-256, SHA-384
  and SHA-512 imprints are accepted, and a time-stamp later than the claimed signing time is accepted while one
  that predates it is rejected (JAdES previously rejected a late stamp). A malformed or empty JAdES `x5c`
  is `Invalid.Malformed` instead of an exception, and the chain handed to PKIX is ordered and filtered.
- Trust lists: a service granted after the validation time does not qualify that signature, and a CA listed both
  withdrawn and granted resolves to granted whatever the list order. The ETSI validator no longer reports
  "cert path validated" for results that never reached path validation (time-stamp, profile, expiry) and fails
  the time-stamp step on a bad archival token.
- Revocation evaluation: an OCSP / CRL response without `nextUpdate` vouches for the present only for 24 hours;
  a signature-verified "revoked" entry in a CRL with critical extensions (delta, partitioned) is honoured though
  such a CRL cannot vouch that a certificate is good; and `revocationIssuerCertificates` are trust anchors:
  evaluation stops at a carried certificate that is one, so an intermediate must be carried in the signature.
  The evaluator, time-stamp verifier and path helper now have direct tests in `tsa-core`.
- **Trust path validation uses the trusted time.** `TrustAnchorResolver` gains
  `resolve(signerCert, chain, validationTime)` (default: ignore the time); `DefaultTrustAnchorResolver`
  validates the PKIX path as of that time, and the XAdES and JAdES verifiers pass the authenticated time-stamp
  time (never the signer's claimed time). A certificate that has since expired therefore validates for a
  signature it time-stamped while valid.
- Revocation evaluation fixes: a genuine CRL / OCSP entry saying "revoked" now counts however old the
  response is (freshness only decides whether a response may vouch that a certificate is good); an OCSP
  response with conflicting entries for one certificate is revoked whatever the order; a certificate is
  self-signed only when its own key verifies its signature.

- **BREAKING — timestamps are now `kotlin.time.Instant` and `kotlin.time.Clock` (kotlinx-datetime
  0.8.0).** kotlinx-datetime 0.7 moved `Instant`/`Clock` into the Kotlin standard library and 0.8
  no longer ships `kotlinx.datetime.Instant`/`Clock`. Every public signature, model field and
  default clock that used them (credential and DID document models, signatures, wallets, trust
  registry, anchors) now uses `kotlin.time.Instant`/`kotlin.time.Clock`. Migration for consumers:
  replace `import kotlinx.datetime.Instant` with `import kotlin.time.Instant` and
  `import kotlinx.datetime.Clock` with `import kotlin.time.Clock`; `Instant.parse`,
  `Clock.System.now()`, `toLocalDateTime` and the other `kotlinx.datetime` extension functions keep
  working. JSON stays ISO-8601 (verified by the existing serialization round-trip tests). The ABI
  dumps changed only by this type rename. Other kotlinx-datetime 0.7 changes (`TimeZone.UTC` id
  `"UTC"`, `dayOfMonth`/`monthNumber` renamed) do not affect TrustWeave sources.

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

- **Credential and signature verification is stricter.**
  - `PresentationNonceStore` keys are scoped as `<len>:<scope>:<nonce>` (scope from
    `PresentationNonceStore.SCOPE_OPTION_KEY` or `expectedDomain`); a store that cannot honour the proof
    type, is full or errors returns `Invalid` instead of being skipped or throwing.
  - XAdES refuses a `QualifiedWithdrawn` trust result unless a time-stamp predates the withdrawal
    (`allowWithdrawnTrustWithoutAuthenticatedTime` opts out), validates CA validity, `basicConstraints`,
    `pathLen` and key usage, and can verify an RFC 3161 signature time-stamp (`requireSignatureTimestamp`,
    `timestampTrustAnchors`; verify-only `XadesProfile.B_T`). Revocation is opt-in; see the XAdES revocation entry under "Added".
  - Token Status List: `lst` data with a valid ZLIB header must decode as ZLIB (corrupt or trailing bytes
    throw); ES256 (P-256) is supported; `defaultTtlSeconds` / `requireTtl` are new options.
  - The OID4VCI server maps errors per RFC (`invalid_proof` 400 with a fresh `c_nonce`, `invalid_token` 401
    with `WWW-Authenticate`, `server_error` 500), requires the access token on `/notification`, and
    bounds and expires offers, tokens and deferred credentials (503 when full).
  - `DatabaseDidCommReplayStore` keys rows by the SHA-256 of the message id, so rows written by earlier
    versions stop matching. Trust-registry `?status=` rejects unknown values with 400.

- **Hardening that can change observable behaviour.** The in-memory DID document cache and registrar job
  storage are bounded and expire entries (deactivation records are retained); PLC resolves HTTP 410 as
  deactivated and refuses redirects; API keys are refused over cleartext `http://` (loopback excepted);
  rate limiting counts failed authentications separately and no longer resets all callers' budgets under
  a caller spray; `InMemoryKeyManagementService` signs empty data.

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

- **Remaining small gaps (round five).**
  - `DefaultUniversalRegistrar` now sends its `apiKey` (`Authorization: Bearer`) on every request via
    `StandardUniversalRegistrarAdapter(apiKey)`; it was accepted and dropped before. The key is refused
    over cleartext `http://` to a non-loopback host. `UniversalRegistrarProtocolAdapter` gains
    `withApiKey(apiKey)`; its default throws `UnsupportedOperationException`, so a custom adapter that
    cannot authenticate fails at construction instead of silently dropping the key.
  - `DatabaseStatusListManager` revoke/suspend/unrevoke/unsuspend throw `TrustWeaveException.InvalidState`
    on a database or decoding failure (and when the list is full) instead of returning `false`.
    `false` now means only "no such status list" or "wrong purpose".
  - `EbsiException.httpError` and `OrbException.httpError` no longer put the upstream response body in
    the exception message (status only). The body is written, sanitised and capped at 512 characters,
    to the debug log. A transport failure surfaces as "Orb node request failed (no HTTP response)".
  - did:orb long-form DIDs of the shape `did:orb:<anchor>:<suffix>:<initial-state>` are verified (the
    initial state must hash to the suffix, else `invalidDid`) and may be answered under their canonical
    short form `did:orb:<anchor>:<suffix>`. Long-form requests whose initial state is not a consistent
    `{suffixData, delta}` are therefore now rejected. Any other id difference is still a mismatch.
  - KMS `sign()` rejection of empty data now names the provider constraint: AWS KMS (`Message` is
    1 to 4096 bytes) and Google Cloud KMS (`AsymmetricSign` needs non-empty `data` or `digest`).
    Vault Transit now accepts empty data and sends an empty base64 `input`; a Vault refusal is returned
    as `SignResult.Failure.Error`.
  - `EudiwOid4VciProfile.validateCredentialOffer` no longer echoes anything but the scheme of a rejected
    offer URI (it may carry a pre-authorized code).

### Added

- **CAdES B-LT verification.** `DefaultCadesVerifier` now reads the revocation values a CMS signature carries
  (the `id-aa-ets-revocationValues` unsigned attribute, whose `ocspVals` are re-wrapped as `OCSPResponse`, and the RFC 5652
  `SignedData.crls` field including `id-ri-ocsp-response`) and feeds them with caller evidence into
  `CertificateRevocationEvaluator`, as XAdES and JAdES do. New `CadesProfile.B_LT`: reported only when a trusted
  time-stamp authenticates the time and the embedded evidence alone shows the signer and every CA below the trust
  anchor not revoked; requiring `B_LT` implies `RevocationPolicy.REQUIRED`. Embedded data that does not parse is
  refused (`Invalid.Malformed`); at most 64 items of each kind are kept and items over 4 MiB are dropped.
  `CadesSigningRequest` rejects `B_LT` (the signer produces B-B and B-T only). Adding an enum entry changes
  the `CadesProfile` ABI, so exhaustive `when` expressions over it need a branch.

- Build and test hygiene: `distributionSha256Sum` is pinned in both Gradle wrapper properties; jars, sources
  jars and zips are reproducible (`isPreserveFileTimestamps = false`, `isReproducibleFileOrder = true`);
  `scripts/check-module-maturity-counts.py` fails when the test counts in `docs/api-reference/module-maturity.md`
  drift from the sources (several rows were out by up to 25x and three modules marked untested had tests);
  the dependency-graph job is blocking and the OSV gate runs with `--strict-stale`. The assertion-free
  `ScratchUriEquivalenceTest` was removed and the network-dependent, tautological godiddy resolver tests and two
  other always-true assertions were replaced by deterministic ones.
- Certificate revocation checking (CRL and OCSP) for XAdES, JAdES and the ETSI validation pipeline, and XAdES
  B-T / B-LT output. The shared evaluator is `org.trustweave.signatures.revocation.CertificateRevocationEvaluator`
  in `tsa-core`, with `RevocationPolicy` (`NOT_CHECKED` by default, so existing callers are unaffected;
  `CHECK_IF_AVAILABLE`; `REQUIRED`, which fails closed) and `RevocationEvidence`. Each CRL / OCSP response is
  verified against the issuing CA (an OCSP responder certificate needs the OCSP-signing purpose), CRLs with
  critical extensions are ignored, and evidence must be fresh for the signature: issued at or after an
  authenticated time-stamp, or current when the time is only claimed. A revocation dated after an
  authenticated signing time does not invalidate the signature.
  - XAdES: `XadesVerificationOptions` gains `revocationPolicy`, `revocationEvidence` and
    `revocationIssuerCertificates`; evidence also comes from `<xades:RevocationValues>`. New results
    `Invalid.CertificateRevoked` and `Invalid.RevocationUnavailable`; `Valid.revocationChecked` is `true` only
    when every certificate below the trust anchor was shown good. `XadesProfile.B_LT` is new: it is reported
    only when the time-stamp is trusted and the evidence embedded in the signature itself covers the chain
    (otherwise B-T), and requiring it implies `REQUIRED`. `DefaultXadesSigner` now produces B-T and B-LT
    (`XadesSigningRequest.tsaConfig` / `validationData`, new optional `tsaClientFactory`); it throws for no
    other profile. `XadesVerificationOptions.copy()` and `XadesSigningRequest.copy()` gain parameters (ABI dumps
    updated); the constructors keep their overloads.
  - JAdES: `JadesVerificationOptions` gains the same three options and evaluates `rVals`; `Valid.revocationChecked`
    and `Invalid.CertificateRevoked` / `Invalid.RevocationUnavailable` are new (`JAdESProofEngine` maps them to an
    invalid proof). The `sigTst` is not trust-validated by this verifier, so its time is never treated as
    authenticated: evidence must be current and any revocation counts.
  - `etsi-validation`: `EtsiSignaturePolicy` gains the same options. The REVOCATION step passes, fails or is
    inconclusive from the evidence, and LONG_TERM_VALIDATION reports on B-LT / B-LTA data; both stay
    not-applicable under the default `NOT_CHECKED`.
- `Oidc4VciIssuerStateStore` (with the default `InMemoryOidc4VciIssuerStateStore`) makes the OID4VCI
  issuer's offers, access tokens and deferred credentials pluggable through the new trailing
  `stateStore` constructor parameter of `Oidc4VciIssuerService`; existing constructors and behaviour
  are unchanged. `Oidc4VciIssuerStateStoreContract` (module test fixtures) is the abstract contract
  test a database-backed store reuses. `DeferredEntry` is now public.
- `SidetreeLongForm` in `sidetree-core` parses and verifies Sidetree long-form DIDs;
  `KmsInputValidator.validateSignData` gains `allowEmpty` and `emptyDataMessage` parameters.
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
- **XAdES B-LTA.** `XadesProfile.B_LTA` is new and `DefaultXadesSigner` produces it: on top of B-LT it appends an
  XAdES 1.4.1 `ArchiveTimeStamp` (`xades141:ArchiveTimeStamp`, SHA-256 imprint) over the dereferenced reference
  octets, `SignedInfo`, `SignatureValue`, `KeyInfo` and every unsigned signature property before it
  (ETSI EN 319 132-1). `DefaultXadesVerifier` verifies each archive token with `TimeStampTokenVerifier` against
  `timestampTrustAnchors` and recomputes the imprint; a present but untrusted, malformed or non-matching archive
  token fails as `Invalid.TimeStampInvalid` whatever profile was required. B-LTA is reported only when B-LT is
  proved too (otherwise B-LT, B-T or B-B, or `WrongProfile` when required), and requiring B-LTA implies
  `RevocationPolicy.REQUIRED`. `Valid` gains `archiveTimeStamp` (constructor and `copy()` change; ABI dump updated).

### Changed

- GitHub Actions bumped to checkout 7.0.1, setup-java 6.0.1, setup-node 7.0.0, upload-artifact 7.0.1, upload-pages-artifact 5.0.0, github-script 9.0.0, attest-build-provenance 4.2.2 and gradle/actions 6.4.0 (setup-gradle, dependency-submission), all SHA-pinned; `dependabot.yml` now groups Gradle and Actions updates and ignores the major upgrades deferred in `docs/contributing/dependency-upgrade-plan.md`.

- **Reference wallet dependencies refreshed.** Web: Next 16.3.8, React 19.3, TypeScript 7, `@noble/curves` 2 (`.js` subpaths, `ed25519.utils.toMontgomery`), `jose` 6, Vite 8.3, Vitest 5.0.3; Android: `security-crypto` 1.1.0 and Bouncy Castle 1.86. Expo and the Android toolchain upgrades are deferred (see `docs/contributing/dependency-upgrade-plan.md`).
- Ktor 3.4.3 (from 2.3.13) for every server and client module. The six embedded servers
  (`DidRegistrarServer`, `VcApiServer`, `StatusListServer`, `Oidc4VciServer`,
  `AvpAuthorizationServer`, `TrustRegistryServer`) now hold an
  `EmbeddedServer<NettyApplicationEngine, ...>` internally; their public API is unchanged. The AVP
  request-size guard reads the body with the new `io.ktor.utils.io.readAvailable` extension.
  Server tests (including the observability host-export test that starts real Netty servers) pass.
- **Dependency round (JVM)**: kotlinx-coroutines and coroutines-test 1.11.0, JUnit 6.1.3 (Jupiter
  and platform launcher share one version line), H2 2.5.252, mysql-connector-j 26.7.0 (same
  coordinates, Oracle's new year-based numbering), AWS SDK BOM 2.55.11, nimbus-jose-jwt 10.10.
  The didcomm plugin still excludes the standalone Nimbus jar because the didcomm fat jar embeds
  its own. H2 2.5 changes `LENGTH`/`CHAR_LENGTH` on binary values to count bytes.
- web3j 6.0.0 for every EVM consumer (`anchors:plugins:evm-base`, `did:plugins:ethr`, `polygon`,
  `ens`); the `web3j-legacy` catalog alias is removed. web3j 5.0.3+ uses Jackson 3 internally.
- bitcoinj 0.17.1 for `anchors:plugins:bitcoin` and `did:plugins:btcr`; the `bitcoinj-legacy`
  alias is removed. The Bitcoin anchor client now parses raw transactions with
  `Transaction.read`, hex-encodes with `java.util.HexFormat` and detects OP_RETURN with
  `ScriptPattern.isOpReturn`; behaviour is unchanged (the txid-integrity read test covers it).

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

- CI is split into parallel jobs. The Indy ledger suite now has its own workflow (`indy-integration.yml`,
  `TRUSTWEAVE_INDY_INTEGRATION=required`); the inert flag was removed from `ci.yml` and `release-evidence.yml`.

### Fixed

- **DID, KMS and wallet hardening.** `CachingDidResolver` no longer caches or serves requests carrying
  `versionId`, `versionTime` or `additional` options (a versioned result could be returned as the latest, or mask a
  deactivation), takes the TTL timestamp after the delegate returns, and drops a result fetched before a concurrent
  `invalidate`/`clear`. `CyberArkKeyManagementService.sign` rejects an algorithm incompatible with the stored key
  (`UnsupportedAlgorithm`), hashes RSA-3072/4096 with SHA-384/SHA-512 like the in-memory KMS (these keys previously
  signed with SHA-256), and zeroes decoded private-key bytes. `FileWallet` list/query/get/recover skip a record deleted
  concurrently. `CloudWallet` percent-encodes credential ids into one object-key segment (ids made of letters, digits
  and `-_.~:` keep their keys; ids containing `/`, `%`, spaces and similar characters move to encoded keys), refreshes
  `updatedAt` on re-store, and its KDoc no longer claims encryption (none exists). `DefaultUniversalResolver` bounds the
  response-body read by `timeout` and refuses redirects, including ones followed by an injected `HttpClient`.
  `AbstractWebDidMethod` gives its clients a 30 s whole-call deadline when none is set and cancels the HTTP call when the
  coroutine is cancelled. `KeyManagementServices` and `AlgorithmDiscovery` skip a provider that throws
  `ServiceConfigurationError` instead of failing for every provider. No public API signature changed.
- `DefaultXadesSigner` declares `xmlns:xades` and `xmlns:ds` on `QualifyingProperties`. Without them the
  signature was computed over a canonical form that differs from the serialised document's, so a B-B / B-T / B-LT
  signature failed validation after being written out and parsed again (it verified only as the in-memory DOM).
- The Fortanix, Thales, CyberArk and IBM KMS plugins now close every vendor HTTP response on all paths (previously failed deletes and some error paths leaked the connection) and report an empty response body as an error instead of parsing it as `{}`.

- `VerifiableCredential.isValid`, `isExpired`, `isExpiredAt` and `isValidAt` now share one temporal check, `VerifiableCredential.temporalValidity` (new `TemporalValidity` enum): the earliest of `expirationDate`/`validUntil` expires a credential, and it is not valid before the latest of `validFrom`/`issuanceDate`. The extension functions previously ignored VC 2.0 `validUntil`, and `isValidAt` ignored not-yet-valid credentials. These are temporal only, not revocation or proof checks.
- `EncryptedFileLocalKeyStore` serializes load/modify/save, replaces the key file atomically (fsync plus atomic move, never delete-then-rename), and fails loudly on an unparsable stored secret instead of silently dropping it on the next save.
- DID registrar job storage calls (`JobStorage`) run on `Dispatchers.IO` in `KmsBasedRegistrar` and the Ktor and Spring servers; `GET /1.0/jobs/{jobId}` answers 404 only for an unknown job and 400 for other invalid requests.
- `SafeRegex` in presentation-exchange uses a bounded worker pool and queue (saturation fails closed) and a bounded cache of compiled patterns.

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

- Jackson 3 (`tools.jackson`, pulled in transitively by web3j 6) is pinned to 3.1.4 in the root build and
  `gradle/libs.versions.toml`, closing GHSA-2m67-wjpj-xhg9, GHSA-5hh8-q8hv-fr38, GHSA-9fxm-vc8v-hj55 and
  GHSA-rcqc-6cw3-h962 that the OSV gate reported against 3.1.0.
- Netty 4.2 (pulled in transitively by Ktor 3) is pinned to 4.2.18.Final, closing GHSA-558v-64gr-wgg4,
  GHSA-mj4r-2hfc-f8p6 and GHSA-rwm7-x88c-3g2p that the OSV gate reported against 4.2.12. Only the 4.2 line is
  pinned; libraries that use Netty 4.1 keep their own version.
- The fixes above in the "Breaking and behaviour changes" and "Fixed" sections that close
  authentication, SSRF, signature-validation and fail-open paths (registrar servers, Spring
  registrar, did:web, JSON-LD contexts, JWT, SD-JWT, XAdES, wallet storage, offer acceptance).
- Every GitHub Actions reference is pinned to a commit SHA (`setup-gradle` and
  `attest-build-provenance` were repinned) and every workflow declares `permissions`. The nightly
  conformance job's `issues: write` lives in a separate job that runs no repository code.

- Dependency scanning: OSV-Scanner and dependency review run on every push and pull request; the
  `org.didcommx:didcomm` 0.3.2 embedded-Nimbus risk is documented in `SECURITY.md`.
- The OSV gate (`scripts/check-osv-baseline.py`, `config/osv/baseline.json` schema 3) now records a triage
  for every grandfathered advisory (`status`: affected / not-reachable / false-positive / accepted-risk /
  needs-review, plus `reason`, `reviewed`, `expires`) and **fails on an expired triage entry**. Matching is
  version-aware: a new version of a baselined package that the scanner still reports re-surfaces the
  advisory. Initial triage of the 90 entries: 65 affected, 5 not-reachable, 3 accepted-risk (didcomm) and
  17 needs-review (versions found in no resolved classpath); `--strict-stale` is unchanged. A baseline
  entry with a malformed or `TODO` triage is an error (exit 2).


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
