# TrustWeave API Guide

TrustWeave provides multiple APIs for different use cases. This guide helps you choose the right API for your needs.

## Which API Should I Use? 🤔

### 1. **TrustWeave Facade** (Recommended for Most Users) ✅

**Use when:** You want the simplest, most intuitive API

```kotlin
// Create TrustWeave instance with configuration
val trustWeave = TrustWeave.build {
    keys {
        provider("inMemory")
        algorithm("Ed25519")
    }
    did {
        method("key") {
            algorithm("Ed25519")
        }
    }
}

// Simple usage with domain-specific error handling
import com.trustweave.did.exception.DidException
import com.trustweave.did.exception.DidException.DidMethodNotRegistered
import com.trustweave.credential.exception.CredentialException
import com.trustweave.credential.exception.CredentialException.CredentialIssuanceFailed
import com.trustweave.trust.types.IssuerIdentity
import com.trustweave.core.exception.TrustWeaveException

try {
    val did = trustWeave.createDid {
        method("key")
        algorithm("Ed25519")
    }
    println("Created: $did")

    val credential = trustWeave.issue {
        credential {
            type("VerifiableCredential", "ExampleCredential")
            issuer(did)
            subject {
                id("did:key:holder")
                "name" to "Alice"
            }
        }
        signedBy(IssuerIdentity.from(did, "$did#key-1"))
    }
    println("Issued credential: ${credential.id}")
} catch (error: DidException) {
    when (error) {
        is DidMethodNotRegistered -> {
            println("❌ DID method not registered: ${error.method}")
        }
        else -> {
            println("❌ DID error: ${error.message}")
        }
    }
} catch (error: CredentialException) {
    when (error) {
        is CredentialIssuanceFailed -> {
            println("❌ Credential issuance failed: ${error.reason}")
        }
        else -> {
            println("❌ Credential error: ${error.message}")
        }
    }
} catch (error: TrustWeaveException) {
    println("❌ TrustWeave error [${error.code}]: ${error.message}")
} catch (error: Exception) {
    println("❌ Unexpected error: ${error.message}")
}
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
val trustWeave = TrustWeave.build {
    keys {
        provider("inMemory")
        algorithm("Ed25519")
    }
    did {
        method("key") {
            algorithm("Ed25519")
        }
    }
    anchor {
        chain("algorand:testnet") {
            provider("algorand")
        }
    }
    trust {
        provider("inMemory")
    }
}
```

**Pros:** Declarative, readable configuration, type-safe
**Cons:** Learning curve for DSL syntax

## Typed Configuration Building Blocks

| What | Builder | Example |
|------|---------|---------|
| DID creation | `didCreationOptions { … }` | `algorithm = DidCreationOptions.KeyAlgorithm.ED25519` |
| Wallet creation | `WalletCreationOptionsBuilder` via `createWallet { … }` | `enablePresentation = true; property("storagePath", "/data/wallets")` |
| Credential services | `credentialServiceCreationOptions { … }` | `endpoint = "https://issuer.example.com"; apiKey = secret` |

Every builder lives in a public package, so the same typed DSL is available whether you call the facade, wire up a `TrustLayerConfig`, or implement your own SPI provider.

## Error Handling

All TrustWeave facade operations throw exceptions on failure. Always use try-catch blocks:

```kotlin
try {
    val did = trustWeave.createDid {
        method("key")
        algorithm("Ed25519")
    }
    println("Created: $did")
} catch (error: IllegalStateException) {
    // Handle configuration or state errors
    println("DID creation failed: ${error.message}")
} catch (error: IllegalArgumentException) {
    // Handle invalid parameters
    println("Invalid parameter: ${error.message}")
} catch (error: Exception) {
    // Handle other errors
    println("Unexpected error: ${error.message}")
}
```

**Exception Types:**
- `IllegalStateException`: Configuration errors, missing services, invalid state
- `IllegalArgumentException`: Invalid parameters, malformed input
- Domain-specific exceptions: DID, credential, or wallet-specific errors

## Next Steps

- See [Getting Started](GETTING_STARTED.md) for code examples
- Read [Architecture & Modules](ARCHITECTURE.md) for module details
- Check [Available Plugins](PLUGINS.md) for integration options

