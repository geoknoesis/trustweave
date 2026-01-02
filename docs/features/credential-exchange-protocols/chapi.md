---
title: CHAPI Plugin
---

# CHAPI Plugin

Credential Handler API (CHAPI) implementation for TrustWeave.

## Overview

CHAPI is a browser-based API that enables credential wallet interactions through the browser's credential management system. It provides a standardized way for web applications to interact with credential wallets.

## Features

- ✅ Credential offer creation
- ✅ Credential storage
- ✅ Proof request creation
- ✅ Proof presentation
- ✅ Browser-compatible message format
- ✅ Integration with protocol abstraction layer

## Architecture

```
┌─────────────────────────────────────┐
│  ChapiExchangeProtocol              │
│  (Implements CredentialExchangeProtocol) │
└─────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────┐
│      ChapiService                   │
│  - createCredentialOffer()          │
│  - storeCredential()                │
│  - createProofRequest()             │
│  - presentProof()                   │
└─────────────────────────────────────┘
```

## Usage

### Basic Setup

```kotlin
import org.trustweave.credential.chapi.ChapiService
import org.trustweave.credential.chapi.exchange.ChapiExchangeProtocol
import org.trustweave.credential.exchange.*

val chapiService = ChapiService()
val protocol = ChapiExchangeProtocol(chapiService)

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
import org.trustweave.credential.identifiers.*
import org.trustweave.did.identifiers.Did

val offerResult = exchangeService.offer(
    ExchangeRequest.Offer(
        protocolName = "chapi".requireExchangeProtocolName(),
        issuerDid = Did("did:key:issuer"),
        holderDid = Did("did:key:holder"),
        credentialPreview = CredentialPreview(
            attributes = listOf(
                CredentialAttribute("name", "Alice"),
                CredentialAttribute("email", "alice@example.com")
            )
        ),
        options = ExchangeOptions.Empty
    )
)

val offer = when (offerResult) {
    is ExchangeResult.Success -> offerResult.value
    else -> throw IllegalStateException("Offer failed: $offerResult")
}

// The offer contains a CHAPI message that can be used in the browser
val chapiMessage = offer.messageEnvelope.messageData as JsonObject
```

### Browser Integration

In a browser environment, use the CHAPI messages with the Credential Handler API:

```javascript
// Store credential offer
const offer = {
    "@context": ["https://www.w3.org/2018/credentials/v1", "https://w3id.org/credential-handler/v1"],
    "type": ["VerifiableCredential", "CredentialOffer"],
    // ... offer data from offer.offerData
};

navigator.credentials.store(new Credential({
    id: "credential-offer",
    type: "web",
    data: offer
}));
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
        protocolName = "chapi".requireExchangeProtocolName(),
        issuerDid = Did("did:key:issuer"),
        holderDid = Did("did:key:holder"),
        credential = credential,
        requestId = RequestId("request-id"),
        options = ExchangeOptions.Empty
    )
)

val issue = when (issueResult) {
    is ExchangeResult.Success -> issueResult.value
    else -> throw IllegalStateException("Issue failed: $issueResult")
}

// The issue result contains a CHAPI message for browser storage
val chapiMessage = issue.messageEnvelope.messageData as JsonObject
```

### Proof Request

```kotlin
import org.trustweave.credential.exchange.request.ProofExchangeRequest
import org.trustweave.credential.exchange.request.ProofRequest
import org.trustweave.credential.exchange.request.AttributeRequest
import org.trustweave.credential.exchange.request.AttributeRestriction
import org.trustweave.credential.exchange.result.ExchangeResult

val proofRequestResult = exchangeService.requestProof(
    ProofExchangeRequest.Request(
        protocolName = "chapi".requireExchangeProtocolName(),
        verifierDid = Did("did:key:verifier"),
        proverDid = Did("did:key:prover"),
        proofRequest = ProofRequest(
            name = "Proof of Identity",
            requestedAttributes = mapOf(
                "name" to AttributeRequest(
                    name = "name",
                    restrictions = listOf(
                        AttributeRestriction(issuerDid = Did("did:key:issuer"))
                    )
                )
            )
        ),
        options = ExchangeOptions.Empty
    )
)

val proofRequest = when (proofRequestResult) {
    is ExchangeResult.Success -> proofRequestResult.value
    else -> throw IllegalStateException("Proof request failed: $proofRequestResult")
}

// The proof request contains a CHAPI message for browser use
val chapiMessage = proofRequest.messageEnvelope.messageData as JsonObject
```

### Browser Integration for Proof Request

```javascript
// Request proof
const proofRequest = {
    "@context": ["https://www.w3.org/2018/credentials/v1", "https://w3id.org/credential-handler/v1"],
    "type": ["VerifiablePresentationRequest"],
    // ... proof request data from proofRequest.requestData
};

navigator.credentials.get({
    publicKey: {
        challenge: new Uint8Array(32),
        rpId: window.location.hostname,
        userVerification: "preferred"
    },
    web: {
        data: proofRequest
    }
}).then(credential => {
    // Handle the presentation response
    const presentation = credential.data;
});
```

### Proof Presentation

```kotlin
import org.trustweave.credential.model.vc.VerifiablePresentation
import org.trustweave.credential.model.CredentialType

val presentation = VerifiablePresentation(
    type = listOf(CredentialType.fromString("VerifiablePresentation")),
    verifier = Did("did:key:verifier"),
    verifiableCredential = listOf(credential)
)

val presentationResult = exchangeService.presentProof(
    ProofExchangeRequest.Presentation(
        protocolName = "chapi".requireExchangeProtocolName(),
        proverDid = Did("did:key:prover"),
        verifierDid = Did("did:key:verifier"),
        presentation = presentation,
        requestId = proofRequest.requestId,
        options = ExchangeOptions.Empty
    )
)

val presentationResponse = when (presentationResult) {
    is ExchangeResult.Success -> presentationResult.value
    else -> throw IllegalStateException("Presentation failed: $presentationResult")
}

// The presentation result contains a CHAPI message
val chapiMessage = presentationResponse.messageEnvelope.messageData as JsonObject
```

## CHAPI Flow

1. **Credential Offer**: Issuer creates a CHAPI-compatible offer message
2. **Browser Storage**: Offer is stored using `navigator.credentials.store()`
3. **Wallet Interaction**: Wallet processes the offer and stores the credential
4. **Proof Request**: Verifier creates a CHAPI-compatible proof request
5. **Browser Retrieval**: Request is retrieved using `navigator.credentials.get()`
6. **Proof Presentation**: Wallet presents proof using CHAPI message format

## Message Format

CHAPI messages follow the W3C Credential Handler API specification:

```json
{
  "@context": [
    "https://www.w3.org/2018/credentials/v1",
    "https://w3id.org/credential-handler/v1"
  ],
  "type": ["VerifiableCredential", "CredentialOffer"],
  "credentialPreview": {
    "@type": "https://didcomm.org/issue-credential/3.0/credential-preview",
    "attributes": [...]
  },
  "issuer": "did:key:issuer"
}
```

## Limitations

- Credential requests are not supported (use `wallet.get()` directly)
- Requires browser environment for full functionality
- Server-side implementation generates messages; actual wallet interaction happens in browser

## Browser Support

CHAPI requires a browser that supports the Credential Handler API. Check compatibility:

- Chrome/Edge: Supported via Credential Handler polyfill
- Firefox: Limited support
- Safari: Not supported

## References

- [Credential Handler API Specification](https://w3c.github.io/webappsec-credential-management/)
- [Credential Handler API Polyfill](https://github.com/digitalbazaar/credential-handler-polyfill)

