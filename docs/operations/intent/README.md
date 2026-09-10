# Intent host operations and recovery exercise

The executable fixture is `IntentOperationsExerciseTest` in the verifiable-intent module.
It starts a loopback HTTP host, authorizes pre-validated fixture requests against the real
ledger, exports bounded Prometheus metrics, stops its own database, and restores into a
separate PostgreSQL container. Signature and recurrence integration are tested separately
by `IssuanceRoundTripTest`. This is a component operations exercise, not a deployable public
authorization endpoint or certification of production infrastructure.

Run from the repository root:

```shell
./gradlew :credentials:plugins:verifiable-intent:test --tests '*IntentOperationsExerciseTest'
```

Docker is required. Only containers created by the fixture are stopped or removed. A temporary
backup file is deleted by the fixture. Results are written to
`credentials/plugins/verifiable-intent/build/reports/intent-operations.json` and retained by CI.
The test uses 100 requests, concurrency 8, a 500-unit cap and 10-unit reservations. Exactly
50 reservations must pass. It checks simultaneous idempotent settlement release, conflicting
outcomes, HTTP 503 on storage failure, latency metrics, alert signals, and exact reservation
and settlement counts after restore. Released/settled transaction replay must still fail.
The fixture uses an unpooled DataSource and measures server-handler latency for the initial
100 requests, excluding client connection and server queue time. These are measured fixture
results, not production throughput or availability promises.

## Alert rules

`alerts.yml` provides SQL-failure, reconciliation-backlog, age, p95-latency, health
freshness, host-availability and missing-instrumentation rules for runtime ledger metrics.
`alerts.test.yml` contains 37 assertions covering pending states, firing, recovery,
and expected cancellation/rejection outcomes that must not page as SQL failures, using
Prometheus promtool. CI uses image digest
`sha256:63805ebb8d2b3920190daf1cb14a60871b16fd38bed42b857a3182bc621f4996` (Prometheus 3.5.0).
The thresholds are fixture defaults: choose host SLOs and reconciliation age/volume limits
before production deployment. Configure the expected scrape job as `trustweave-intent`
or consistently change the job selectors in the availability rules and their tests.
Rule evaluation is tested. The notification exercise below additionally checks real
Prometheus-to-Alertmanager delivery to a local receiver. External pager delivery remains
a deployment acceptance check.

Run in this directory with that version of promtool:

```shell
promtool test rules alerts.test.yml
```

Metric labels must not contain transaction IDs, tokens, tenants, credentials or exception
messages. The reference host now exports the runtime `IntentLedgerDiagnostics` collector,
not a separate test-only metric implementation. Its fixed series contain four operation
labels (`migrate`, `reserve`, `reconcile`, `health`) and six outcome labels (`success`,
`rejected`, `invalid`, `storage_failure`, `failure`, `cancelled`). Duration histograms
include database connection acquisition and transaction completion; they do not include
host request queueing or the preceding cryptographic verification.
`PostgresIntentLedger.healthSnapshot()` exposes terminal counts and oldest
pending time. For migrated pending records without a recorded creation time, oldest time
is null (unknown), never fabricated as migration time. Scrape aggregate state periodically; it scans reservation state, so size the
monitoring interval for the retained dataset instead of querying on every authorization.

## Host wiring and metric lifecycle

Create one `IntentLedgerDiagnostics` per monitored database and share it with the
authorization, reconciliation and monitoring ledger instances. The existing one-argument
`PostgresIntentLedger(dataSource)` constructor remains valid and creates its own collector.
The additive two-argument constructor accepts a shared collector. Do not merge independent
databases into one collector: their health snapshots would overwrite one another.

Run `monitoringLedger.healthSnapshot()` on a host-owned bounded scheduler using the
monitoring database role, initially and then periodically (30 seconds is a starting
point for small ledgers). Configure connection, socket and query timeouts, and prevent
overlapping polls. Handle SQL failure without terminating the scheduler; propagate
cancellation during shutdown. The collector records success or failure automatically.
The stale-health alert detects a scheduler that silently stops, including one that
last succeeded. Pick the polling interval after measuring the retained dataset scan.

