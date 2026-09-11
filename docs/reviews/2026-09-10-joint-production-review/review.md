# TrustWeave + TrustWeave SaaS · joint production review

Date: 2026-09-10

The current evidence does not support an overall score above 9.7. This review identifies concrete implementation defects and the qualification work needed for a defensible 9.8 target. It changes review artifacts only; findings remain open.

Provisional scores: SDK 8.9/10; SaaS 7.1/10; joint planning mean 8.0/10. Target: 9.8. Production qualification: NOT MET.

Provisional engineering judgment. Six equally weighted categories and equally weighted repositories; joint mean is a planning indicator, not a deployability gate. Historical SDK 9.3 had narrower scope. New SDK reassessment includes the selected provider boundary. No point changes imply statistical precision.

Zero open P0/P1; both repository means >9.7 unrounded; critical category floors >=9.7; immutable pair and actual declared deployment/custody/recovery gates pass. A high average cannot waive a blocker.

## Verification

- Backend: FAIL — 556 tests, 111 failures, 0 errors, 3 skips. Current workspace, not pinned release pair.
- Frontend: 298/298 pass; lint and build pass; initial JavaScript 454,760 / 500,000 byte budget.
- Source-pair verifier: FAIL (SDK revision mismatch).
- KMS mixed-profile probe: unsafe acceptance reproduced.
- Vault driver-shape probe: String-to-Map mismatch reproduced; no live Vault call.
- Prior SDK candidate: 3,924 tests, 3,909 pass, 15 optional skips; historical, not a fresh full SDK run.

## Scores

| Category | SDK | SaaS | Joint |
|---|---:|---:|---:|
| Security and access control | 9.0 | 7.5 | 8.25 |
| Observability and diagnosability | 9.6 | 7.5 | 8.55 |
| Reliability and scale | 8.5 | 6.5 | 7.50 |
| Configuration and data | 8.5 | 7.5 | 8.00 |
| Deployment and release | 8.5 | 6.5 | 7.50 |
| Testing and documentation | 9.0 | 7.0 | 8.00 |

## Findings

### R18 · P1 · Custom requestContextFilter bean prevents Spring MVC startup

Repository: SaaS. Evidence: Reproduced in full backend suite. Status: Open.

**Evidence:** The custom @Component RequestContextFilter receives the default bean name requestContextFilter. Spring Boot MVC auto-configuration registers its own bean with that name. The full backend run reproduces BeanDefinitionOverrideException in 11 initial context loads, followed by cached failure-threshold errors in dependent cases.

**Impact:** ApplicationContextSmokeTest and multiple real integration contexts cannot start. This is a code-level bean-name collision, not a missing Docker or identity-provider prerequisite. The 111 failing test cases are not 111 independent defects.

**Required change:** Give the telemetry filter an explicit distinct bean name (or rename the class), preserve Spring’s request-context filter, and keep bean overriding disabled. Re-run real application smoke and all affected integration suites.

**Acceptance:** Both framework and telemetry filters exist with intended ordering and no duplicate registration. The application boots, MDC is cleaned after errors/async dispatch, and the complete suite passes without enabling bean-definition overriding.

- SaaS/server/src/main/kotlin/com/geoknoesis/trustweave/saas/server/observability/RequestContextFilter.kt:29
- SaaS/server/src/test/kotlin/com/geoknoesis/trustweave/saas/server/ApplicationContextSmokeTest.kt:1

### R01 · P1 · Scheduled usage delivery bypasses its transaction boundary

Repository: SaaS. Evidence: Source-confirmed. Status: Open.

**Evidence:** scheduledDrain() invokes drainOnce() on the same bean. Only drainOnce() has @Transactional. The native claim query uses FOR UPDATE SKIP LOCKED. Under Spring’s default proxy transaction mode, this internal call does not open the intended encompassing transaction.

**Impact:** The scheduler path cannot rely on row locks covering delivery and persistence. Whether the provider rejects the query or releases locks early must be established with the real scheduled entry point; neither outcome is acceptable as the claimed concurrency guarantee.

**Required change:** Move work behind a separate proxied worker or explicit transaction boundary. Prefer short claim/finalize transactions with durable leases; do not merely put a long transaction around HTTP delivery.

**Acceptance:** Invoke the Spring-managed scheduled entry with PostgreSQL, assert the claim transaction, pause two workers at claim/send boundaries, and kill/restart one worker. Verify recovery and provider idempotency.

