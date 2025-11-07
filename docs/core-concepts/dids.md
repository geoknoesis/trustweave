# Decentralized Identifiers (DIDs)

## What is a DID?

A **Decentralized Identifier (DID)** is a new type of identifier that enables verifiable, decentralized digital identity. A DID refers to any subject (person, organization, thing, data model, abstract entity, etc.) as determined by the controller of the DID.

## DID Format

A DID consists of three parts:

```
did:method:identifier
```

- **`did:`** - The scheme identifier
- **`method:`** - The DID method (e.g., `key`, `web`, `ion`)
- **`identifier:`** - The method-specific identifier

### Examples

```
did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK
did:web:example.com:user:alice
did:ion:EiClkZMDnmYGhX8tR8i3z2b5M5fN5hJ5vK5xL5yM5zN5oP5q
```

## DID Document

A **DID Document** is a JSON-LD document that describes how to use a DID. It contains:

- **Verification Methods**: Public keys for authentication and signing
- **Service Endpoints**: URLs for interacting with the DID
- **Authentication**: Methods for proving control of the DID
- **Key Agreement**: Methods for establishing secure channels

### Example DID Document

```json
{
  "@context": "https://www.w3.org/ns/did/v1",
  "id": "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK",
  "verificationMethod": [{
    "id": "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK#keys-1",
    "type": "Ed25519VerificationKey2020",
    "controller": "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK",
    "publicKeyMultibase": "z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK"
  }],
  "authentication": ["#keys-1"],
  "assertionMethod": ["#keys-1"]
}
```

## DID Methods

Different DID methods use different mechanisms for creating and resolving DIDs:

- **`did:key`**: Simple, self-contained DIDs based on public keys
- **`did:web`**: DIDs resolved via HTTPS from a domain
- **`did:ion`**: Microsoft's ION method using Bitcoin
- **`did:polygonid`**: Polygon-based DIDs
- **`did:indy`**: Hyperledger Indy DIDs

## Using DIDs in VeriCore

### Creating a DID

```kotlin
import io.geoknoesis.vericore.did.DidRegistry
import io.geoknoesis.vericore.did.DidMethod

// Register a DID method
val didMethod: DidMethod = // ... get DID method
DidRegistry.register(didMethod)

// Create a DID
val didDocument = DidRegistry.resolve("did:key:...")
val did = didDocument.id
```

### Resolving a DID

```kotlin
// Resolve a DID to its DID Document
val result = DidRegistry.resolve("did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK")
val document = result.document

// Access verification methods
val verificationMethod = document.verificationMethod.first()
```

### Updating a DID Document

```kotlin
// Update a DID Document
val updated = DidRegistry.resolve("did:key:...").let { result ->
    val method = DidRegistry.get("key")!!
    method.updateDid(result.document.id) { current ->
        current.copy(
            service = current.service + Service(
                id = "#service-1",
                type = "LinkedDomains",
                serviceEndpoint = "https://example.com"
            )
        )
    }
}
```

## Benefits of DIDs

1. **Self-Sovereign**: You control your own identity
2. **Portable**: Not tied to any single provider
3. **Privacy-Preserving**: You choose what to reveal
4. **Cryptographically Verifiable**: Proof of ownership
5. **Decentralized**: No central authority required

## Common Use Cases

- **Identity Verification**: Prove who you are without revealing personal data
- **Credential Issuance**: Receive credentials tied to your DID
- **Authentication**: Sign in to services using your DID
- **Decentralized Applications**: Use DIDs in dApps

## Next Steps

- Learn about [Verifiable Credentials](verifiable-credentials.md) that use DIDs
- Explore [Wallets](wallets.md) for managing DIDs and credentials
- Check out the [DID API Reference](../api-reference/did-api.md)

