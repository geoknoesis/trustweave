---
title: SIOPv2 Plugin
redirect_from:
  - /features/credential-exchange-protocols/siopv2/
parent: Feature Reference
grand_parent: API Reference
---

# SIOPv2 Plugin

Self-Issued OpenID Provider v2 (SIOPv2) implementation for TrustWeave.

## Overview

SIOPv2 is the OpenID specification that lets a wallet act as its own OpenID Provider. Instead of a centralized IdP, the wallet self-issues an `id_token` whose `iss` and `sub` are the holder's DID and which is signed by a key the wallet controls. Verifiers may also request a Verifiable Presentation (`vp_token`) alongside or instead of the `id_token` using OpenID for Verifiable Presentations (OID4VP) constructs such as `presentation_definition` and `presentation_submission`.

This plugin exposes SIOPv2 both as a standalone service (`SiopV2Service`) and through TrustWeave's unified `CredentialExchangeProtocol` SPI so that a wallet or verifier can drive it via the protocol-agnostic `ExchangeService`.

## Features

- Authorization request creation (verifier side)
- Authorization request parsing from URL or `request_uri` reference (wallet side)
- Authorization response building with wallet-issued `id_token` and/or `vp_token`
- Direct-post response submission to the verifier's `response_uri`
- In-memory session tracking by `sessionId`
- Cross-device (QR / `request_uri`) and same-device (deep link) flows
- SPI auto-discovery via `ServiceLoader` under the name `siop-v2`
- Integration with TrustWeave's `ExchangeService` for `requestProof` / `presentProof`

## Architecture

```
┌─────────────────────────────────────────────┐
│  SiopV2ExchangeProtocol                     │
│  (Implements CredentialExchangeProtocol)    │
└─────────────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────┐
│      SiopV2Service                          │
│  - createAuthorizationRequest()             │
│  - parseAuthorizationRequest()              │
│  - getSession()                             │
│  - buildAuthorizationResponse()             │
│  - submitResponse()                         │
└─────────────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────┐
│  KeyManagementService  +  OkHttpClient      │
│  (JWT signing)            (HTTP transport)  │
└─────────────────────────────────────────────┘
```

## Usage

### Basic Setup

```kotlin
import okhttp3.OkHttpClient
import org.trustweave.credential.exchange.ExchangeServices
import org.trustweave.credential.exchange.registry.ExchangeProtocolRegistries
import org.trustweave.credential.siop.SiopV2Config
import org.trustweave.credential.siop.SiopV2Service
import org.trustweave.credential.siop.exchange.SiopV2ExchangeProtocol

val kms = // Your KeyManagementService instance
val credentialService = // Your CredentialService instance
val didResolver = // Your DidResolver instance
val httpClient = OkHttpClient()

val siopV2Service = SiopV2Service(
    kms = kms,
    config = SiopV2Config(),
    httpClient = httpClient,
)

val protocol = SiopV2ExchangeProtocol(siopV2Service)

val registry = ExchangeProtocolRegistries.default()
registry.register(protocol)

val exchangeService = ExchangeServices.createExchangeService(
    protocolRegistry = registry,
    credentialService = credentialService,
    didResolver = didResolver,
)
```

Alternatively, the plugin auto-registers via `ServiceLoader`. The provider name is `"siop-v2"`, and it expects a `KeyManagementService` under option key `"kms"` (and an optional `OkHttpClient` under `"httpClient"`):

```kotlin
import org.trustweave.credential.exchange.ExchangeServices

val exchangeService = ExchangeServices.createExchangeServiceWithAutoDiscovery(
    credentialService = credentialService,
    didResolver = didResolver,
    options = mapOf("kms" to kms),
)
```

### Verifier: Create an Authorization Request

```kotlin
import kotlinx.coroutines.runBlocking
import org.trustweave.credential.siop.SiopV2Config
import org.trustweave.credential.siop.SiopV2Service
import org.trustweave.testkit.kms.InMemoryKeyManagementService

fun main() = runBlocking {
    val kms = InMemoryKeyManagementService()
    val service = SiopV2Service(kms = kms, config = SiopV2Config())

    val session = service.createAuthorizationRequest(
        clientId = "did:key:z6Mkw...verifier",
        responseUri = "https://verifier.example.com/siop/response",
        responseType = "vp_token id_token",
        // presentationDefinition = pexDefinition,  // optional
    )

    // Persist session.sessionId for later correlation; render the request
    // as a deep link (same-device) or QR code (cross-device).
    println("sessionId = ${session.sessionId}")
    println("nonce     = ${session.request.nonce}")
    println("state     = ${session.request.state}")
}
```

`createAuthorizationRequest` returns a `SiopV2Session` that bundles a generated `sessionId` with the underlying `SiopV2AuthorizationRequest`. The session is also stored in the service's in-memory map so the wallet-side code can look it up later via `getSession(sessionId)`.

### Wallet: Parse a Request and Submit a Response

