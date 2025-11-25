# Credential Exchange Protocols - API Reference

Complete API reference for the credential exchange protocol abstraction layer.

## Table of Contents

1. [Core Interfaces](#core-interfaces)
2. [Request Models](#request-models)
3. [Response Models](#response-models)
4. [Registry API](#registry-api)
5. [Protocol-Specific Options](#protocol-specific-options)
6. [Error Reference](#error-reference)

---

## Core Interfaces

### CredentialExchangeProtocol

Main interface that all protocols implement.

```kotlin
interface CredentialExchangeProtocol {
    val protocolName: String
    val supportedOperations: Set<ExchangeOperation>
    
    suspend fun offerCredential(request: CredentialOfferRequest): CredentialOfferResponse
    suspend fun requestCredential(request: CredentialRequestRequest): CredentialRequestResponse
    suspend fun issueCredential(request: CredentialIssueRequest): CredentialIssueResponse
    suspend fun requestProof(request: ProofRequestRequest): ProofRequestResponse
    suspend fun presentProof(request: ProofPresentationRequest): ProofPresentationResponse
}
```

#### Properties

| Property | Type | Description |
|----------|------|-------------|
| `protocolName` | `String` | Protocol identifier (e.g., "didcomm", "oidc4vci", "chapi") |
| `supportedOperations` | `Set<ExchangeOperation>` | Set of operations this protocol supports |

#### Methods

| Method | Description | Throws |
|--------|-------------|--------|
| `offerCredential()` | Creates a credential offer | `IllegalArgumentException`, protocol-specific errors |
| `requestCredential()` | Requests a credential | `IllegalArgumentException`, protocol-specific errors |
| `issueCredential()` | Issues a credential | `IllegalArgumentException`, protocol-specific errors |
| `requestProof()` | Requests a proof presentation | `UnsupportedOperationException`, protocol-specific errors |
| `presentProof()` | Presents a proof | `UnsupportedOperationException`, protocol-specific errors |

---

## Request Models

### CredentialOfferRequest

Request for creating a credential offer.

```kotlin
data class CredentialOfferRequest(
    val issuerDid: String,
    val holderDid: String,
    val credentialPreview: CredentialPreview,
    val options: Map<String, Any?> = emptyMap()
)
```

#### Properties

| Property | Type | Required | Description |
|----------|------|----------|-------------|
| `issuerDid` | `String` | ✅ Yes | DID of the credential issuer (must be valid DID format) |
| `holderDid` | `String` | ✅ Yes | DID of the credential holder (must be valid DID format) |
| `credentialPreview` | `CredentialPreview` | ✅ Yes | Preview of credential attributes (must not be empty) |
| `options` | `Map<String, Any?>` | ❌ No | Protocol-specific options (see [Protocol-Specific Options](#protocol-specific-options)) |

#### Validation Rules

- `issuerDid` must match DID format: `did:<method>:<identifier>`
- `holderDid` must match DID format: `did:<method>:<identifier>`
- `credentialPreview.attributes` must not be empty
- Protocol-specific options must match protocol requirements (see below)

#### Example

```kotlin
val request = CredentialOfferRequest(
    issuerDid = "did:key:z6Mk...",
    holderDid = "did:key:z6Mk...",
    credentialPreview = CredentialPreview(
        attributes = listOf(
            CredentialAttribute("name", "Alice"),
            CredentialAttribute("email", "alice@example.com")
        )
    ),
    options = mapOf(
        "fromKeyId" to "did:key:issuer#key-1",
        "toKeyId" to "did:key:holder#key-1"
    )
)
```

---

### CredentialRequestRequest

Request for requesting a credential after receiving an offer.

```kotlin
data class CredentialRequestRequest(
    val holderDid: String,
    val issuerDid: String,
    val offerId: String,
    val options: Map<String, Any?> = emptyMap()
)
```

#### Properties

| Property | Type | Required | Description |
|----------|------|----------|-------------|
| `holderDid` | `String` | ✅ Yes | DID of the credential holder |
| `issuerDid` | `String` | ✅ Yes | DID of the credential issuer |
| `offerId` | `String` | ✅ Yes | ID of the offer being requested (must exist) |
| `options` | `Map<String, Any?>` | ❌ No | Protocol-specific options |

#### Validation Rules

- `holderDid` must be valid DID format
- `issuerDid` must be valid DID format
- `offerId` must reference an existing offer
- Protocol-specific options must match protocol requirements

#### Example

```kotlin
val request = CredentialRequestRequest(
    holderDid = "did:key:holder",
    issuerDid = "did:key:issuer",
    offerId = "offer-123",
    options = mapOf(
        "fromKeyId" to "did:key:holder#key-1",
        "toKeyId" to "did:key:issuer#key-1"
    )
)
```

---

### CredentialIssueRequest

Request for issuing a credential after receiving a request.

```kotlin
data class CredentialIssueRequest(
    val issuerDid: String,
    val holderDid: String,
    val credential: VerifiableCredential,
    val requestId: String,
    val options: Map<String, Any?> = emptyMap()
)
```

#### Properties

| Property | Type | Required | Description |
|----------|------|----------|-------------|
| `issuerDid` | `String` | ✅ Yes | DID of the credential issuer |
| `holderDid` | `String` | ✅ Yes | DID of the credential holder |
| `credential` | `VerifiableCredential` | ✅ Yes | The credential to issue (must be valid) |
| `requestId` | `String` | ✅ Yes | ID of the request being fulfilled (must exist) |
| `options` | `Map<String, Any?>` | ❌ No | Protocol-specific options |

#### Validation Rules

- `issuerDid` must match `credential.issuer`
- `holderDid` must match `credential.credentialSubject.id` (if present)
- `credential` must be a valid VerifiableCredential
- `requestId` must reference an existing request
- Protocol-specific options must match protocol requirements

#### Example

```kotlin
val request = CredentialIssueRequest(
    issuerDid = "did:key:issuer",
    holderDid = "did:key:holder",
    credential = verifiableCredential,
    requestId = "request-123",
    options = mapOf(
        "fromKeyId" to "did:key:issuer#key-1",
        "toKeyId" to "did:key:holder#key-1"
    )
)
```

---

### ProofRequestRequest

Request for requesting a proof presentation.

```kotlin
data class ProofRequestRequest(
    val verifierDid: String,
    val proverDid: String,
    val name: String,
    val version: String = "1.0",
    val requestedAttributes: Map<String, RequestedAttribute>,
    val requestedPredicates: Map<String, RequestedPredicate> = emptyMap(),
    val goalCode: String? = null,
    val options: Map<String, Any?> = emptyMap()
)
```

#### Properties

| Property | Type | Required | Description |
|----------|------|----------|-------------|
| `verifierDid` | `String` | ✅ Yes | DID of the verifier requesting proof |
| `proverDid` | `String` | ✅ Yes | DID of the prover who will present proof |
| `name` | `String` | ✅ Yes | Name of the proof request |
| `version` | `String` | ❌ No | Version of the proof request (default: "1.0") |
| `requestedAttributes` | `Map<String, RequestedAttribute>` | ✅ Yes | Map of requested attributes (must not be empty) |
| `requestedPredicates` | `Map<String, RequestedPredicate>` | ❌ No | Map of requested predicates |
| `goalCode` | `String?` | ❌ No | Goal code for the proof request |
| `options` | `Map<String, Any?>` | ❌ No | Protocol-specific options |

#### Validation Rules

- `verifierDid` must be valid DID format
- `proverDid` must be valid DID format
- `requestedAttributes` must not be empty
- Protocol-specific options must match protocol requirements

#### Example

```kotlin
val request = ProofRequestRequest(
    verifierDid = "did:key:verifier",
    proverDid = "did:key:prover",
    name = "Age Verification",
    requestedAttributes = mapOf(
        "age" to RequestedAttribute(
            name = "age",
            restrictions = listOf(
                AttributeRestriction(issuerDid = "did:key:issuer")
            )
        )
    ),
    requestedPredicates = mapOf(
        "age_verification" to RequestedPredicate(
            name = "age",
            pType = ">=",
            pValue = 18
        )
    ),
    options = mapOf(
        "fromKeyId" to "did:key:verifier#key-1",
        "toKeyId" to "did:key:prover#key-1"
    )
)
```

---

### ProofPresentationRequest

Request for presenting a proof after receiving a proof request.

```kotlin
data class ProofPresentationRequest(
    val proverDid: String,
    val verifierDid: String,
    val presentation: VerifiablePresentation,
    val requestId: String,
    val options: Map<String, Any?> = emptyMap()
)
```

#### Properties

| Property | Type | Required | Description |
|----------|------|----------|-------------|
| `proverDid` | `String` | ✅ Yes | DID of the prover presenting proof |
| `verifierDid` | `String` | ✅ Yes | DID of the verifier receiving proof |
| `presentation` | `VerifiablePresentation` | ✅ Yes | The verifiable presentation (must be valid) |
| `requestId` | `String` | ✅ Yes | ID of the proof request being fulfilled (must exist) |
| `options` | `Map<String, Any?>` | ❌ No | Protocol-specific options |

#### Validation Rules

- `proverDid` must match `presentation.holder`
- `verifierDid` must match the verifier from the original request
- `presentation` must be a valid VerifiablePresentation
- `requestId` must reference an existing proof request
- Protocol-specific options must match protocol requirements

---

## Response Models

### CredentialOfferResponse

Response from credential offer operation.

```kotlin
data class CredentialOfferResponse(
    val offerId: String,
    val offerData: Any,
    val protocolName: String
)
```

#### Properties

| Property | Type | Description |
|----------|------|-------------|
| `offerId` | `String` | Unique identifier for the offer (use for request) |
| `offerData` | `Any` | Protocol-specific offer data (see below) |
| `protocolName` | `String` | Protocol that created the offer |

#### Protocol-Specific offerData

**DIDComm:**
- Type: `DidCommMessage`
- Contains: Encrypted/signed DIDComm message with offer

**OIDC4VCI:**
- Type: `Oidc4VciOffer`
- Contains: OIDC credential offer URI

**CHAPI:**
- Type: `ChapiOffer`
- Contains: CHAPI-compatible offer for browser

---

### CredentialRequestResponse

Response from credential request operation.

```kotlin
data class CredentialRequestResponse(
    val requestId: String,
    val requestData: Any,
    val protocolName: String
)
```

#### Properties

| Property | Type | Description |
|----------|------|-------------|
| `requestId` | `String` | Unique identifier for the request (use for issue) |
| `requestData` | `Any` | Protocol-specific request data |
| `protocolName` | `String` | Protocol that created the request |

---

### CredentialIssueResponse

Response from credential issue operation.

```kotlin
data class CredentialIssueResponse(
    val issueId: String,
    val credential: VerifiableCredential,
    val issueData: Any,
    val protocolName: String
)
```

#### Properties

| Property | Type | Description |
|----------|------|-------------|
| `issueId` | `String` | Unique identifier for the issue |
| `credential` | `VerifiableCredential` | The issued verifiable credential |
| `issueData` | `Any` | Protocol-specific issue data |
| `protocolName` | `String` | Protocol that issued the credential |

---

### ProofRequestResponse

Response from proof request operation.

```kotlin
data class ProofRequestResponse(
    val requestId: String,
    val requestData: Any,
    val protocolName: String
)
```

#### Properties

| Property | Type | Description |
|----------|------|-------------|
| `requestId` | `String` | Unique identifier for the proof request (use for presentation) |
| `requestData` | `Any` | Protocol-specific request data |
| `protocolName` | `String` | Protocol that created the request |

---

### ProofPresentationResponse

Response from proof presentation operation.

```kotlin
data class ProofPresentationResponse(
    val presentationId: String,
    val presentation: VerifiablePresentation,
    val presentationData: Any,
    val protocolName: String
)
```

#### Properties

| Property | Type | Description |
|----------|------|-------------|
| `presentationId` | `String` | Unique identifier for the presentation |
| `presentation` | `VerifiablePresentation` | The verifiable presentation |
| `presentationData` | `Any` | Protocol-specific presentation data |
| `protocolName` | `String` | Protocol that created the presentation |

---

## Registry API

### CredentialExchangeProtocolRegistry

Registry for managing and using credential exchange protocols.

```kotlin
class CredentialExchangeProtocolRegistry(
    initialProtocols: Map<String, CredentialExchangeProtocol> = emptyMap()
)
```

#### Constructor

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `initialProtocols` | `Map<String, CredentialExchangeProtocol>` | ❌ No | Initial protocols to register (default: empty) |

#### Methods

##### register

Registers a credential exchange protocol.

```kotlin
fun register(protocol: CredentialExchangeProtocol)
```

**Parameters:**
- `protocol`: The protocol to register

**Throws:**
- Nothing (replaces existing protocol if name matches)

**Example:**
```kotlin
val registry = CredentialExchangeProtocolRegistry()
registry.register(DidCommExchangeProtocol(didCommService))
```

---

##### unregister

Unregisters a protocol.

```kotlin
fun unregister(protocolName: String)
```

**Parameters:**
- `protocolName`: Name of the protocol to unregister

**Throws:**
- Nothing (no-op if protocol not registered)

---

##### get

Gets a protocol by name.

```kotlin
fun get(protocolName: String): CredentialExchangeProtocol?
```

**Parameters:**
- `protocolName`: Name of the protocol

**Returns:**
- `CredentialExchangeProtocol?`: The protocol, or `null` if not found

---

##### getAll

Gets all registered protocols.

```kotlin
fun getAll(): Map<String, CredentialExchangeProtocol>
```

**Returns:**
- `Map<String, CredentialExchangeProtocol>`: Map of protocol name to protocol

---

##### getAllProtocolNames

Gets all registered protocol names.

```kotlin
fun getAllProtocolNames(): List<String>
```

**Returns:**
- `List<String>`: List of protocol names

---

##### isRegistered

Checks if a protocol is registered.

```kotlin
fun isRegistered(protocolName: String): Boolean
```

**Parameters:**
- `protocolName`: Name of the protocol

**Returns:**
- `Boolean`: `true` if registered, `false` otherwise

---

##### offerCredential

Creates a credential offer using the specified protocol.

```kotlin
suspend fun offerCredential(
    protocolName: String,
    request: CredentialOfferRequest
): CredentialOfferResponse
```

**Parameters:**
- `protocolName`: Name of the protocol to use
- `request`: Offer request

**Returns:**
- `CredentialOfferResponse`: Offer response

**Throws:**
- `IllegalArgumentException`: If protocol not registered
- `UnsupportedOperationException`: If protocol doesn't support OFFER_CREDENTIAL
- Protocol-specific errors

**Example:**
```kotlin
val offer = registry.offerCredential(
    protocolName = "didcomm",
    request = CredentialOfferRequest(...)
)
```

---

##### requestCredential

Requests a credential using the specified protocol.

```kotlin
suspend fun requestCredential(
    protocolName: String,
    request: CredentialRequestRequest
): CredentialRequestResponse
```

**Parameters:**
- `protocolName`: Name of the protocol to use
- `request`: Request request

**Returns:**
- `CredentialRequestResponse`: Request response

**Throws:**
- `IllegalArgumentException`: If protocol not registered
- `UnsupportedOperationException`: If protocol doesn't support REQUEST_CREDENTIAL
- Protocol-specific errors

---

##### issueCredential

Issues a credential using the specified protocol.

```kotlin
suspend fun issueCredential(
    protocolName: String,
    request: CredentialIssueRequest
): CredentialIssueResponse
```

**Parameters:**
- `protocolName`: Name of the protocol to use
- `request`: Issue request

**Returns:**
- `CredentialIssueResponse`: Issue response

**Throws:**
- `IllegalArgumentException`: If protocol not registered
- `UnsupportedOperationException`: If protocol doesn't support ISSUE_CREDENTIAL
- Protocol-specific errors

---

##### requestProof

Requests a proof using the specified protocol.

```kotlin
suspend fun requestProof(
    protocolName: String,
    request: ProofRequestRequest
): ProofRequestResponse
```

**Parameters:**
- `protocolName`: Name of the protocol to use
- `request`: Proof request

**Returns:**
- `ProofRequestResponse`: Proof request response

**Throws:**
- `IllegalArgumentException`: If protocol not registered
- `UnsupportedOperationException`: If protocol doesn't support REQUEST_PROOF
- Protocol-specific errors

---

##### presentProof

Presents a proof using the specified protocol.

```kotlin
suspend fun presentProof(
    protocolName: String,
    request: ProofPresentationRequest
): ProofPresentationResponse
```

**Parameters:**
- `protocolName`: Name of the protocol to use
- `request`: Presentation request

**Returns:**
- `ProofPresentationResponse`: Presentation response

**Throws:**
- `IllegalArgumentException`: If protocol not registered
- `UnsupportedOperationException`: If protocol doesn't support PRESENT_PROOF
- Protocol-specific errors

---

##### clear

Clears all registered protocols.

```kotlin
fun clear()
```

---

##### snapshot

Creates a snapshot of the registry.

```kotlin
fun snapshot(): CredentialExchangeProtocolRegistry
```

**Returns:**
- `CredentialExchangeProtocolRegistry`: New registry with current protocols

---

## Protocol-Specific Options

### DIDComm Options

| Option | Type | Required | Description |
|--------|------|----------|-------------|
| `fromKeyId` | `String` | ✅ Yes | Sender's key ID (format: `did:key:...#key-1`) |
| `toKeyId` | `String` | ✅ Yes | Recipient's key ID (format: `did:key:...#key-1`) |
| `encrypt` | `Boolean` | ❌ No | Whether to encrypt message (default: `true`) |
| `thid` | `String` | ❌ No | Thread ID for message threading |

**Example:**
```kotlin
options = mapOf(
    "fromKeyId" to "did:key:issuer#key-1",
    "toKeyId" to "did:key:holder#key-1",
    "encrypt" to true,
    "thid" to "thread-123"
)
```

---

### OIDC4VCI Options

| Option | Type | Required | Description |
|--------|------|----------|-------------|
| `credentialIssuer` | `String` | ✅ Yes | OIDC credential issuer URL |
| `credentialTypes` | `List<String>` | ❌ No | List of credential types |
| `redirectUri` | `String` | ❌ No | Redirect URI for authorization |
| `authorizationCode` | `String` | ❌ No | Authorization code (for token exchange) |

**Example:**
```kotlin
options = mapOf(
    "credentialIssuer" to "https://issuer.example.com",
    "credentialTypes" to listOf("VerifiableCredential", "PersonCredential"),
    "redirectUri" to "https://holder.example.com/callback"
)
```

---

### CHAPI Options

| Option | Type | Required | Description |
|--------|------|----------|-------------|
| (None) | - | - | CHAPI typically doesn't require additional options |

**Example:**
```kotlin
options = emptyMap()  // CHAPI doesn't require options
```

---

## Error Reference

### IllegalArgumentException

**When it occurs:**
- Protocol not registered
- Invalid argument format
- Missing required options

**Error message format:**
```
Protocol '<name>' not registered. Available: [<names>]
```

**Solutions:**
1. Register the protocol before use
2. Check available protocols: `registry.getAllProtocolNames()`
3. Verify argument format matches requirements

---

### UnsupportedOperationException

**When it occurs:**
- Protocol doesn't support the requested operation

**Error message format:**
```
Protocol '<name>' does not support <OPERATION> operation
```

**Solutions:**
1. Check supported operations: `protocol.supportedOperations`
2. Use a different protocol that supports the operation
3. Use a different operation that the protocol supports

---

### Protocol-Specific Errors

See [Error Handling Guide](./ERROR_HANDLING.md) for protocol-specific errors.

---

## Related Documentation

- **[Quick Start](./QUICK_START.md)** - Get started quickly (5 minutes)
- **[Error Handling](./ERROR_HANDLING.md)** - Complete error reference
- **[Workflows](./WORKFLOWS.md)** - Step-by-step workflows
- **[Examples](./EXAMPLES.md)** - Complete code examples
- **[Troubleshooting](./TROUBLESHOOTING.md)** - Common issues and solutions
- **[Glossary](./GLOSSARY.md)** - Terms and concepts
- **[Best Practices](./BEST_PRACTICES.md)** - Security and performance guidelines
- **[Versioning](./VERSIONING.md)** - Version info and migration guides
- **[Core Concepts](../../core-concepts/credential-exchange-protocols.md)** - Deep dive into protocol abstraction

