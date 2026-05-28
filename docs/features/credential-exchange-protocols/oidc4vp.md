---
title: OIDC4VP Plugin
---

# OIDC4VP Plugin

OpenID for Verifiable Presentations (OIDC4VP) implementation for TrustWeave.

## Overview

OIDC4VP (OpenID for Verifiable Presentations, OID4VP) is an OAuth 2.0 / OpenID
Connect extension that lets a verifier request one or more Verifiable Presentations
from a wallet. The verifier sends an authorization request (typically as an
`openid4vp://` URL or QR code) and the wallet responds with a signed `vp_token`,
optionally accompanied by a DIF Presentation Exchange `presentation_submission`.

When paired with [SIOPv2](./siopv2.md), the same authorization request can yield
both an `id_token` (self-issued identity proof) and a `vp_token` (credential
presentation) in a single round-trip.

## Features

- Parse `openid4vp://` authorization URLs (direct params and `request_uri` fetch)
- Build and submit a `vp_token` via `direct_post` and `direct_post.jwt`
- DIF Presentation Exchange v2 integration (`presentation_definition`)
- DCQL query support (`dcql_query` parameter pass-through)
- High Assurance Interoperability Profile (HAIP) validation via
  `HaipProfileValidator`
- Multiple `client_id_scheme` values: `pre-registered`, `redirect_uri`,
  `entity_id`, `did`, `x509_san_dns`, `x509_san_uri`, `verifier_attestation`
- Verifier metadata discovery at `/.well-known/openid-credential-verifier`
- Integration with the protocol-agnostic `ExchangeService` via the
  `CredentialExchangeProtocol` SPI (auto-registered)

## Architecture

```
+-------------------------------------------+
|  Oidc4VpExchangeProtocol                  |
|  (Implements CredentialExchangeProtocol)  |
|  - requestProof()                         |
|  - presentProof()                         |
+-------------------------------------------+
                  |
                  v
+-------------------------------------------+
|  Oidc4VpService                           |
|  - parseAuthorizationUrl()                |
|  - createPermissionResponse()             |
|  - submitPermissionResponse()             |
|  - fetchVerifierMetadata()                |
+-------------------------------------------+
                  |
       +----------+----------+
       v                     v
+--------------+    +--------------------+
| HAIP         |    | Presentation       |
| Profile      |    | Exchange plugin    |
| Validator    |    | (definition match) |
+--------------+    +--------------------+
```

## Usage

### Basic Setup

```kotlin
import org.trustweave.credential.oidc4vp.Oidc4VpService
import org.trustweave.credential.oidc4vp.exchange.Oidc4VpExchangeProtocol
import org.trustweave.credential.exchange.ExchangeServices
import org.trustweave.credential.exchange.registry.ExchangeProtocolRegistries
import okhttp3.OkHttpClient

val kms = // your KeyManagementService
val credentialService = // your CredentialService
val didResolver = // your DidResolver
val httpClient = OkHttpClient()

val oidc4vpService = Oidc4VpService(
    kms = kms,
    httpClient = httpClient,
    haipMode = false, // set true to enforce HAIP on every parsed request
)

val protocol = Oidc4VpExchangeProtocol(oidc4vpService)

val registry = ExchangeProtocolRegistries.default()
registry.register(protocol)

val exchangeService = ExchangeServices.createExchangeService(
    protocolRegistry = registry,
    credentialService = credentialService,
    didResolver = didResolver,
)
```

The plugin is also discovered automatically via the Java ServiceLoader file
`META-INF/services/org.trustweave.credential.exchange.spi.CredentialExchangeProtocolProvider`,
which points at `org.trustweave.credential.oidc4vp.exchange.spi.Oidc4VpExchangeProtocolProvider`.
Auto-discovery is opt-in through:

```kotlin
val exchangeService = ExchangeServices.createExchangeServiceWithAutoDiscovery(
    credentialService = credentialService,
    didResolver = didResolver,
    options = mapOf("kms" to kms, "httpClient" to httpClient),
)
```

### Verifier Side: Building an Authorization Request

The plugin is wallet/holder-centric, so the verifier publishes the authorization
request as a JSON document served from `request_uri` plus an `openid4vp://` deep
link pointing at it. The payload embeds a DIF Presentation Exchange
`presentation_definition`:

