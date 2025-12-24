---
title: DIDComm V2 Plugin
---

# DIDComm V2 Plugin

DIDComm V2 implementation for TrustWeave, providing secure, private, and decentralized messaging using Decentralized Identifiers (DIDs).

## Overview

This plugin implements the DIDComm V2 protocol specification, enabling:

- **Secure Messaging**: Encrypted communication using ECDH-1PU (AuthCrypt)
- **Protocol Support**: Issue Credential, Present Proof, Basic Message protocols
- **Message Threading**: Support for conversation threads
- **Credential Exchange**: Secure credential issuance and presentation

## Features

- ✅ JWM (JSON Web Message) format support
- ✅ Message packing and unpacking
- ✅ Protocol message builders (Issue Credential, Present Proof, Basic Message)
- ✅ In-memory message storage
- ✅ Thread-based message organization
- ⚠️ **Crypto Implementation**:
  - Placeholder implementation included (for development)
  - Production implementation ready (requires `didcomm-java` library)
  - See [Integration Guide](./didcomm-integration-guide.md) for production setup

## Installation

Add the DIDComm plugin to your dependencies:

```kotlin
dependencies {
    implementation(project(":credentials:plugins:didcomm"))
}
```

## Usage

### Basic Setup

```kotlin
import org.trustweave.credential.didcomm.*
import org.trustweave.credential.didcomm.protocol.*
import org.trustweave.kms.*
import org.trustweave.did.*

val kms = InMemoryKeyManagementService()

// DID resolution function
val resolveDid: suspend (String) -> DidDocument? = { did ->
    // Your DID resolution logic
    resolveDidDocument(did)
}

// Create DIDComm service
val didcomm = DidCommFactory.createInMemoryService(kms, resolveDid)
```

### Sending a Basic Message

```kotlin
val message = BasicMessageProtocol.createBasicMessage(
    fromDid = "did:key:alice",
    toDid = "did:key:bob",
    content = "Hello, Bob!"
)

val messageId = didcomm.sendMessage(
    message = message,
    fromDid = "did:key:alice",
    fromKeyId = "did:key:alice#key-1",
    toDid = "did:key:bob",
    toKeyId = "did:key:bob#key-1",
    encrypt = true
)
```

### Receiving a Message

```kotlin
val received = didcomm.receiveMessage(
    packedMessage = packedMessageJson,
    recipientDid = "did:key:bob",
    recipientKeyId = "did:key:bob#key-1",
    senderDid = "did:key:alice"
)

val content = BasicMessageProtocol.extractContent(received)
println("Received: $content")
```

### Credential Exchange

#### Issuing a Credential

```kotlin
// 1. Create credential offer
val preview = CredentialProtocol.CredentialPreview(
    attributes = listOf(
        CredentialProtocol.CredentialAttribute("name", "Alice"),
        CredentialProtocol.CredentialAttribute("email", "alice@example.com")
    )
)

val offer = CredentialProtocol.createCredentialOffer(
    fromDid = issuerDid,
    toDid = holderDid,
    credentialPreview = preview
)

didcomm.sendMessage(offer, issuerDid, issuerKeyId, holderDid, holderKeyId)

// 2. Holder requests credential
val request = CredentialProtocol.createCredentialRequest(
    fromDid = holderDid,
    toDid = issuerDid,
    thid = offer.id
)

didcomm.sendMessage(request, holderDid, holderKeyId, issuerDid, issuerKeyId)

// 3. Issuer issues credential
val credential = // ... create verifiable credential
val issue = CredentialProtocol.createCredentialIssue(
    fromDid = issuerDid,
    toDid = holderDid,
    credential = credential,
    thid = request.id
)

didcomm.sendMessage(issue, issuerDid, issuerKeyId, holderDid, holderKeyId)

// 4. Extract credential from message
val receivedIssue = didcomm.receiveMessage(packedIssue, holderDid, holderKeyId, issuerDid)
val receivedCredential = CredentialProtocol.extractCredential(receivedIssue)
```

### Proof Presentation

