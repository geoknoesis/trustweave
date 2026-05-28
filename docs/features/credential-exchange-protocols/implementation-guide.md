---
title: Protocol Implementation Guide
---

# Protocol Implementation Guide

Complete guide for implementing credential exchange protocols in TrustWeave.

> **TODO (audit 2026-05):** The skeleton in [Step 2: Implement CredentialExchangeProtocol](#step-2-implement-credentialexchangeprotocol) and the `@Test` in [Unit Tests](#unit-tests) describe the obsolete request/response API (`offerCredential(CredentialOfferRequest)` returning `CredentialOfferResponse`, raw `protocolName: String`, `supportedOperations: Set<…>` on the protocol). The real SPI signatures are:
>
> - `interface CredentialExchangeProtocol { val protocolName: ExchangeProtocolName; val capabilities: ExchangeProtocolCapabilities; suspend fun offer(request: ExchangeRequest.Offer): ExchangeMessageEnvelope; suspend fun request(request: ExchangeRequest.Request): ExchangeMessageEnvelope; suspend fun issue(request: ExchangeRequest.Issue): Pair<VerifiableCredential, ExchangeMessageEnvelope>; suspend fun requestProof(request: ProofExchangeRequest.Request): ExchangeMessageEnvelope; suspend fun presentProof(request: ProofExchangeRequest.Presentation): Pair<VerifiablePresentation, ExchangeMessageEnvelope> }`
> - Supported operations are declared via `capabilities = ExchangeProtocolCapabilities(supportedOperations = setOf(ExchangeOperation.OFFER_CREDENTIAL, …))`.
> - The SPI provider interface is `org.trustweave.credential.spi.exchange.CredentialExchangeProtocolProvider` with `name: String`, `supportedProtocols: List<String>`, and `create(protocolName: String, options: Map<String, Any?>): CredentialExchangeProtocol?`.
>
> See `credentials/plugins/didcomm/src/main/kotlin/org/trustweave/credential/didcomm/exchange/DidCommExchangeProtocol.kt` and `…/exchange/spi/DidCommExchangeProtocolProvider.kt` for a working reference implementation.

## Overview

This guide covers how to implement new credential exchange protocols using the protocol abstraction layer. All protocols implement the `CredentialExchangeProtocol` interface, allowing them to be used interchangeably.

## Architecture

```
┌─────────────────────────────────────────────┐
│  ExchangeProtocolRegistry                   │
│  (Manages all protocol implementations)     │
└─────────────────────────────────────────────┘
                     │
         ┌───────────┼───────────┐
         │           │           │
         ▼           ▼           ▼
┌─────────────┐ ┌──────────┐ ┌──────────┐
│  DIDComm    │ │ OIDC4VCI  │ │  CHAPI   │
│  Protocol   │ │ Protocol  │ │ Protocol │
└─────────────┘ └──────────┘ └──────────┘
                     │
         ┌───────────┴───────────┐
         │                       │
         ▼                       ▼
┌──────────────────┐  ┌──────────────────┐
│  ExchangeService  │  │  CredentialService│
│  (Unified API)    │  │  (Issuance/Verify)│
└──────────────────┘  └──────────────────┘
```

## Implemented Protocols

### 1. DIDComm V2

**Status**: ✅ Fully Implemented

- **Protocol Name**: `"didcomm"`
- **Library**: Custom implementation with `didcomm-java` integration
- **Supported Operations**: All (offer, request, issue, proof request, proof presentation)
- **Documentation**: `credentials/plugins/didcomm/README.md`

### 2. OIDC4VCI

**Status**: ✅ Implemented (Basic)

- **Protocol Name**: `"oidc4vci"`
- **Library**: walt.id `waltid-openid4vc` (optional)
- **Supported Operations**: Issuance only (offer, request, issue)
- **Documentation**: `credentials/plugins/oidc4vci/README.md`

### 3. CHAPI

**Status**: ✅ Implemented (Basic)

- **Protocol Name**: `"chapi"`
- **Library**: Custom implementation (browser API wrapper)
- **Supported Operations**: Offer, issue, proof request, proof presentation
- **Documentation**: `credentials/plugins/chapi/README.md`

## Implementing a New Protocol

### Step 1: Create Plugin Structure

```
credentials/plugins/your-protocol/
├── build.gradle.kts
├── README.md
└── src/main/kotlin/org.trustweave/credential/yourprotocol/
    ├── YourProtocolService.kt
    ├── models/
    │   └── YourProtocolModels.kt
    └── exchange/
        ├── YourProtocolExchangeProtocol.kt
        └── spi/
            └── YourProtocolExchangeProtocolProvider.kt
```

### Step 2: Implement CredentialExchangeProtocol

```kotlin
package org.trustweave.credential.yourprotocol.exchange

import org.trustweave.credential.exchange.*

class YourProtocolExchangeProtocol(
    private val service: YourProtocolService
) : CredentialExchangeProtocol {

    override val protocolName = "yourprotocol"

    override val supportedOperations = setOf(
        ExchangeOperation.OFFER_CREDENTIAL,
        ExchangeOperation.REQUEST_CREDENTIAL,
        ExchangeOperation.ISSUE_CREDENTIAL
    )

    override suspend fun offerCredential(
        request: CredentialOfferRequest
    ): CredentialOfferResponse {
        val offer = service.createOffer(
            issuerDid = request.issuerDid,
            holderDid = request.holderDid,
            preview = request.credentialPreview
        )

        return CredentialOfferResponse(
            offerId = offer.id,
            offerData = offer,
            protocolName = protocolName
        )
    }

    // Implement other operations...
}
```

### Step 3: Create Service Implementation

```kotlin
package org.trustweave.credential.yourprotocol

class YourProtocolService {
    suspend fun createOffer(
        issuerDid: String,
        holderDid: String,
        preview: CredentialPreview
    ): YourProtocolOffer {
        // Protocol-specific implementation
    }
}
```

### Step 4: Create SPI Provider

```kotlin
package org.trustweave.credential.yourprotocol.exchange.spi

import org.trustweave.credential.exchange.CredentialExchangeProtocol
import org.trustweave.credential.exchange.spi.CredentialExchangeProtocolProvider

class YourProtocolExchangeProtocolProvider : CredentialExchangeProtocolProvider {
    override val name = "yourprotocol"
    override val supportedProtocols = listOf("yourprotocol")

    override fun create(
        protocolName: String,
        options: Map<String, Any?>
    ): CredentialExchangeProtocol? {
        if (protocolName != "yourprotocol") return null

        val service = YourProtocolService(
            // Initialize from options
        )

        return YourProtocolExchangeProtocol(service)
    }
}
```

### Step 5: Register SPI Provider

Create `src/main/resources/META-INF/services/org.trustweave.credential.exchange.spi.CredentialExchangeProtocolProvider`:

```
org.trustweave.credential.yourprotocol.exchange.spi.YourProtocolExchangeProtocolProvider
```

### Step 6: Add to Build File

```kotlin
dependencies {
    implementation(project(":credentials:credential-core"))
    implementation(project(":did:did-core"))
    implementation(project(":common"))

    // Protocol-specific dependencies
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
}
```

## Protocol-Specific Options

Each protocol may require specific options in the `options` map:

### DIDComm

```kotlin
import org.trustweave.credential.exchange.options.ExchangeOptions

val options = ExchangeOptions.builder()
    .addMetadata("fromKeyId", "did:key:issuer#key-1")
    .addMetadata("toKeyId", "did:key:holder#key-1")
    .addMetadata("encrypt", true)
    .threadId("thread-id")
    .build()
```

### OIDC4VCI

```kotlin
import org.trustweave.credential.exchange.options.ExchangeOptions
import kotlinx.serialization.json.JsonPrimitive

val options = ExchangeOptions.builder()
    .addMetadata("credentialIssuer", "https://issuer.example.com")
    .addMetadata("credentialTypes", JsonPrimitive("VerifiableCredential,PersonCredential"))
    .addMetadata("grants", JsonPrimitive("authorization_code"))
    .addMetadata("redirectUri", "https://holder.example.com/callback")
    .build()
```

### CHAPI

```kotlin
import org.trustweave.credential.exchange.options.ExchangeOptions

val options = ExchangeOptions.Empty
// CHAPI typically doesn't require additional options
// Messages are generated for browser use
```

## Testing

### Unit Tests

```kotlin
@Test
fun `test offer credential`() = runTest {
    val service = YourProtocolService()
    val protocol = YourProtocolExchangeProtocol(service)

    val offer = protocol.offerCredential(
        CredentialOfferRequest(
            issuerDid = "did:key:issuer",
            holderDid = "did:key:holder",
            credentialPreview = CredentialPreview(
                attributes = listOf(CredentialAttribute("name", "Alice"))
            )
        )
    )

    assertEquals("yourprotocol", offer.protocolName)
    assertNotNull(offer.offerId)
}
```

### Integration Tests

```kotlin
import org.trustweave.credential.exchange.*
import org.trustweave.credential.exchange.registry.ExchangeProtocolRegistries
import org.trustweave.credential.exchange.request.ExchangeRequest
import org.trustweave.credential.exchange.result.ExchangeResult
import org.trustweave.credential.identifiers.*

@Test
fun `test complete exchange flow`() = runTest {
    val registry = ExchangeProtocolRegistries.default()
    registry.register(YourProtocolExchangeProtocol(service))
    
    val exchangeService = ExchangeServices.createExchangeService(
        protocolRegistry = registry,
        credentialService = credentialService,
        didResolver = didResolver
    )

    // Test full flow
    val offerResult = exchangeService.offer(
        ExchangeRequest.Offer(
            protocolName = "yourprotocol".requireExchangeProtocolName(),
            issuerDid = issuerDid,
            holderDid = holderDid,
            credentialPreview = preview,
            options = ExchangeOptions.builder().build()
        )
    )
    val offer = when (offerResult) {
        is ExchangeResult.Success -> offerResult.value
        else -> throw IllegalStateException("Offer failed: $offerResult")
    }
    
    val requestResult = exchangeService.request(
        ExchangeRequest.Request(
            protocolName = "yourprotocol".requireExchangeProtocolName(),
            holderDid = holderDid,
            issuerDid = issuerDid,
            offerId = offer.offerId,
            options = ExchangeOptions.builder().build()
        )
    )
    val request = when (requestResult) {
        is ExchangeResult.Success -> requestResult.value
        else -> throw IllegalStateException("Request failed: $requestResult")
    }
    
    val issueResult = exchangeService.issue(
        ExchangeRequest.Issue(
            protocolName = "yourprotocol".requireExchangeProtocolName(),
            issuerDid = issuerDid,
            holderDid = holderDid,
            credential = credential,
            requestId = request.requestId,
            options = ExchangeOptions.builder().build()
        )
    )
    val issue = when (issueResult) {
        is ExchangeResult.Success -> issueResult.value
        else -> throw IllegalStateException("Issue failed: $issueResult")
    }

    assertNotNull(issue.credential)
}
```

## Best Practices

1. **Error Handling**: Always validate inputs and provide clear error messages
2. **Type Safety**: Use strongly-typed models for protocol-specific data
3. **Documentation**: Document protocol-specific options and limitations
4. **Testing**: Write comprehensive tests for all operations
5. **SPI Registration**: Always register SPI providers for auto-discovery
6. **Protocol Metadata**: Implement `supportedOperations` correctly

## Common Patterns

### Converting Common Models to Protocol-Specific

```kotlin
// Convert common preview to protocol-specific
val protocolPreview = ProtocolPreview(
    attributes = request.credentialPreview.attributes.map { attr ->
        ProtocolAttribute(
            name = attr.name,
            value = attr.value,
            mimeType = attr.mimeType
        )
    }
)
```

### Handling Optional Operations

```kotlin
override suspend fun requestProof(
    request: ProofRequestRequest
): ProofRequestResponse {
    if (!supportedOperations.contains(ExchangeOperation.REQUEST_PROOF)) {
        throw UnsupportedOperationException(
            "Protocol '$protocolName' does not support proof requests"
        )
    }
    // Implementation...
}
```

### Protocol-Specific Data in Responses

```kotlin
return CredentialOfferResponse(
    offerId = offer.id,
    offerData = offer, // Protocol-specific format
    protocolName = protocolName
)
```

## Future Protocols

Potential protocols to implement:

- **OIDC4VP**: OpenID Connect for Verifiable Presentations
- **SIOPv2**: Self-Issued OpenID Provider v2
- **WACI**: Wallet and Credential Interactions
- **ISO/IEC 18013-5**: Mobile Driving License (mDL)
- **ISO/IEC 23220**: Verifiable Credentials standard

## References

- Protocol Abstraction Documentation](../../core-concepts/credential-exchange-protocols.md)
- DIDComm Implementation](./didcomm.md)
- OIDC4VCI Implementation](./oidc4vci.md)
- CHAPI Implementation](./chapi.md)