Expose `diagnostics.prometheus()` as `text/plain; version=0.0.4` on the host's authenticated
or network-restricted metrics route. Export never polls the database. Do not let a
database outage disable this route. Initial/failed health polling emits
`trustweave_intent_health_poll_success 0` and omits pending/age gauges. The last successful
poll timestamp remains available. Do not replace absent/unknown values with zero in dashboards.
Process-local counters and histograms reset on restart; use `rate`/`increase`, not raw
counter subtraction. Reconciliation gauges describe durable database state at the
last successful poll. The collector retains fixed arrays, not request histories.

The preceding `intent_*` names existed only in the component test. The runtime contract
uses the `trustweave_intent_*` prefix in `alerts.yml`. Update dashboards copied from the
old fixture together with these rules. `build/reports/intent-metrics.prom` captures a
fresh scrape for `promtool check metrics` and is retained by CI with the exercise report.

## Storage failure

Use the alert's `job` and `instance` to inspect the host and database connection pool.
Separate connection exhaustion, network reachability and database lock/query timeouts
using host/database telemetry. Ledger SQL failures include uncertain commit outcomes;
do not retry them as fresh authorizations or release reservations. Follow the journal
reconciliation procedure below. Page the database/service owner if failures persist.
Rejection and cancellation counters do not contribute to this alert.

## Reconciliation backlog and age

Investigate the authoritative payment journal and the reconciliation worker. Compare
pending, settled and released counts. Unknown oldest age identifies undated legacy
rows; investigate their provenance rather than treating them as new. Do not fabricate
dates, delete replay records or release funds solely to clear an alert. Restore the
worker or reconcile authenticated outcomes; validate that age/count gauges recover.

## Latency and in-flight work

Compare reserve p95 with `trustweave_intent_in_flight`, connection pool occupancy and
database lock waits. Reduce admission when overloaded and diagnose hot mandate rows.
The collector distinguishes operation time from host queueing; instrument queue time
and cryptographic verification separately in the host. Do not bypass row locks or
increase concurrency without checking downstream capacity.

## Health polling failure or staleness

Check the monitoring role, connection timeouts and polling scheduler before interpreting
reservation gauges. A running process with a stale success timestamp is not healthy
evidence. Restore polling and confirm the timestamp advances and the success gauge is 1.
Avoid having every scrape trigger an expensive database scan.

## Missing host or failed scrape

For `IntentHostUnavailable`, inspect the target, TLS/authentication, network access and
metrics route. For `IntentHostMissing`, inspect service discovery and the expected job
configuration. For `IntentTelemetryMissing`, the scrape works but the ledger metric
contract is incomplete: either the health-success flag or last-success timestamp is
absent for that same job and instance. A healthy second host cannot mask the missing
timestamp. Inspect deployment wiring and version compatibility. Do not suppress
these alerts by removing the target. Validate recovery through a real scrape.

## Remaining deployment acceptance

The rule engine, component-host failure/recovery and local notification pipeline are
exercised locally. Production
qualification still requires real host queue/pool/trace telemetry, agreed SLO thresholds,
retention and access controls, and a delivered-and-resolved notification through the
intended Alertmanager/pager route. No paging service or external recipient is contacted
by these tests. A library collector is not end-to-end distributed tracing or an audit ledger.

## Notification delivery exercise

After generating the runtime scrape with `IntentOperationsExerciseTest`, run from the
repository root with Python 3.11+ on Windows or Linux amd64:

```shell
python -m pip install PyYAML==6.0.3
python scripts/intent-notification-exercise.py
```

The script downloads Prometheus 3.5.0 and Alertmanager 0.28.1 from their official releases,
verifies pinned SHA-256 checksums before extracting the executables, and caches the archives
under `.gradle/intent-monitoring-tools`. It checks the archives again on subsequent runs.
These are reproducible test versions, not a recommendation about the current production
release. No Docker daemon is needed for this notification exercise. The preceding ledger
exercise still requires PostgreSQL containers.

