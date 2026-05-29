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
    implementation("org.trustweave:did-plugins-godiddy:0.6.0")
    implementation("org.trustweave:did-did-core:0.6.0")
    implementation("org.trustweave:common:0.6.0")

    // HTTP client (OkHttp recommended)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
```

## Configuration

### Basic Configuration

```kotlin
import org.trustweave.godiddy.*
import org.trustweave.did.*

// Create configuration
val config = GodiddyConfig(
    baseUrl = "https://api.godiddy.com",  // Default public API
    timeout = 30000,
    apiKey = null  // Optional API key if required
)

// Create GoDiddy client
val client = GodiddyClient(config)

// Build a DID method (supplying method name, resolver, and optional registrar)
import org.trustweave.godiddy.resolver.GodiddyResolver
import org.trustweave.godiddy.registrar.GodiddyRegistrar
import org.trustweave.godiddy.did.GodiddyDidMethod

val resolver = GodiddyResolver(client)
val registrar = GodiddyRegistrar(client)
val method = GodiddyDidMethod(method = "key", resolver = resolver, registrar = registrar)
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
val resolver = GodiddyResolver(client)
val registrar = GodiddyRegistrar(client)
val method = GodiddyDidMethod(method = "key", resolver = resolver, registrar = registrar)
```

### SPI Auto-Discovery

When the `did/plugins/godiddy` module is on the classpath, GoDiddy is automatically available:

```kotlin
import org.trustweave.did.*
import org.trustweave.did.spi.DidMethodProvider
import java.util.ServiceLoader

// Discover GoDiddy provider (matches any method it supports, e.g. "key", "web", "ethr"...)
val providers = ServiceLoader.load(DidMethodProvider::class.java)
val godiddyProvider = providers.find { it.name == "godiddy" }

// Create method with configuration. Note: pass the underlying DID method name
// (e.g. "key", "web", "ethr") — there is no "godiddy" DID method itself.
val options = didCreationOptions {
    property("baseUrl", "https://api.godiddy.com")
    property("timeout", 30000L)
}

val method = godiddyProvider?.create("key", options)
```

## Usage Examples

### DID Resolution

```kotlin
import org.trustweave.godiddy.*
import org.trustweave.godiddy.resolver.GodiddyResolver
import org.trustweave.did.resolver.DidResolutionResult
import kotlinx.coroutines.runBlocking

val client = GodiddyClient(GodiddyConfig.default())
val resolver = GodiddyResolver(client)

// Resolve any DID via Universal Resolver (suspend, so run in a coroutine)
runBlocking {
    val did = "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK"
    val resolutionResult = resolver.resolveDid(did)

    when (resolutionResult) {
        is DidResolutionResult.Success -> {
            println("Resolved DID: ${resolutionResult.document.id}")
            println("Document: ${resolutionResult.document}")
        }
        is DidResolutionResult.Failure -> {
            println("Resolution failed: ${resolutionResult::class.simpleName}")
        }
    }
}
```

### DID Registration

```kotlin
import org.trustweave.godiddy.*
import org.trustweave.godiddy.registrar.GodiddyRegistrar
import org.trustweave.did.registrar.model.CreateDidOptions
import org.trustweave.did.registrar.model.KeyManagementMode
import org.trustweave.did.registrar.model.OperationState
import kotlinx.coroutines.runBlocking

val client = GodiddyClient(GodiddyConfig.default())
val registrar = GodiddyRegistrar(client)

// Register DID via Universal Registrar (returns DidRegistrationResponse with didState)
runBlocking {
    val options = CreateDidOptions(
        keyManagementMode = KeyManagementMode.INTERNAL_SECRET,
        storeSecrets = false,
        returnSecrets = false
    )

    val response = registrar.createDid(method = "key", options = options)
    when (response.didState.state) {
        OperationState.FINISHED -> println("Registered DID: ${response.didState.did}")
        OperationState.WAIT     -> println("Pending (jobId=${response.jobId})")
        else                    -> println("Registration failed: ${response.didState.reason}")
    }
}
```

### Credential Issuance

```kotlin
import org.trustweave.godiddy.*
import org.trustweave.godiddy.issuer.GodiddyIssuer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*

val client = GodiddyClient(GodiddyConfig.default())
val issuer = GodiddyIssuer(client)

// Issue credential via GoDiddy Issuer (suspend; takes a JsonObject + options map)
runBlocking {
    val credentialJson: JsonObject = buildJsonObject {
        put("@context", JsonArray(listOf(JsonPrimitive("https://www.w3.org/2018/credentials/v1"))))
        put("type", JsonArray(listOf(JsonPrimitive("VerifiableCredential"))))
        put("issuer", "did:key:z6Mk...")
        putJsonObject("credentialSubject") {
            put("id", "did:example:subject")
            put("name", "Alice")
        }
    }

    val issued = issuer.issueCredential(credentialJson)
    println("Issued credential id: ${(issued["id"] as? JsonPrimitive)?.content}")
}
```

### Credential Verification

```kotlin
import org.trustweave.godiddy.*
import org.trustweave.godiddy.verifier.GodiddyVerifier
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject

val client = GodiddyClient(GodiddyConfig.default())
val verifier = GodiddyVerifier(client)

// verifyCredential is suspend and expects the credential as JsonObject
fun main() = runBlocking {
    val credentialJson: JsonObject = /* build or parse VC JSON */
    val verificationResult = verifier.verifyCredential(credentialJson)
    if (verificationResult.verified) {
        println("Credential is valid")
    } else {
        println("Credential is invalid: ${verificationResult.error}")
    }
}
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

The GoDiddy integration follows TrustWeave's error handling patterns. `resolver.resolveDid(...)`
returns the sealed [`DidResolutionResult`](../../did/did-core/src/main/kotlin/org/trustweave/did/resolver/DidResolutionResult.kt):

```kotlin
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.resolver.errorMessage

when (val result = resolver.resolveDid(did)) {
    is DidResolutionResult.Success ->
        println("Resolved: ${result.document.id}")
    is DidResolutionResult.Failure ->
        println("Resolution failed: ${result.errorMessage}")
}
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

- Review [DID Concepts](../../core-concepts/dids.md) for DID fundamentals
- See [Verifiable Credentials](../../core-concepts/verifiable-credentials.md) for credential workflows
- Check [Creating Plugins](../../contributing/creating-plugins.md) to understand DID method implementation
- Explore [Other DID Integrations](README.md) for alternative DID methods

## References

- GoDiddy Documentation](https://godiddy.com/docs)
- Universal Resolver](https://dev.uniresolver.io/)
- TrustWeave DID Module](../modules/trustweave-did.md)
- TrustWeave Core API](../api-reference/core-api.md)

