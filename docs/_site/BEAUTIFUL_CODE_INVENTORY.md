# The Most Beautiful Code in TrustWeave

**An inventory of elegant code snippets that showcase the power, simplicity, and elegance of TrustWeave's API design.**

---

## Table of Contents

1. [Protocol Abstraction](#1-protocol-abstraction)
2. [Declarative DSL Configuration](#2-declarative-dsl-configuration)
3. [Pluggable Architecture](#3-pluggable-architecture)
4. [Simple Facade API](#4-simple-facade-api)
5. [Chain-Agnostic Anchoring](#5-chain-agnostic-anchoring)
6. [Type-Safe Credential Issuance](#6-type-safe-credential-issuance)

---

## 1. Protocol Abstraction

### The Problem

Different credential exchange protocols (DIDComm, OIDC4VCI, CHAPI) have completely different APIs, message formats, encryption schemes, and transport layers. Supporting multiple protocols typically requires separate code paths for each.

### The Solution

**One unified API for all protocols.**

```kotlin
// Register protocols
val registry = CredentialExchangeProtocolRegistry()
registry.register(DidCommExchangeProtocol(didCommService))
registry.register(Oidc4VciExchangeProtocol(oidc4vciService))
registry.register(ChapiExchangeProtocol(chapiService))

// Use any protocol with identical API
val request = CredentialOfferRequest(
    issuerDid = "did:key:issuer",
    holderDid = "did:key:holder",
    credentialPreview = CredentialPreview(...)
)

// Switch protocols by changing one string
val didCommOffer = registry.offerCredential("didcomm", request)
val oidcOffer = registry.offerCredential("oidc4vci", request)
val chapiOffer = registry.offerCredential("chapi", request)
```

### Why It's Beautiful

**Before (without TrustWeave):**
```kotlin
// Each protocol requires completely different code
val didCommOffer = didCommService.createOffer(
    from = issuerDid,
    to = holderDid,
    credentialPreview = preview,
    encryptionKey = keyAgreementKey,
    signingKey = signingKey
)

val oidc4vciOffer = oidc4vciClient.requestCredentialOffer(
    issuerUrl = issuerEndpoint,
    clientId = oauthClientId,
    redirectUri = callbackUrl,
    scope = "credential_offer"
)

val chapiOffer = chapiHandler.createOfferMessage(
    credentialManifest = manifest,
    wallet = browserWallet,
    options = chapiOptions
)
```

**After (with TrustWeave):**
```kotlin
// One API, any protocol
val didCommOffer = registry.offerCredential("didcomm", request)
val oidc4vciOffer = registry.offerCredential("oidc4vci", request)
val chapiOffer = registry.offerCredential("chapi", request)
```

### Benefits

- ✅ **Write once, support all protocols** - Same code works for DIDComm, OIDC4VCI, CHAPI
- ✅ **Runtime flexibility** - Switch protocols without code changes
- ✅ **Future-proof** - Add new protocols without modifying application code
- ✅ **Type-safe** - Compile-time guarantees with unified interface

---

## 2. Declarative DSL Configuration

### The Problem

Configuring a trust layer requires setting up multiple services (KMS, DID methods, blockchain anchors, credential services) with complex interdependencies. Traditional configuration is verbose and error-prone.

### The Solution

**Declarative DSL that reads like configuration.**

```kotlin
val trustLayer = TrustLayer.build {
    keys {
        provider("inMemory")
        algorithm("Ed25519")
    }

    did {
        method("key") {
            algorithm("Ed25519")
        }
        method("web") {
            domain("example.com")
        }
    }

    anchor {
        chain("algorand:testnet") {
            provider("algorand")
        }
        chain("polygon:mainnet") {
            provider("polygon")
        }
    }

    trust {
        provider("inMemory")
    }
}
```

### Why It's Beautiful

**Before (without DSL):**
```kotlin
// Verbose, imperative configuration
val kms = InMemoryKeyManagementService()
val didMethod = DidKeyMethod(kms)
val didRegistry = DidMethodRegistry()
didRegistry.register("key", didMethod)

val algorandClient = AlgorandBlockchainAnchorClient(...)
val polygonClient = PolygonBlockchainAnchorClient(...)
val anchorRegistry = BlockchainAnchorRegistry()
anchorRegistry.register("algorand:testnet", algorandClient)
anchorRegistry.register("polygon:mainnet", polygonClient)

val trustRegistry = InMemoryTrustRegistry()
val config = TrustLayerConfig(
    kms = kms,
    didRegistry = didRegistry,
    anchorRegistry = anchorRegistry,
    trustRegistry = trustRegistry
)
val trustLayer = TrustLayer(config)
```

**After (with DSL):**
```kotlin
// Declarative, readable configuration
val trustLayer = TrustLayer.build {
    keys { provider("inMemory"); algorithm("Ed25519") }
    did { method("key") { algorithm("Ed25519") } }
    anchor {
        chain("algorand:testnet") { provider("algorand") }
        chain("polygon:mainnet") { provider("polygon") }
    }
    trust { provider("inMemory") }
}
```

### Benefits

- ✅ **Readable** - Configuration reads like documentation
- ✅ **Type-safe** - IDE autocomplete guides you
- ✅ **Composable** - Build complex configurations from simple blocks
- ✅ **Less boilerplate** - 80% less code than imperative style

---

## 3. Pluggable Architecture

### The Problem

Different use cases require different implementations:
- Different DID methods (did:key, did:web, did:ethr, did:polygon, did:ion)
- Different blockchains (Algorand, Ethereum, Polygon, Bitcoin, Cardano)
- Different KMS providers (in-memory, HSM, cloud KMS)
- Different credential storage (database, file system, cloud storage)

Supporting all combinations requires a flexible plugin system.

### The Solution

**Registry pattern with runtime plugin discovery.**

```kotlin
// Register DID methods
val didRegistry = DidMethodRegistry()
didRegistry.register("key", DidKeyMethod(kms))
didRegistry.register("web", DidWebMethod(domain = "example.com"))
didRegistry.register("ethr", EthrDidMethod(ethereumClient))
didRegistry.register("polygon", PolygonDidMethod(polygonClient))

// Use any method with same API
val did1 = didRegistry.create("key", options)
val did2 = didRegistry.create("web", options)
val did3 = didRegistry.create("ethr", options)

// Register blockchain anchors
val anchorRegistry = BlockchainAnchorRegistry()
anchorRegistry.register("algorand:testnet", algorandClient)
anchorRegistry.register("polygon:mainnet", polygonClient)
anchorRegistry.register("ethereum:sepolia", ethereumClient)

// Anchor to any chain with same API
val result1 = anchorRegistry.anchor("algorand:testnet", payload)
val result2 = anchorRegistry.anchor("polygon:mainnet", payload)
val result3 = anchorRegistry.anchor("ethereum:sepolia", payload)
```

### Why It's Beautiful

**Before (without pluggable architecture):**
```kotlin
// Hard-coded implementations
if (chainId == "algorand:testnet") {
    val client = AlgorandBlockchainAnchorClient(...)
    val result = client.writePayload(payload)
} else if (chainId == "polygon:mainnet") {
    val client = PolygonBlockchainAnchorClient(...)
    val result = client.writePayload(payload)
} else if (chainId == "ethereum:sepolia") {
    val client = EthereumBlockchainAnchorClient(...)
    val result = client.writePayload(payload)
} else {
    throw UnsupportedChainException(chainId)
}
```

**After (with pluggable architecture):**
```kotlin
// One API, any implementation
val result = anchorRegistry.anchor(chainId, payload)
```

### Benefits

- ✅ **Write once, deploy anywhere** - Same code works with any DID method or blockchain
- ✅ **Easy to extend** - Add new implementations without changing existing code
- ✅ **Testable** - Swap implementations for testing (e.g., in-memory for production)
- ✅ **Future-proof** - Support new chains/methods by registering new plugins

---

## 4. Simple Facade API

### The Problem

Creating verifiable credentials requires coordinating multiple services:
- Key management for signing
- DID creation and resolution
- Proof generation
- Credential serialization

This typically requires 50+ lines of setup code.

### The Solution

**One-line setup, three-line credential issuance.**

```kotlin
val trustweave = TrustWeave.create()

val issuerDid = trustweave.dids.create()
val credential = trustweave.credentials.issue(
    issuer = issuerDid.id,
    subject = buildJsonObject {
        put("name", "Alice")
        put("role", "Engineer")
    },
    config = IssuanceConfig(...),
    types = listOf("VerifiableCredential", "EmployeeCredential")
)
```

### Why It's Beautiful

**Before (without facade):**
```kotlin
// 50+ lines of setup
val kms = InMemoryKeyManagementService()
val key = kms.generateKey("Ed25519")
val didMethod = DidKeyMethod(kms)
val didRegistry = DidMethodRegistry().apply { register("key", didMethod) }
val didDoc = didMethod.createDid()
val issuerDid = didDoc.id
val keyId = didDoc.verificationMethod.first().id

val proofGenerator = Ed25519ProofGenerator(kms)
val credentialBuilder = VerifiableCredentialBuilder()
val credential = credentialBuilder
    .setId("credential:123")
    .setIssuer(issuerDid)
    .setTypes(listOf("VerifiableCredential", "EmployeeCredential"))
    .setCredentialSubject(buildJsonObject { ... })
    .setIssuanceDate(Instant.now())
    .build()

val proof = proofGenerator.generateProof(credential, keyId, issuerDid)
val signedCredential = credential.copy(proof = listOf(proof))
```

**After (with facade):**
```kotlin
// 3 lines
val trustweave = TrustWeave.create()
val issuerDid = trustweave.dids.create()
val credential = trustweave.credentials.issue(...)
```

### Benefits

- ✅ **95% less code** - From 50+ lines to 3 lines
- ✅ **Sensible defaults** - Works out of the box
- ✅ **Production-ready** - Same API works for prototypes and production
- ✅ **Progressive disclosure** - Start simple, customize when needed

---

## 5. Chain-Agnostic Anchoring

### The Problem

Different blockchains have different APIs:
- Algorand uses application calls
- Ethereum uses smart contracts
- Polygon uses similar contracts but different networks
- Bitcoin uses OP_RETURN outputs

Supporting multiple chains requires chain-specific code.

### The Solution

**One API for all blockchains using CAIP-2 chain identifiers.**

```kotlin
val anchorRegistry = BlockchainAnchorRegistry()
anchorRegistry.register("algorand:testnet", algorandClient)
anchorRegistry.register("polygon:mainnet", polygonClient)
anchorRegistry.register("ethereum:sepolia", ethereumClient)
anchorRegistry.register("bitcoin:mainnet", bitcoinClient)

// Anchor to any chain with same API
val payload = Json.encodeToJsonElement(credential)
val result = anchorRegistry.anchor("algorand:testnet", payload)

// Switch chains by changing one string
val result2 = anchorRegistry.anchor("polygon:mainnet", payload)
val result3 = anchorRegistry.anchor("ethereum:sepolia", payload)
```

### Why It's Beautiful

**Before (without abstraction):**
```kotlin
// Chain-specific code for each blockchain
when (chainId) {
    "algorand:testnet" -> {
        val appId = algorandClient.createApplication(...)
        val tx = algorandClient.callApplication(appId, payload)
        val result = AnchorResult(tx.hash, "algorand:testnet", appId)
    }
    "polygon:mainnet" -> {
        val contract = polygonClient.deployContract(...)
        val tx = polygonClient.callContract(contract, payload)
        val result = AnchorResult(tx.hash, "polygon:mainnet", contract)
    }
    "ethereum:sepolia" -> {
        val contract = ethereumClient.deployContract(...)
        val tx = ethereumClient.sendTransaction(contract, payload)
        val result = AnchorResult(tx.hash, "ethereum:sepolia", contract)
    }
    else -> throw UnsupportedChainException(chainId)
}
```

**After (with abstraction):**
```kotlin
// One API, any chain
val result = anchorRegistry.anchor(chainId, payload)
```

### Benefits

- ✅ **Chain-agnostic** - Write once, anchor to any blockchain
- ✅ **CAIP-2 standard** - Uses industry-standard chain identifiers
- ✅ **Portable** - `AnchorRef` works across all chains
- ✅ **Future-proof** - Add new chains by registering new clients

---

## 6. Type-Safe Credential Issuance

### The Problem

Creating verifiable credentials requires:
- Correct JSON-LD structure
- Proper proof generation
- Key management coordination
- DID resolution

Errors are often discovered at runtime.

### The Solution

**Type-safe DSL with compile-time guarantees.**

```kotlin
val credential = trustLayer.issue {
    credential {
        id("credential:123")
        type("EducationCredential", "DegreeCredential")
        issuer("did:key:university")
        subject {
            id("did:key:student")
            claim("degree", "Bachelor of Science")
            claim("university", "Example University")
            claim("graduationDate", "2023-05-15")
        }
        issued(Instant.now())
    }
    by(issuerDid = "did:key:university", keyId = "key-1")
}
```

### Why It's Beautiful

**Before (without DSL):**
```kotlin
// Error-prone, verbose manual construction
val credentialSubject = buildJsonObject {
    put("id", "did:key:student")
    put("degree", "Bachelor of Science")
    put("university", "Example University")
    put("graduationDate", "2023-05-15")
}

val credential = VerifiableCredential(
    id = "credential:123",
    type = listOf("VerifiableCredential", "EducationCredential", "DegreeCredential"),
    issuer = "did:key:university",
    credentialSubject = credentialSubject,
    issuanceDate = Instant.now(),
    proof = emptyList() // Must add proof separately
)

// Must manually generate proof
val proof = proofGenerator.generateProof(
    credential = credential,
    keyId = "did:key:university#key-1",
    issuerDid = "did:key:university"
)

val signedCredential = credential.copy(proof = listOf(proof))
```

**After (with DSL):**
```kotlin
// Type-safe, readable, complete in one block
val credential = trustLayer.issue {
    credential {
        id("credential:123")
        type("EducationCredential", "DegreeCredential")
        issuer("did:key:university")
        subject {
            id("did:key:student")
            claim("degree", "Bachelor of Science")
            claim("university", "Example University")
            claim("graduationDate", "2023-05-15")
        }
        issued(Instant.now())
    }
    by(issuerDid = "did:key:university", keyId = "key-1")
}
```

### Benefits

- ✅ **Type-safe** - Compile-time validation
- ✅ **Readable** - Code reads like the credential structure
- ✅ **Complete** - Proof generation handled automatically
- ✅ **Less error-prone** - IDE autocomplete prevents mistakes

---

## Summary

TrustWeave's elegant API design solves complex problems with simple, beautiful code:

1. **Protocol Abstraction** - One API for DIDComm, OIDC4VCI, CHAPI
2. **Declarative DSL** - Configuration that reads like documentation
3. **Pluggable Architecture** - Write once, deploy anywhere
4. **Simple Facade** - 95% less code for common operations
5. **Chain-Agnostic** - One API for all blockchains
6. **Type-Safe DSL** - Compile-time guarantees for credentials

**The result:** Complex decentralized identity operations become simple, readable, and maintainable.

---

*This inventory showcases the power of elegant abstraction. Each snippet demonstrates how TrustWeave turns protocol complexity into developer-friendly APIs.*