All three listeners bind to `127.0.0.1` on temporary ports; Alertmanager cluster gossip is
disabled. The generated receiver is local and has no credentials or external destination.
The metrics fixture replays the runtime collector's actual series and types while injecting
synthetic health failures, a missing freshness timestamp and HTTP 503 scrapes. It resets backlog
gauges to a healthy baseline. This validates monitoring wiring, not a new database outage.
The nine repository alert rules load unchanged, including their full pending durations.
Each of the three critical scenarios must pass pending, delivered firing, and delivered
resolved states with matching fingerprints, job/instance labels and runbook annotations.
The receiver deliberately returns HTTP 503 once; the same alert must be retried successfully.

Allow about seven minutes after downloads. A bounded timeout fails each unmet transition.
Owned subprocesses are stopped on success, failure or interruption. Temporary monitoring
data is removed after shutdown. Configurations, process logs, webhook payloads and source
hashes remain under `build/reports/intent-notifications`; the CI and release-evidence workflows
run this gate and retain failure diagnostics. This directory contains only synthetic fixture
data, but its generated configurations include local paths and temporary ports.

The script follows the official [Prometheus configuration](https://prometheus.io/docs/prometheus/latest/configuration/configuration/)
and [Alertmanager routing and webhook configuration](https://prometheus.io/docs/alerting/latest/configuration/).
Production acceptance additionally needs protected endpoints, durable retention, host and
database-pool telemetry, distributed traces, agreed SLOs, and firing/resolved delivery through
the intended on-call route. A local receiver cannot establish those deployment properties.

## Reconciliation contract

Use a privileged host process with an authoritative payment journal. Authenticate the journal
and match transaction, mandate, amount and currency to the original authorized request before
calling `reconcile`. Preserve the evidence record durably: the ledger stores its reference hash,
not proof that the provider supplied it. A timeout, missing response or unknown outcome must
stay RESERVED. SETTLED retains spending. RELEASED requires conclusive non-execution and refunds
the reservation once; the downstream payment must then be fenced from execution. Both terminal
states preserve nonce/transaction replay markers and occurrence consumption. Conflicting final
outcomes require operator investigation, not overwriting the earlier evidence.

Use separate database roles/connections for authorization, reconciliation and monitoring.
Authorization needs SELECT/INSERT on reservations and SELECT/INSERT/UPDATE on accounts;
reconciliation additionally needs UPDATE on reservations. Migration privileges are separate.
Deploy the upgraded ledger consistently before enabling releases; mixed older writers or
rollback to a writer that ignores occurrence limits is not qualified.

## Production backup/restore acceptance procedure

1. Stop admission on every host and drain active authorizations and reconciliation writes.
   Fence downstream execution and record the authoritative payment-journal watermark.
2. Take an encrypted, access-controlled backup with the schema and all reservation/evidence
   records. Preserve the backup checksum, migration version and journal watermark separately.
3. Restore into an empty, isolated database. Keep admission disabled. Compare account balances,
   reservation/terminal counts, oldest pending state and replay markers against the backup and
   journal. Resolve unknown outcomes through the privileged reconciler.
4. Prove that pre-backup transactions and challenges cannot authorize again, including RELEASED
   records. Verify a valid new request can reserve only genuinely available funds.
5. Switch all hosts to the restored database, verify monitoring and pager delivery, then remove
   the execution fence and enable admission. Preserve measured outage, restore duration and
   data-loss bounds as deployment-specific evidence.

The fixture performs a quiesced backup and proves zero lost records for that workload. Its
restore timer covers recovery-container startup, pg_restore, state comparison and host switch;
it is not total production RTO. It does not exercise stale backups, PITR, replication or an
external payment system. If a backup predates accepted payments, do not reopen authorization
based on that backup alone. Restore complete WAL/journal state and reconcile first. No API here
can infer missing historical authorizations or safely reconstruct unknown provider outcomes.

References: [VI constraints](https://www.verifiableintent.dev/spec/constraints/) and
[Prometheus rule tests](https://prometheus.io/docs/prometheus/latest/configuration/unit_testing_rules/).
