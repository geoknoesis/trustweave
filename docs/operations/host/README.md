# Shared SDK host observability

The `org.trustweave:observability` module provides `HostTelemetry` and
`HostObservability` for Ktor 2.3 hosts. Status-list, VC API, OID4VCI, AVP authorization,
DID registrar and trust-registry embedded servers expose `withObservability` before
`start`. Existing constructors and default behavior are preserved. This integration
does not apply to the separate Spring SaaS host or instrument every third-party SDK.

A complete [executable host example](example.md) is compiled and tested in CI.

## Configure a host

Supply your application's OpenTelemetry instance; the library never installs a global
SDK or chooses an exporter. Its default is OpenTelemetry's no-op implementation.
The example assumes `openTelemetry` is a configured application-owned instance and
`metricsToken` comes from a secret store. Enable TLS at the host ingress or keep this
listener on loopback behind an authenticated TLS proxy.

```kotlin
import org.trustweave.observability.HostAuthentication
import org.trustweave.observability.HostObservability
import org.trustweave.observability.HostTelemetry
import org.trustweave.credential.statuslist.server.StatusListServer

val telemetry = HostTelemetry(
    openTelemetry = openTelemetry,
    maxConcurrentRequests = 128,
    maxQueuedRequests = 64,
    queueTimeoutMillis = 500,
)
val observability = HostObservability(
    telemetry = telemetry,
    metricsBearerToken = metricsToken,
    trustRemoteParent = false,
)
val server = StatusListServer()
    .withObservability(observability)
    .withAuthentication(
        HostAuthentication.bearerToken(
            token = adminToken,
            rateLimit = HostAuthentication.RateLimit(permits = 120),
        ),
    )
server.start()
```

`withAuthentication` is not optional for a server that mutates. Until it is called, the shipped
servers refuse POST, PUT, PATCH and DELETE with 503 and keep serving reads. Where something in
front already authenticates callers, say so — `HostAuthentication.frontedByProxy("mTLS at the
ingress")` admits everything and records the decision where the next operator will read it.

The per-caller rate limit above bounds one caller; `HostTelemetry`'s admission limit bounds the
server as a whole. Behind a proxy that does not preserve the client address, every request shares
one remote host, so configure the per-caller limit at the proxy instead.

For a host-owned Ktor application, call `observability.install(application, HostKind.VC_API)`
before configuring routes. Use one telemetry instance per admission domain/process.
Sharing one instance deliberately shares admission permits and aggregate queue gauges.
The default has no effective admission limit; choose explicit limits after measuring
downstream capacity. These limits govern application handler work, not accepted sockets,
proxy queues or Netty work before the Monitoring pipeline phase.

## Traces and safe correlation

Each request gets a locally generated `X-Request-ID`; incoming IDs are ignored. A valid
trace context also returns `X-Trace-ID`. The generated request ID is a span attribute,
so status-list error logs and response bodies join to the same trace. Labels in metrics
never contain IDs. The fixed span names identify the host or phase; request paths,
queries, authorization headers, credential/DID values, exception messages and stack
traces are not recorded by this module.

`telemetry.phase(HostPhase.VERIFY) { ... }` creates a child span and records elapsed
time. Other fixed phases are SIGN, PROVIDER and DATABASE_ACQUIRE. Context follows coroutine
dispatch changes via OpenTelemetry's Kotlin context adapter. Use
`W3CTraceContextPropagator` to inject `Context.current()` into an outgoing trusted service
request. The receiver must opt in to `trustRemoteParent`; the default starts a new trace
at the boundary. A trusted ingress must remove untrusted trace headers before enabling
that option. The host extracts only `traceparent`, never baggage or arbitrary tracestate.
Request/phase spans end on cancellation and failure; original exceptions propagate.

Use an asynchronous `BatchSpanProcessor`, not a synchronous network exporter on the
request path. Reference settings are queue 2048, batch 256, export timeout 5 seconds,
and parent-based 10% root sampling. Configure OTLP HTTPS with a secret/header supplier
or mTLS, resource service name/version, and bounded exporter connection/read timeouts.
The application owns shutdown and force-flush deadlines. These settings are operator
choices; the library does not create background exporter threads or select destinations.

Collect OpenTelemetry SDK self-monitoring metrics as well. With Java SDK 1.65.0's legacy
schema, `processedSpans` distinguishes dropped spans and `queueSize` measures the batch
queue. OTLP exporter metrics include successful/failed exports. Select and pin the
schema in your host; metric names differ under the newer semantic-convention schema.
Alert on sustained drops/failures through a separate monitoring route, not through the
same failing trace exporter. The local test verifies that a blocked export does not delay application
work, that the SDK queue drops excess spans, and that application
phase counts still advance. Trace loss is observable but not durable audit evidence.

