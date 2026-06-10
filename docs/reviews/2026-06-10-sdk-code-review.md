# TrustWeave SDK Code Review — 2026-06-10

Full-codebase review targeting "best blockchain-agnostic decentralized ID + VC SDK on the JVM".
Six parallel domain reviews (core, DID, credentials, wallet, KMS, anchors/trust-registry), synthesized.

## Scores

| Domain | Score | One-line verdict |
|---|---|---|
| Core architecture (common, trust, testkit, distribution) | 6/10 | Strong intent (sealed results, SPI, DSL); dead config paths, no binary-compat posture, lifecycle on paper only |
| DID (did-core + plugins) | 5.5/10 | Good skeleton; flagship plugins (key/web/peer) have cross-vendor interop bugs; no DID URL dereferencing |
| Credentials (credential-api + plugins) | 4/10 | Best-in-class scaffolding; cryptographic core has break-the-product flaws (proof options unsigned, JSON-LD drops claims) |
| Wallet | 4/10 | Excellent capability-segregated SPI; implementations have AES/ECB, path traversal, silent query no-ops |
| KMS (kms-core + 16 plugins) | 5/10 | HSM-friendly SPI; undefined signature-format contract breaks the SDK's own JWS round-trip; 16-way copy-paste drift |
| Anchors (anchor-core + 12 plugins) | 3.5/10 | Good payment/CAIP-2 SPI design; demo-grade impls, silent fake anchoring, 7× EVM copy-paste |
| Trust registry | 3/10 | Flat allowlist, not a trust registry; unauthenticated server; unknown DID reported as PENDING |

**Overall core score: 4.5/10** — architecture and API-shape are 7–8/10; the security-critical internals
(signing, verification, canonicalization, anchoring truthfulness) are 2–3/10 and gate everything else.

---

## P0 — Security-critical (block production use)

1. **Data Integrity proof options are not signed** — `credentials/credential-api/.../VcLdProofEngine.kt:110-156, 198-205`.
   Only the canonicalized document is signed; challenge/domain/created/proofPurpose live in the proof, which is
   excluded from the signed bytes — yet `PresentationVerification.kt:55,94` reads challenge/domain *from the proof*.
   **VP replay protection is fully bypassable** (attacker rewrites `proof.challenge` to the verifier's fresh nonce).
   Also non-interoperable with conformant Ed25519Signature2020 verifiers.

2. **Undefined JSON-LD terms ⇒ unsigned claims** — `JsonLdDocumentBuilder.kt:33-36` hardcodes `@context`;
   arbitrary `credentialSubject` claims are undefined terms that jsonld-java silently drops from N-Quads
   (`JsonLdUtils.kt:137`). Subject claims are largely **not covered by the signature**. The silent plain-JSON
   fallback when canonicalization fails (`JsonLdUtils.kt:157-171`) makes signing input non-deterministic.

3. **BBS engine takes the public key from the attacker-controlled proof** — `Bbs2023ProofEngine.kt:311-332`.
   No issuer DID resolution, no key binding: anyone can mint a "valid" BBS credential for any issuer.

4. **SD-JWT trusts unsigned envelope fields** — `DefaultCredentialService.kt:137-138`, `SdJwtProofEngine.kt:151-257`.
   Expiry checked against the unsigned wrapper (strip `expirationDate` → bypass); signed `exp`/`nbf`/`iss` never
   checked; envelope claims never reconciled against disclosures; KB-JWT created but **never verified**.

5. **Bitstring status list is unverifiable** — `BitstringStatusListManager.kt:639,661,835,859,243`.
   Fresh Ed25519 key generated per call, signs raw non-canonicalized JSON, `verificationMethod` points at a key
   absent from the issuer's DID doc; LSB-first bit order violates spec; no bounds check (out-of-range index
   **fail-opens to "not revoked"**); fetched status-list URLs never dereferenced/verified.

6. **Silent fake anchoring** — `anchors/anchor-core/.../AbstractBlockchainAnchorClient.kt:104-122`.
   Missing/unparseable credentials → fabricated tx hash from an in-memory map; parse failures swallowed
   (`EthereumBlockchainAnchorClient.kt:73-79`). A misconfigured issuer "anchors" successfully to nowhere.

7. **Wallet at-rest crypto broken** — `wallet/plugins/file/.../FileWallet.kt:247,263` uses `Cipher.getInstance("AES")`
   = AES/ECB, no IV, no integrity, no KDF; plaintext by default (`:44`). **Path traversal**: `:79-82,109,162` resolve
   filenames from attacker-controllable `credential.id` (`"../../.."` escapes the wallet dir).

8. **Holder-binding prefix check** — `DefaultCredentialService.kt:350` `startsWith(holderDid)`:
   `did:example:abc` matches `did:example:abcdef#key-1`. Compare pre-`#` segment for equality.

9. **No proofPurpose enforcement** — `ProofEngineUtils.resolveVerificationMethod` (78-146) accepts any key in
   `verificationMethod` (incl. keyAgreement); never validated against assertionMethod/authentication.

