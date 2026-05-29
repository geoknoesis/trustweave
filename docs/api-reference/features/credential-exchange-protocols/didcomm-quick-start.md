---
title: DIDComm V2 Quick Start
redirect_from:
  - /features/credential-exchange-protocols/didcomm-quick-start/
parent: Feature Reference
grand_parent: API Reference
---

# DIDComm V2 Quick Start

## Installation

Add to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation(project(":credentials:plugins:didcomm"))
}
```

## Basic Usage

### 1. Setup

```kotlin
import org.trustweave.credential.didcomm.*
import org.trustweave.credential.didcomm.protocol.*
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import org.trustweave.did.*

val kms = InMemoryKeyManagementService()

// DID resolution function
val resolveDid: suspend (String) -> DidDocument? = { did ->
    // Your DID resolution logic
    resolveDidDocument(did)
}

// Create DIDComm service
val didcomm = DidCommFactory.createInMemoryServiceWithPlaceholderCrypto(kms, resolveDid)
```

### 2. Send a Basic Message

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

### 3. Receive a Message

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

### 4. Credential Exchange

```kotlin
// Issuer creates offer
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

// Holder requests credential
val request = CredentialProtocol.createCredentialRequest(
    fromDid = holderDid,
    toDid = issuerDid,
    thid = offer.id
)

didcomm.sendMessage(request, holderDid, holderKeyId, issuerDid, issuerKeyId)

// Issuer issues credential
val credential = // ... create verifiable credential
val issue = CredentialProtocol.createCredentialIssue(
    fromDid = issuerDid,
    toDid = holderDid,
    credential = credential,
    thid = request.id
)

didcomm.sendMessage(issue, issuerDid, issuerKeyId, holderDid, holderKeyId)
```

## Production setup

The plugin already ships **didcomm-java**. Provide a **`SecretResolver`** and use:

```kotlin
val didcomm = DidCommFactory.createInMemoryService(kms, resolveDid, secretResolver)
```

For demos without real keys, use **`createInMemoryServiceWithPlaceholderCrypto`**.

See [didcomm-integration-guide.md](didcomm-integration-guide.md) for details.

## Next Steps

- Read [README.md](README.md) for full documentation
- See [didcomm-integration-guide.md](didcomm-integration-guide.md) for production setup
- Check [didcomm-crypto-implementation-notes.md](didcomm-crypto-implementation-notes.md) for crypto details
- Review [didcomm-implementation.md](didcomm-implementation.md) for architecture