- SaaS/server/src/main/kotlin/com/geoknoesis/trustweave/saas/server/billing/accountly/UsageReporter.kt:39
- SaaS/server/src/main/kotlin/com/geoknoesis/trustweave/saas/server/repository/UsageOutboxRepository.kt:38

### R02 · P1 · Outbox lease and outage handling do not bound delivery safely

Repository: SaaS. Evidence: Source-confirmed design risk. Status: Open.

**Evidence:** A five-minute scheduler lease protects up to 100 sequential events with a default 20-second request timeout. One timeout per event alone can exceed 33 minutes. Generic transport and circuit-open failures consume the ten-attempt poison budget; only HTTP 429/503 are exempt.

**Impact:** A long batch can outlive its scheduler lease. When transactions are corrected, remote calls inside the transaction can exhaust DB capacity. A prolonged transient outage can leave valid billable events terminally FAILED and dependent on manual intervention.

**Required change:** Use per-row durable claim tokens/expiry, bounded batches and deadlines, short transactions, remote idempotency, and classified exponential retry with jitter. Expose authenticated redrive and oldest-pending alerts.

**Acceptance:** Test lease expiry, provider commit followed by lost response, 429/503, repeated connection failures and recovery after more than ten drain ticks. No valid event is silently abandoned or billed twice.

- SaaS/server/src/main/kotlin/com/geoknoesis/trustweave/saas/server/billing/accountly/UsageReporter.kt:27
- SaaS/server/src/main/kotlin/com/geoknoesis/trustweave/saas/server/billing/accountly/UsageReporter.kt:37
- SaaS/server/src/main/kotlin/com/geoknoesis/trustweave/saas/server/billing/accountly/AccountlyBillingProperties.kt:13

### R03 · P1 · Usage reporter tests exercise the obsolete repository contract

Repository: SaaS. Evidence: Reproduced: seven reporter cases fail. Status: Open.

**Evidence:** UsageReporterTest stubs findByStatusInAndAttemptsLessThanOrderByCreatedAtAsc, while production calls claimForDelivery. It constructs UsageReporter directly and calls drainOnce(), so it also misses scheduler/proxy semantics.

**Impact:** Existing assertions do not qualify the changed claim path. Merely replacing mock method names would still leave the transaction defect undetected.

**Required change:** Update unit contracts and add real PostgreSQL/Spring proxy integration tests for the scheduled entry point, concurrent claims and retry state transitions.

**Acceptance:** All existing reporter cases pass against the current method; a deliberately removed transaction or broken claim lease makes an integration test fail.

- SaaS/server/src/test/kotlin/com/geoknoesis/trustweave/saas/server/billing/accountly/UsageReporterTest.kt:38
- SaaS/server/src/main/kotlin/com/geoknoesis/trustweave/saas/server/billing/accountly/UsageReporter.kt:54

### R04 · P1 · Shared rate-limit failures allow every request

Repository: SaaS. Evidence: Source-confirmed. Status: Open.

**Evidence:** RateLimiter.check catches every Exception from the shared store, logs a warning, and returns success. This policy applies to public token, claim and redemption paths as well as verification. No dedicated failure-policy metric is emitted by this class.

**Impact:** Counter-table permission loss or a store outage removes abuse limits on sensitive public operations. This is not a JWT/signature bypass, but authentication alone does not replace abuse controls.

**Required change:** Define endpoint-specific failure policy. Fail closed with bounded 503 on sensitive mutations; use an explicitly bounded local fallback only for approved read operations. Add low-cardinality metrics and sampled logs.

**Acceptance:** Break only the counter-table access while the rest of the service remains available. Sensitive operations reject without downstream mutation; approved read fallback remains bounded across concurrent callers.

- SaaS/server/src/main/kotlin/com/geoknoesis/trustweave/saas/server/security/RateLimiter.kt:50

### R05 · P2 · Shared limiter uses caller clocks and lacks a cardinality budget

Repository: SaaS. Evidence: Source-confirmed design risk. Status: Open.

**Evidence:** The upsert and expiry sweep use Instant.now() from each application instance. Shared rows have no configured maximum, unlike the bounded local map; rejected hits still increment an integer counter.

**Impact:** Clock skew can make nodes disagree about expiry. High-cardinality traffic and a hot key can load the primary DB. No measured failure threshold is claimed by this review.

**Required change:** Use database time for the shared decision, saturate counts, bound statement latency and retention, and define a key-cardinality/pool budget. Consider a dedicated limiter store only if measured DB costs justify it.