```kotlin
import org.trustweave.credential.pex.PresentationDefinition
import org.trustweave.credential.pex.InputDescriptor
import org.trustweave.credential.pex.Constraints
import org.trustweave.credential.pex.Field
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

val definition = PresentationDefinition(
    id = "employee-verification",
    inputDescriptors = listOf(
        InputDescriptor(
            id = "employee_credential",
            constraints = Constraints(
                fields = listOf(
                    Field(path = listOf("$.type")),
                    Field(path = listOf("$.credentialSubject.employeeId")),
                ),
            ),
        ),
    ),
)

val authorizationRequest = buildJsonObject {
    put("client_id", "https://verifier.example.com")
    put("client_id_scheme", "x509_san_dns")
    put("response_mode", "direct_post")
    put("response_uri", "https://verifier.example.com/oidc4vp/response")
    put("nonce", java.util.UUID.randomUUID().toString())
    put("state", java.util.UUID.randomUUID().toString())
    put("presentation_definition", Json.encodeToJsonElement(
        PresentationDefinition.serializer(), definition,
    ))
}
// Serve from your request_uri endpoint and share:
// openid4vp://authorize?client_id=...&request_uri=https://verifier.example.com/req/abc
```

See the [Presentation Exchange plugin README](../../../credentials/plugins/presentation-exchange)
for the full `PresentationDefinition` API.

### Holder Side: Parse, Respond, Submit

The wallet drives the flow through the unified `ExchangeService` API or directly
against `Oidc4VpService` for finer control. The high-level path uses
`ProofExchangeRequest.Request` / `ProofExchangeRequest.Presentation`:

```kotlin
import org.trustweave.credential.exchange.request.ProofExchangeRequest
import org.trustweave.credential.exchange.request.ProofRequest
import org.trustweave.credential.exchange.options.ExchangeOptions
import org.trustweave.credential.exchange.result.ExchangeResult
import org.trustweave.credential.identifiers.ExchangeProtocolName
import org.trustweave.credential.identifiers.RequestId
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.VerifiablePresentation
import org.trustweave.core.identifiers.Iri
import org.trustweave.did.identifiers.Did
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

fun main() = runBlocking {
    val verifierDid = Did("did:web:verifier.example.com")
    val holderDid = Did("did:key:zHolder...")

    // 1) Parse the verifier's authorization URL (e.g., from a scanned QR code).
    val requestResult = exchangeService.requestProof(
        ProofExchangeRequest.Request(
            protocolName = ExchangeProtocolName.Oidc4Vp,
            verifierDid = verifierDid,
            proverDid = holderDid,
            proofRequest = ProofRequest(
                name = "oid4vp",
                requestedAttributes = emptyMap(), // PE definition carries the details
            ),
            options = ExchangeOptions(
                metadata = mapOf(
                    "authorizationUrl" to JsonPrimitive(
                        "openid4vp://authorize?client_id=...&request_uri=https://verifier.example.com/req/abc",
                    ),
                ),
            ),
        ),
    )

    // The OID4VP plugin tracks its own session by the `requestId` it minted in
    // parseAuthorizationUrl(); it is round-tripped through messageEnvelope.metadata.
    // (requestResult.value.requestId is a separate UUID minted by ExchangeService
    // and is NOT the OID4VP session id.)
    val response = when (requestResult) {
        is ExchangeResult.Success -> requestResult.value
        is ExchangeResult.Failure -> error("Proof request failed: ${requestResult.errors}")
    }
    val requestId = RequestId(
        (response.messageEnvelope.metadata["requestId"] as JsonPrimitive).content,
    )

    // 2) Build a VP locally and submit it back through the same protocol.
    val vp = VerifiablePresentation(
        type = listOf(CredentialType.Custom("VerifiablePresentation")),
        holder = Iri(holderDid.value),
        verifiableCredential = listOf(employeeCredential),
    )

    val presentResult = exchangeService.presentProof(
        ProofExchangeRequest.Presentation(
            protocolName = ExchangeProtocolName.Oidc4Vp,
            proverDid = holderDid,
            verifierDid = verifierDid,
            presentation = vp,
            requestId = requestId,
            options = ExchangeOptions(
                metadata = mapOf(
                    "keyId" to JsonPrimitive("did:key:zHolder...#key-1"),
                    "selectedCredentials" to JsonArray(
                        listOf(JsonPrimitive("employee_credential")),
                    ),
                    "selectedFields" to buildJsonObject {
                        put("employee_credential", JsonArray(
                            listOf(JsonPrimitive("employeeId")),
                        ))
                    },
                ),
            ),
        ),
    )

    when (presentResult) {
        is ExchangeResult.Success -> println("vp_token submitted, response id = ${presentResult.value.presentationId}")
        is ExchangeResult.Failure -> error("Presentation failed: ${presentResult.errors}")
    }
}
```

