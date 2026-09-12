# Library telemetry

Until 11 September 2026 the SDK's instrumentation stopped at the HTTP boundary. A host running the
embedded servers got request spans, protected metrics and an `X-Request-ID`, but the work
underneath — resolving a DID, signing with a KMS, reading a wallet, verifying a credential —
emitted nothing. A slow request could not be attributed, and a failed verification could not be
joined to a cause. This is the missing half.

## Shape

The SPI lives in `:common` and depends on nothing but the Kotlin standard library, so the core
modules do not acquire OpenTelemetry because a host wants tracing. `:observability` adapts it.

```
did-core · kms-core · wallet-core · credential-api
        │  org.trustweave.core.telemetry.Telemetry   (no-op until installed)
        ▼
   LibraryTelemetry  (:observability)  ──▶  OpenTelemetry spans + Prometheus
```

## Turning it on

```kotlin
val library = LibraryTelemetry(openTelemetry)
Telemetry.install(library)

val server = StatusListServer()
    .withObservability(HostObservability(telemetry, metricsToken, library = library))
    .withAuthentication(HostAuthentication.bearerToken(adminToken))
```

Passing `library` to `HostObservability` does two things: the library scrape is appended to
`/internal/metrics`, and each request's id is placed in the coroutine context so library events
carry it. Nothing is recorded until `Telemetry.install` is called; the cost before that is one
volatile read per operation.

KMS is instrumented by wrapping, because there is no single implementation to instrument:

```kotlin
val kms = AwsKeyManagementService(...).withTelemetry()
```

## What is emitted

`trustweave_library_operations_total{operation,outcome}` — counter
`trustweave_library_operation_seconds{operation}` — histogram, buckets 5ms to 30s

Both labels are closed enums, so the series count is fixed no matter what traffic arrives.

| `operation` | Emitted from |
|---|---|
| `did_resolve` | `CachingDidResolver.resolve` |
| `kms_generate_key`, `kms_sign` | `TelemetryKeyManagementService` |
| `credential_issue`, `credential_verify` | `DefaultCredentialService` |

Other `Operation` values are declared and carry a zero series until their call sites are
instrumented; the enum is the contract, and a host can chart against it today.

### Outcomes

| `outcome` | Meaning |
|---|---|
| `success` | Completed and returned a successful result |
| `rejected` | Completed correctly, and the answer was no — an invalid credential, an unresolvable DID |
| `failure` | Threw |
| `cancelled` | The caller's coroutine was cancelled |

**`rejected` is deliberately not `failure`.** A credential that fails verification is the library
working; a host that pages on it will page on ordinary traffic. Alert on `failure`, chart
`rejected` as a rate and alert only on a change in that rate.

`cancelled` is also separate: a cancelled caller is not a failed operation, and conflating the two
is what makes cancellation bugs invisible.

### Reason codes

`reason` is the exception's simple class name, never its message. Messages carry DIDs, file paths,
key identifiers and provider error text, and reason codes end up as span attributes. If you need
the message, log it on your side from the exception you already caught.

## Span attributes

`trustweave.operation`, `trustweave.outcome`, `trustweave.reason`, `trustweave.request_id`, plus
operation attributes: `did.method`, `kms.algorithm`. All are low-cardinality by construction.

A library span is emitted at the end of the operation, back-dated by the measured duration — the
library already timed it, and re-timing at the bridge would double-count.

## What this does not do

- It does not install a logging backend, an exporter or a global OpenTelemetry instance. The host
  owns all three.
- It does not record arguments, subjects, key material or credential contents.
- It does not replace the host request surface in [host/README.md](host/README.md); it sits under it.
- Wallet operations are declared in the enum but not yet wired to call sites.
