# Configuration and ledger data integrity

Plugin configuration is strict UTF-8 JSON. `PluginConfigurationLoader` accepts at most
1 MiB of encoded input and 64 nested containers. It rejects unknown schema properties,
duplicate decoded JSON member names (including Unicode-escaped aliases), malformed UTF-8,
duplicate plugin IDs, empty or duplicate provider chains and invalid identifiers.
Identifiers are 1–128 ASCII letters/digits with dots, underscores, colons and hyphens after
the first letter/digit. A configuration may contain at most 1,024 plugins, 128 default
provider entries, 128 chains and 64 providers per chain. SPI-discovered fallback providers
need not appear in the plugin list. Provider-specific configuration values remain opaque
strings: each provider must validate its own required settings before accepting work.

This intentionally tightens previous permissive parsing. Convert YAML to JSON, remove
unknown fields, resolve duplicated keys and identifiers, and validate configuration with
the loader before rolling out. Nullable typed convenience getters retain their existing
behavior. Directly constructing configuration models does not invoke loader validation.
Avoid logging the serialized configuration or its individual properties: the loader's
errors omit input and parser excerpts, and model string rendering omits the `config` map,
but serialization and explicit getters necessarily still expose configured values.

## Recovery checkpoint procedure

`PostgresIntentLedger.verifyIntegrity()` reads a repeatable-read snapshot. It checks
account spending against non-released reservations, budget bounds, occurrence consumption,
currency format, orphan reservations, terminal state and settlement evidence consistency.
The versioned SHA-256 checkpoint covers every account and reservation field, including
replay identifiers, policy maximums, retained occurrence counts, terminal evidence and
timestamps. Null legacy timestamps remain distinguishable. Higher conservative occurrence
counts are allowed, but lower counts fail. This is a read-only audit; it never repairs data.

1. Drain and fence all authorization and reconciliation writers for the recovery boundary.
2. Obtain the checkpoint with `ledger.verifyIntegrity()` using a trusted, unfiltered database
   connection. Retain it in independently authenticated, access-controlled storage with the
   backup identity, database identity, recovery target and deployment/configuration version.
3. Back up the database while the boundary remains fenced. Validate backup checksums and
   retained WAL. Keep the checkpoint outside the backup's rollback domain.
4. Restore into an isolated database. Before enabling traffic, call
   `ledger.verifyIntegrity(expectedCheckpoint)`. A mismatch or exception keeps admission
   fenced. Do not replace the expected checkpoint with the restored database's own digest.
5. If recovery deliberately targets a different point, select the independently retained
   checkpoint for that point, or reconcile against the authoritative external journal under
   an approved recovery procedure. A stale snapshot can be internally consistent; an audit
   without a trusted expected checkpoint cannot establish freshness.

The audit streams results in batches of 256 with 60-second SQL timeouts. PostgreSQL still
performs full scans and ordered reads; schedule the operation outside the admission path
and qualify its duration against deployment data size. Row-level security is disabled
transaction-locally for the audit so a role subject to filtering fails rather than silently
auditing a partial view. The SDK does not automatically fence admission or store checkpoints.
The `v1` digest format is a recovery artifact contract; future format changes need explicit
migration and must not reinterpret existing stored checkpoints.

## Authoritative settlement remains a host trust boundary

`reconcile` is privileged. Authenticate the journal and bind each result to the original
mandate, transaction, amount and currency before calling it. Retain the durable journal
reference. `RELEASED` requires confirmed non-execution and downstream fencing; timeout or
unknown status is not proof. Matching repeats are idempotent; contradictory outcomes or
references fail. A valid database checkpoint cannot prove an external payment did not run.

The regression suites are `ConfigurationBoundaryTest` and `LedgerIntegrityTest`. The wider
[reliability qualification](intent/reliability.md) covers physical backup/WAL restoration,
corruption rejection and process-crash recovery. These component checks do not establish
production RPO/RTO, key custody, external-journal authentication or backup-store access policy.