**Acceptance:** Run skewed-clock multi-node tests plus hot-key and high-cardinality load; record p95/p99 latency, pool occupancy, retained rows, cleanup time and rejection correctness.

- SaaS/server/src/main/kotlin/com/geoknoesis/trustweave/saas/server/security/SharedRateLimitStore.kt:38
- SaaS/server/src/main/kotlin/com/geoknoesis/trustweave/saas/server/security/SharedRateLimitStore.kt:59

### R06 · P1 · Production plus local profile accepts an ephemeral KMS

Repository: SaaS. Evidence: Reproduced locally. Status: Open.

**Evidence:** validateForProfiles allows the in-memory provider whenever any profile is dev/test/local. A local Java probe rejects prod alone but accepts prod+local and staging+local. TrustWeaveConfig is disabled only for test, so local does not disable facade construction.

**Impact:** An accidental profile mix can deploy issuer keys that disappear on restart, leaving persisted issuer identities unable to sign.

**Required change:** Make deployed profiles dominate development allowances, reject incompatible profile combinations, and validate the effective persistent provider before startup.

**Acceptance:** Table-test empty, unknown, mixed, case-varied and production profiles; boot prod+local and staging+local and require rejection. A permitted persistent profile must retain the same signing identity across restart.

- SaaS/server/src/main/kotlin/com/geoknoesis/trustweave/saas/server/kms/KmsProviderConfig.kt:34
- SaaS/server/src/main/kotlin/com/geoknoesis/trustweave/saas/server/config/TrustWeaveConfig.kt:18

### R07 · P2 · Bound KMS options are silently unused

Repository: SaaS. Evidence: Source-confirmed. Status: Open.

**Evidence:** KmsProviderConfig exposes provider-specific options, but TrustWeaveConfig forwards only provider and algorithm to the keys builder. No kmsConfig.options consumer was found.

**Impact:** An operator can supply a documented option without changing the effective provider configuration. A provider may separately read environment settings; this finding does not claim all environment-based setups fail.

**Required change:** Forward typed, validated provider options through the actual factory or reject unsupported configuration explicitly. Publish a secret-safe effective-configuration fingerprint.

**Acceptance:** A non-default endpoint/namespace is observed by a fake provider; unknown or unused options fail startup and no secret appears in diagnostics.

- SaaS/server/src/main/kotlin/com/geoknoesis/trustweave/saas/server/kms/KmsProviderConfig.kt:26
- SaaS/server/src/main/kotlin/com/geoknoesis/trustweave/saas/server/config/TrustWeaveConfig.kt:40

### R08 · P1 · Request logging context includes capability-bearing paths

Repository: SaaS. Evidence: Source-confirmed. Status: Open.

**Evidence:** RequestContextFilter puts raw request.requestURI into MDC HTTP_PATH. Production JSON logging includes httpPath. PublicClaimController serves /api/public/claim/{offerCode}, where offerCode is used to locate the claim offer.

**Impact:** Logs emitted while handling a claim can retain its capability value. requestURI excludes query strings; this finding concerns path tokens and does not assert that every request emits a log.

**Required change:** Use route templates or an allowlisted path redactor before logging. Apply the same rule to access logs, trace attributes and error diagnostics.

**Acceptance:** Send unique canary offer codes through success and failure cases; captured logs/traces contain no canary while request correlation and route-level metrics remain useful.

- SaaS/server/src/main/kotlin/com/geoknoesis/trustweave/saas/server/observability/RequestContextFilter.kt:41
- SaaS/server/src/main/kotlin/com/geoknoesis/trustweave/saas/server/controller/PublicClaimController.kt:52
- SaaS/server/src/main/resources/logback-spring.xml:35

### R09 · P2 · Readiness accepts a missing identity realm as healthy

Repository: SaaS. Evidence: Source-confirmed. Status: Open.

**Evidence:** The dependency probe treats every status below 500 as UP, including 401, 404 and 429. Keycloak uses a public OIDC discovery URL and participates in readiness.

**Impact:** A deleted realm or incorrect probe URL can remain green after startup. Reachability is useful information, but is insufficient for a functional readiness decision.

**Required change:** Separate reachability from readiness. Require successful, valid discovery metadata for the configured issuer, with bounded timeouts and controlled probe caching; keep liveness independent.

**Acceptance:** Exercise 401/404/429/500, malformed 200, wrong issuer and valid discovery responses. Readiness changes correctly without a liveness restart storm.

