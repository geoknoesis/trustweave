---
title: Protocol Implementation Guide
---

# Protocol Implementation Guide

Complete guide for implementing credential exchange protocols in TrustWeave.

## Overview

This guide covers how to implement new credential exchange protocols using the protocol abstraction layer. All protocols implement the `CredentialExchangeProtocol` interface, allowing them to be used interchangeably.

## Architecture

```
┌─────────────────────────────────────────────┐
│  CredentialExchangeProtocolRegistry         │
│  (Manages all protocol implementations)    │
└─────────────────────────────────────────────┘
                     │
         ┌───────────┼───────────┐
         │           │           │
         ▼           ▼           ▼
┌─────────────┐ ┌──────────┐ ┌──────────┐
│  DIDComm    │ │ OIDC4VCI  │ │  CHAPI   │
│  Protocol   │ │ Protocol  │ │ Protocol │
└─────────────┘ └──────────┘ └──────────┘
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
└── src/main/kotlin/com/trustweave/credential/yourprotocol/
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
package com.trustweave.credential.yourprotocol.exchange

import com.trustweave.credential.exchange.*

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
package com.trustweave.credential.yourprotocol

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
package com.trustweave.credential.yourprotocol.exchange.spi

import com.trustweave.credential.exchange.CredentialExchangeProtocol
import com.trustweave.credential.exchange.spi.CredentialExchangeProtocolProvider

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

Create `src/main/resources/META-INF/services/com.trustweave.credential.exchange.spi.CredentialExchangeProtocolProvider`:

```
com.trustweave.credential.yourprotocol.exchange.spi.YourProtocolExchangeProtocolProvider
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
options = mapOf(
    "fromKeyId" to "did:key:issuer#key-1",
    "toKeyId" to "did:key:holder#key-1",
    "encrypt" to true,
    "thid" to "thread-id"
)
```

### OIDC4VCI

```kotlin
options = mapOf(
    "credentialIssuer" to "https://issuer.example.com",
    "credentialTypes" to listOf("VerifiableCredential", "PersonCredential"),
    "grants" to mapOf("authorization_code" to mapOf(...)),
    "redirectUri" to "https://holder.example.com/callback"
)
```

### CHAPI

```kotlin
options = mapOf(
    // CHAPI typically doesn't require additional options
    // Messages are generated for browser use
)
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
@Test
fun `test complete exchange flow`() = runTest {
    val registry = CredentialExchangeProtocolRegistry()
    registry.register(YourProtocolExchangeProtocol(service))

    // Test full flow
    val offer = registry.offerCredential("yourprotocol", offerRequest)
    val request = registry.requestCredential("yourprotocol", requestRequest)
    val issue = registry.issueCredential("yourprotocol", issueRequest)

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

- [Protocol Abstraction Documentation](../../core-concepts/credential-exchange-protocols.md)
- [DIDComm Implementation](./didcomm.md)
- [OIDC4VCI Implementation](./oidc4vci.md)
- [CHAPI Implementation](./chapi.md)

