---
title: Features Usage Guide
redirect_from:
  - /features/USAGE_GUIDE/
parent: Feature Reference
grand_parent: API Reference
---

# Features Usage Guide

Where the application-level features around credential issuance and exchange live, and which of
them TrustWeave actually ships.

Earlier versions of this page showed code for packages such as `org.trustweave.audit`,
`org.trustweave.metrics` or `org.trustweave.notifications`. **Those packages do not exist**, and
the examples did not compile. They have been removed; the table below says what to use instead.

## Shipped features

| Feature | Gradle module | Entry point | Guide |
| ------- | ------------- | ----------- | ----- |
| OIDC4VCI issuance | `credentials:plugins:oidc4vci` | `org.trustweave.credential.oidc4vci.Oidc4VciService` | [OIDC4VCI](credential-exchange-protocols/oidc4vci.md) |
| Credential-offer URLs for QR codes | `credentials:plugins:oidc4vci` | `org.trustweave.credential.oidc4vci.qr.QrCodeGenerator` | [below](#qr--deep-link-content-oidc4vci) |
| OIDC4VP presentation | `credentials:plugins:oidc4vp` | see guide | [OIDC4VP](credential-exchange-protocols/oidc4vp.md) |
| DIDComm v2 messaging | `credentials:plugins:didcomm` | `org.trustweave.credential.didcomm.DidCommService`, `DidCommFactory` | [DIDComm quick start](credential-exchange-protocols/didcomm-quick-start.md) |
| CHAPI (browser wallets) | `credentials:plugins:chapi` | `org.trustweave.credential.chapi.ChapiService` | [CHAPI](credential-exchange-protocols/chapi.md) |
| Revocation / status lists | `credentials:plugins:status-list:*` | `trustWeave.revoke { }`, `trustWeave.revocation { }` | [Revoke credentials](../../how-to/revoke-credentials.md) |
| Library telemetry (OpenTelemetry) | `observability` | `org.trustweave.observability.LibraryTelemetry` | [Library telemetry](../../operations/library-telemetry.md) |
| Host metrics, health and authentication for the bundled servers | `observability` | `org.trustweave.observability.HostObservability`, `HostAuthentication` | [Host operations](../../operations/host/README.md) |

The DIDComm plugin embeds an outdated JOSE library; read
[SECURITY.md, Known Dependency Risks](https://github.com/geoknoesis/trustweave/blob/main/SECURITY.md#known-dependency-risks)
before exposing it to untrusted messages.

## Not shipped: implement in your application

TrustWeave has no API for the following. Build them on your own infrastructure, typically around the
results of `trustWeave.issue { }`, `trustWeave.verify(...)` and the wallet APIs:

| Feature | Suggested approach |
| ------- | ------------------ |
| Audit logging | Record issuance, verification and revocation results in your audit store. |
| Metrics | Use `LibraryTelemetry` (OpenTelemetry) for TrustWeave's own spans and metrics; add your own counters around calls. |
| Notifications | Send from your application when an issuance, revocation or expiry event occurs. |
| Credential versioning, backup and recovery | Store issued credentials in your database or a wallet storage plugin (`wallet:plugins:*`) and use its backup tooling. |
| Expiration management | Query stored credentials by `validUntil`/`expirationDate` and re-issue or notify. |
| Analytics and reporting | Aggregate your own audit records. |
| Multi-party issuance | Orchestrate approvals before calling `trustWeave.issue { }`; the credential is signed once by the issuing DID. |
| Health checks | For the bundled servers use `HostObservability`; for your application, expose health from your framework. |
| Credential rendering | Render `VerifiableCredential` fields in your UI; no renderer is provided. |

## QR / deep-link content (OIDC4VCI)

TrustWeave does not ship a QR image API. The **OIDC4VCI plugin** builds **credential-offer URLs** you
can encode with any QR library (ZXing, etc.):

```kotlin
import org.trustweave.credential.oidc4vci.qr.QrCodeGenerator

// URL string to turn into a QR image in your app
val offerUrl = QrCodeGenerator.generateCredentialOfferUrl(
    credentialIssuer = "https://issuer.example.com",
    credentialConfigurationIds = listOf("PersonCredential")
)
// Encode `offerUrl` with ZXing or another QR library for PNG/SVG bytes
```

## Integration example

Issue a credential and hand the holder an OIDC4VCI offer URL, with your own audit and metrics hooks
around the call:

```kotlin
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.oidc4vci.qr.QrCodeGenerator
import org.trustweave.credential.results.getOrThrow
import org.trustweave.did.identifiers.Did
import org.trustweave.trust.TrustWeave

// Your application's own audit and metrics hooks.
fun interface AuditSink { fun record(action: String, target: String) }
fun interface Counter { fun increment(name: String) }

suspend fun issueCredentialWithTracking(
    trustWeave: TrustWeave,
    issuerDid: Did,
    issuerKeyId: String,
    subjectDid: String,
    audit: AuditSink,
    metrics: Counter,
): Pair<VerifiableCredential, String> {
    val credential = trustWeave.issue {
        credential {
            type("PersonCredential")
            issuer(issuerDid)
            subject {
                id(subjectDid)
                "name" to "Alice"
            }
        }
        signedBy(issuerDid = issuerDid, keyId = issuerKeyId)
    }.getOrThrow()

    metrics.increment("credentials.issued")
    audit.record("ISSUE_CREDENTIAL", credential.id?.toString() ?: "unknown")

    val offerUrl = QrCodeGenerator.generateCredentialOfferUrl(
        credentialIssuer = "https://issuer.example.com",
        credentialConfigurationIds = listOf("PersonCredential"),
    )
    return credential to offerUrl
}
```