- SaaS/server/src/main/kotlin/com/geoknoesis/trustweave/saas/server/observability/DependencyHealthIndicators.kt:45
- SaaS/server/src/main/resources/application.yml:99

### R10 · P1 · Committed Fly recipe disagrees with the application port and datasource contract

Repository: SaaS. Evidence: Source-confirmed recipe defect. Status: Open.

**Evidence:** fly.toml routes to 8080 while application.yml defaults to 8081 and the Docker image exposes/checks 8081. The Fly environment supplies no SERVER_PORT override, uses a postgres:// example URL where the datasource expects a JDBC URL, and retains a placeholder Keycloak host.

**Impact:** The committed recipe is not a reproducible production deployment. External secrets or platform overrides may make an existing deployment work; none were inspected here.

**Required change:** Choose one supported port/configuration contract, remove example production fallbacks, require validated datasource/issuer settings, and qualify the actual image through the documented deployment recipe.

**Acceptance:** Build and boot the image with the declared profile, reach readiness through the configured proxy port, reject missing secrets, and migrate a real supported PostgreSQL instance.

- SaaS/fly.toml:16
- SaaS/server/src/main/resources/application.yml:77
- SaaS/Dockerfile:19
- SaaS/server/src/main/resources/application-fly.yml:1

### R11 · P1 · Current repositories do not form the pinned SDK/SaaS candidate pair

Repository: Joint. Evidence: Reproduced source gate failure. Status: Open.

**Evidence:** The SaaS pin expects a41d482a96dcdd3962c2a0a95fd1da469f310fa9; the sibling SDK HEAD is e0a4464fc6cafa339b94121fd56f33812c6601e4 with local changes. verify-sdk-source.py rejects this pair. The previously qualified SDK candidate is 5e8dc04c0e45dd51b323b6b3c475d302615715af.

**Impact:** Current local integration tests cannot certify the pinned release pair. This does not prove that SaaS CI against its intended pinned revision fails.

**Required change:** Create immutable reviewed candidates in both repositories, update the pin and reviewed source digest manifest deliberately, and run all integration and artifact gates on that exact pair.

**Acceptance:** Source verifier, clean builds and hosted integration pass on the same recorded pair; all delivered artifacts and reports carry both SHAs and source digests.

- SaaS/.trustweave-revision:1
- SaaS/scripts/verify-sdk-source.py:1
- SaaS/settings.gradle.kts:15

### R12 · P2 · Release evidence stops before the deployable artifact

Repository: SaaS. Evidence: Qualification gap in inspected CI. Status: Open.

**Evidence:** The inspected CI tests backend sources and builds frontend assets, but has no bootJar/container release build, SBOM/provenance attestation, image promotion/rollback qualification or deployed digest verification. Actions use version tags and the Docker base is a floating tag.

**Impact:** Green source tests do not establish the identity or readiness of the image that is deployed. No specific vulnerable dependency is alleged; no fresh vulnerability scan was run.

**Required change:** Build the runtime image once from the qualified pair, pin build inputs, produce SBOM/provenance, enforce a vulnerability policy, verify signatures/digests at promotion, and exercise rollback.

**Acceptance:** Tampered provenance or mismatched SDK/image digest blocks promotion; an immutable image passes startup, smoke, migration and rollback tests with archived evidence.

- SaaS/.github/workflows/ci.yml:37
- SaaS/Dockerfile:8

### R13 · P2 · Coverage and browser harness policy leave important paths unqualified

Repository: Joint. Evidence: Source-confirmed assurance gap. Status: Open.

**Evidence:** SaaS backend coverage verification has a 0.40 floor. Frontend coverage has reporters but no thresholds and CI runs ordinary tests. MSW is configured to warn on unexpected requests; the current passing suite emits network warnings. Previous SDK global coverage was 57.41% line / 40.04% branch.

**Impact:** Passing counts and category scores are not coverage percentages. Proxy, provider response and deployment defects can survive mock-heavy tests. Network warnings need classification rather than blanket suppression.

**Required change:** Define critical-path branch and mutation targets, exercise real transaction/provider contracts, and make unexpected browser requests fail deterministically after fixture cleanup. Retain an explicit skip/discovery manifest.

**Acceptance:** Critical negative cases fail when their guards are removed; risk-based coverage thresholds are enforced in CI and no unapproved test skips or unexpected requests remain.

- SaaS/server/build.gradle.kts:208
- SaaS/frontend/vitest.config.ts:13
- SaaS/frontend/src/test/setup.ts:10

