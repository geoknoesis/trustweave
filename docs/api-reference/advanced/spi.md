---
title: Service Provider Interface (SPI)
nav_exclude: true
redirect_from:
  - /advanced/spi/
nav_order: 10
---

# Service Provider Interface (SPI)

This guide explains how TrustWeave uses the Java Service Provider Interface (SPI) for auto-discovery of plugins and adapters.

## Overview

TrustWeave uses SPI to automatically discover and load plugins at runtime without requiring explicit registration code. This enables:

- **Auto-discovery** – plugins are automatically found on the classpath
- **Loose coupling** – plugins can be added without modifying application code
- **Modularity** – plugins are discovered independently of the main application

## How SPI Works in TrustWeave

### Provider Interfaces

TrustWeave defines several provider interfaces:

- `org.trustweave.did.spi.DidMethodProvider` – discovers DID method implementations
- `org.trustweave.kms.spi.KeyManagementServiceProvider` – discovers KMS implementations
- `org.trustweave.anchor.spi.BlockchainAnchorClientProvider` – discovers blockchain adapter implementations
- `org.trustweave.credential.spi.proof.ProofEngineProvider` – discovers VC proof-suite engines (VC-LD, VC-JWT, SD-JWT-VC)
- `org.trustweave.credential.spi.exchange.CredentialExchangeProtocolProvider` – discovers credential exchange protocol implementations
- `org.trustweave.credential.spi.transform.CredentialFormatConverter` – discovers cross-format credential converters

> **Note:** TrustWeave does not currently expose a single `CredentialServiceProvider` SPI. The `CredentialService` is wired in `TrustWeave.build { ... }` from the discovered proof engines and other credential SPIs above.

### Service Discovery

SPI uses service files in `META-INF/services/` to discover providers:

```
META-INF/services/org.trustweave.did.spi.DidMethodProvider
META-INF/services/org.trustweave.kms.spi.KeyManagementServiceProvider
META-INF/services/org.trustweave.anchor.spi.BlockchainAnchorClientProvider
```

Each file contains the fully qualified class name of the provider implementation.

## Using SPI

### Discovering Providers

```kotlin
import org.trustweave.did.spi.DidMethodProvider
import java.util.ServiceLoader

// Discover DID method providers
val providers = ServiceLoader.load(DidMethodProvider::class.java)

// Iterate through discovered providers
providers.forEach { provider ->
    println("Provider: ${provider.name}")
    println("Supported methods: ${provider.supportedMethods}")
}
```

**What this does:** Uses `ServiceLoader` to discover all `DidMethodProvider` implementations on the classpath.

**Outcome:** Automatically finds all DID method providers without explicit registration.

### Creating Providers

```kotlin
import org.trustweave.did.spi.DidMethodProvider
import org.trustweave.did.*

class MyDidMethodProvider : DidMethodProvider {
    override val name: String = "my-did-method"
    override val supportedMethods: List<String> = listOf("mydid")

    override fun create(
        methodName: String,
        options: DidCreationOptions
    ): DidMethod? {
        if (methodName != "mydid") return null

        return MyCustomDidMethod(options) // your DidMethod implementation
    }
}
```

**What this does:** Implements `DidMethodProvider` to provide a custom DID method.

**Outcome:** Enables auto-discovery of your custom DID method.

### Registering Service Files

Create a service file at:

```
src/main/resources/META-INF/services/org.trustweave.did.spi.DidMethodProvider
```

With content:

```
com.example.MyDidMethodProvider
```

**What this does:** Registers your provider for auto-discovery.

**Outcome:** Your provider is automatically discovered when the module is on the classpath.

## SPI in TrustWeave Modules

### DID Method Providers

```kotlin
// did/plugins/key/src/main/resources/META-INF/services/...
org.trustweave.did.key.KeyDidMethodProvider
```

### KMS Providers

```kotlin
// kms/plugins/aws/src/main/resources/META-INF/services/...
org.trustweave.awskms.AwsKeyManagementServiceProvider
```

### Blockchain Adapter Providers

```kotlin
// chains/plugins/algorand/src/main/resources/META-INF/services/...
org.trustweave.algorand.AlgorandBlockchainAnchorClientProvider
```

## Benefits of SPI

### Modularity

Plugins can be added or removed without modifying application code:

```kotlin
// Add chains/plugins/algorand dependency to enable Algorand adapter
dependencies {
    implementation("org.trustweave:anchors-plugins-algorand:0.6.0")
}
```

### Loose Coupling

Applications don't need to know about specific implementations:

```kotlin
// Application code works with any DID method provider
val providers = ServiceLoader.load(DidMethodProvider::class.java)
val provider = providers.find { it.supportedMethods.contains("key") }
```

### Testability

SPI enables easy mocking and testing:

```kotlin
// Test can provide mock providers
val mockProvider = MockDidMethodProvider()
// Test implementation
```

## Best Practices

### Provider Naming

- Use descriptive, unique names
- Follow naming conventions (lowercase, hyphen-separated)
- Include module/plugin identifier

### Error Handling

```kotlin
import org.trustweave.did.DidCreationOptions

val options = DidCreationOptions()
val providers = ServiceLoader.load(DidMethodProvider::class.java)

providers.forEach { provider ->
    try {
        val method = provider.create("key", options)
        // Use method (may be null if provider doesn't support "key")
    } catch (e: Exception) {
        // Handle provider creation errors
        println("Failed to create method from provider ${provider.name}: ${e.message}")
    }
}
```

### Provider Validation

```kotlin
override fun create(
    methodName: String,
    options: DidCreationOptions
): DidMethod? {
    // Validate method name
    if (!supportedMethods.contains(methodName)) {
        return null
    }

    // Validate required options
    val requiredOption = options.additionalProperties["requiredOption"] as? String
        ?: return null

    return MyCustomDidMethod(options)
}
```

## Troubleshooting

### Provider Not Found

**Problem:** Provider not discovered

**Solution:**
1. Verify service file exists in `META-INF/services/`
2. Check service file contains correct class name
3. Ensure provider class is on classpath
4. Check for typos in service file

### Multiple Providers

**Problem:** Multiple providers for same method

**Solution:**
- TrustWeave uses the first provider found
- Use explicit provider selection if needed
- Order providers via dependency order if critical

## Next Steps

- Review [Creating Plugins](../../contributing/creating-plugins.md) for plugin implementation
- See [Plugin Lifecycle](plugin-lifecycle.md) for lifecycle management
- SPI interfaces are included in `trustweave-common`. See [Core Modules](../modules/core-modules.md) for details

## References

- Java Service Provider Interface](https://docs.oracle.com/javase/tutorial/ext/basics/spi.html)
- Core Modules](../modules/core-modules.md)
- Creating Plugins](../contributing/creating-plugins.md)

