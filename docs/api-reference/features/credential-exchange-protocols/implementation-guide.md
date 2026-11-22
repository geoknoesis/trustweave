---
title: Protocol Implementation Guide
redirect_from:
  - /features/credential-exchange-protocols/implementation-guide/
parent: Feature Reference
grand_parent: API Reference
---

# Protocol Implementation Guide

Complete guide for implementing and registering credential exchange protocols in TrustWeave.

## Overview

All credential exchange protocols implement `org.trustweave.credential.exchange.CredentialExchangeProtocol`, enabling `ExchangeService` and `ExchangeProtocolRegistry` to invoke DIDComm, OIDC4VCI, CHAPI, OID4VP, SIOP, or custom protocols through a uniform, type-safe API.

For a complete working reference, inspect `credentials/plugins/didcomm/src/main/kotlin/org/trustweave/credential/didcomm/exchange/DidCommExchangeProtocol.kt` and `DidCommExchangeProtocolProvider.kt`.

## Architecture

```text
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
│  ExchangeService │  │ CredentialService│
│  (Unified API)   │  │ (Issuance/Verify)│
└──────────────────┘  └──────────────────┘
```

## Implemented Protocols

| Protocol | `ExchangeProtocolName` | Module | Supported Operations |
|----------|------------------------|--------|----------------------|
| **DIDComm V2** | `ExchangeProtocolName.DidComm` (`"didcomm"`) | `credentials:plugins:didcomm` | Offer, Request, Issue, Proof Request, Proof Presentation |
| **OIDC4VCI** | `ExchangeProtocolName.Oidc4Vci` (`"oidc4vci"`) | `credentials:plugins:oidc4vci` | Issuance flows (Offer, Request, Issue) |
| **CHAPI** | `ExchangeProtocolName.Chapi` (`"chapi"`) | `credentials:plugins:chapi` | Browser-mediated Offer, Issue, Proof Request, Proof Presentation |
| **OID4VP / SIOP** | `"oidc4vp"` / `"siop"` | `credentials:plugins:oidc4vp`, `credentials:plugins:siop` | Verification & presentation flows |

## Implementing a New Protocol

### Step 1: Create Plugin Structure

```text
credentials/plugins/your-protocol/
├── build.gradle.kts
├── README.md
└── src/main/
    ├── kotlin/org/trustweave/credential/yourprotocol/
    │   ├── YourProtocolService.kt
    │   ├── models/
    │   │   └── YourProtocolModels.kt
    │   └── exchange/
    │       ├── YourProtocolExchangeProtocol.kt
    │       └── spi/
    │           └── YourProtocolExchangeProtocolProvider.kt
    └── resources/META-INF/services/
        └── org.trustweave.credential.spi.exchange.CredentialExchangeProtocolProvider
```

### Step 2: Implement `CredentialExchangeProtocol`

Implement `protocolName`, `capabilities`, and the five `suspend` exchange operations returning `ExchangeMessageEnvelope` (plus the VC/VP pair for `issue` and `presentProof`):

