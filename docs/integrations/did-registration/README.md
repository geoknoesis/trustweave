---
title: DID Method Registration via JSON
---

# DID Method Registration via JSON

This package provides support for registering DID methods using JSON files that follow the [DID Registration specification](https://identity.foundation/did-registration/). This makes it easy to add support for new DID methods without writing code.

## Quick Start

### 1. Create a JSON Registration File

Create a JSON file in `src/main/resources/did-methods/` (or any directory) using the **official DID Method Registry format**:

```json
{
  "name": "example",
  "status": "implemented",
  "specification": "https://example.com/did-method-spec",
  "contact": {
    "name": "Example Team",
    "email": "contact@example.com"
  },
  "implementations": [
    {
      "name": "Universal Resolver",
      "driverUrl": "https://dev.uniresolver.io",
      "testNet": false
    }
  ]
}
```

This format matches the official DID Method Registry structure from https://identity.foundation/did-registration/, making it easy to use registry entries directly.

### 2. Load and Register Methods

```kotlin
import com.trustweave.did.registration.DidMethodRegistration
import com.trustweave.did.registry.DidMethodRegistry
import com.trustweave.kms.InMemoryKeyManagementService

val kms = InMemoryKeyManagementService()
val registry = DidMethodRegistry()

// Load from classpath (default: "did-methods")
val registeredMethods = DidMethodRegistration.registerFromClasspath(registry, kms)
println("Registered methods: $registeredMethods")

// Or load from a directory
val methods = DidMethodRegistration.registerFromDirectory(
    registry, 
    kms, 
    Paths.get("my-did-methods")
)

// Or load a single file
val methodName = DidMethodRegistration.registerFromFile(
    registry,
    kms,
    Paths.get("did-methods/example.json")
)
```

### 3. Use the Registered Methods

```kotlin
// Resolve a DID
val result = registry.resolve("did:example:123")
println("DID Document: ${result.document}")
```

## JSON Registration Format

This implementation supports the **official DID Method Registry format** from https://identity.foundation/did-registration/.

### Required Fields

- `name`: The DID method name (e.g., "web", "key", "ion")
- `implementations`: Array of implementations, at least one with a `driverUrl`

### Implementations

The `implementations` array specifies available resolver services:

```json
{
  "implementations": [
    {
      "name": "Universal Resolver",
      "driverUrl": "https://dev.uniresolver.io",
      "testNet": false
    }
  ]
}
```

**Fields:**
- `driverUrl` (required): URL to the resolver service (typically Universal Resolver)
- `name` (optional): Name of the implementation
- `testNet` (optional): Whether this is a test network (default: false)

**Implementation Selection:**
- Non-testnet implementations are preferred
- Protocol adapter is automatically determined:
  - URLs containing "godiddy" → GoDiddy adapter
  - Otherwise → Standard adapter

### Complete Example

```json
{
  "name": "web",
  "status": "implemented",
  "specification": "https://w3c-ccg.github.io/did-method-web/",
  "contact": {
    "name": "W3C CCG",
    "email": "contact@example.com",
    "url": "https://www.w3.org/community/credentials/"
  },
  "implementations": [
    {
      "name": "Universal Resolver",
      "driverUrl": "https://dev.uniresolver.io",
      "testNet": false
    }
  ]
}
```

**Note:** Currently, only resolution is supported for JSON-registered methods. The `driverUrl` enables automatic DID resolution via the specified resolver service.

### Optional Fields

- `status`: Implementation status (e.g., "implemented", "proposed")
- `specification`: URL to the DID method specification
- `contact`: Contact information for method maintainers

## Advanced Usage

### Using JsonDidMethodLoader Directly

```kotlin
import com.trustweave.did.registration.JsonDidMethodLoader

val loader = JsonDidMethodLoader(kms)

// Load from various sources
val method1 = loader.loadFromFile(Paths.get("example.json"))
val method2 = loader.loadFromString(jsonString)
val method3 = loader.loadFromInputStream(inputStream)
val methods = loader.loadFromDirectory(Paths.get("did-methods"))
val methods = loader.loadFromClasspath("did-methods")
```

### Using JsonDidMethodProvider (SPI)

The `JsonDidMethodProvider` can be registered via Java ServiceLoader:

```kotlin
import com.trustweave.did.registration.JsonDidMethodProvider

// Create provider from classpath
val provider = JsonDidMethodProvider.fromClasspath(kms)

// Or from directory
val provider = JsonDidMethodProvider.fromDirectory(kms, Paths.get("did-methods"))

// Methods are available via provider
val method = provider.create("example", DidCreationOptions())
```

## Examples

See `src/main/resources/did-methods/` for example registration files:
- `example.json`: Basic example
- `web.json`: did:web method
- `ion.json`: did:ion method

## Limitations

1. **Resolution Only**: Currently, only DID resolution is fully supported. Create, update, and deactivate operations require native implementations.

2. **HTTP Endpoint Dependency**: JSON-registered methods require an HTTP endpoint (Universal Resolver or compatible service) that supports the method.

3. **Protocol Adapters**: Custom protocol adapters require code. Only "standard" and "godiddy" are supported out of the box.

## Benefits

1. **No Code Required**: Add new DID method support by simply creating a JSON file
2. **Standard Format**: Follows the official DID Registration specification
3. **Easy Discovery**: Methods can be loaded from classpath, filesystem, or programmatically
4. **Flexible**: Works with any HTTP endpoint that follows the Universal Resolver protocol (Universal Resolver instances, custom resolvers, or single endpoints)

## Integration with Existing Code

JSON-registered methods work seamlessly with existing TrustWeave code:

```kotlin
// Register JSON methods
DidMethodRegistration.registerFromClasspath(registry, kms)

// Also register native methods (they work together)
val keyMethod = KeyDidMethod(kms)
registry.register(keyMethod)

// Use any registered method
val result = registry.resolve("did:key:...")
```

