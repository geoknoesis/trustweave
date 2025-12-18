---
title: GoDiddy Integration
parent: Integration Modules
---

# GoDiddy Integration

> This guide covers the GoDiddy integration for TrustWeave. The GoDiddy plugin provides HTTP integration with GoDiddy services, including Universal Resolver, Registrar, Issuer, and Verifier, supporting 20+ DID methods.

## Overview

The `did/plugins/godiddy` module provides HTTP integration with GoDiddy services for DID operations. This integration enables you to:

- Resolve DIDs using GoDiddy Universal Resolver
- Register DIDs via GoDiddy Registrar
- Issue credentials using GoDiddy Issuer
- Verify credentials using GoDiddy Verifier
- Support 20+ DID methods through GoDiddy's universal interfaces

## Installation

Add the GoDiddy module to your dependencies:

```kotlin
dependencies {
    implementation("com.trustweave.did:godiddy:1.0.0-SNAPSHOT")
    implementation("com.trustweave:trustweave-did:1.0.0-SNAPSHOT")
    implementation("com.trustweave:trustweave-common:1.0.0-SNAPSHOT")

    // HTTP client (OkHttp recommended)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
```

## Configuration

### Basic Configuration

```kotlin
import com.trustweave.godiddy.*
import com.trustweave.did.*

// Create configuration
val config = GodiddyConfig(
    baseUrl = "https://api.godiddy.com",  // Default public API
    timeout = 30000,
    apiKey = null  // Optional API key if required
)

// Create GoDiddy client
val client = GodiddyClient(config)

// Create DID method
val method = GodiddyDidMethod(client, config)
```

### Custom Configuration

```kotlin
// Use self-hosted GoDiddy instance
val config = GodiddyConfig(
    baseUrl = "https://godiddy.example.com",
    timeout = 60000,
    apiKey = "your-api-key"
)

val client = GodiddyClient(config)
val method = GodiddyDidMethod(client, config)
```

### SPI Auto-Discovery

When the `did/plugins/godiddy` module is on the classpath, GoDiddy is automatically available:

```kotlin
import com.trustweave.did.*
import java.util.ServiceLoader

// Discover GoDiddy provider
val providers = ServiceLoader.load(DidMethodProvider::class.java)
val godiddyProvider = providers.find { it.supportedMethods.contains("godiddy") }

// Create method with configuration
val options = didCreationOptions {
    property("baseUrl", "https://api.godiddy.com")
    property("timeout", 30000L)
}

val method = godiddyProvider?.create("godiddy", options)
```

## Usage Examples

### DID Resolution

```kotlin
import com.trustweave.godiddy.*

val client = GodiddyClient(GodiddyConfig.default())
val resolver = GodiddyResolver(client)

// Resolve any DID via Universal Resolver
val did = "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK"
val resolutionResult = resolver.resolveDid(did)

resolutionResult.fold(
    onSuccess = { result ->
        println("Resolved DID: ${result.didDocument?.id}")
        println("Document: ${result.didDocument}")
    },
    onFailure = { error ->
        println("Resolution failed: ${error.message}")
    }
)
```

### DID Registration

```kotlin
import com.trustweave.godiddy.*

val client = GodiddyClient(GodiddyConfig.default())
val registrar = GodiddyRegistrar(client)

// Register DID via Registrar
val options = didCreationOptions {
    property("method", "key")
    property("algorithm", "Ed25519")
}

val registrationResult = registrar.registerDid(options)

registrationResult.fold(
    onSuccess = { didDoc ->
        println("Registered DID: ${didDoc.id}")
    },
    onFailure = { error ->
        println("Registration failed: ${error.message}")
    }
)
```

### Credential Issuance

```kotlin
import com.trustweave.godiddy.*

val client = GodiddyClient(GodiddyConfig.default())
val issuer = GodiddyIssuer(client)

// Issue credential via GoDiddy Issuer
val credentialRequest = mapOf(
    "issuerDid" to "did:key:z6Mk...",
    "credentialSubject" to mapOf(
        "id" to "did:key:z6Mk...",
        "type" to "VerifiableCredential",
        "credentialSubject" to mapOf(
            "id" to "did:example:subject",
            "name" to "Alice"
        )
    )
)

val issuanceResult = issuer.issueCredential(credentialRequest)

issuanceResult.fold(
    onSuccess = { credential ->
        println("Issued credential: ${credential.id}")
    },
    onFailure = { error ->
        println("Issuance failed: ${error.message}")
    }
)
```

### Credential Verification

```kotlin
import com.trustweave.godiddy.*

val client = GodiddyClient(GodiddyConfig.default())
val verifier = GodiddyVerifier(client)

// Verify credential via GoDiddy Verifier
val verificationResult = verifier.verifyCredential(credential)

verificationResult.fold(
    onSuccess = { result ->
        if (result.valid) {
            println("Credential is valid")
        } else {
            println("Credential is invalid: ${result.reason}")
        }
    },
    onFailure = { error ->
        println("Verification failed: ${error.message}")
    }
)
```

## Supported DID Methods

GoDiddy supports 20+ DID methods through Universal Resolver, including:

- did:key
- did:web
- did:ethr
- did:ion
- did:cheqd
- did:pkh
- did:polygonid
- did:iden3
- And many more...

See the [GoDiddy Documentation](https://godiddy.com/docs) for a complete list of supported methods.

## Error Handling

The GoDiddy integration follows TrustWeave's error handling patterns:

```kotlin
import com.trustweave.core.exception.TrustWeaveError

val result = resolver.resolveDid(did)
result.fold(
    onSuccess = { result -> /* handle success */ },
    onFailure = { error ->
        when (error) {
            is TrustWeaveError.DidResolutionFailed -> {
                println("Resolution failed: ${error.reason}")
            }
            is TrustWeaveError.NetworkError -> {
                println("Network error: ${error.message}")
            }
            else -> println("Error: ${error.message}")
        }
    }
)
```

## Configuration Options

### GodiddyConfig

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `baseUrl` | String | `"https://api.godiddy.com"` | Base URL for GoDiddy services |
| `timeout` | Long | `30000` | HTTP request timeout in milliseconds |
| `apiKey` | String? | `null` | API key for authentication (if required) |

## Testing

```bash
# Run all GoDiddy tests
./gradlew :did/plugins/godiddy:test

# Run specific test class
./gradlew :did/plugins/godiddy:test --tests "GodiddyDidMethodTest"
```

## Next Steps

- Review [DID Concepts](../core-concepts/dids.md) for DID fundamentals
- See [Verifiable Credentials](../core-concepts/verifiable-credentials.md) for credential workflows
- Check [Creating Plugins](../contributing/creating-plugins.md) to understand DID method implementation
- Explore [Other DID Integrations](README.md) for alternative DID methods

## References

- [GoDiddy Documentation](https://godiddy.com/docs)
- [Universal Resolver](https://dev.uniresolver.io/)
- [TrustWeave DID Module](../modules/trustweave-did.md)
- [TrustWeave Core API](../api-reference/core-api.md)

