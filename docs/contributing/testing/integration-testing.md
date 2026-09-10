---
title: Integration Testing Best Practices
parent: Testing
grand_parent: Contributing to TrustWeave
---

# Integration Testing Best Practices

Start with the [testing guidelines](../testing-guidelines.md) for commands and result
contracts. A test using a mock or emulator establishes only that boundary; reserve
provider-qualified claims for runs against the identified provider and configuration.

## TestContainers usage

The PostgreSQL intent-ledger and status-list suites use real disposable databases.
Docker must be running and able to pull the pinned images before the test begins.
Do not catch startup errors and return success, and do not substitute H2 for PostgreSQL
locking, migration or recovery claims. Missing required infrastructure is a failed gate.

Use the existing [PostgreSQL ledger test](../../../credentials/plugins/verifiable-intent/src/test/kotlin/org/trustweave/credential/vi/PostgresIntentLedgerTest.kt)
and [status mutation test](../../../credentials/plugins/status-list/database/src/test/kotlin/org/trustweave/revocation/database/PostgresStatusMutationTest.kt)
as working references. Each creates isolated state and exercises the actual database
semantics. Image versions and connection setup are maintained in the test sources.

## Local integration tests

The shared host [export integration test](../../../observability/src/test/kotlin/org/trustweave/observability/HostExportIntegrationTest.kt)
starts two actual Netty servers and a loopback OTLP collector. It verifies exported wire
spans and authenticated delivery. Hikari/H2 tests qualify connection-pool instrumentation
and transaction preservation, not PostgreSQL transaction-isolation behavior.

Bind to loopback and ephemeral ports. Close clients, servers, executors and collectors
in `finally` or `use`, even if an assertion fails. Use finite connection/read/export
and test deadlines. Record artifact locations and inspect the actual test XML.

## Concurrency and recovery

Use barriers to place the system in the required state before inducing a race. Verify
exact admitted/denied counts, absence of leaked permits or reservations, and successful
work after recovery. Test both clean failures and uncertain outcomes; a transport timeout
does not prove that a remote payment or write never happened.

[Intent operations](../../operations/intent/README.md) documents the database recovery
exercise. Do not present a quiesced local restore as production PITR or an RTO guarantee.
The [cross-stack guide](../../operations/vi-cross-stack.md) records independent peer pins,
interoperable profiles and deliberate stricter rejection behavior.

## Hosted providers

Use an identified disposable account/resource with explicit authorization. Record the
provider version, region, operation, sanitized result, source revision and cleanup. Keep
credentials out of logs, fixtures, reports and exported telemetry. A placeholder rejection
test or an in-memory signer does not qualify managed signing, custody or recovery.

If a provider scenario is optional, make its skip explicit in JUnit output and report the
reason. Required release scenarios must fail when their prerequisite is absent. Do not
turn an exception into a printed ?Skipping? message followed by an apparent pass.

## CI evidence

Run the full build before the compiled-test and required-result gates. Upload XML and
failure diagnostics even when later steps fail. Distinguish failures, errors, skips,
undiscovered methods and successful invocations. Keep full-suite results separate from
filtered local runs; filtering is useful during development but is not release acceptance.