10. **DIDComm placeholder ECDH returns a constant shared secret** — `DidCommCrypto.kt:295-311` (`ByteArray(32)`).

11. **Secrets in toString/logs** — `AwsKmsConfig.kt:20-27` (data class with secretAccessKey),
    `ConfigCacheKey.kt:57-60` (secrets serialized into static cache keys),
    `WalletCreationOptions.additionalProperties` carrying `encryptionKey`/db password.

12. **Trust-registry server has zero authentication** — `TrustRegistryRoutes.kt:25-27,43-48`; and
    `InMemoryTrustRegistry.kt:111-112` reports unknown DIDs as `PENDING` (unknown ≠ awaiting approval).

## P1 — Interop/correctness (the SDK can't round-trip with other stacks, or with itself)

13. **KMS signature-format chaos** — no DER vs P1363 contract on `sign()`: inmemory/AWS/Vault emit DER, Azure raw
    P1363. `VcLdProofEngine.signDocumentAsJws` (466-474) embeds DER bytes in a JWS → Nimbus rejects **every
    EC-signed proof, including TrustWeave's own round-trip** (`DefaultJsonWebSignature2020Adapter.kt:91`).
    No DER↔concat transcoding exists in the repo. Fix: `SignatureEncoding` param + shared `EcdsaSignatureCodec`
    (with low-s normalization for secp256k1 — EIP-2/Bitcoin reject high-s; "blockchain-agnostic" requires it).

14. **did:web path mapping wrong** — `WebDidMethod.kt:75-81` appends `/.well-known/did.json` after paths
    (spec: `{path}/did.json`; well-known only for bare domains). Ports unsupported (no `%3A` decoding,
    `DidMethodUtils.kt:257-270`).

15. **did:key EC points uncompressed** — `KeyDidMethod.kt:279` emits `0x04||x||y`; multicodec requires compressed
    SEC1. Generated EC did:keys are non-interoperable; compliant compressed keys from other stacks fail to parse
    (`:325-369`). No X25519 multicodec (0xec) in `DidMethodUtils.kt:297-324` — did:peer:2 keyAgreement keys
    silently dropped (`PeerDidMethod.kt:276-287`) — fatal for DIDComm.

16. **did:peer:2 services base58 instead of base64url** (`PeerDidMethod.kt:199-201,288-301`);
    did:peer:1 raw SHA-256 instead of multihash (`:172-179`). Aries/Credo peer DIDs unparseable.

17. **publicKeyMultibase unsupported in verification** — `ProofEngineUtils.kt:324-328` returns null — breaks the
    most common did:key/Ed25519VerificationKey2020 documents.

18. **OIDC4VCI/VP non-functional**: pre-auth flow leaves token null (`Oidc4VciService.kt:147-156`); no `c_nonce`
    anywhere (PoP nonce is self-generated UUID, `:536`); `alg: "Ed25519"` is not a JOSE alg (must be `EdDSA`);
    vp_token built without `verifiableCredential` (`Oidc4VpService.kt:425-433`) or with data-class `toString()`
    (`:465`); `presentation_submission` never populated; request objects not JAR-verified.

19. **VC DM 2.0 claimed but not emitted** — `VcLdProofEngine.kt:76` says 2.0, always emits 1.1 context +
    `issuanceDate` (`:160-163`). No eddsa-rdfc-2022/ecdsa-rdfc-2019 cryptosuites, no JWT-VC engine, no
    enveloped-proof path.

20. **Facade bugs**: `rotateKey` structurally unusable (`TrustWeaveFactory.kt:128` hardcodes `kmsService = null`,
    no builder setter); `revocation(provider)` and `schemas(defaultFormat)` silently ignored
    (`TrustWeaveFactory.kt:259-262`, `TrustWeaveConfig.kt:258-268`); `ProviderChain.execute`
    (`ProviderChain.kt:62-69`) swallows `CancellationException`; `withTimeout` leaks
    `TimeoutCancellationException` through the sealed-result contract (`DidManagementService.kt:180-233`).

21. **Wallet plugin breakage**: `CloudWallet.store()` throws CCE for any credential with an id
    (`CloudWallet.kt:71,92`); `query { byTag/byCollection }` is a silent no-op in **every** implementation
    (returns all credentials — dangerous for presentation candidate selection); S3 stream leak
    (`AwsS3Wallet.kt:61-62`); Hikari pool leak per `create()` (`DatabaseWalletFactory.kt:56,81`).

22. **Anchors**: Algorand reads never work (wrong reflection target + pending-pool endpoint,
    `AlgorandBlockchainAnchorClient.kt:371-386`); EVM gas estimate ~9M (`EthereumBlockchainAnchorClient.kt:150`)
    makes `maxFee` always trip; no confirmation wait (`:265-278`); nonce race (`:249`, LATEST not PENDING);
    Bitcoin spends all UTXOs with float math (`BitcoinBlockchainAnchorClient.kt:140-155`);
    `ChainId.Eip155` doesn't recognize `eip155:1` (`ChainId.kt:98-105`).

