# DID Method Registration via JSON

This guide explains how to register DID methods using JSON files that follow the [DID Registration specification](https://identity.foundation/did-registration/). This makes it easy to add support for new DID methods without writing code.

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

### 2. Load and Register the Method

```kotlin
import org.trustweave.did.registration.loader.JsonDidMethodLoader
import org.trustweave.did.registry.DidMethodRegistry
import java.nio.file.Paths

val loader = JsonDidMethodLoader()
val registry = DidMethodRegistry()

// Load from file
val method = loader.loadFromFile(Paths.get("src/main/resources/did-methods/example.json"))
registry.register(method)

// Or load from classpath resource
val method2 = loader.loadFromResource("did-methods/example.json")
registry.register(method2)
```

### 3. Use the Registered Method

```kotlin
val resolver = RegistryBasedResolver(registry)
val did = Did("did:example:123")
val result = resolver.resolve(did)
```

## JSON Specification

See [DID Method Registration JSON Specification](did-method-registration-specification.md) for complete details on the JSON format and mapping.

## Quick Reference

See [DID Method Registration Quick Reference](did-method-registration-quick-reference.md) for a concise reference guide.

