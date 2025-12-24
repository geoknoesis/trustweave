# Third-Party Integrations

TrustWeave includes optional integration modules for third-party services.

## walt.id Integration

TrustWeave includes an optional `TrustWeave-waltid` module that provides walt.id-based implementations of TrustWeave interfaces using the SPI (Service Provider Interface) pattern.

### Using walt.id Adapters

Add the walt.id adapter module to your dependencies:

```kotlin
dependencies {
    implementation("org.trustweave:TrustWeave-waltid:1.0.0-SNAPSHOT")
}
```

### Automatic Discovery via SPI

walt.id adapters are automatically discovered via Java ServiceLoader:

```kotlin
import org.trustweave.waltid.WaltIdIntegration
import org.trustweave.did.DidMethodRegistry
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    // Automatically discover and register walt.id adapters
    val registry = DidMethodRegistry()
    val result = WaltIdIntegration.discoverAndRegister(registry)

    println("Registered DID methods: ${result.registeredDidMethods}")
    // Output: Registered DID methods: [key, web]

    // Use TrustWeave APIs as normal - walt.id adapters are now registered
    val didDocument = registry.resolve("did:key:...")
}
```

### Manual Setup

You can also manually configure walt.id integration:

```kotlin
import org.trustweave.waltid.WaltIdIntegration
import org.trustweave.waltid.WaltIdKeyManagementService
import org.trustweave.did.DidMethodRegistry

fun main() = runBlocking {
    // Create walt.id KMS
    val kms = WaltIdKeyManagementService()
    val registry = DidMethodRegistry()

    // Setup integration with specific DID methods
    val result = WaltIdIntegration.setup(
        kms = kms,
        registry = registry,
        didMethods = listOf("key", "web")
    )

    // Create a DID using walt.id
    val keyMethod = registry.get("key")
    val document = keyMethod!!.createDid()
    println("Created DID: ${document.id}")
}
```

### Supported Features

The walt.id adapter module provides:

- **Key Management**: `WaltIdKeyManagementService` implementing `KeyManagementService`
- **DID Methods**:
  - `did:key` via `WaltIdKeyMethod`
  - `did:web` via `WaltIdWebMethod`
- **SPI Discovery**: Automatic discovery and registration via Java ServiceLoader

### SPI Provider Interfaces

TrustWeave defines SPI interfaces for adapter discovery:

- `KeyManagementServiceProvider`: For KMS implementations
- `DidMethodProvider`: For DID method implementations

These interfaces allow any adapter module to be discovered automatically at runtime.

## godiddy Integration

TrustWeave includes an optional `TrustWeave-godiddy` module that provides HTTP-based integration with godiddy services (Universal Resolver, Universal Registrar, Universal Issuer, Universal Verifier) using the SPI pattern.

### Using godiddy Adapters

Add the godiddy adapter module to your dependencies:

```kotlin
dependencies {
    implementation("org.trustweave:TrustWeave-godiddy:1.0.0-SNAPSHOT")
}
```

### Automatic Discovery via SPI

godiddy adapters are automatically discovered via Java ServiceLoader:

```kotlin
import org.trustweave.godiddy.GodiddyIntegration
import org.trustweave.did.DidMethodRegistry
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    // Automatically discover and register godiddy adapters
    val registry = DidMethodRegistry()
    val result = GodiddyIntegration.discoverAndRegister(registry)

    println("Registered DID methods: ${result.registeredDidMethods}")
    // Output: Registered DID methods: [key, web, ion, ethr, ...]

    // Use TrustWeave APIs as normal - godiddy adapters are now registered
    val didDocument = registry.resolve("did:key:...")
}
```

### Manual Setup with Custom Configuration

You can manually configure godiddy integration with a custom base URL:

```kotlin
import org.trustweave.godiddy.GodiddyIntegration
import org.trustweave.did.DidMethodRegistry

fun main() = runBlocking {
    // Setup integration with custom base URL (for self-hosted instances)
    val registry = DidMethodRegistry()
    val result = GodiddyIntegration.setup(
        baseUrl = "https://custom.godiddy.com",
        registry = registry,
        didMethods = listOf("key", "web", "ion") // Optional: specify methods
    )

    // Access service clients directly
    val resolver = result.resolver
    val registrar = result.registrar
    val issuer = result.issuer
    val verifier = result.verifier

    // Use services
    val resolutionResult = resolver?.resolveDid("did:key:...")
}
```

### Supported Services

The godiddy adapter module provides:

- **Universal Resolver**: Resolve DIDs across multiple methods
- **Universal Registrar**: Create and manage DIDs
- **Universal Issuer**: Issue Verifiable Credentials
- **Universal Verifier**: Verify Verifiable Credentials
- **DID Methods**: Supports all methods available via Universal Resolver (key, web, ion, ethr, polygonid, cheqd, dock, indy, and many more)

### Configuration Options

The godiddy integration supports the following configuration options:

```kotlin
val result = GodiddyIntegration.discoverAndRegister(
    registry = DidMethodRegistry(),
    options = mapOf(
        "baseUrl" to "https://resolver.godiddy.com", // Default: public godiddy service
        "timeout" to 30000L, // HTTP timeout in milliseconds
        "apiKey" to "your-api-key" // Optional: for authenticated requests
    )
)
```

### Example: Issuing and Verifying Credentials

```kotlin
import org.trustweave.godiddy.GodiddyIntegration
import org.trustweave.did.DidMethodRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*

fun main() = runBlocking {
    val registry = DidMethodRegistry()
    val result = GodiddyIntegration.discoverAndRegister(registry)

    // Issue a credential
    val credential = buildJsonObject {
        put("id", "vc-12345")
        put("type", buildJsonArray { add("VerifiableCredential") })
        put("issuer", "did:key:...")
        put("credentialSubject", buildJsonObject {
            put("id", "subject-123")
        })
    }

    val issuedCredential = result.issuer?.issueCredential(
        credential = credential,
        options = mapOf("format" to "json-ld")
    )

    // Verify the credential
    val verificationResult = result.verifier?.verifyCredential(
        credential = issuedCredential ?: credential,
        options = emptyMap()
    )

    println("Verified: ${verificationResult?.verified}")
}
```

## Next Steps

- See [Available Plugins](PLUGINS.md) for native plugin support
- Read [Architecture & Modules](ARCHITECTURE.md) for module details
- Check [Getting Started](GETTING_STARTED.md) for usage examples




