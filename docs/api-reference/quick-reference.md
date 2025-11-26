---
title: Quick Reference
nav_order: 1
parent: API Reference
keywords:
  - quick reference
  - api methods
  - methods
  - lookup
  - cheat sheet
---

# API Quick Reference

Quick lookup table of all TrustWeave API methods organized by category.

## DIDs

| Method | Description | Link |
|--------|-------------|------|
| `createDid { }` | Create a new DID | [createDid()](core-api.md#create) |
| `resolveDid(did)` | Resolve a DID to get its document | [resolveDid()](core-api.md#resolve) |
| `updateDid { }` | Update a DID document | [updateDid()](core-api.md#update) |
| `deactivateDid(did)` | Deactivate a DID | [deactivateDid()](core-api.md#deactivate) |

## Credentials

| Method | Description | Link |
|--------|-------------|------|
| `issue { }` | Issue a verifiable credential | [issue()](core-api.md#issue) |
| `verify { }` | Verify a verifiable credential | [verify()](core-api.md#verify) |

## Wallets

| Method | Description | Link |
|--------|-------------|------|
| `wallet { }` | Create a wallet | [wallet()](core-api.md#wallet) |
| `wallet.store(credential)` | Store a credential in wallet | [Wallet API](wallet-api.md#credentialstorage) |
| `wallet.get(credentialId)` | Get credential by ID | [Wallet API](wallet-api.md#credentialstorage) |
| `wallet.list()` | List all credentials | [Wallet API](wallet-api.md#credentialstorage) |
| `wallet.query { }` | Query credentials with filters | [Wallet API](wallet-api.md#credentialstorage) |

## Blockchain Anchoring

| Method | Description | Link |
|--------|-------------|------|
| `anchor { }` | Anchor data to blockchain | [anchor()](core-api.md#anchor) |
| `readAnchor { }` | Read anchored data | [readAnchor()](core-api.md#readanchor) |

## Delegation

| Method | Description | Link |
|--------|-------------|------|
| `delegate { }` | Delegate authority between DIDs | [delegate()](core-api.md#delegate) |

## Key Management

| Method | Description | Link |
|--------|-------------|------|
| `rotateKey { }` | Rotate keys in DID documents | [rotateKey()](core-api.md#rotatekey) |

## Methods by Category

### DID Operations

**Create DID:**
```kotlin
import com.trustweave.trust.types.Did

val did: Did = trustWeave.createDid {
    method("key")
    algorithm("Ed25519")
}
// Access DID string value: did.value
```

**Resolve DID:**
```kotlin
import com.trustweave.trust.types.Did

val did = Did("did:key:example")
val document = trustWeave.context.resolveDid(did)
```

**Update DID:**
```kotlin
val updated = trustLayer.updateDid {
    did("did:key:example")
    addService { ... }
}
```

**Deactivate DID:**
```kotlin
val deactivated = trustLayer.deactivateDid("did:key:example")
```

### Credential Operations

**Issue Credential:**
```kotlin
import com.trustweave.trust.types.IssuerIdentity

val issuerIdentity = IssuerIdentity.from(issuerDid, keyId)

val credential = trustWeave.issue {
    credential {
        type("VerifiableCredential", "PersonCredential")
        issuer(issuerDid)
        subject {
            id("did:key:holder")
            claim("name", "Alice")
        }
    }
    signedBy(issuerIdentity)
}
```

**Verify Credential:**
```kotlin
import com.trustweave.trust.types.VerificationResult

val result: VerificationResult = trustWeave.verify {
    credential(credential)
}

when (result) {
    is VerificationResult.Valid -> println("Valid!")
    is VerificationResult.Invalid.Expired -> println("Expired")
    // ... exhaustive error handling
}
```

### Wallet Operations

**Create Wallet:**
```kotlin
val wallet = trustLayer.wallet {
    holder("did:key:holder")
    enableOrganization()
    enablePresentation()
}
```

**Store Credential:**
```kotlin
val credentialId = wallet.store(credential)
```

**Query Credentials:**
```kotlin
val results = wallet.query {
    byIssuer("did:key:issuer")
    notExpired()
    byType("PersonCredential")
}
```

### Blockchain Operations

**Anchor Data:**
```kotlin
val anchorResult = trustLayer.anchor {
    data(digest)
    chain("algorand:testnet")
}
```

**Read Anchored Data:**
```kotlin
val data = trustLayer.readAnchor<MyData> {
    ref(anchorRef)
}
```

### Delegation Operations

**Delegate Authority:**
```kotlin
val delegation = trustLayer.delegate {
    from("did:key:delegator")
    to("did:key:delegatee")
    capability("issue")
}
```

### Key Rotation

**Rotate Key:**
```kotlin
val updated = trustLayer.rotateKey {
    did("did:key:example")
    oldKeyId("did:key:example#key-1")
    newAlgorithm("Ed25519")
}
```

## Related Documentation

- **[Core API](core-api.md)** - Complete API reference with parameters
- **[Wallet API](wallet-api.md)** - Complete wallet API reference
- **[Credential Service API](credential-service-api.md)** - Lower-level credential API
- **[Error Types](error-types.md)** - Complete error type reference
- **[Data Types](data-types.md)** - Complete data type reference