```kotlin
// 1. Verifier requests proof
val proofRequest = ProofProtocol.ProofRequest(
    name = "Proof of Age",
    requestedAttributes = mapOf(
        "age" to ProofProtocol.RequestedAttribute(
            name = "age",
            restrictions = listOf(
                ProofProtocol.AttributeRestriction(
                    schemaId = "https://example.com/schemas/age"
                )
            )
        )
    )
)

val request = ProofProtocol.createProofRequest(
    fromDid = verifierDid,
    toDid = proverDid,
    proofRequest = proofRequest
)

didcomm.sendMessage(request, verifierDid, verifierKeyId, proverDid, proverKeyId)

// 2. Prover presents proof
val presentation = // ... create verifiable presentation
val presentationMsg = ProofProtocol.createProofPresentation(
    fromDid = proverDid,
    toDid = verifierDid,
    presentation = presentation,
    thid = request.id
)

didcomm.sendMessage(presentationMsg, proverDid, proverKeyId, verifierDid, verifierKeyId)

// 3. Extract presentation
val receivedPresentation = didcomm.receiveMessage(
    packedPresentation,
    verifierDid,
    verifierKeyId,
    proverDid
)
val presentation = ProofProtocol.extractPresentation(receivedPresentation)
```

### Message Threading

```kotlin
val thid = "conversation-123"

val message1 = BasicMessageProtocol.createBasicMessage(
    fromDid = "did:key:alice",
    toDid = "did:key:bob",
    content = "First message",
    thid = thid
)

val message2 = BasicMessageProtocol.createBasicMessage(
    fromDid = "did:key:bob",
    toDid = "did:key:alice",
    content = "Reply",
    thid = thid
)

// Retrieve all messages in thread
val threadMessages = didcomm.getThreadMessages(thid)
```

## Architecture

### Components

- **DidCommMessage**: Core message model following JWM format
- **DidCommEnvelope**: Encrypted message envelope
- **DidCommCrypto**: Placeholder cryptographic operations (for development)
- **DidCommCryptoProduction**: Production crypto using `didcomm-java` library
- **DidCommCryptoAdapter**: Adapter to switch between implementations
- **DidCommPacker**: Message packing/unpacking
- **DidCommService**: Service interface for messaging
- **Protocol Helpers**: Builders for common protocols

### Message Flow

1. **Packing**: Message → JSON → Encryption → Envelope
2. **Transport**: Envelope sent via HTTP, WebSocket, etc.
3. **Unpacking**: Envelope → Decryption → JSON → Message

### Encryption

DIDComm V2 uses:
- **ECDH-1PU**: Authenticated key agreement (AuthCrypt)
- **AES-256-GCM**: Content encryption
- **AES-256-KW**: Key wrapping

**Implementation Status:**
- Placeholder implementation included (returns dummy data)
- Production implementation available via `didcomm-java` library
- See [Crypto Implementation Notes](./didcomm-crypto-implementation-notes.md) for details

## Protocol Support

### Issue Credential Protocol

- `offer-credential`: Issuer offers credential
- `request-credential`: Holder requests credential
- `issue-credential`: Issuer issues credential
- `ack`: Acknowledgment

### Present Proof Protocol

- `request-presentation`: Verifier requests proof
- `presentation`: Prover presents proof
- `ack`: Acknowledgment

### Basic Message Protocol

- `message`: Simple text messages

## Production Setup

⚠️ **Important**: The default implementation uses placeholder crypto (returns dummy data). For production:

1. **Add didcomm-java dependency** to `build.gradle.kts`:
   ```kotlin
   implementation("org.didcommx:didcomm:0.3.2")
   ```

2. **Uncomment production code** in `DidCommCryptoProduction.kt`

3. **Enable production crypto** in factory:
   ```kotlin
   val didcomm = DidCommFactory.createInMemoryService(
       kms = kms,
       resolveDid = resolveDid,
       useProductionCrypto = true
   )
   ```

See [Integration Guide](./didcomm-integration-guide.md) for detailed instructions.

## Limitations

1. **Message Delivery**: The in-memory service doesn't actually deliver messages. Implement HTTP/WebSocket delivery for real-world use.

2. **Key Management**: Ensure proper key management for encryption keys (private key access required).

3. **Error Handling**: Add comprehensive error handling for edge cases.

4. **Testing**: Expand test coverage for cryptographic operations.

## References

- [DIDComm V2 Specification](https://didcomm.org/book/v2/)
- [JWM Specification](https://datatracker.ietf.org/doc/html/draft-ietf-oauth-jwsreq-26)
- [Issue Credential Protocol](https://github.com/decentralized-identity/waci-presentation-exchange)
- [Present Proof Protocol](https://github.com/decentralized-identity/presentation-exchange)

## License

Part of TrustWeave - see main project LICENSE file.

