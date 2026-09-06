# Operating TrustWeave services

TrustWeave is a library. The hosting application owns request tracing, durable audit
records, metrics export and HTTP error mapping. Configure an SLF4J backend in the
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
emit `event=status_list_failure` and the exception type, while anonymous callers
receive a generic error. Cancellation propagates and is not an operational failure.

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
