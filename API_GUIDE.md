# TrustWeave API Guide

TrustWeave provides multiple APIs for different use cases. This guide helps you choose the right API for your needs.

## Which API Should I Use? 🤔

### 1. **TrustWeave Facade** (Recommended for Most Users) ✅

**Use when:** You want the simplest, most intuitive API

```kotlin
val TrustWeave = trustweave.create()

// Simple usage with error handling
val didResult = trustweave.createDid()
didResult.fold(
    onSuccess = { did -> println("Created: ${did.id}") },
    onFailure = { error -> println("Error: ${error.message}") }
)

// Or use getOrThrow for simple cases
val did = trustweave.createDid().getOrThrow()
val credential = trustweave.issueCredential(...).getOrThrow()
```

**Pros:** Simple, type-safe, sensible defaults, consistent error handling  
**Cons:** Less configuration flexibility

### 2. **Wallets & Wallet Factory** (For Credential Storage) 📦

**Use when:** You need type-safe credential storage with optional capabilities

```kotlin
val wallet = trustweave.createWallet(
    holderDid = "did:key:alice"
) {
    label = "Alice Wallet"
    enableOrganization = true
    enablePresentation = true
    property("storagePath", "/var/lib/wallets/alice")
}.getOrThrow()
```

Need an in-memory instance for tests? `Wallets.inMemory(holderDid)` still exists for quick fixtures.

**Pros:** Typed options, capability toggles, no reflection  
**Cons:** Provide your own `WalletFactory` for production storage

### 3. **Direct APIs** (For Advanced Users) ⚙️

**Use when:** You need fine-grained control or advanced features

```kotlin
val didMethod = DidKeyMockMethod(kms)
val doc = didMethod.createDid(options)
```

**Pros:** Maximum flexibility and control  
**Cons:** More verbose, requires manual setup

### 4. **DSL** (For Complex Workflows) 🎨

**Use when:** Building complex trust layer configurations

```kotlin
val trustLayer = trustLayer {
    keys { provider("inMemory") }
    did { method("key") }
}
```

**Pros:** Declarative, readable configuration  
**Cons:** Learning curve for DSL syntax

## Typed Configuration Building Blocks

| What | Builder | Example |
|------|---------|---------|
| DID creation | `didCreationOptions { … }` | `algorithm = DidCreationOptions.KeyAlgorithm.ED25519` |
| Wallet creation | `WalletCreationOptionsBuilder` via `createWallet { … }` | `enablePresentation = true; property("storagePath", "/data/wallets")` |
| Credential services | `credentialServiceCreationOptions { … }` | `endpoint = "https://issuer.example.com"; apiKey = secret` |

Every builder lives in a public package, so the same typed DSL is available whether you call the facade, wire up a `TrustLayerConfig`, or implement your own SPI provider.

## Error Handling

All TrustWeave facade operations return `Result<T>` for predictable error handling:

```kotlin
val result = trustweave.createDid()
result.fold(
    onSuccess = { did -> 
        println("Created: ${did.id}")
    },
    onFailure = { error ->
        when (error) {
            is TrustWeaveError.DidMethodNotRegistered -> {
                println("Method not registered: ${error.method}")
            }
            else -> {
                println("Error: ${error.message}")
            }
        }
    }
)
```

Or use `getOrThrow()` for simple cases where you want exceptions:

```kotlin
try {
    val did = trustweave.createDid().getOrThrow()
} catch (e: TrustWeaveException) {
    // Handle error
}
```

## Next Steps

- See [Getting Started](GETTING_STARTED.md) for code examples
- Read [Architecture & Modules](ARCHITECTURE.md) for module details
- Check [Available Plugins](PLUGINS.md) for integration options

