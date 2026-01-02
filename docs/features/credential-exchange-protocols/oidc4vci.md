---
title: OIDC4VCI Plugin
---

# OIDC4VCI Plugin

OpenID Connect for Verifiable Credential Issuance (OIDC4VCI) implementation for TrustWeave.

## Overview

OIDC4VCI is a protocol that enables credential issuance using OpenID Connect flows. It provides a standardized way for issuers to offer credentials and for holders to request and receive them.

## Features

- ✅ Credential offer creation
- ✅ Credential request handling
- ✅ Credential issuance
- ✅ Credential issuer metadata discovery
- ✅ Integration with protocol abstraction layer

## Architecture

```
┌─────────────────────────────────────┐
│  Oidc4VciExchangeProtocol          │
│  (Implements CredentialExchangeProtocol) │
└─────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────┐
│      Oidc4VciService                │
│  - createCredentialOffer()          │
│  - createCredentialRequest()        │
│  - issueCredential()                │
└─────────────────────────────────────┘
```

## Usage

### Basic Setup

```kotlin
import org.trustweave.credential.oidc4vci.Oidc4VciService
import org.trustweave.credential.oidc4vci.exchange.Oidc4VciExchangeProtocol
import org.trustweave.credential.exchange.*
import okhttp3.OkHttpClient

val kms = // Your KMS instance
val httpClient = OkHttpClient()

val oidc4vciService = Oidc4VciService(
    credentialIssuerUrl = "https://issuer.example.com",
    kms = kms,
    httpClient = httpClient
)

val protocol = Oidc4VciExchangeProtocol(oidc4vciService)

import org.trustweave.credential.exchange.registry.ExchangeProtocolRegistries
import org.trustweave.credential.exchange.ExchangeServices

val registry = ExchangeProtocolRegistries.default()
registry.register(protocol)

val exchangeService = ExchangeServices.createExchangeService(
    protocolRegistry = registry,
    credentialService = credentialService,
    didResolver = didResolver
)
```

### Credential Offer

```kotlin
import org.trustweave.credential.exchange.*
import org.trustweave.credential.exchange.request.ExchangeRequest
import org.trustweave.credential.exchange.result.ExchangeResult
import org.trustweave.credential.exchange.options.ExchangeOptions
import org.trustweave.credential.identifiers.*
import org.trustweave.did.identifiers.Did
import kotlinx.serialization.json.JsonPrimitive

val offerResult = exchangeService.offer(
    ExchangeRequest.Offer(
        protocolName = "oidc4vci".requireExchangeProtocolName(),
        issuerDid = Did("did:key:issuer"),
        holderDid = Did("did:key:holder"),
        credentialPreview = CredentialPreview(
            attributes = listOf(
                CredentialAttribute("name", "Alice"),
                CredentialAttribute("email", "alice@example.com")
            )
        ),
        options = ExchangeOptions.builder()
            .addMetadata("credentialIssuer", "https://issuer.example.com")
            .addMetadata("credentialTypes", JsonPrimitive("VerifiableCredential,PersonCredential"))
            .addMetadata("grants", JsonPrimitive("authorization_code"))
            .addMetadata("issuer_state", JsonPrimitive("state123"))
            .build()
    )
)

val offer = when (offerResult) {
    is ExchangeResult.Success -> offerResult.value
    else -> throw IllegalStateException("Offer failed: $offerResult")
}

// The offer contains an offer URI that can be shared with the holder
val offerUri = (offer.offerData as Oidc4VciOffer).offerUri
// Format: openid-credential-offer://?credential_issuer=...
```

### Credential Request

```kotlin
val requestResult = exchangeService.request(
    ExchangeRequest.Request(
        protocolName = "oidc4vci".requireExchangeProtocolName(),
        holderDid = Did("did:key:holder"),
        issuerDid = Did("did:key:issuer"),
        offerId = offer.offerId,
        options = ExchangeOptions.builder()
            .addMetadata("redirectUri", "https://holder.example.com/callback")
            .build()
    )
)

val credentialRequest = when (requestResult) {
    is ExchangeResult.Success -> requestResult.value
    else -> throw IllegalStateException("Request failed: $requestResult")
}
```

### Credential Issuance

```kotlin
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.CredentialType
import org.trustweave.core.identifiers.Iri
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.datetime.Clock

val credential = VerifiableCredential(
    type = listOf(CredentialType.fromString("VerifiableCredential"), CredentialType.fromString("PersonCredential")),
    issuer = Issuer.IriIssuer(Iri("did:key:issuer")),
    issuanceDate = Clock.System.now(),
    credentialSubject = CredentialSubject(
        id = Did("did:key:holder"),
        claims = mapOf(
            "name" to JsonPrimitive("Alice"),
            "email" to JsonPrimitive("alice@example.com")
        )
    )
)

val issueResult = exchangeService.issue(
    ExchangeRequest.Issue(
        protocolName = "oidc4vci".requireExchangeProtocolName(),
        issuerDid = Did("did:key:issuer"),
        holderDid = Did("did:key:holder"),
        credential = credential,
        requestId = credentialRequest.requestId,
        options = ExchangeOptions.builder().build()
    )
)

val issue = when (issueResult) {
    is ExchangeResult.Success -> issueResult.value
    else -> throw IllegalStateException("Issue failed: $issueResult")
}
```

## OIDC4VCI Flow

1. **Credential Offer**: Issuer creates an offer URI or object
2. **Authorization**: Holder authorizes the request (if using authorization code flow)
3. **Token Exchange**: Holder exchanges authorization code for access token
4. **Credential Request**: Holder requests credential with proof of possession
5. **Credential Issuance**: Issuer issues credential via credential endpoint

## Protocol-Specific Options

### Credential Offer Options

- `credentialIssuer` (required): URL of the credential issuer
- `credentialTypes`: List of credential types to offer
- `grants`: Grant types (e.g., `authorization_code`)

### Credential Request Options

- `redirectUri`: Redirect URI for authorization code flow

## Credential Issuer Metadata

The service can fetch credential issuer metadata from `/.well-known/openid-credential-issuer`:

```kotlin
val metadata = oidc4vciService.fetchCredentialIssuerMetadata(
    credentialIssuerUrl = "https://issuer.example.com"
)

println("Credential endpoint: ${metadata.credentialEndpoint}")
println("Token endpoint: ${metadata.tokenEndpoint}")
println("Supported formats: ${metadata.credentialConfigurationsSupported.keys}")
```

## Integration with walt.id Library

This implementation is designed to work with walt.id's `waltid-openid4vc` library. To enable full integration:

1. Uncomment the dependency in `build.gradle.kts`:
   ```kotlin
   implementation("id.walt:waltid-openid4vc:1.0.0")
   ```

2. Update `Oidc4VciService` to use walt.id's classes directly

## Limitations

- Proof requests and presentations are not supported (use DIDComm or OIDC4VP)
- Currently uses a simplified implementation; full OIDC4VCI flow requires additional HTTP calls
- Token exchange and proof of possession need to be implemented for production use

## References

- [OIDC4VCI Specification](https://openid.net/specs/openid-4-verifiable-credential-issuance-1_0.html)
- [walt.id OpenID4VC Library](https://github.com/walt-id/waltid-openid4vc)