### R14 · P2 · User synchronization holds a broad transaction across remote work

Repository: SaaS. Evidence: Source-confirmed design risk. Status: Open.

**Evidence:** syncAllUsers is transactional, fetches the user list, then calls a method that fetches each user remotely and saves it. Per-user exceptions are caught inside the outer transaction. The list call has no explicit pagination at this layer.

**Impact:** Large or slow identity-provider responses can lengthen transactions; a database failure may poison the outer transaction despite per-user error counting. Pagination completeness needs an explicit contract; no undocumented provider page-size assumption is made.

**Required change:** Fetch bounded pages outside DB transactions, persist each bounded unit through a real transaction boundary, checkpoint progress and make retries idempotent.

**Acceptance:** Sync more than one provider page, inject a mid-page DB failure and slow identity responses, restart and resume, and demonstrate bounded DB occupancy and correct completion counts.

- SaaS/server/src/main/kotlin/com/geoknoesis/trustweave/saas/server/services/UserSyncService.kt:64
- SaaS/server/src/main/kotlin/com/geoknoesis/trustweave/saas/server/services/KeycloakService.kt:97

### R15 · P1 · Vault public-key extraction casts String to Map

Repository: SDK. Evidence: Driver-shape reproduced; source-confirmed adapter defect. Status: Open.

**Evidence:** VaultKeyManagementService generateKey/getPublicKey reads keyInfo.data["keys"] and casts it to Map. The pinned driver LogicalResponse.getData() returns Map<String,String>. javap and a local nested-JSON fixture show keys is a java.lang.String; the cast cannot succeed. The compiler reports this at both paths. getDataObject() preserves structured JSON.

**Impact:** A valid nested Vault key response cannot yield a public key through these extraction paths. Key generation can create a provider-side key and then return failure. This is especially material to SaaS staging, which selects Vault.

**Required change:** Parse the driver’s structured response with explicit schema/version/type validation. Cover missing/invalid fields and avoid orphaning/recreating keys on retry. Then qualify the declared Vault algorithm and lifecycle end to end.

**Acceptance:** A driver-faithful fixture passes generate/get-public-key behavior; malformed versions fail safely. Against an isolated Vault instance, create/sign/independently verify/restart/retrieve/rotate succeeds with stable identity and no private-key leakage.

- SDK/kms/plugins/hashicorp/src/main/kotlin/org/trustweave/hashicorpkms/VaultKeyManagementService.kt:126
- SDK/kms/plugins/hashicorp/src/main/kotlin/org/trustweave/hashicorpkms/VaultKeyManagementService.kt:234
- SDK/kms/plugins/hashicorp/build.gradle.kts:17
- SaaS/server/src/main/resources/application-staging.yml:58

### R16 · P1 · No declared production custody profile is fully qualified

Repository: Joint. Evidence: Explicit qualification gap. Status: Open.

**Evidence:** The SDK custody runbook explicitly says not qualified. Its assessed capability catalog has no supported entries; deployment gating is opt-in outside selected factories and compatibility defaults remain LEGACY. The reference wallet adapters remain experimental.

**Impact:** A strong core SDK score cannot certify all providers, physical authenticators or the SaaS-managed signing path. Broad production-ready claims exceed available evidence.

**Required change:** Declare the exact initial production provider/algorithm/wallet surface, enforce its policy at host construction, and complete real signing, denied access, outage, restart, rotation and authorized recovery evidence. Keep excluded adapters explicitly experimental.

**Acceptance:** The chosen profile has immutable provider/key identity, independent signature verification, negative authorization and replay tests, recovery approval/audit evidence, and no fallback to ephemeral custody.

- SDK/docs/operations/custody-qualification.md:3
- SDK/docs/api-reference/provider-deployment-profiles.md:49
- SDK/common/src/main/resources/trustweave-capabilities.json:1

### R17 · P1 · Component recovery is not an end-to-end admission and journal recovery proof

Repository: Joint. Evidence: Explicit qualification gap. Status: Open.

**Evidence:** Previous SDK tests qualify component WAL recovery and a read-only ledger integrity helper. The helper depends on a separately trusted checkpoint; it does not itself store that checkpoint, authenticate an external payment journal or fence new admission during reconciliation. SaaS recovery of billing, claims and custody together is not evidenced by those tests.

**Impact:** A consistent but stale restore can still be unsafe to resume if external effects and authorization consumption are not reconciled. RPO/RTO and replica fencing remain deployment-specific.