```kotlin
package org.trustweave.credential.yourprotocol.exchange

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.trustweave.credential.exchange.CredentialExchangeProtocol
import org.trustweave.credential.exchange.ExchangeOperation
import org.trustweave.credential.exchange.capability.ExchangeProtocolCapabilities
import org.trustweave.credential.exchange.model.ExchangeMessageEnvelope
import org.trustweave.credential.exchange.model.ExchangeMessageType
import org.trustweave.credential.exchange.request.ExchangeRequest
import org.trustweave.credential.exchange.request.ProofExchangeRequest
import org.trustweave.credential.identifiers.ExchangeProtocolName
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.model.vc.VerifiablePresentation

class YourProtocolExchangeProtocol(
    private val service: YourProtocolService
) : CredentialExchangeProtocol {

    override val protocolName = ExchangeProtocolName("yourprotocol")

    override val capabilities = ExchangeProtocolCapabilities(
        supportedOperations = setOf(
            ExchangeOperation.OFFER_CREDENTIAL,
            ExchangeOperation.REQUEST_CREDENTIAL,
            ExchangeOperation.ISSUE_CREDENTIAL
        ),
        supportsAsync = false,
        requiresTransportSecurity = true
    )

    override suspend fun offer(request: ExchangeRequest.Offer): ExchangeMessageEnvelope {
        val offer = service.createOffer(
            issuerDid = request.issuerDid.value,
            holderDid = request.holderDid.value,
            preview = request.credentialPreview
        )
        return ExchangeMessageEnvelope(
            protocolName = protocolName,
            messageType = ExchangeMessageType.Offer,
            messageData = buildJsonObject {
                put("offerId", JsonPrimitive(offer.id))
            },
            metadata = mapOf("messageId" to JsonPrimitive(offer.id))
        )
    }

    override suspend fun request(request: ExchangeRequest.Request): ExchangeMessageEnvelope {
        val req = service.createRequest(
            holderDid = request.holderDid.value,
            issuerDid = request.issuerDid.value,
            offerId = request.offerId.value
        )
        return ExchangeMessageEnvelope(
            protocolName = protocolName,
            messageType = ExchangeMessageType.Request,
            messageData = buildJsonObject {
                put("requestId", JsonPrimitive(req.id))
            },
            metadata = mapOf("messageId" to JsonPrimitive(req.id))
        )
    }

    override suspend fun issue(
        request: ExchangeRequest.Issue
    ): Pair<VerifiableCredential, ExchangeMessageEnvelope> {
        val issueId = service.createIssueMessage(
            issuerDid = request.issuerDid.value,
            holderDid = request.holderDid.value,
            credential = request.credential,
            requestId = request.requestId.value
        )
        val envelope = ExchangeMessageEnvelope(
            protocolName = protocolName,
            messageType = ExchangeMessageType.Issue,
            messageData = buildJsonObject {
                put("issueId", JsonPrimitive(issueId))
            },
            metadata = mapOf("messageId" to JsonPrimitive(issueId))
        )
        return Pair(request.credential, envelope)
    }

    override suspend fun requestProof(request: ProofExchangeRequest.Request): ExchangeMessageEnvelope {
        throw UnsupportedOperationException(
            "Protocol '${protocolName.value}' does not support proof requests"
        )
    }

    override suspend fun presentProof(
        request: ProofExchangeRequest.Presentation
    ): Pair<VerifiablePresentation, ExchangeMessageEnvelope> {
        throw UnsupportedOperationException(
            "Protocol '${protocolName.value}' does not support proof presentations"
        )
    }
}
```

### Step 3: Create Service Implementation

```kotlin
package org.trustweave.credential.yourprotocol

import org.trustweave.credential.exchange.model.CredentialPreview

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

Implement `org.trustweave.credential.spi.exchange.CredentialExchangeProtocolProvider`:

```kotlin
package org.trustweave.credential.yourprotocol.exchange.spi

import org.trustweave.credential.exchange.CredentialExchangeProtocol
import org.trustweave.credential.spi.exchange.CredentialExchangeProtocolProvider

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

Create `src/main/resources/META-INF/services/org.trustweave.credential.spi.exchange.CredentialExchangeProtocolProvider`:

```text
org.trustweave.credential.yourprotocol.exchange.spi.YourProtocolExchangeProtocolProvider
```

`ExchangeServices.createExchangeServiceWithAutoDiscovery(...)` discovers providers registered under this `ServiceLoader` entry automatically.

### Step 6: Add Dependencies to `build.gradle.kts`

```kotlin
dependencies {
    implementation(project(":credentials:credential-api"))
    implementation(project(":did:did-core"))
    implementation(project(":common"))

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
}
```

## Protocol-Specific Options

Pass protocol options via `ExchangeOptions.builder()`:

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
```

## Testing

### Unit Tests

Test the `CredentialExchangeProtocol` implementation directly using `ExchangeRequest.*` and `ExchangeMessageEnvelope`:

```kotlin
import kotlinx.coroutines.test.runTest
import org.trustweave.credential.exchange.model.CredentialAttribute
import org.trustweave.credential.exchange.model.CredentialPreview
import org.trustweave.credential.exchange.model.ExchangeMessageType
import org.trustweave.credential.exchange.request.ExchangeRequest
import org.trustweave.credential.identifiers.ExchangeProtocolName
import org.trustweave.did.identifiers.Did

