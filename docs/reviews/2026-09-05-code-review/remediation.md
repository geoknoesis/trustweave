# Remediation work log

The original HTML is a historical review snapshot. Implementation and validation status are tracked here; the final HTML remediation addendum will link the two.

## Implemented and validated

- S01: Signed presentation contents and holder/subject identity must agree. The profile accepts subject-bound JSON credentials; implicit bearer/delegation is refused.
- S02/S12: Requested types/claims enforced; descriptor mappings validated when supplied; unsupported embedded representations rejected, not dropped. Receiver profile advertised.
- S03/S04: Database conditional terminal transitions; replay conflict responses; expiry on reads and completion.
- S05: Terminal query data stops polling; countdown and connection retry state.
- S06/S07: Container peer address replaces untrusted forwarding-header parsing; bounded expiring rate limits with independent presentation endpoint budget.
- S08/S11: OS-aware Docker discovery; immutable library revision file and CI source-revision summary.
- S09/S10: Modal stack keyboard ownership and focus restoration; route lazy loading.
- T01/T02: Explicit unknown/resolved status and metadata-only filter; complete, ordered database queries instead of arbitrary 1,000-row truncation.
- T03/T04: Atomic file replacement; storage-record capability with stable anonymous handles, preserving signed credential contents.
- T05/T06: Bounded expiring exchange stores, purge/cancel/close, completion cleanup; receive-format capability negotiation rejects unsupported offers before token exchange.
- T07/T08: Device-bound non-extractable browser signing/agreement keys, preserving legacy identity during migration; non-destructive schema migration, recovery export and retry UI.
- T09/T10: Browser-wallet test/build CI, real-signature custody tests, runtime capability catalog and generated documentation with drift gate; stub startup validation.

## Boundaries retained intentionally

- No new compact-format or delegated-presentation support is claimed. Unsupported formats are negotiated/rejected explicitly. See the format matrix for the supported paths.
- WebCrypto custody prevents private-key export; it does not claim hardware-backed storage or WebAuthn user verification. The reference-wallet UI states the remaining demo boundary. Device loss requires issuer reissuance.
- Rate limits are per process. Production ingress must enforce shared limits and explicitly configure trusted proxies before enabling native forwarded-address processing.
- Library revision pin initially records the reviewed commit. A release containing these workspace changes must update it to the resulting immutable library commit before deployment.

## Final validation

- SaaS backend: full `:server:test` and JaCoCo report/coverage verification passed. 507 tests, 0 failures/errors, 2 skipped.
- SaaS frontend: 292 tests across 62 files, lint, TypeScript/Vite production build passed. Initial JavaScript budget passed: 441,620 bytes against 500,000 bytes. Main chunk 441.57 kB / 144.99 kB gzip, previously 1,286.06 kB / 383.86 kB gzip.
- SDK: 804 tests across common, wallet-core, file, database, cloud, testkit and OID4VCI; 0 failures/errors, 3 skipped. The corresponding ktlint checks and Starknet compile/ktlint passed. Gradle reused unchanged results; this is not the full SDK test matrix.
- Reference wallet: 6 tests, TypeScript and Next production build passed. Tests use Node WebCrypto and fake IndexedDB, not a live browser.
- Generated capability documentation drift check passed. HTML validated for 22 finding entries, unique IDs and local navigation targets; no visual-browser review was available.
- Both repositories pass `git diff --check`. Original user work was preserved. No commit or deployment was made.

See [the HTML addendum](remediation.html) for each finding's implementation, evidence and boundaries. The original report and scores remain a historical snapshot.
