# TrustWeave SDK Code Review — 2026-06-11 (post-hardening re-audit)

Re-review of main @ `0e0d1d68` after the five-commit hardening effort (P0 security → P1 interop →
round-3 architecture → interop vectors → round-4 follow-ups). Same six-domain structure and rubric
as the 2026-06-10 baseline ([2026-06-10-sdk-code-review.md](2026-06-10-sdk-code-review.md)).

## Scores vs baseline

| Domain | 2026-06-10 | 2026-06-11 | Movement |
|---|---|---|---|
| Core architecture | 6/10 | 5.5/10 | ↓ stricter audit surfaced packaging/entry-point breakage |
| DID | 5.5/10 | 6/10 | ↑ flagship plugins fixed; ethr/stubs/DID-URLs remain |
| Credentials | 4/10 | 6.5/10 | ↑↑ core pipeline hardened & interop-proven; unhardened plugins remain |
| Wallet | 4/10 | 5.5/10 | ↑ file/db hardened; cloud wallets & presentation remain |
| KMS | 5/10 | 6/10 | ↑ P1363 contract holds; testkit drift, fictional backends remain |
| Anchors | 3.5/10 | 6/10 | ↑↑ evm-base/digest/EIP-155 solid; bitcoin/finality/KMS-signing remain |
| Trust registry | 3/10 | 4/10 | ↑ auth + UNKNOWN fixed; still a directory, not a trust registry |

**Overall: 6/10** (was 4.5/10). The verdict inverted: in June-10 the architecture was good and the
security-critical internals were broken; now the hardened core **held up under fresh adversarial
review** (proof-options signing, RDFC-1.0 canonicalization, KB-JWT, EIP-155, digest anchoring,
P1363 contract — all verified sound), and the remaining issues cluster in three buckets:
(a) corners the hardening explicitly deferred, (b) packaging/publishing, (c) deeper model gaps.

---

## Top findings (new audit)

### Bucket A — unhardened corners carrying the bug classes fixed in the core

1. **SD-JWT holder binding is opt-out by default** — `enforceHolderBinding = false`
   (`VerificationOptions.kt:64`) and issuance emits no `cnf` claim; KB-JWT validates against the
   attacker-controlled envelope `holder`. A stolen compact SD-JWT re-presents under the attacker's
   DID by default. Flip the default / add `cnf` at issuance.
2. **mdoc engine missed the hardening**: unsigned envelope claims never reconciled against
   MSO-signed items (the exact SD-JWT Finding-4c class); issuer key looked up in the verifier's
   KMS by attacker-chosen IRI (no x5chain/IACA path validation); selective disclosure keeps the
   full unfiltered subject in the envelope (`MdocProofEngine.kt:162-314`).
3. **SIOP plugin**: unauthenticated request objects + unrestricted `request_uri` fetch (SSRF) —
   the classes fixed in oidc4vp (`SiopV2Service.kt:62-88`).
4. **oidc4vci-server**: credential endpoint ignores the PoP `proof` entirely (no c_nonce check, no
   key binding), tokens never expire, non-constant-time tx_code compare, JSON injection in the
   minimal credential builder (`Oidc4VciIssuerService.kt:85-111`).
5. **BBS**: message encoding `"key=value"` is collision-prone (`("a","b=c")` ≡ `("a=b","c")`) —
   claim forgery within one signature (`Bbs2023ProofEngine.kt:291-294`); derived proofs emit
   `bbs-2023-derived` which the verifier categorically rejects, and derivation signs with the
   holder's key (`:482,502`).
6. **Cloud wallets are pre-hardening quality**: Azure `list()` is functionally broken (delimiter
   passed as prefix, `AzureBlobWallet.kt:98`); plaintext at rest everywhere outside FileWallet;
   raw `RuntimeException`s violating the WalletException contract and wrapping cancellation;
   client/resource leaks (no `close()`); raw credential ids as object keys.
7. **Bitcoin plugin is production-broken**: digest envelope (~103 B) cannot fit the 80-byte
   OP_RETURN it itself enforces; spends ALL wallet UTXOs per anchor (concurrent anchors
   double-spend); dust change unrejected; raw private key shipped over RPC; no confirmation wait.
8. **did:ethr is fake**: address derived from `keyHandle.id.hashCode()` (not Keccak-256), can emit
   `-` in "hex"; no ERC-1056 resolution (`EthrDidMethod.kt:299-311`).
9. **testkit KMS violates the P1363 contract** (raw DER for secp256k1, no low-s) and still derives
   garbage secp256k1 JWKs from SPKI header bytes (`InMemoryKeyManagementService.kt:90-95,186`) —
   dev-on-testkit / prod-on-real-KMS divergence.
10. **Stub/fictional plugins ship as real**: btcr/tezos/threebox SPI-register and report
    not-implemented as `invalidDid`; KMS ibm targets a non-existent API, venafi isn't a KMS,
    cyberark is a secrets-manager round-trip of raw private keys; starknet passes the fail-closed
    gate then throws `Unknown`.

