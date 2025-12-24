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

val registry = CredentialExchangeProtocolRegistry()
registry.register(protocol)
```

### Credential Offer

```kotlin
val offer = registry.offerCredential(
    protocolName = "chapi",
    request = CredentialOfferRequest(
        issuerDid = "did:key:issuer",
        holderDid = "did:key:holder",
        credentialPreview = CredentialPreview(
            attributes = listOf(
                CredentialAttribute("name", "Alice"),
                CredentialAttribute("email", "alice@example.com")
            )
        )
    )
)

// The offer contains a CHAPI message that can be used in the browser
val chapiMessage = offer.offerData as JsonObject
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
    protocolName = "chapi",
    request = CredentialIssueRequest(
        issuerDid = "did:key:issuer",
        holderDid = "did:key:holder",
        credential = credential,
        requestId = "request-id"
    )
)

// The issue result contains a CHAPI message for browser storage
val chapiMessage = issue.issueData as JsonObject
```

### Proof Request

```kotlin
val proofRequest = registry.requestProof(
    protocolName = "chapi",
    request = ProofRequestRequest(
        verifierDid = "did:key:verifier",
        proverDid = "did:key:prover",
        name = "Proof of Identity",
        requestedAttributes = mapOf(
            "name" to RequestedAttribute(
                name = "name",
                restrictions = listOf(
                    AttributeRestriction(issuerDid = "did:key:issuer")
                )
            )
        )
    )
)

// The proof request contains a CHAPI message for browser use
val chapiMessage = proofRequest.requestData as JsonObject
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
val presentation = VerifiablePresentation(
    type = listOf("VerifiablePresentation"),
    verifier = "did:key:verifier",
    verifiableCredential = listOf(credential)
)

val presentationResult = registry.presentProof(
    protocolName = "chapi",
    request = ProofPresentationRequest(
        proverDid = "did:key:prover",
        verifierDid = "did:key:verifier",
        presentation = presentation,
        requestId = proofRequest.requestId
    )
)

// The presentation result contains a CHAPI message
val chapiMessage = presentationResult.presentationData as JsonObject
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