**Required change:** Own checkpoint custody and journal authentication at the host, keep admission fenced until verification/reconciliation completes, and exercise application-wide restore with custody and external effects.

**Acceptance:** Restore an isolated production-shaped dataset, reject stale/tampered checkpoints and missing WAL, reconcile acknowledged external effects, prove no reused authorization or duplicate billing, and measure agreed RPO/RTO before opening admission.

- SDK/docs/operations/configuration-data.md:1
- SDK/docs/operations/intent/reliability.md:1

## Implementation and qualification backlog

Effort: S = usually less than one engineer-day; M = roughly 1–3 days; L = multi-day/integration work. These are planning estimates, not delivery commitments. External resources and soak duration can dominate elapsed time. All tasks are open.

### Wave 0 · Scope and candidate identity

- **T01 — Declare the initial production support envelope** (Joint · Tech lead · effort S · depends on none · findings R16, R17). Done when: Record supported provider, algorithms, wallet flows, deployment platform, PostgreSQL version, tenant scale and excluded experimental operations.

- **T02 — Select and bind an immutable candidate pair** (Joint · Release engineering · effort M · depends on T01 · findings R11). Done when: Both clean candidate SHAs, reviewed source manifest and source verifier agree; preserve existing local work.

### Wave 1 · Fix release-blocking behavior

- **T03 — Correct Vault structured response parsing** (SDK · KMS maintainers · effort M · depends on T01 · findings R15). Done when: Faithful nested driver fixtures cover create/get, missing keys, version selection and malformed types.

- **T04 — Qualify the selected Vault lifecycle** (SDK · KMS maintainers · effort L · depends on T03 · findings R15, R16). Done when: Isolated provider create/sign/independent verification/restart/rotation passes; no orphaned retry keys.

- **T05 — Reject incompatible production KMS profiles** (SaaS · Platform backend · effort S · depends on none · findings R06). Done when: Truth table and actual Spring startup reject prod+local, staging+local and unintended ephemeral configurations.

- **T06 — Make KMS options effective and validated** (SaaS · Platform backend · effort M · depends on T05 · findings R07). Done when: Non-default endpoint/namespace is used; unknown/unconsumed options fail startup; diagnostics redact secrets.

- **T07 — Establish real outbox transaction boundaries** (SaaS · Billing backend · effort M · depends on none · findings R01, R03). Done when: Spring-managed scheduler/proxy tests demonstrate short atomic claim/finalize transactions.

- **T08 — Implement durable bounded delivery leases** (SaaS · Billing backend · effort L · depends on T07 · findings R02). Done when: Two-worker, expired-lease and worker-crash tests preserve claim ownership with fencing tokens and bounded batch runtime.

- **T09 — Separate transient retries from poison events** (SaaS · Billing backend · effort M · depends on T08 · findings R02). Done when: Connection outage beyond ten ticks recovers; permanent rejection enters an audited terminal queue; retry delay and Retry-After are bounded.

- **T10 — Prove remote idempotency and authenticated redrive** (SaaS · Billing backend · effort M · depends on T09 · findings R02). Done when: Provider commit/response-loss and operator redrive produce exactly one remote charge/effect per event key.

- **T11 — Enforce endpoint-specific limiter failure policy** (SaaS · Security backend · effort M · depends on none · findings R04). Done when: Counter-only outage causes sensitive mutation rejection and bounded approved read fallback; no blanket allow path.

- **T12 — Use authoritative time and bounded shared counters** (SaaS · Security backend · effort M · depends on T11 · findings R05). Done when: Skewed caller clocks cannot reset budgets; counters saturate and TTL/pool limits survive hot-key and churn load.

- **T13 — Remove capability values from telemetry** (SaaS · Observability · effort M · depends on none · findings R08). Done when: Canary path tokens are absent from logs, spans, access logs and error cases while correlation remains intact.

- **T14 — Make readiness verify dependency function** (SaaS · Platform backend · effort S · depends on none · findings R09). Done when: 401/404/429/500, malformed discovery and issuer mismatch are not UP; liveness remains independent.

- **T15 — Repair and test the chosen deployment recipe** (SaaS · Deployment engineering · effort M · depends on T01, T46 · findings R10). Done when: One port/JDBC/issuer contract builds and boots through its actual proxy; missing secrets fail safely.

- **T16 — Replace stale outbox mocks with contract coverage** (SaaS · Test engineering · effort M · depends on T07, T08 · findings R03). Done when: Current unit cases pass and a regression removing scheduler transaction/lease correctness is detected.