### Bucket B — packaging / publishing (newly surfaced, blocks any release)

11. **`TrustWeave.quickStart()` throws at runtime** — `trust { provider("inMemory") }` requires a
    `TrustRegistryFactory` nobody supplies (`TrustWeaveFactory.kt:297`). The README/CLAUDE.md
    one-liner is broken; no test calls it bare. Ship a default in-memory TrustRegistryFactory.
12. **`:trust` declares its API dependencies as `implementation`** — facade signatures reference
    `DidResolutionResult`/`IssuanceResult`/etc., unresolvable for consumers of the published
    artifact (`trust/build.gradle.kts:10-17`). Must be `api`.
13. **testkit leaks into production**: `distribution:all` has `api(project(":testkit"))`, and
    testkit SPI-registers mock DID/KMS providers ServiceLoader-discoverable by the factory.
14. **BOM omits `:trust`, `:common`, key plugins — but includes `:testkit`.** No sources/javadoc
    jars in publishing; no explicitApi/binary-compat validator; SLF4J still 1.7.x.
15. **`trustweaveCatching`/`mapSequential` capture `CancellationException`**
    (`ResultExtensions.kt:84-113`); verify/revoke still leak `TimeoutCancellationException`
    through the sealed contract (known deferred item).

### Bucket C — model gaps for best-in-class

16. **Trust registry is a flat allow-list**: no accreditation chains, validity windows, or
    point-in-time `isAuthorized(issuer, type, at)`; single shared bearer token; no DID-control
    proof; unsigned responses; DB plugin still SELECT-*-then-filter.
17. **No finality/reorg model in anchoring**; no `getAnchorStatus`/resume after timeout; anchor
    signing still bypasses the KMS SPI (raw keys in option maps); EVM sends are legacy type-0
    (no EIP-1559); per-client nonce race under concurrency; `estimate()` only on 2 of 9 chains;
    digest verification is byte-exact (needs RFC 8785 JCS for real third-party verifiability).
18. **DID URLs remain broken/absent**: `Did` rejects all DID URLs while exposing dead `path`/
    `baseDid`; no dereferencing; unbounded per-method resolver caches (memory DoS);
    did:jwk mislabels X25519 keys as Ed25519; no did:tdw/webvh.
19. **Wallet lifecycle still hollow**: the only `CredentialPresentation` implementation lives in
    testkit and emits unsigned VPs with pass-through "selective disclosure"; no status write-back,
    no backup/export, dead `archived` column, LIMIT-1000 pagination, three mutually inconsistent
    (all wrong) `getStatistics` semantics; wallet-core-mp still a 3-file stub.
20. **Missing cryptosuites/engines**: no eddsa-rdfc-2022/DataIntegrityProof, no enveloped proofs
    (JOSE/COSE), no JWT-VC engine, PEX `submission_requirements` modeled but never evaluated, no
    verifier-side HTTP status-list fetch+verify, no conformance-suite integration.

---

## What the re-audit confirmed held up

- Data Integrity proof-options signing + RDFC-1.0/URDNA2015 canonicalization (byte-identical
  official contexts, external W3C + digitalbazaar vectors) — verified sound, no bypass found.
- Fail-closed JSON-LD with dropped-claims guard (count-based heuristic noted as improvable).
- SD-JWT signed-claim checks, KB-JWT verification incl. freshness; proofPurpose enforcement.
- KMS P1363/low-s contract holds across implemented cloud plugins; EcdsaSignatureCodec correct.
- EVM base-class pipeline: EIP-155, PENDING nonce, calldata gas, confirmation waits, receipt fees.
- Digest anchoring + verifyAnchor mechanics (constant-time compare, strict envelope detection).
- DIDComm signed-message verification over raw parsed JSON + AuthCrypt-bound sender authentication.
- did:web injection hardening, did:key compressed-EC math, did:peer purpose codes/encodings.
- FileWallet AES-GCM + hashed filenames; DatabaseWallet dialect-aware locked writes; bitstring
  row-locked status writes; trust-registry fail-closed auth; CachingDidResolver design.

## Recommended next sequence

1. **Release blockers (bucket B)**: default TrustRegistryFactory (fix quickStart), `api` deps on
   :trust, drop testkit from distribution:all + BOM fix, sources jars, CE rethrow in
   ResultExtensions, verify/revoke timeout mapping.
2. **Security defaults**: `enforceHolderBinding=true` + `cnf` at SD-JWT issuance; mdoc envelope
   reconciliation + SD filtering; SIOP request-object verification; oidc4vci-server PoP.
3. **Honesty pass**: de-register or clearly gate every stub/fictional plugin (btcr, tezos,
   threebox, starknet, ibm, thales, fortanix, venafi, cyberark relabel, did:ethr experimental).
4. **Then**: BBS encoding + derived-proof verification, cloud wallet parity with file/db,
   Bitcoin coin-selection/digest fit, trust-registry TRQP-shaped query, KMS shared base +
   testkit contract conformance, JCS for digest anchoring.