```kotlin
import kotlinx.coroutines.runBlocking
import org.trustweave.credential.siop.SiopV2Service
import org.trustweave.kms.Algorithm
import org.trustweave.kms.results.GenerateKeyResult
import org.trustweave.testkit.kms.InMemoryKeyManagementService

fun main() = runBlocking {
    val kms = InMemoryKeyManagementService()
    val service = SiopV2Service(kms = kms)

    val keyHandle = when (val r = kms.generateKey(Algorithm.Ed25519)) {
        is GenerateKeyResult.Success -> r.keyHandle
        else -> error("Key generation failed: $r")
    }
    val holderDid = "did:key:z6Mkp...holder"

    // Same-device deep link (or scanned from a cross-device QR that carries
    // request_uri=...; parseAuthorizationRequest follows either form).
    val authorizationUrl =
        "openid-vc://?response_type=id_token" +
            "&client_id=did:key:z6Mkw...verifier" +
            "&client_id_scheme=did" +
            "&response_uri=https://verifier.example.com/siop/response" +
            "&nonce=abc123"

    val session = service.parseAuthorizationRequest(authorizationUrl)

    val response = service.buildAuthorizationResponse(
        session = session,
        holderDid = holderDid,
        keyId = keyHandle.id.value,
        // presentation = vp,                      // required if responseType contains vp_token
        // presentationSubmission = submission,
    )

    service.submitResponse(session, response)
}
```

The wallet-issued `id_token` is a compact JWT whose payload carries `iss = sub = holderDid`, `aud = clientId`, `iat`, `exp` (10 minutes), and the original `nonce`. When `response_type` contains `vp_token` and a `VerifiablePresentation` is supplied, `vp_token` is a JWT-VP wrapping the presentation under the standard `vp` claim.

### Using the Unified ExchangeService

`SiopV2ExchangeProtocol` exposes `REQUEST_PROOF` and `PRESENT_PROOF` through the protocol-agnostic API. Issuance operations (`offer`/`request`/`issue`) are intentionally not supported and will surface as `ExchangeResult.Failure` from the service.

```kotlin
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import org.trustweave.credential.exchange.options.ExchangeOptions
import org.trustweave.credential.exchange.request.ProofExchangeRequest
import org.trustweave.credential.exchange.request.ProofRequest
import org.trustweave.credential.exchange.result.ExchangeResult
import org.trustweave.credential.identifiers.RequestId
import org.trustweave.credential.identifiers.requireExchangeProtocolName
import org.trustweave.did.identifiers.Did

fun main() = runBlocking {
    // Verifier: ask for a proof.
    val requestResult = exchangeService.requestProof(
        ProofExchangeRequest.Request(
            protocolName = "siop-v2".requireExchangeProtocolName(),
            verifierDid = Did("did:key:z6Mkw...verifier"),
            proverDid = Did("did:key:z6Mkp...holder"),
            proofRequest = ProofRequest(
                name = "login",
                requestedAttributes = emptyMap(),
            ),
            options = ExchangeOptions.builder()
                .addMetadata("responseUri", JsonPrimitive("https://verifier.example.com/siop/response"))
                .addMetadata("clientId", JsonPrimitive("did:key:z6Mkw...verifier"))
                .build(),
        ),
    )

    val response = when (requestResult) {
        is ExchangeResult.Success -> requestResult.value
        is ExchangeResult.Failure -> error("requestProof failed: ${requestResult.errors}")
    }

    // sessionId is round-tripped through messageEnvelope.metadata; the wallet
    // side uses it as the requestId on the presentation step. (response.requestId
    // is a separate UUID minted by ExchangeService and is NOT the SIOPv2 sessionId.)
    val sessionId = (response.messageEnvelope.metadata["sessionId"] as JsonPrimitive).content
}
```

```kotlin
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import org.trustweave.core.identifiers.Iri
import org.trustweave.credential.exchange.options.ExchangeOptions
import org.trustweave.credential.exchange.request.ProofExchangeRequest
import org.trustweave.credential.exchange.result.ExchangeResult
import org.trustweave.credential.identifiers.RequestId
import org.trustweave.credential.identifiers.requireExchangeProtocolName
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.VerifiablePresentation
import org.trustweave.did.identifiers.Did

fun main() = runBlocking {
    // Wallet: present the proof.
    val presentation = VerifiablePresentation(
        type = listOf(CredentialType.fromString("VerifiablePresentation")),
        holder = Iri("did:key:z6Mkp...holder"),  // holder is Iri, not Did
        verifiableCredential = listOf(/* credentials */),
    )

    val presentResult = exchangeService.presentProof(
        ProofExchangeRequest.Presentation(
            protocolName = "siop-v2".requireExchangeProtocolName(),
            proverDid = Did("did:key:z6Mkp...holder"),
            verifierDid = Did("did:key:z6Mkw...verifier"),
            presentation = presentation,
            requestId = RequestId(sessionId),
            options = ExchangeOptions.builder()
                .addMetadata("keyId", JsonPrimitive("holder-key-id"))
                .build(),
        ),
    )

    when (presentResult) {
        is ExchangeResult.Success -> println("Presented: ${presentResult.value}")
        is ExchangeResult.Failure -> error("presentProof failed: ${presentResult.errors}")
    }
}
```

### Inspecting Capabilities