- **T46 — Remove the Spring request-context bean collision** (SaaS · Platform backend · effort S · depends on none · findings R18). Done when: Explicitly named telemetry filter coexists with Spring MVC, overriding stays disabled, smoke startup and affected integration cases pass.

### Wave 2 · Data, custody and isolation

- **T17 — Page and checkpoint user synchronization** (SaaS · Identity backend · effort M · depends on none · findings R14). Done when: Multi-page provider fixtures plus a failed page and restart demonstrate complete, idempotent recovery.

- **T18 — Bound synchronization transactions** (SaaS · Identity backend · effort M · depends on T17 · findings R14). Done when: Slow identity HTTP does not hold a DB transaction; one failed persistence unit does not roll back unrelated successful units.

- **T19 — Enforce provider policy at host startup** (Joint · Security architecture · effort M · depends on T01, T04, T06 · findings R16). Done when: The declared profile rejects unknown/stub/disallowed experimental providers before opening resources; no LEGACY bypass in production entry points.

- **T20 — Exercise signing denial and outage boundaries** (Joint · Custody operations · effort L · depends on T19 · findings R16). Done when: Wrong tenant/key/algorithm, expired and replayed proofs fail before provider signing; outage does not select a fallback key.

- **T21 — Exercise authorized recovery and replacement** (Joint · Custody operations · effort L · depends on T20 · findings R16). Done when: Restart/access recovery preserve identity; replacement has independent authorization, revoked old binding and durable audit. Physical-device tests apply only if included in T01.

- **T22 — Persist independently trusted recovery checkpoints** (Joint · Data engineering · effort M · depends on T01 · findings R17). Done when: Checkpoint authenticity and freshness survive DB compromise/restore; application DB alone cannot rewrite the trust anchor.

- **T23 — Fence admission during restore and reconciliation** (Joint · Data engineering · effort L · depends on T22 · findings R17). Done when: No new authorization is admitted until ledger/checkpoint/external journal verification completes; failure remains fenced.

- **T24 — Reconcile external effects after restore** (Joint · Billing and data · effort L · depends on T10, T23 · findings R17). Done when: Authenticated journal and local outbox reconcile commit/ack-loss and stale restore without duplicate effects or reused authorization.

- **T25 — Qualify production-shaped backup and recovery** (Joint · SRE · effort L · depends on T21, T24 · findings R17). Done when: Record agreed RPO/RTO, dataset size and timings; missing WAL/corrupt backup/stale checkpoint fail closed; recovered service resumes safely.

- **T26 — Qualify schema upgrades and rollback compatibility** (SaaS · Data engineering · effort M · depends on T15 · findings R10, R12). Done when: Upgrade from two declared supported schema versions under writes; old/new application compatibility and interrupted migration recovery are explicit.

- **T27 — Complete cross-tenant negative authorization matrix** (Joint · Security test engineering · effort L · depends on T01 · findings R16). Done when: Every declared public/admin/tenant mutation has wrong-tenant, wrong-role, expired, replay and identifier-substitution outcomes tied to source and tests.

- **T28 — Qualify outbound network restrictions** (Joint · Security engineering · effort M · depends on T01 · findings qualification). Done when: Declared DID, webhook and provider clients reject disallowed destinations/redirects and bound size/time; tests use actual host integration.

### Wave 3 · Operational and release qualification

- **T29 — Verify distributed traces through billing and SDK calls** (SaaS · Observability · effort M · depends on T07, T13, T46 · findings qualification). Done when: A real incoming request produces correlated outgoing provider spans with trace propagation and authenticated export; prove manual RestClient construction receives instrumentation.

- **T30 — Add queue, limiter and recovery signals** (SaaS · Observability · effort M · depends on T09, T11, T23 · findings R02, R04, R17). Done when: Oldest outbox age, leased/failed rows, limiter degradation, recovery fence and reconciliation failures have bounded labels and verified alerts.

- **T31 — Define measurable service objectives and alert ownership** (Joint · SRE · effort M · depends on T01, T30 · findings qualification). Done when: Availability, latency, signing and delivery objectives have error budgets, accountable owners and tested notification acknowledgement.

- **T32 — Qualify telemetry privacy and retention** (Joint · SRE and security · effort M · depends on T13, T29 · findings R08). Done when: Access controls and retention are enforced in the selected backend; sensitive canaries never reach storage; cardinality/load limits are measured.

