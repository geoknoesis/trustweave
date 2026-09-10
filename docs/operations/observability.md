# Operating TrustWeave services

TrustWeave is a library. The hosting application owns telemetry destinations, durable audit
records and deployment policy. The [shared SDK host integration](host/README.md) adds
request tracing, protected metrics, admission timing and pool diagnostics to six Ktor
servers through explicit `withObservability` configuration. Configure an SLF4J backend in the
application; the SDK does not install one or configure your log destinations.

## Correlating requests safely

Generate a random request ID at the HTTP boundary. Return that ID with a generic
error response and put it in the application's structured log context. Do not use
an incoming credential ID, DID, nonce or bearer token as the correlation ID. In
coroutines, propagate your logging context explicitly with the logging backend's
coroutine context adapter; thread-local MDC alone does not survive dispatch changes.

Record fixed fields: `event`, `operation`, `provider`, `outcome`, `error_code`,
`duration_ms`, and the generated `request_id`. Use the typed exception code when
available and an allowlisted exception type otherwise. Do not serialize arbitrary
exception context or log provider response bodies, connection strings, credentials,
key material, presentations or raw exception messages. Status-list server failures
emit `event=status_list_failure`, a fixed `error_code` and a random `request_id`.
Both status-list routes return their generated ID in `X-Request-ID` and in the
`requestId` field of error bodies. Incoming request IDs are ignored. The ID is
passed explicitly through the request coroutine rather than stored in thread-local
MDC. Match it to the server log; no status-list ID or provider message is logged.
Error codes are `TIMEOUT`, `STORAGE_FAILURE`, `CONFIGURATION_FAILURE`, or `INTERNAL_FAILURE`. Anonymous
callers receive generic errors. Cancellation propagates and is not logged as an
operational failure. A host SLF4J backend and retention policy remain required.

## Metrics and alerts

At the service boundary, count operations by bounded labels (operation, provider,
outcome). Measure latency in a histogram and alert on error rate and tail latency.
Never use wallet IDs, tenant IDs, DIDs, URLs or exception messages as metric labels.
Monitor database pool saturation, remote timeouts, status UNKNOWN counts, session
capacity rejection, and recovery failures separately. UNKNOWN is not ACTIVE.

The in-memory OID4VP and SIOP stores expire entries after five minutes and
cap each store at 1,000 pending entries. Reads and updates do not extend expiry.
`SessionCapacityExceededException` indicates admission rejection: map it to HTTP
429 with a bounded retry policy, without evicting another user's pending request.
OID4VCI reports its analogous condition as `Oidc4VciException.CapacityExceeded`.
CHAPI message construction is stateless and retains no offers or proof requests.
Use a shared, bounded `SessionStore` implementation for multi-node OID4VP services;
the default store cannot survive restart or route requests across nodes.

## Wallet queries and deployment policy

A revoked filter includes only credentials whose status is known to match. Unknown
status matches neither `revoked=true` nor `revoked=false`; an unfiltered query still
returns those records. Inject `WalletStatusResolver` through the file or database
factory when live status is required. Treat resolver failure as UNKNOWN.

Database `countCredentials()` runs SQL COUNT without reading credentials or calling
status providers. Full statistics require a live scan and are advisory under
concurrent writes. Database scans use 500-record pages (plus pagination look-ahead) and stop at
the initial active-row budget; cloud scans retain one decoded credential at a time
plus the object-key listing. Cloud corrupt records still fail strict listing; use
`recoverRecords()` to obtain explicit per-record failures. A concurrently deleted
object is skipped. Neither scan is a transactionally consistent remote snapshot.

`SUPPORTED_ONLY` currently admits no wallet implementation because none has earned
supported status. This is intentional. `EXPERIMENTAL` is an explicit development
choice for assessed experimental providers. Testkit factories accept `LEGACY` only;
they do not provide production custody. Unassessed custom plugins remain rejected
by strict policy until their capabilities are reviewed and added to the catalog.

## Release evidence

A local test pass is not hosted CI or live-provider evidence. Preserve the commit
SHA, commands, test counts, skipped tests and workflow run links. Run the actual
release-evidence workflow and retain its attested artifacts before claiming a
qualified release. Keep provider qualification separate from mock transport tests.

## Status-list write integrity

Database status-list mutations lock the parent row before reading the bitmap.
Single changes, batches, allocation and expansion use the same transaction;
failed batches roll back both allocation and bitmap changes. Monitor database
lock waits and pool saturation when many writers target one list. Parallelize
across lists rather than bypassing the row lock. Expansion uses declared list
size, not bitmap storage capacity. Invalid batch indices fail before mutation.

## Coverage gates

CI enforces `config/coverage-policy.json` against the merged Kover XML using
`scripts/check-coverage-policy.py`. Missing scopes, absent counters and empty
reports fail. Current floors preserve the measured baseline; they are not a
claim of adequate security coverage. Raise them as meaningful regression and
conformance tests land. Do not lower them solely to make a build green.

## Intent authorization host exercise

See [the executable host and recovery runbook](intent/README.md) for bounded metrics,
Prometheus alert tests, durable settlement reconciliation and separate-database restore.
The fixture reports measured local results; production acceptance requires repeating the
runbook with the deployed host, payment journal, backup system and pager route.

The runtime `PostgresIntentLedger` now instruments schema initialization,
reservation, reconciliation and health reads through `IntentLedgerDiagnostics`.
Counts distinguish success, policy rejection, invalid input, SQL failure,
unexpected failure and cancellation. Duration includes connection acquisition and
transaction completion. No callbacks or log backends participate in the
authorization decision; metrics retain only fixed counters and histogram buckets.
The host exports `ledger.diagnostics.prometheus()` on a protected metrics route.
That method never accesses the database and continues working during an outage.

Poll `healthSnapshot()` periodically using a monitoring connection; share one
diagnostics instance with the authorization ledger for that database. Scraping
only reads the latest measured health. Failed polling withdraws pending/age gauges;
the latest successful timestamp remains visible so a stopped polling job cannot
look healthy indefinitely. Legacy unknown ages remain unknown. The runbook includes
scrape, stale-poll, missing-telemetry and age alerts with tested firing/recovery.

The [notification delivery exercise](intent/README.md#notification-delivery-exercise)
uses checksum-pinned Prometheus and Alertmanager binaries to verify pending, firing,
retry after a receiver HTTP 503, and resolved webhook delivery on loopback. CI retains
the notification payloads, configuration checks and process logs, including on failure.
The shared host module now provides local queue/pool/trace instrumentation and tests.
Deployment-wide coverage and the actual on-call route remain acceptance work.