@Test
fun `test offer credential`() = runTest {
    val service = YourProtocolService()
    val protocol = YourProtocolExchangeProtocol(service)

    val envelope = protocol.offer(
        ExchangeRequest.Offer(
            protocolName = ExchangeProtocolName("yourprotocol"),
            issuerDid = Did("did:key:issuer"),
            holderDid = Did("did:key:holder"),
            credentialPreview = CredentialPreview(
                attributes = listOf(CredentialAttribute("name", "Alice"))
            )
        )
    )

    assertEquals(ExchangeProtocolName("yourprotocol"), envelope.protocolName)
    assertEquals(ExchangeMessageType.Offer, envelope.messageType)
    assertNotNull(envelope.metadata["messageId"])
}
```

### Integration Tests

Exercise the full flow through `ExchangeService` and `ExchangeResult`:

```kotlin
import org.trustweave.credential.exchange.ExchangeServices
import org.trustweave.credential.exchange.options.ExchangeOptions
import org.trustweave.credential.exchange.registry.ExchangeProtocolRegistries
import org.trustweave.credential.exchange.request.ExchangeRequest
import org.trustweave.credential.exchange.result.ExchangeResult
import org.trustweave.credential.identifiers.ExchangeProtocolName

@Test
fun `test complete exchange flow`() = runTest {
    val protocolName = ExchangeProtocolName("yourprotocol")
    val registry = ExchangeProtocolRegistries.default()
    registry.register(YourProtocolExchangeProtocol(service))

    val exchangeService = ExchangeServices.createExchangeService(
        protocolRegistry = registry,
        credentialService = credentialService,
        didResolver = didResolver
    )

    val offer = when (val offerResult = exchangeService.offer(
        ExchangeRequest.Offer(
            protocolName = protocolName,
            issuerDid = issuerDid,
            holderDid = holderDid,
            credentialPreview = preview,
            options = ExchangeOptions.Empty
        )
    )) {
        is ExchangeResult.Success -> offerResult.value
        is ExchangeResult.Failure -> error("Offer failed: ${offerResult.errors}")
    }

    val request = when (val requestResult = exchangeService.request(
        ExchangeRequest.Request(
            protocolName = protocolName,
            holderDid = holderDid,
            issuerDid = issuerDid,
            offerId = offer.offerId,
            options = ExchangeOptions.Empty
        )
    )) {
        is ExchangeResult.Success -> requestResult.value
        is ExchangeResult.Failure -> error("Request failed: ${requestResult.errors}")
    }

    val issue = when (val issueResult = exchangeService.issue(
        ExchangeRequest.Issue(
            protocolName = protocolName,
            issuerDid = issuerDid,
            holderDid = holderDid,
            credential = credential,
            requestId = request.requestId,
            options = ExchangeOptions.Empty
        )
    )) {
        is ExchangeResult.Success -> issueResult.value
        is ExchangeResult.Failure -> error("Issue failed: ${issueResult.errors}")
    }

    assertNotNull(issue.credential)
}
```

## Best Practices

1. **Declare accurate `capabilities`**: List only operations your protocol truly implements in `capabilities.supportedOperations` so `ExchangeService` fails fast with `ExchangeResult.Failure.OperationNotSupported`.
2. **Validate inputs and options**: Reject missing required metadata (such as `thid` in DIDComm) with `IllegalArgumentException` or structured `ExchangeException` subtypes.
3. **Keep protocol payloads inside `ExchangeMessageEnvelope`**: Store the protocol wire object in `messageData` and correlation keys (such as `messageId`) in `metadata`.
4. **Register SPI providers**: Ship `META-INF/services/org.trustweave.credential.spi.exchange.CredentialExchangeProtocolProvider` for auto-discovery.
5. **Enforce transport & input bounds**: Follow TrustWeave security rules (HTTPS-or-loopback URLs, bounded response reads, SSRF-safe clients).

## References

- [API Reference](API_REFERENCE.md)
- [Protocol Abstraction Documentation](../../../core-concepts/credential-exchange-protocols.md)
- [DIDComm Implementation](./didcomm.md)
- [OIDC4VCI Implementation](./oidc4vci.md)
- [CHAPI Implementation](./chapi.md)