23. **AWS KMS**: session token silently dropped (`AwsKmsClientFactory.kt:53-63` — both branches use
    `AwsBasicCredentials`); map-config path caches key metadata forever (`AwsKmsConfig.kt:89`);
    pre-hash instruction double-hashes (always `MessageType.RAW`, `AwsKeyManagementService.kt:409-414`).
    PKCS11 maps P-384/P-521 to SHA-256 and mixes RSA-PSS with v1.5 (`Pkcs11KeyManagementService.kt:280-291,338`).

## P2 — Architecture & gaps for best-in-class

- **Copy-paste plugin sprawl**: 16 KMS plugins, zero shared base (drift confirmed); 7 near-identical EVM anchor
  clones (only Ethereum got the payment plane). Extract `AbstractKeyManagementService` and
  `AbstractEvmAnchorClient`. The "waltid" KMS is a mislabeled inmemory clone; StarkNet/btcr/tezos/threebox are
  stubs; wallet-core-mp is a 3-file stub, not a port. **Depth over breadth: fewer, conformant plugins.**
- **Anchor model**: full JSON payload on-chain by default (PII/GDPR liability); no digest/merkle mode in core,
  no `verifyAnchor(payload, ref)` for third-party verification, no finality/reorg/confirmation-depth concepts,
  anchoring bypasses the KMS SPI (raw private keys in string maps).
- **Result-pattern schism**: five error idioms on the facade (sealed results, kotlin.Result, Boolean, nullable,
  thrown ISE); `DidMethod` create/update/deactivate throw while resolve returns sealed results.
- **Lifecycle dead infrastructure**: `PluginLifecycle` never invoked; `TrustWeave.close()` only closes KMS/chain
  clients; plugin version ranges never evaluated.
- **No binary-compat posture**: no `explicitApi()`, no binary-compatibility-validator, no @JvmOverloads; Java
  interop absent (suspend-only, no CompletableFuture adapters); JVM 21 toolchain locks out Android.
- **No caching anywhere in DID resolution** (no TTL, `nextUpdate` modeled but unused); no
  registry→universal-resolver fallback composite; no DID URL dereferencing
  (`DidResolutionV03.kt:15` "not yet implemented").
- **Missing crypto surface**: no encrypt/decrypt/ECDH/wrap in KMS SPI (DIDComm v2 can't be HSM-backed); no
  X25519 key type; `Algorithm.BLS12_381` declared, no provider implements it (BBS+ impossible today); no RFC 7638
  thumbprint; no key import/export.
- **Wallet gaps**: no backup/restore/export (no Universal Wallet 2020), no pagination (silent 1000-row cap,
  `DatabaseWallet.kt:294`), no status write-back, no lock/unlock, only presentation impl is in testkit and emits
  unsigned VPs with pass-through "selective disclosure" (`InMemoryWallet.kt:304-318`).
- **Trust registry**: no accreditation chains/validity windows/governance refs; no ToIP TRQP / OpenID Federation
  alignment; no first-class `isAuthorized(issuer, type, at)` query; DB plugin filters in memory.
- **Observability**: slf4j 1.7.36 only; no metrics/tracing/event hooks. **Serialization split-brain**:
  kotlinx + Jackson. Repo hygiene: stale `vericore-examples`/`trustweave-examples`/empty bom dirs; root littered
  with old review/summary files.

## Recommended sequence

1. **Week 1 quick wins**: holder-binding equality fix; `EdDSA` alg name; bitstring bounds+bit-order+`u` prefix;
   `AwsSessionCredentials`; CloudWallet `.id?.value`; S3/OkHttp `use {}`; ProviderChain cancellation rethrow;
   delete duplicate Result extensions; wire `kmsService` through builder; ChainId table sync.
2. **Security sprint**: sign proof options per Data Integrity spec; fail-closed canonicalization with real
   context handling; issuer-DID key binding in BBS; SD-JWT signed-claim checks + KB-JWT verification;
   proofPurpose enforcement; AES-GCM + filename hashing in FileWallet; opt-in-only in-memory anchor fallback.
3. **Interop sprint**: `EcdsaSignatureCodec` (DER↔P1363 + low-s) wired through all KMS plugins and JWS path;
   did:web/did:key/did:peer fixes; publicKeyMultibase support; OIDC4VCI c_nonce/pre-auth/vp_token rebuild.
4. **Architecture sprint**: shared KMS/EVM base classes; digest anchoring + `verifyAnchor`; unify Result pattern;
   `explicitApi()` + binary-compat validator; caching DID resolver; KMS-backed anchor signing.
5. **Scope cut**: retire or clearly flag stub plugins (waltid, starknet, btcr, tezos, threebox); pick VC DM 2.0 +
   SD-JWT VC + OID4VC as the conformance targets and drive `distribution/conformance` against external test
   suites (W3C VC test suite, DIF/OpenID conformance).
