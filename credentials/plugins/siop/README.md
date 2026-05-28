# TrustWeave SIOPv2 Plugin

Self-Issued OpenID Provider v2 (SIOPv2) implementation for TrustWeave, providing DID-based authentication and verifiable presentation exchange built on top of the OpenID Connect protocol family.

## Overview

This module implements the [`CredentialExchangeProtocol`](../../credential-api/src/main/kotlin/org/trustweave/credential/exchange/CredentialExchangeProtocol.kt) SPI for SIOPv2, the OpenID specification that lets a wallet act as its own OpenID Provider. Instead of a centralized IdP, the wallet self-issues an `id_token` whose `iss` and `sub` are the holder's DID and which is signed by a key the wallet controls. Verifiers can also request Verifiable Presentations (`vp_token`) alongside or instead of the `id_token` using OpenID for Verifiable Presentations (OID4VP) constructs such as `presentation_definition` and `presentation_submission`.

The module ships:

- [`SiopV2Service`](src/main/kotlin/org/trustweave/credential/siop/SiopV2Service.kt) — low-level service that builds, parses, signs, and submits SIOPv2 authorization requests and responses.
- [`SiopV2ExchangeProtocol`](src/main/kotlin/org/trustweave/credential/siop/exchange/SiopV2ExchangeProtocol.kt) — adapter that exposes the service through the generic `CredentialExchangeProtocol` SPI.
- [`SiopV2ExchangeProtocolProvider`](src/main/kotlin/org/trustweave/credential/siop/exchange/spi/SiopV2ExchangeProtocolProvider.kt) — `ServiceLoader`-discovered factory.
- Wire models in [`SiopV2Models.kt`](src/main/kotlin/org/trustweave/credential/siop/models/SiopV2Models.kt) and configuration in [`SiopV2Config.kt`](src/main/kotlin/org/trustweave/credential/siop/SiopV2Config.kt).

## Key concepts

**Cross-device vs same-device flows.** SIOPv2 supports both. In the *cross-device* flow the verifier renders the authorization request as a QR code containing a `request_uri`; the wallet on another device scans it and fetches the full request via HTTP. In the *same-device* flow the request is delivered as a deep link with the parameters in the URL query string. `SiopV2Service.parseAuthorizationRequest` handles both: if a `request_uri` query parameter is present it fetches the JSON request object, otherwise it decodes the parameters in-place.

**Request object and `request_uri`.** A SIOPv2 request can be transmitted inline or by reference. The `request_uri` form is preferred for cross-device flows because QR codes have a size budget. Either way the result is a [`SiopV2AuthorizationRequest`](src/main/kotlin/org/trustweave/credential/siop/models/SiopV2Models.kt) with fields such as `response_type` (`id_token`, `vp_token`, or both), `client_id`, `nonce`, optional `state`, optional `presentation_definition`, and a `response_uri` to which the wallet posts the response.

**Wallet-issued `id_token`.** The wallet, not a centralized provider, issues the `id_token`. The JWT has `iss == sub == <holder DID>`, `aud == <verifier client_id>`, copies the `nonce` from the request, and is signed with a holder-controlled key via [`KeyManagementService`](../../../kms/kms-core/src/main/kotlin/org/trustweave/kms/KeyManagementService.kt). When the verifier requested a `vp_token`, the wallet also produces a JWT-VP carrying the selected presentation. Both tokens are returned to the verifier through `response_mode=direct_post` (HTTP POST to `response_uri`).

## Usage

### Gradle dependency

```kotlin
dependencies {
    implementation("org.trustweave.credentials:credentials-plugins-siop:0.6.0")
}
```

### Verifier: create an authorization request

```kotlin
import kotlinx.coroutines.runBlocking
import org.trustweave.credential.siop.SiopV2Config
import org.trustweave.credential.siop.SiopV2Service
import org.trustweave.kms.Algorithm
import org.trustweave.kms.results.GenerateKeyResult
import org.trustweave.testkit.kms.InMemoryKeyManagementService

fun main() = runBlocking {
    val kms = InMemoryKeyManagementService()
    val service = SiopV2Service(kms = kms, config = SiopV2Config())

    val session = service.createAuthorizationRequest(
        clientId = "did:key:z6Mkw...verifier",
        responseUri = "https://verifier.example.com/siop/response",
        responseType = "vp_token id_token",
    )

    // Persist session.sessionId; render the request as a deep link or QR code.
    println("sessionId = ${session.sessionId}")
    println("nonce     = ${session.request.nonce}")
    println("state     = ${session.request.state}")
}
```

### Wallet: parse a request and submit a response

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

    // Deep link delivered to the wallet (same-device) or scanned from a QR
    // (cross-device, in which case the URL carries a request_uri).
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
        // presentation = vp,                    // required when responseType contains vp_token
        // presentationSubmission = submission,
    )

    service.submitResponse(session, response)
}
```

The wallet-issued `id_token` is a 3-part JWT (`EdDSA` by default) whose payload carries `iss = sub = holderDid`, `aud = clientId`, `iat`, `exp` (10 minutes), and the original `nonce`. When `response_type` contains `vp_token` and a `VerifiablePresentation` is supplied, `vp_token` is a JWT-VP wrapping the presentation under the standard `vp` claim.

### Verifier: receive and verify the response

`SiopV2AuthorizationResponse` is what the verifier receives at its `response_uri`:

```kotlin
import kotlinx.serialization.json.Json
import org.trustweave.credential.siop.models.SiopV2AuthorizationResponse