Direct calls against `Oidc4VpService` give access to the intermediate
`PermissionRequest` / `PermissionResponse` (e.g., to prompt the user before
submitting):

```kotlin
import org.trustweave.credential.oidc4vp.models.PresentableCredential
import kotlinx.coroutines.runBlocking

runBlocking {
    val permissionRequest = oidc4vpService.parseAuthorizationUrl(authorizationUrl)
    val selected = listOf(
        PresentableCredential(
            credentialId = "employee_credential",
            credential = employeeCredential,
            credentialType = "EmployeeCredential",
        ),
    )
    val response = oidc4vpService.createPermissionResponse(
        permissionRequest = permissionRequest,
        selectedCredentials = selected,
        selectedFields = listOf(listOf("employeeId")),
        holderDid = "did:key:zHolder...",
        keyId = "did:key:zHolder...#key-1",
    )
    oidc4vpService.submitPermissionResponse(response)
}
```

### Presentation Exchange Integration

The `presentation_definition` carried in the OID4VP request is a plain
DIF Presentation Exchange v2 document. Match it against the holder's wallet with
`PresentationDefinitionMatcher` from the `presentation-exchange` plugin:

```kotlin
import org.trustweave.credential.pex.PresentationDefinition
import org.trustweave.credential.pex.PresentationDefinitionMatcher
import kotlinx.serialization.json.Json

val definitionJson = permissionRequest.authorizationRequest.presentationDefinition
    ?: error("Verifier did not include a presentation_definition")

val definition = Json.decodeFromJsonElement(
    PresentationDefinition.serializer(), definitionJson,
)
val matches = PresentationDefinitionMatcher.match(definition, walletCredentials)
val submission = PresentationDefinitionMatcher.buildSubmission(definition, matches)
```

The matcher emits a `PresentationSubmission` you can attach to the response
(`PermissionResponse.presentationSubmission`). See the
[Presentation Exchange plugin README](../../../credentials/plugins/presentation-exchange)
for the complete `PresentationDefinition` schema and matcher options.

### HAIP Profile Validation

The High Assurance Interoperability Profile (HAIP) tightens OID4VP to a
high-assurance subset:

- `client_id_scheme` in {`did`, `x509_san_dns`, `verifier_attestation`}
- `response_mode` in {`direct_post`, `direct_post.jwt`} with a `response_uri`
- A non-empty `nonce`
- Either `presentation_definition` or `dcql_query` present
- Credential formats limited to `vc+sd-jwt` and `mso_mdoc`

Enable enforcement on every parsed request by constructing the service with
`haipMode = true`, or run a one-off check:

```kotlin
import org.trustweave.credential.oidc4vp.haip.HaipProfileValidator
import org.trustweave.credential.oidc4vp.exception.Oidc4VpException

val violations = HaipProfileValidator.validateAuthorizationRequest(
    permissionRequest.authorizationRequest,
)
if (violations.isNotEmpty()) {
    throw Oidc4VpException.HaipViolationException(violations)
}

// Format-only check (e.g., when inspecting verifier metadata):
HaipProfileValidator.validateFormat("vc+sd-jwt") // returns null when compliant
```

When `haipMode = true`, `Oidc4VpService.parseAuthorizationUrl()` throws
`Oidc4VpException.HaipViolationException` before any further processing.

### SIOPv2 + OIDC4VP Combined Flow