```kotlin
val caps = protocol.capabilities
println("Supported operations: ${caps.supportedOperations}")  // [REQUEST_PROOF, PRESENT_PROOF]
println("Selective disclosure: ${caps.supportsSelectiveDisclosure}")
println("Requires TLS:         ${caps.requiresTransportSecurity}")
```

## Error Handling

`SiopV2Service` raises a single exception type, `SiopV2Exception`, with the following codes:

| Code | Source | Meaning |
|---|---|---|
| `FETCH_FAILED` | `parseAuthorizationRequest` | The `request_uri` returned an empty body. |
| `NO_RESPONSE_URI` | `submitResponse` | The parsed request had no `response_uri`. |
| `SUBMISSION_FAILED` | `submitResponse` | The verifier returned a non-2xx HTTP response. |
| `SIGN_FAILED` | `buildAuthorizationResponse` | The KMS signing call failed (key not found, unsupported algorithm, or generic error). |

```kotlin
import org.trustweave.credential.siop.SiopV2Exception

try {
    service.submitResponse(session, response)
} catch (e: SiopV2Exception) {
    when (e.code) {
        "NO_RESPONSE_URI" -> println("Verifier request had no response_uri")
        "SUBMISSION_FAILED" -> println("Verifier rejected the response: ${e.message}")
        "SIGN_FAILED" -> println("Could not sign the token: ${e.message}")
        else -> throw e
    }
}
```

When you drive SIOPv2 through `ExchangeService`, failures surface as `ExchangeResult.Failure` instead. The sealed variants are `ProtocolNotSupported`, `OperationNotSupported`, `InvalidRequest`, `MessageNotFound`, `NetworkError`, and `Unknown`:

```kotlin
import org.trustweave.credential.exchange.result.ExchangeResult

when (val r = exchangeService.requestProof(req)) {
    is ExchangeResult.Success -> handle(r.value)
    is ExchangeResult.Failure.ProtocolNotSupported ->
        println("siop-v2 not registered; available: ${r.availableProtocols}")
    is ExchangeResult.Failure.OperationNotSupported ->
        println("Operation ${r.operation} not supported by ${r.protocolName.value}")
    is ExchangeResult.Failure.InvalidRequest ->
        println("Invalid ${r.field}: ${r.reason}")
    is ExchangeResult.Failure.MessageNotFound ->
        println("No SIOPv2 session for ${r.messageId}")
    is ExchangeResult.Failure.NetworkError ->
        println("Transport failed: ${r.reason}")
    is ExchangeResult.Failure.Unknown ->
        println("Unexpected error: ${r.reason}")
}
```

## Limitations

The current `SiopV2Service` is intentionally minimal and ships with the following known gaps:

- **No inbound verification.** The service signs and submits responses, but the verifier-side JWT signature check, DID resolution, presentation-submission matching, and replay protection (`nonce`/`state` correlation) are left to the consuming application.
- **In-memory session store.** Sessions are kept in a `ConcurrentHashMap` inside `SiopV2Service` and are lost when the process restarts. Production deployments should plug in a persistent store.
- **EdDSA-only signing.** The JWT header is hardcoded to `alg=EdDSA`; the algorithm lists in `SiopV2Config` are advertised metadata only.
- **No JAR / signed Request Object validation.** Authorization request objects fetched from `request_uri` are parsed as plaintext JSON; JWS-wrapped JAR requests are not validated.
- **No encrypted responses.** `direct_post.jwt` is declared in the `SiopResponseMode` enum but only plain `direct_post` JSON is sent by `submitResponse`.
- **Presentation Exchange is pass-through.** A supplied `presentation_definition` is carried through verbatim; matching candidate credentials against the definition is the caller's responsibility (see the `presentation-exchange` plugin).

## Configuration

`SiopV2Config` controls service defaults:

| Field | Default | Purpose |
|---|---|---|
| `defaultClientIdScheme` | `SiopClientIdScheme.DID` | Value stamped into `client_id_scheme` on outbound authorization requests. Other options: `PRE_REGISTERED`, `REDIRECT_URI`, `ENTITY_ID`. |
| `supportedResponseModes` | `[DIRECT_POST]` | Response modes the verifier advertises. Also defined: `DIRECT_POST_JWT`, `FRAGMENT`, `FORM_POST`. |
| `idTokenSigningAlgorithms` | `["EdDSA", "ES256"]` | Advertised `id_token` signing algorithms. |
| `vpTokenSigningAlgorithms` | `["EdDSA", "ES256"]` | Advertised `vp_token` signing algorithms. |
| `requestUriTimeoutSeconds` | `300` | Lifetime of a `request_uri`-referenced request object. |

## References

- [Self-Issued OpenID Provider v2 (SIOPv2)](https://openid.net/specs/openid-connect-self-issued-v2-1_0.html)
- [OpenID for Verifiable Presentations (OID4VP)](https://openid.net/specs/openid-4-verifiable-presentations-1_0.html)
- [Presentation Exchange 2.0](https://identity.foundation/presentation-exchange/spec/v2.0.0/)
- [W3C Verifiable Credentials Data Model](https://www.w3.org/TR/vc-data-model/)
