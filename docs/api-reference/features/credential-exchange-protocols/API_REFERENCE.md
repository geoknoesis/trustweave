---
title: Credential Exchange Protocols - API Reference
redirect_from:
  - /features/credential-exchange-protocols/API_REFERENCE/
parent: Feature Reference
grand_parent: API Reference
---

# Credential Exchange Protocols - API Reference

Complete API reference for the credential exchange protocol abstraction layer (`credentials:credential-models-mp` and `credentials:credential-api`).

## Table of Contents

1. [Core Interfaces](#core-interfaces)
2. [Request Models](#request-models)
3. [Response Models & Results](#response-models--results)
4. [Registry API](#registry-api)
5. [Protocol-Specific Options](#protocol-specific-options)
6. [Error Reference](#error-reference)

---

## Core Interfaces

### `CredentialExchangeProtocol`

Protocol-agnostic SPI interface implemented by all exchange protocol plugins (`org.trustweave.credential.exchange.CredentialExchangeProtocol`).

```kotlin
package org.trustweave.credential.exchange

import org.trustweave.credential.exchange.capability.ExchangeProtocolCapabilities
import org.trustweave.credential.exchange.model.ExchangeMessageEnvelope
import org.trustweave.credential.exchange.request.ExchangeRequest
import org.trustweave.credential.exchange.request.ProofExchangeRequest
import org.trustweave.credential.identifiers.ExchangeProtocolName
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.model.vc.VerifiablePresentation

interface CredentialExchangeProtocol {
    val protocolName: ExchangeProtocolName
    val capabilities: ExchangeProtocolCapabilities

    fun supports(operation: ExchangeOperation): Boolean =
        operation in capabilities.supportedOperations

    fun supportsCapability(predicate: ExchangeProtocolCapabilities.() -> Boolean): Boolean =
        predicate(capabilities)

    suspend fun offer(request: ExchangeRequest.Offer): ExchangeMessageEnvelope
    suspend fun request(request: ExchangeRequest.Request): ExchangeMessageEnvelope
    suspend fun issue(request: ExchangeRequest.Issue): Pair<VerifiableCredential, ExchangeMessageEnvelope>
    suspend fun requestProof(request: ProofExchangeRequest.Request): ExchangeMessageEnvelope
    suspend fun presentProof(request: ProofExchangeRequest.Presentation): Pair<VerifiablePresentation, ExchangeMessageEnvelope>
}
```

#### Properties & Methods

| Member | Type / Return | Description |
|--------|---------------|-------------|
| `protocolName` | `ExchangeProtocolName` | Type-safe protocol identifier (e.g., `ExchangeProtocolName.DidComm`, `ExchangeProtocolName.Oidc4Vci`, `ExchangeProtocolName.Chapi`) |
| `capabilities` | `ExchangeProtocolCapabilities` | Declares `supportedOperations: Set<ExchangeOperation>`, `supportsAsync`, `supportsMultipleCredentials`, `supportsSelectiveDisclosure`, `requiresTransportSecurity`, and `metadata` |
| `offer(request)` | `ExchangeMessageEnvelope` | Creates a credential offer wrapped in an opaque envelope |
| `request(request)` | `ExchangeMessageEnvelope` | Creates a credential request in response to an offer |
| `issue(request)` | `Pair<VerifiableCredential, ExchangeMessageEnvelope>` | Issues a credential and returns both the VC and the protocol envelope |
| `requestProof(request)` | `ExchangeMessageEnvelope` | Creates a proof presentation request |
| `presentProof(request)` | `Pair<VerifiablePresentation, ExchangeMessageEnvelope>` | Presents a proof and returns both the VP and the protocol envelope |

### `ExchangeService` and `ExchangeServices`

Application code interacts with `org.trustweave.credential.exchange.ExchangeService`, which dispatches to registered protocols and returns sealed `ExchangeResult<T>` values rather than throwing on expected protocol errors:

```kotlin
import org.trustweave.credential.exchange.ExchangeServices
import org.trustweave.credential.exchange.registry.ExchangeProtocolRegistries

// Option 1: explicit registry
val registry = ExchangeProtocolRegistries.default()
// registry.register(didCommProtocol)
val exchangeService = ExchangeServices.createExchangeService(
    protocolRegistry = registry,
    credentialService = credentialService,
    didResolver = didResolver
)

// Option 2: SPI auto-discovery via ServiceLoader
val autoService = ExchangeServices.createExchangeServiceWithAutoDiscovery(
    credentialService = credentialService,
    didResolver = didResolver,
    options = emptyMap()
)
```

`ExchangeService` exposes:

- `suspend fun offer(request: ExchangeRequest.Offer): ExchangeResult<ExchangeResponse.Offer>`
- `suspend fun request(request: ExchangeRequest.Request): ExchangeResult<ExchangeResponse.Request>`
- `suspend fun issue(request: ExchangeRequest.Issue): ExchangeResult<ExchangeResponse.Issue>`
- `suspend fun requestProof(request: ProofExchangeRequest.Request): ExchangeResult<ProofExchangeResponse.Request>`
- `suspend fun presentProof(request: ProofExchangeRequest.Presentation): ExchangeResult<ProofExchangeResponse.Presentation>`
- `fun supports(protocolName: ExchangeProtocolName): Boolean`
- `fun supports(protocolName: ExchangeProtocolName, operation: ExchangeOperation): Boolean`
- `fun supports(operation: ExchangeOperation): Boolean`
- `fun supportsCapability(protocolName: ExchangeProtocolName, predicate: ExchangeProtocolCapabilities.() -> Boolean): Boolean`
- `fun supportedProtocols(): List<ExchangeProtocolName>`
- `fun getCapabilities(protocolName: ExchangeProtocolName): ExchangeProtocolCapabilities?`

---

## Request Models

All request models live in `org.trustweave.credential.exchange.request` and use strongly typed identifiers (`ExchangeProtocolName`, `Did`, `OfferId`, `RequestId`) and `ExchangeOptions`.

### `ExchangeRequest` (Sealed Hierarchy)

| Subtype | Fields |
|---------|--------|
| `ExchangeRequest.Offer` | `protocolName: ExchangeProtocolName`, `issuerDid: Did`, `holderDid: Did`, `credentialPreview: CredentialPreview`, `options: ExchangeOptions = ExchangeOptions.Empty` |
| `ExchangeRequest.Request` | `protocolName: ExchangeProtocolName`, `holderDid: Did`, `issuerDid: Did`, `offerId: OfferId`, `options: ExchangeOptions = ExchangeOptions.Empty` |
| `ExchangeRequest.Issue` | `protocolName: ExchangeProtocolName`, `issuerDid: Did`, `holderDid: Did`, `credential: VerifiableCredential`, `requestId: RequestId`, `options: ExchangeOptions = ExchangeOptions.Empty` |

### `ProofExchangeRequest` (Sealed Hierarchy)

| Subtype | Fields |
|---------|--------|
| `ProofExchangeRequest.Request` | `protocolName: ExchangeProtocolName`, `verifierDid: Did`, `proverDid: Did`, `proofRequest: ProofRequest`, `options: ExchangeOptions = ExchangeOptions.Empty` |
| `ProofExchangeRequest.Presentation` | `protocolName: ExchangeProtocolName`, `proverDid: Did`, `verifierDid: Did`, `presentation: VerifiablePresentation`, `requestId: RequestId`, `options: ExchangeOptions = ExchangeOptions.Empty` |

Supporting proof models:

- `ProofRequest(name: String, version: String = "1.0", requestedAttributes: Map<String, AttributeRequest>, options: ExchangeOptions = ExchangeOptions.Empty)`
- `AttributeRequest(name: String, restrictions: List<AttributeRestriction> = emptyList(), required: Boolean = true)`
- `AttributeRestriction(issuerDid: Did? = null, schemaId: SchemaId? = null, credentialType: List<CredentialType>? = null, metadata: Map<String, JsonElement> = emptyMap())`

#### Example

```kotlin
import org.trustweave.credential.exchange.model.CredentialAttribute
import org.trustweave.credential.exchange.model.CredentialPreview
import org.trustweave.credential.exchange.options.ExchangeOptions
import org.trustweave.credential.exchange.request.ExchangeRequest
import org.trustweave.credential.identifiers.ExchangeProtocolName
import org.trustweave.did.identifiers.Did

val offerRequest = ExchangeRequest.Offer(
    protocolName = ExchangeProtocolName.DidComm,
    issuerDid = Did("did:key:z6MkIssuer"),
    holderDid = Did("did:key:z6MkHolder"),
    credentialPreview = CredentialPreview(
        attributes = listOf(
            CredentialAttribute("name", "Alice"),
            CredentialAttribute("email", "alice@example.com")
        )
    ),
    options = ExchangeOptions.builder()
        .addMetadata("fromKeyId", "did:key:z6MkIssuer#key-1")
        .addMetadata("toKeyId", "did:key:z6MkHolder#key-1")
        .build()
)
```

---

## Response Models & Results

### `ExchangeMessageEnvelope`

Opaque protocol-agnostic wrapper (`org.trustweave.credential.exchange.model.ExchangeMessageEnvelope`) carrying protocol-specific JSON payloads:

```kotlin
data class ExchangeMessageEnvelope(
    val protocolName: ExchangeProtocolName,
    val messageType: ExchangeMessageType,
    val messageData: JsonElement,
    val metadata: Map<String, JsonElement> = emptyMap()
)
```

`ExchangeMessageType` variants: `Offer`, `Request`, `Issue`, `ProofRequest`, `ProofPresentation`, `Acknowledgment`, `Custom(identifier)`.

### `ExchangeResponse` and `ProofExchangeResponse`

Located in `org.trustweave.credential.exchange.response`:

| Type | Fields |
|------|--------|
| `ExchangeResponse.Offer` | `protocolName`, `messageEnvelope`, `offerId: OfferId` |
| `ExchangeResponse.Request` | `protocolName`, `messageEnvelope`, `requestId: RequestId` |
| `ExchangeResponse.Issue` | `protocolName`, `messageEnvelope`, `issueId: IssueId`, `credential: VerifiableCredential` |
| `ProofExchangeResponse.Request` | `protocolName`, `messageEnvelope`, `requestId: RequestId` |
| `ProofExchangeResponse.Presentation` | `protocolName`, `messageEnvelope`, `presentationId: PresentationId`, `presentation: VerifiablePresentation` |

### `ExchangeResult<out T>`

Located in `org.trustweave.credential.exchange.result`:

- `ExchangeResult.Success<T>(val value: T)`
- `ExchangeResult.Failure`:
  - `ProtocolNotSupported(protocolName, availableProtocols, errors, warnings)`
  - `OperationNotSupported(protocolName, operation, supportedOperations, errors, warnings)`
  - `InvalidRequest(field, reason, errors, warnings)`
  - `MessageNotFound(messageId, messageType, errors, warnings)`
  - `NetworkError(reason, errors, warnings, cause)`
  - `Unknown(reason, errors, warnings, cause)`

Extension helpers include `onSuccess`, `onFailure`, `getOrThrow`, `getOrNull`, `map`, `flatMap`, `fold`, `recover`, and `recoverCatching`.

---

## Registry API

### `ExchangeProtocolRegistry`

Located in `org.trustweave.credential.exchange.registry.ExchangeProtocolRegistry` (create via `ExchangeProtocolRegistries.default()`). Registration and capability lookup live on the registry; execution of `offer` / `request` / `issue` / `requestProof` / `presentProof` lives on `ExchangeService`.

```kotlin
interface ExchangeProtocolRegistry {
    fun register(protocol: CredentialExchangeProtocol)
    fun get(protocolName: ExchangeProtocolName): CredentialExchangeProtocol?
    fun getAll(): Map<ExchangeProtocolName, CredentialExchangeProtocol>
    fun getSupportedProtocols(): List<ExchangeProtocolName>
    fun isRegistered(protocolName: ExchangeProtocolName): Boolean
    fun unregister(protocolName: ExchangeProtocolName): CredentialExchangeProtocol?
    fun getCapabilities(protocolName: ExchangeProtocolName): ExchangeProtocolCapabilities?
    fun supports(protocolName: ExchangeProtocolName, operation: ExchangeOperation): Boolean
    fun clear()
}
```

#### Example

```kotlin
import org.trustweave.credential.exchange.ExchangeServices
import org.trustweave.credential.exchange.options.ExchangeOptions
import org.trustweave.credential.exchange.registry.ExchangeProtocolRegistries
import org.trustweave.credential.exchange.request.ExchangeRequest
import org.trustweave.credential.exchange.result.ExchangeResult
import org.trustweave.credential.identifiers.ExchangeProtocolName
import org.trustweave.did.identifiers.Did

val registry = ExchangeProtocolRegistries.default()
// registry.register(didCommProtocol)

val exchangeService = ExchangeServices.createExchangeService(
    protocolRegistry = registry,
    credentialService = credentialService,
    didResolver = didResolver
)

when (val result = exchangeService.offer(
    ExchangeRequest.Offer(
        protocolName = ExchangeProtocolName.DidComm,
        issuerDid = Did("did:key:issuer"),
        holderDid = Did("did:key:holder"),
        credentialPreview = credentialPreview,
        options = ExchangeOptions.Empty
    )
)) {
    is ExchangeResult.Success -> println("Offer ID: ${result.value.offerId}")
    is ExchangeResult.Failure -> println("Offer failed: ${result.errors.joinToString("; ")}")
}
```

---

## Protocol-Specific Options

Pass protocol-specific parameters through `ExchangeOptions.builder().addMetadata(...)`:

### DIDComm Options

| Key | Type | Required | Description |
|-----|------|----------|-------------|
| `fromKeyId` | `String` | Recommended | Sender key ID (`did:key:...#key-1`) |
| `toKeyId` | `String` | Recommended | Recipient key ID (`did:key:...#key-1`) |
| `encrypt` | `Boolean` | No | Encrypt message envelope (default: `true`) |
| `thid` | `String` | Required for `request` / `issue` / `presentProof` | Thread ID correlating the exchange |
| `requestedPredicates` | `JsonObject` | No | Predicate definitions for `requestProof` |

### OIDC4VCI Options

| Key | Type | Required | Description |
|-----|------|----------|-------------|
| `credentialIssuer` | `String` | Yes | OIDC credential issuer URL |
| `credentialTypes` | `JsonElement` | No | Credential types / configuration IDs |
| `redirectUri` | `String` | No | Redirect URI for authorization |
| `authorizationCode` | `String` | No | Authorization code for token exchange |

### CHAPI Options

Pass `ExchangeOptions.Empty`; CHAPI browser mediation payloads generally require no extra metadata.

---

## Error Reference

### Result Failures (`ExchangeService`)

`ExchangeService` catches protocol and validation errors and surfaces them as `ExchangeResult.Failure.*`:

```kotlin
import org.trustweave.credential.exchange.result.ExchangeResult

when (val result = exchangeService.requestProof(proofExchangeRequest)) {
    is ExchangeResult.Success -> { /* handle result.value */ }
    is ExchangeResult.Failure.ProtocolNotSupported ->
        println("Unsupported protocol: ${result.protocolName}; available=${result.availableProtocols}")
    is ExchangeResult.Failure.OperationNotSupported ->
        println("${result.protocolName} does not support ${result.operation}")
    is ExchangeResult.Failure.InvalidRequest ->
        println("Invalid field '${result.field}': ${result.reason}")
    is ExchangeResult.Failure.MessageNotFound ->
        println("Missing message: ${result.messageId}")
    is ExchangeResult.Failure.NetworkError ->
        println("Transport error: ${result.reason}")
    is ExchangeResult.Failure.Unknown ->
        println("Unexpected error: ${result.reason}")
}
```

### Structured Exceptions (`ExchangeException` and Plugin Exceptions)

Lower-level protocol adapters throw structured exceptions extending `TrustWeaveException`:

- **Core (`org.trustweave.credential.exchange.exception.ExchangeException`)**: `ProtocolNotRegistered`, `OperationNotSupported`, `MissingRequiredOption`, `InvalidRequest`, `OfferNotFound`, `RequestNotFound`, `ProofRequestNotFound`, `MessageNotFound`, `Unknown`.
- **DIDComm (`org.trustweave.credential.didcomm.exception.DidCommException`)**: `EncryptionFailed`, `DecryptionFailed`, `PackingFailed`, `UnpackingFailed`, `ProtocolError`.
- **OIDC4VCI (`org.trustweave.credential.oidc4vci.exception.Oidc4VciException`)**: extends `TrustWeaveException` directly (`HttpRequestFailed`, `TokenExchangeFailed`, `MetadataFetchFailed`, `CredentialRequestFailed`).
- **CHAPI (`org.trustweave.credential.chapi.exception.ChapiException`)**: `BrowserNotAvailable`.

See [Error Handling Guide](ERROR_HANDLING.md) for complete handling patterns.

---

## Related Documentation

- **[Quick Start](QUICK_START.md)** - Get started quickly (5 minutes)
- **[Error Handling](ERROR_HANDLING.md)** - Complete error reference
- **[Workflows](WORKFLOWS.md)** - Step-by-step workflows
- **[Examples](EXAMPLES.md)** - Complete code examples
- **[Troubleshooting](TROUBLESHOOTING.md)** - Common issues and solutions
- **[Glossary](GLOSSARY.md)** - Terms and concepts
- **[Versioning](VERSIONING.md)** - Version info and migration guides
- **[Core Concepts](../../../core-concepts/credential-exchange-protocols.md)** - Deep dive into protocol abstraction