val json = Json { ignoreUnknownKeys = true }
val response = json.decodeFromString(
    SiopV2AuthorizationResponse.serializer(),
    requestBody,
)

// response.idToken / response.vpToken are compact JWTs.
// Decode the header to discover `kid`, resolve it through your DID resolver,
// then verify the JWT signature and the standard claims (iss, sub, aud, nonce,
// exp). The verifier should also check that the `nonce` and `state` match the
// session it created in `createAuthorizationRequest`.
```

`SiopV2Service` deliberately does **not** verify inbound responses — JWT signature verification, DID resolution, and presentation evaluation are the verifier application's responsibility (typically delegated to the credential-api verification pipeline).

## SPI auto-registration

`SiopV2ExchangeProtocolProvider` is registered through Java `ServiceLoader` via [`META-INF/services/org.trustweave.credential.spi.exchange.CredentialExchangeProtocolProvider`](src/main/resources/META-INF/services/org.trustweave.credential.spi.exchange.CredentialExchangeProtocolProvider). Anything on the classpath that discovers `CredentialExchangeProtocolProvider` implementations will pick it up automatically. The provider exposes a single protocol name, `siop-v2`, and requires a `KeyManagementService` (key `"kms"`) in the options map; an `OkHttpClient` may optionally be passed under `"httpClient"`.

```kotlin
import java.util.ServiceLoader
import org.trustweave.credential.spi.exchange.CredentialExchangeProtocolProvider

val provider = ServiceLoader.load(CredentialExchangeProtocolProvider::class.java)
    .first { it.name == "siop-v2" }

val protocol = provider.create(
    protocolName = "siop-v2",
    options = mapOf("kms" to kms),
) ?: error("SIOPv2 provider declined to create protocol")
```

The resulting [`SiopV2ExchangeProtocol`](src/main/kotlin/org/trustweave/credential/siop/exchange/SiopV2ExchangeProtocol.kt) implements `requestProof` and `presentProof`. Issuance operations (`offer`, `request`, `issue`) throw `TrustWeaveException.InvalidOperation` because they are not part of the SIOPv2 spec. When driving the protocol through this adapter, supply `responseUri` (required), and optionally `clientId`, `nonce`, and `state` under `options.metadata` for `requestProof`; supply `keyId` under `options.metadata` for `presentProof`.

## Configuration

[`SiopV2Config`](src/main/kotlin/org/trustweave/credential/siop/SiopV2Config.kt) controls service defaults:

| Field | Default | Purpose |
|---|---|---|
| `defaultClientIdScheme` | `SiopClientIdScheme.DID` | Value stamped into `client_id_scheme` on outbound authorization requests. Other options: `PRE_REGISTERED`, `REDIRECT_URI`, `ENTITY_ID`. |
| `supportedResponseModes` | `[DIRECT_POST]` | Advertised response modes. Also supported by the model: `DIRECT_POST_JWT`, `FRAGMENT`, `FORM_POST`. |
| `idTokenSigningAlgorithms` | `["EdDSA", "ES256"]` | Algorithms the verifier advertises for `id_token` signatures. |
| `vpTokenSigningAlgorithms` | `["EdDSA", "ES256"]` | Algorithms the verifier advertises for `vp_token` signatures. |
| `requestUriTimeoutSeconds` | `300` | Lifetime of a `request_uri`-referenced request object. |

Note that the JWT signer in `SiopV2Service` currently hard-codes `alg=EdDSA` in the produced header; the algorithm lists in `SiopV2Config` are metadata, not a switch.

## Limitations

- **No inbound verification.** The service signs and submits responses, but the verifier-side JWT signature check, DID resolution, presentation-submission matching, and replay protection (`nonce`/`state` correlation) are not implemented here.
- **In-memory session store.** Sessions are kept in a `ConcurrentHashMap` inside `SiopV2Service` and are lost when the process restarts. Production deployments should plug in a persistent store.
- **EdDSA-only signing.** The JWT header is hardcoded to `alg=EdDSA`; configuring other algorithms is metadata-only today.
- **No JAR / `request_uri` signing.** Authorization request objects fetched from `request_uri` are parsed as plaintext JSON; signed Request Objects (JAR) are not validated.
- **No encrypted responses.** `direct_post.jwt` (encrypted/signed response mode) is declared in the enum but not implemented in `submitResponse` — only plain `direct_post` JSON is sent.
- **Presentation Exchange.** The `presentation_definition` is carried through verbatim; matching candidate credentials against the definition is left to the caller (see the `presentation-exchange` plugin).

## References

- [Self-Issued OpenID Provider v2 (SIOPv2)](https://openid.net/specs/openid-connect-self-issued-v2-1_0.html)
- [OpenID for Verifiable Presentations (OID4VP)](https://openid.net/specs/openid-4-verifiable-presentations-1_0.html)
- [Presentation Exchange 2.0](https://identity.foundation/presentation-exchange/spec/v2.0.0/)
- TrustWeave [`CredentialExchangeProtocol` SPI](../../credential-api/src/main/kotlin/org/trustweave/credential/exchange/CredentialExchangeProtocol.kt)
