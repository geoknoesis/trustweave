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
import com.trustweave.credential.oidc4vci.Oidc4VciService
import com.trustweave.credential.oidc4vci.exchange.Oidc4VciExchangeProtocol
import com.trustweave.credential.exchange.*
import okhttp3.OkHttpClient

val kms = // Your KMS instance
val httpClient = OkHttpClient()

val oidc4vciService = Oidc4VciService(
    credentialIssuerUrl = "https://issuer.example.com",
    kms = kms,
    httpClient = httpClient
)

val protocol = Oidc4VciExchangeProtocol(oidc4vciService)

val registry = CredentialExchangeProtocolRegistry()
registry.register(protocol)
```

### Credential Offer

```kotlin
val offer = registry.offerCredential(
    protocolName = "oidc4vci",
    request = CredentialOfferRequest(
        issuerDid = "did:key:issuer",
        holderDid = "did:key:holder",
        credentialPreview = CredentialPreview(
            attributes = listOf(
                CredentialAttribute("name", "Alice"),
                CredentialAttribute("email", "alice@example.com")
            )
        ),
        options = mapOf(
            "credentialIssuer" to "https://issuer.example.com",
            "credentialTypes" to listOf("VerifiableCredential", "PersonCredential"),
            "grants" to mapOf("authorization_code" to mapOf("issuer_state" to "state123"))
        )
    )
)

// The offer contains an offer URI that can be shared with the holder
val offerUri = (offer.offerData as Oidc4VciOffer).offerUri
// Format: openid-credential-offer://?credential_issuer=...
```

### Credential Request

```kotlin
val credentialRequest = registry.requestCredential(
    protocolName = "oidc4vci",
    request = CredentialRequestRequest(
        holderDid = "did:key:holder",
        issuerDid = "did:key:issuer",
        offerId = offer.offerId,
        options = mapOf(
            "redirectUri" to "https://holder.example.com/callback"
        )
    )
)
```

### Credential Issuance

```kotlin
val credential = VerifiableCredential(
    type = listOf("VerifiableCredential", "PersonCredential"),
    issuer = "did:key:issuer",
    credentialSubject = buildJsonObject {
        put("id", "did:key:holder")
        put("name", "Alice")
        put("email", "alice@example.com")
    },
    issuanceDate = Instant.now().toString()
)

val issue = registry.issueCredential(
    protocolName = "oidc4vci",
    request = CredentialIssueRequest(
        issuerDid = "did:key:issuer",
        holderDid = "did:key:holder",
        credential = credential,
        requestId = credentialRequest.requestId
    )
)
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

