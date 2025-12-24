---
title: DIDComm V2 Quick Start
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
import org.trustweave.testkit.InMemoryKeyManagementService
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

## Production Setup

For production use, you need to enable production crypto:

1. Add dependency:
```kotlin
implementation("org.didcommx:didcomm:0.3.2")
```

2. Enable production crypto:
```kotlin
val didcomm = DidCommFactory.createInMemoryService(
    kms = kms,
    resolveDid = resolveDid,
    useProductionCrypto = true
)
```

See [INTEGRATION_GUIDE.md](INTEGRATION_GUIDE.md) for complete setup instructions.

## Next Steps

- Read [README.md](README.md) for full documentation
- See [INTEGRATION_GUIDE.md](INTEGRATION_GUIDE.md) for production setup
- Check [CRYPTO_IMPLEMENTATION_NOTES.md](CRYPTO_IMPLEMENTATION_NOTES.md) for crypto details
- Review [IMPLEMENTATION.md](IMPLEMENTATION.md) for architecture