## Queue and pool saturation

`trustweave_host_active_requests` and `trustweave_host_queued_requests` expose admission
state. A full queue or admission timeout returns 503 with `Retry-After: 1`. Cancellation
removes queued work and returns acquired permits. Retrying an authorization still needs
the original idempotency/replay protocol; a 503 is not proof that a payment never ran.

Wrap a DataSource with `telemetry.observeDataSource(source)` to measure connection
acquisition, including wait and failure, under the current request trace. Connections
are returned unchanged: transaction, rollback and close semantics remain with the
driver/caller. Do not log connection arguments. Pass the **original** HikariDataSource
as `HostObservability(pool = pool)` to expose in-memory active/idle/waiting/capacity
counters. Unsupported, uninitialized or closed pools export availability 0 and omit
the other gauges. That indicates absent pool telemetry, not proof of a database outage.
MXBean readings are advisory snapshots; no query or new connection runs during export.

`trustweave_host_request_seconds` includes admission and handler time;
`trustweave_host_queue_seconds` isolates admission wait. Phase histograms isolate known
operations. Compare pool waiters with acquisition failures and queue/handler latency.
Reduce admission during saturation and investigate database lock waits or provider
latency before raising concurrency. Configure real driver timeouts: coroutine cancellation
alone cannot interrupt every blocking JDBC call. Hikari connection wait/failure/recovery
is exercised against an actual local H2 pool; deployed PostgreSQL timing remains separate.

## Protected scraping

`GET /internal/metrics` is registered only when a 32â€“256 character printable ASCII
secret is configured. Missing/incorrect authorization returns 401; success requires
`Authorization: Bearer <secret>` and returns `Cache-Control: no-store`. Constant-time
byte comparison avoids prefix comparisons. The endpoint bypasses application admission
so a full request queue does not disable monitoring. It does not query the database.
The integration test checks unauthorized denial and a successful scrape while the real
status-list server has a blocked request and rejects another request due to overload.

Configure Prometheus's expected job name as `trustweave-host`, with `metrics_path:
/internal/metrics` and bearer credentials read from a secret file. Protect scrape
configuration, TSDB, dashboards and collector access independently. Add the static
label `expects_pool="true"` only to hosts configured with an underlying Hikari pool.
The poolless-host case must not page as an unavailable database. A live target missing
the host metric contract triggers `HostTelemetryMissing`. Add infrastructure target-down
and service-discovery alerts alongside these host rules.

## Availability and admission

`slo.json` defines **reference defaults**, not an agreed or achieved deployment SLO:
99.9% availability over 30 days, counting server errors and admission rejection as
failures; cancellation is excluded. Client errors count as serviced requests. Fast
burn requires both 5-minute and 1-hour ratios above 14.4 times the 0.1% budget, held for
2 minutes. Slow burn uses 30-minute/6-hour windows above 6 times the budget, held for
5 minutes. A short spike alone must not satisfy the long window. Empty and cancellation-only
traffic must not page. Queue p95 above 100 ms is the reference capacity warning.

`alerts.test.yml` contains 32 assertions for the six rules. Use the pinned Prometheus
3.5.0 promtool already used by the intent notification exercise. Deployment owners must
choose targets, budget definitions, minimum-traffic policy, escalation and thresholds
from representative load and business requirements. Do not claim the synthetic local
measurements establish production throughput, availability, MTTR or RTO.

## Retention and acceptance

The module retains fixed counters and histogram buckets, never request histories or
completed spans. The host SDK controls a bounded exporter queue; its exporter/backend
controls transmitted data and retention. Proposed limits in `slo.json` are metrics 30
days, traces 7 days and logs 14 days, subject to the deployment's data policy. Enforce
those limits in the actual backends, verify access separation and deletion, and audit
who can change exporters or scrape credentials. Retention is not implemented by this
in-process library.

Local gates cover private-field exclusion, correlation across 40 concurrent requests,
two real HTTP hosts exporting 10 four-span traces as authenticated OTLP protobuf,
20,000 requests across eight workers with fixed series count, 1,000 queued cancellation/
timeout contenders, Hikari pool saturation, a blocked exporter, and the actual status-list
host during overload. Read executed XML and artifacts before claiming these gates passed.
CI retains `observability/build/reports` and the server test results. No external on-call
recipient is used; the preceding [notification exercise](../intent/README.md#notification-delivery-exercise)
covers a local Alertmanager receiver.

References: [OpenTelemetry Java API](https://opentelemetry.io/docs/languages/java/api/),
[SDK batching, exporters and self-monitoring](https://opentelemetry.io/docs/languages/java/sdk/),
and [Ktor application plugins](https://api.ktor.io/2.3.x/ktor-server-core/io.ktor.server.application/create-application-plugin.html).