- **T33 — Run representative multi-node soak and overload tests** (Joint · Performance engineering · effort L · depends on T08, T12, T18, T31 · findings R02, R05, R14). Done when: Agree workload first; run proposed 72-hour steady/peak soak and burst/fault phases, tracking p99, saturation, queue age, DB growth and recovery. This is a target, not a completed measurement.

- **T34 — Record scale ceilings and backpressure policy** (Joint · Performance engineering · effort M · depends on T33 · findings qualification). Done when: Document tested tenant/data/request limits, capacity headroom and bounded degradation, including KMS/IdP/billing outages.

- **T35 — Define risk-based coverage and mutation gates** (Joint · Test engineering · effort M · depends on T16, T20, T24, T27 · findings R13). Done when: Critical auth/custody/claim/recovery branches have explicit agreed floors (proposed 90% line, 85% branch) and meaningful mutation checks; exceptions are reviewed, not hidden.

- **T36 — Make browser tests deterministic and enforce coverage** (SaaS · Frontend engineering · effort M · depends on none · findings R13). Done when: Classify current network warnings, add missing fixtures and fail unexpected requests without the prior worker serialization problem; CI runs coverage with agreed floors.

- **T37 — Bind test discovery and skips to release evidence** (Joint · Test engineering · effort M · depends on T02, T35, T36 · findings R11, R13). Done when: Test IDs/counts, failures, documented optional skips and required provider profiles are archived and checked against the immutable candidate pair.

- **T38 — Build the runtime image in CI** (SaaS · Release engineering · effort M · depends on T02, T15 · findings R12). Done when: bootJar, frontend assets and OCI image are built from the candidate pair; production-profile startup and smoke tests run against that image.

- **T39 — Pin inputs and generate SBOM/provenance** (SaaS · Supply-chain security · effort M · depends on T38 · findings R12). Done when: Actions/base image are immutable; dependency/image scans enforce a defined policy; SBOM and provenance identify both repository commits.

- **T40 — Verify artifact identity during promotion** (Joint · Release engineering · effort M · depends on T39 · findings R11, R12). Done when: A modified image, wrong source pair or untrusted attestation is rejected; the tested digest is the promoted digest.

- **T41 — Exercise canary rollout and rollback** (SaaS · Release engineering · effort L · depends on T26, T31, T40 · findings R12). Done when: Promote a qualified digest with explicit health/error-budget gates and demonstrate rollback with the supported schema compatibility window.

### Wave 4 · Independent acceptance and rescore

- **T42 — Publish one production support and operations contract** (Joint · Documentation owners · effort M · depends on T25, T34, T41 · findings R16, R17). Done when: Setup, configuration precedence, limits, provider scope, backup, rotation, incident and rollback instructions match tested commands and evidence.

- **T43 — Run an operator drill from the documentation** (Joint · SRE · effort M · depends on T42 · findings qualification). Done when: An operator other than the author executes restore, custody outage, stuck-delivery redrive and rollback using only published runbooks; gaps are fixed.

- **T44 — Re-review closure evidence and remaining risks** (Joint · Independent reviewer · effort M · depends on T37, T43 · findings qualification). Done when: All P1 findings are closed by behavior tests, scope exclusions are explicit, and evidence is tied to the exact released candidate pair.

- **T45 — Recalculate scores only after acceptance** (Joint · Review owner · effort S · depends on T44 · findings qualification). Done when: Each repository is above 9.7 unrounded, every critical category is at least 9.7, and production acceptance gates pass. Target 9.8; no points are awarded simply for adding documentation/tests.

## Authority for framework semantics

- Spring proxy/self-invocation: https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html
- ShedLock lease lifetime: https://github.com/lukas-krecan/ShedLock

## Limits

Targeted source review and local verification, not an exhaustive penetration test or deployed-system certification. No live managed provider, physical authenticator, production secrets, actual deployment, fresh vulnerability scan, full-scale soak or end-to-end recovery was exercised. Existing local edits were preserved. Evidence source hashes identify cited files; the whole workspace was not a clean release candidate.

## Backend failure triage

556 cases: 442 passed, 111 failed, 3 skipped. Seven failures are UsageReporterTest assertions/verifications against the obsolete repository method. The other 104 failures are initial Spring context errors or cached context-failure cascades; the initial errors identify the requestContextFilter bean collision. Do not count those cascades as independent production defects. The three skipped cases are AccountlyDeploymentContractTest (1) and AccountlyLiveContractTest (2); real Accountly deployment qualification remains open. No fresh backend coverage result is claimed from this failed invocation.
