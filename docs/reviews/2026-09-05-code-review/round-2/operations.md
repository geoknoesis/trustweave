# Round 2 operational changes

## Before deployment

Apply Flyway V22 (verification work leases and history index) and V23 (failed webhook recovery). Use Node 24 for both web projects. Configure `security.jwt.expected-audience` and an OIDC audience mapper on every authorized Keycloak client: the access token must contain this API in `aud`. `azp` is no longer a substitute; a blank expected audience refuses startup.

Publish the reviewed TrustWeave SDK commit and replace `.trustweave-revision` with its full commit SHA. `python scripts/verify-sdk-source.py ../trustweave` checks the normalized runtime-source fingerprint against `.trustweave-source-sha256`. The existing pin intentionally cannot satisfy that new gate while it points at older SDK sources. Record the fingerprint only after reviewing and validating an intentional SDK update. Run the complete Linux CI against the exact pair of commits; this local Windows run is not evidence of a staging deployment.

In staging, verify the trusted ingress strips forged forwarding headers, the API sees the expected client address, resource audiences are enforced, and instance scaling preserves the shared admission/rate limits. No staging traffic or release was performed by this remediation.

## API and wallet compatibility

- Verification history returns a Spring Page (`content`, `totalElements`, `number`, `size`) with defaults page 0 / size 50, maximum 100.
- POST `/api/trust-spaces/{trustSpaceId}/verification-requests/{sessionId}/cancel` requires access to the trust space. Cancellation is terminal and racing submissions cannot overwrite it.
- Credential IDs are no longer public retrieval invitations. Issue a new bearer invitation or use proof-bound OID4VCI. Token issuance requires a live pending offer and rotates previous unconsumed grants.
- Browser wallet `store`, `deleteCredential`, and `resetWallet` return promises and must be awaited. Use the wallet facade; the storage adapter is internal and callers must not perform unlocked read/modify/write operations.
- Imports support Ed25519 `did:key` issuer proofs. Unsupported issuer profiles fail closed. Signature checking on import is not a current issuer-trust or revocation assessment.
- Share one SD-JWT credential at a time. Selective claims start unchecked. JSON VC presentations disclose complete credentials; request metadata and the UI now state that limitation.

## Data retention and reconciliation

`trustweave.verification.history-retention-days` defaults to 90 and accepts 1-3650. After a ten-minute startup delay, the hourly worker deletes at most 500 expired sessions older than this interval, including holder IDs, disclosed claim names and detailed results. Monitor backlog; the configured age is an eligibility cutoff, not a guarantee that a large backlog has drained immediately. Keep your approved retention policy reflected in deployment configuration.

Signature-verified Stripe failures are retained in `failed_webhook_events`; invalid signatures never enter this table. The event is not marked successfully processed when parsing or application fails. Inspect the recovery queue with a privileged database connection:

```sql
SELECT id, reason, last_failed_at FROM failed_webhook_events
ORDER BY last_failed_at LIMIT 100;
```

Treat the stored payload as billing personal data. Alert on nonempty/aging recovery queues. Fix the parser/version mismatch, then use Stripe's authenticated event redelivery workflow to retry the same event ID. The transactional dedup ledger applies it once; a successful retry removes the recovery row. Retained original payloads are available for investigation if provider redelivery is no longer available; do not send them through an unsigned public endpoint. No public replay endpoint was introduced.

## Work limits

A verification accepts at most 256 KiB, depth 32 and 20 credentials; descriptor mappings are limited to 16 KiB. The worker pool allows 8 active tasks per server and 2 per trust space, with a 15-second verification deadline. A 30-second database lease prevents simultaneous work on one session across instances and recovers after worker/process loss. Non-cooperative providers can occupy a worker after cancellation, but cannot create unbounded additional workers. Maintain shared ingress/request quotas as well as these instance budgets.

## SDK storage

Use `PagedCredentialStorage.pageRecords(limit, after, filter)` for large wallets (page size 1-500, plus one lookahead record for continuation). Continue until `nextCursor` is null, including after empty filtered pages. PostgreSQL pushes issuer/type/subject containment predicates into an indexed query; H2 and residual status/expiry filters scan only the bounded page. No full-scale production performance benchmark is claimed. Legacy List APIs still materialize their complete result for compatibility.

Normal file/cloud listings and statistics fail on unreadable data. `CredentialRecovery.recoverRecords()` explicitly returns recoverable records and per-record failures. Recovery results must not be treated as complete unless `complete` is true. File locks serialize record/metadata mutations within a JVM; atomic file replacement does not constitute a distributed filesystem transaction.

## Browser custody and encrypted claims

Non-extractable WebCrypto keys reside in IndexedDB and signing/import/reset operations use an origin-wide Web Lock. This is a demo browser custody boundary: it does not protect signing from hostile same-origin JavaScript, provide hardware attestation or guarantee recovery after profile/key loss. Export credentials before reset; lost keys require issuer reissuance.

Encrypted disclosures retain their original issuer-committed bytes. For selected encrypted claims only, `trustweave_claim_keys` in the signed KB-JWT maps the disclosure's SHA-256 digest to its base64url AES-256 content key. The TrustWeave verifier checks issuer/holder signatures, audience, nonce, digest and content binding before using it. This is a TrustWeave extension, not generic SD-JWT encrypted-claim interoperability. Other verifiers may display the original ciphertext; integrate against the supported profile explicitly. Both the standard HKDF envelope and the legacy demo envelope are readable; new issuers should use the standard HKDF profile.