OID4VP composes with [SIOPv2](./siopv2.md): a single authorization request can
specify `response_type=vp_token id_token` and the wallet returns both tokens in
the same `direct_post`. Register both protocols on the same `ExchangeService`
and dispatch by `protocolName`. See the SIOPv2 doc for the `id_token` issuance
and the combined response shape.

## Error Handling

`Oidc4VpService` throws subclasses of `Oidc4VpException` (which extends
`TrustWeaveException`):

```kotlin
import org.trustweave.credential.oidc4vp.exception.Oidc4VpException
import kotlinx.coroutines.runBlocking

runBlocking {
    try {
        val request = oidc4vpService.parseAuthorizationUrl(authorizationUrl)
        // ...
    } catch (e: Oidc4VpException.UrlParseFailed) {
        println("Bad URL '${e.url}': ${e.reason}")
    } catch (e: Oidc4VpException.AuthorizationRequestFetchFailed) {
        println("Could not fetch request_uri '${e.requestUri}': ${e.reason}")
    } catch (e: Oidc4VpException.MetadataFetchFailed) {
        println("Could not fetch verifier metadata from '${e.verifierUrl}': ${e.reason}")
    } catch (e: Oidc4VpException.HttpRequestFailed) {
        println("HTTP ${e.statusCode} from ${e.url}: ${e.reason}")
    } catch (e: Oidc4VpException.PresentationSubmissionFailed) {
        println("Submission to ${e.verifierUrl} failed: ${e.reason}")
    } catch (e: Oidc4VpException.HaipViolationException) {
        e.violations.forEach { println("HAIP violation on ${it.field}: ${it.message}") }
    }
}
```

When going through the unified `ExchangeService`, results come back as
`ExchangeResult<T>` and you pattern-match on `Failure` variants:

```kotlin
import org.trustweave.credential.exchange.result.ExchangeResult

when (val result = exchangeService.requestProof(req)) {
    is ExchangeResult.Success -> handle(result.value)
    is ExchangeResult.Failure.ProtocolNotSupported ->
        println("oidc4vp not registered; available: ${result.availableProtocols}")
    is ExchangeResult.Failure.OperationNotSupported ->
        println("${result.protocolName} does not support ${result.operation}")
    is ExchangeResult.Failure.InvalidRequest ->
        println("Invalid '${result.field}': ${result.reason}")
    is ExchangeResult.Failure.MessageNotFound ->
        println("Missing message id=${result.messageId}")
    is ExchangeResult.Failure.NetworkError ->
        println("Network error: ${result.reason}")
    is ExchangeResult.Failure.Unknown ->
        println("Unknown error: ${result.reason}")
}
```

## Limitations

- Wallet/holder-centric: verifier-side authorization endpoint, request signing,
  and JAR (JWT-Secured Authorization Request) packaging must be implemented by
  the verifier service.
- `vp_token` generation uses a simplified embedded-JWT layout signed with the
  KMS-resolved holder key. For full W3C VC 1.1/2.0 VP serialization and SD-JWT
  selective disclosure, build the `VerifiablePresentation` yourself (e.g., via
  the SD-JWT plugin) and pass it into `createPermissionResponse`.
- `dcql_query` is parsed and forwarded verbatim; no built-in DCQL evaluator is
  bundled.
- `Oidc4VpExchangeProtocol.capabilities.supportedOperations` is
  `{REQUEST_PROOF, PRESENT_PROOF}` only — `OFFER_CREDENTIAL`,
  `REQUEST_CREDENTIAL`, and `ISSUE_CREDENTIAL` throw `InvalidOperation`. Use the
  OID4VCI plugin for issuance.
- `capabilities.supportsAsync` is `false`; long-running async response flows must
  be coordinated externally.

## References

- [OpenID for Verifiable Presentations 1.0](https://openid.net/specs/openid-4-verifiable-presentations-1_0.html)
- [DIF Presentation Exchange v2.0](https://identity.foundation/presentation-exchange/spec/v2.0.0/)
- [OpenID4VC High Assurance Interoperability Profile (HAIP)](https://openid.net/specs/openid4vc-haip.html)
- [SIOPv2 plugin](./siopv2.md)
- [OIDC4VCI plugin](./oidc4vci.md)
- [Presentation Exchange plugin README](../../../credentials/plugins/presentation-exchange)
