# DID Core Module

> **Note:** This is a minimal README. For complete documentation, see [Module Documentation](../../docs/modules/trustweave-did.md).

The `did-core` module provides a complete implementation of the W3C DID Core specification, including DID identifiers, documents, resolution, and registration.

## Overview

This module implements:
- **DID Identifiers**: Type-safe DID, VerificationMethodId, and DidUrl classes
- **DID Documents**: Complete DID Document model following W3C spec
- **Resolution**: Universal Resolver support with protocol adapters
- **Registration**: DID lifecycle operations (create, update, deactivate)
- **Registry**: DID method registration and management
- **Validation**: DID format and method validation

## Architecture

### Core Components

```
did-core/
├── identifiers/      # DID, VerificationMethodId, DidUrl
├── model/           # DidDocument, VerificationMethod, DidService
├── resolver/        # Resolution implementations
├── registry/        # DID method registry
├── registrar/      # DID registration interfaces
├── validation/      # DID format validation
├── exception/       # DID-specific exceptions
└── util/           # Utility functions
```

### Key Design Patterns

1. **Type Safety**: All identifiers are strongly typed (Did, VerificationMethodId)
2. **Protocol Adapter**: Pluggable adapters for different Universal Resolver implementations
3. **Sealed Classes**: Exhaustive error handling (DidResolutionResult, DidException)
4. **Extension Functions**: Fluent DSL for common operations
5. **Thread Safety**: ConcurrentHashMap for registry, atomic operations

## Quick Start

### Basic Usage

```kotlin
import org.trustweave.did.identifiers.Did
import org.trustweave.did.resolver.DefaultUniversalResolver
import org.trustweave.did.registry.DefaultDidMethodRegistry
import org.trustweave.did.resolver.RegistryBasedResolver

// Create a DID
val did = Did("did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK")

// Resolve using Universal Resolver
val resolver = DefaultUniversalResolver("https://dev.uniresolver.io")
val result = resolver.resolveDid(did.value)

// Or use a registry-based resolver
val registry = DefaultDidMethodRegistry()
// ... register methods
val registryResolver = RegistryBasedResolver(registry)
val result2 = registryResolver.resolve(did)
```

### Idiomatic Kotlin API

The module provides a fluent, idiomatic Kotlin API with DSL builders and extension functions:

```kotlin
import org.trustweave.did.identifiers.Did
import org.trustweave.did.resolver.universalResolver
import org.trustweave.did.registry.didMethodRegistry
import org.trustweave.did.resolver.RegistryBasedResolver

// Builder DSL for resolver
val resolver = universalResolver("https://dev.uniresolver.io") {
    timeout = 60
    apiKey = "my-api-key"
    retry {
        maxRetries = 3
        initialDelayMs = 200
    }
}

// Builder DSL for registry
val registry = didMethodRegistry {
    register(KeyDidMethod(kms))
    register(WebDidMethod())
}

// Operator overloads for registry
val method = registry["key"]  // Bracket notation
if ("key" in registry) {      // `in` operator
    // Method is registered
}
registry["new"] = NewDidMethod()  // Assignment

// Fluent resolution with extensions
val document = Did("did:key:123")
    .resolveWith(resolver)
    .getOrThrow()

// Or with safe access
val doc = Did("did:key:123")
    .resolveWith(resolver)
    .getOrNull()

// Functional style with callbacks
Did("did:key:123")
    .resolveWith(resolver)
    .onSuccess { println("Resolved: ${it.id}") }
    .onFailure { println("Failed: ${it.reason}") }

// Fold for transformation
val message = result.fold(
    onSuccess = { "Resolved: ${it.id.value}" },
    onFailure = { "Failed: ${it.reason}" }
)
```

### DID Method Registration

```kotlin
// Register a DID method
val registry = DefaultDidMethodRegistry()
registry.register(KeyDidMethod(kms))
registry.register(WebDidMethod())

// Check if method is available
if (registry.has("key")) {
    val method = registry.get("key")
    // Use the method
}
```

### Error Handling

```kotlin
import org.trustweave.did.exception.DidException

try {
    val document = resolver.resolveOrThrow(did)
} catch (e: DidException.DidNotFound) {
    println("DID not found: ${e.did.value}")
    println("Available methods: ${e.availableMethods}")
} catch (e: DidException.DidMethodNotRegistered) {
    println("Method '${e.method}' not registered")
} catch (e: DidException.DidResolutionFailed) {
    println("Resolution failed: ${e.reason}")
}
```

## Module Structure

### Identifiers (`identifiers/`)
- `Did`: Type-safe DID identifier
- `VerificationMethodId`: Verification method identifier
- `DidUrl`: DID URL with path and fragment support

### Models (`model/`)
- `DidDocument`: Complete DID Document structure
- `VerificationMethod`: Verification method representation
- `DidService`: Service endpoint (with type-safe extensions)
- `DidDocumentMetadata`: Document metadata

### Resolvers (`resolver/`)
- `DidResolver`: Functional interface for resolution
- `RegistryBasedResolver`: Registry-backed resolver
- `DefaultUniversalResolver`: HTTP-based Universal Resolver client
- `UniversalResolverProtocolAdapter`: Protocol adapter interface
- `StandardUniversalResolverAdapter`: Standard adapter implementation

### Registry (`registry/`)
- `DidMethodRegistry`: Interface for method management
- `DefaultDidMethodRegistry`: Thread-safe in-memory implementation

### Validation (`validation/`)
- `DidValidator`: DID format and method validation

### Exceptions (`exception/`)
- `DidException`: Sealed class hierarchy for DID errors
  - `DidNotFound`
  - `DidMethodNotRegistered`
  - `InvalidDidFormat`
  - `DidResolutionFailed`
  - `DidCreationFailed`
  - `DidUpdateFailed`
  - `DidDeactivationFailed`

### Utilities (`util/`)
- `DidUtils`: Common utility functions
- `DidLogging`: Centralized logging
- `ServiceEndpointExtensions`: Type-safe service endpoint access

## Extension Functions

### DID Extensions

```kotlin
// Resolve with fluent API
val document = did.resolveWith(resolver).getOrThrow()
val documentOrNull = did.resolveOrNull(resolver)

// Check method
if (did isMethod "key") {
    // Handle key method
}
```

### Resolver Extensions

```kotlin
// Convenient resolution
val document = resolver.resolveOrThrow(did)
val documentOrNull = resolver.resolveOrNull(did)
```

## Protocol Adapters

The module supports different Universal Resolver implementations via protocol adapters:

```kotlin
// Standard adapter (default)
val resolver = DefaultUniversalResolver(
    baseUrl = "https://dev.uniresolver.io",
    protocolAdapter = StandardUniversalResolverAdapter()
)

// Custom adapter
val customAdapter = object : UniversalResolverProtocolAdapter {
    // Implement adapter methods
}
val resolver = DefaultUniversalResolver(
    baseUrl = "https://custom-resolver.com",
    protocolAdapter = customAdapter
)
```

## Testing

The module includes comprehensive tests:
- Unit tests for all components
- Concurrency tests for thread safety
- Performance tests for optimization verification
- Edge case coverage

Run tests:
```bash
./gradlew :did:did-core:test
```

## Performance Considerations

- **Lazy Initialization**: DID properties (method, identifier, path) are cached
- **ConcurrentHashMap**: Thread-safe registry without explicit locks
- **Efficient Parsing**: Optimized string operations for DID parsing
- **No Unnecessary Allocations**: Reuses instances where possible

## Error Codes

All DID exceptions include structured error codes:
- `DID_NOT_FOUND`
- `DID_METHOD_NOT_REGISTERED`
- `INVALID_DID_FORMAT`
- `DID_RESOLUTION_FAILED`
- `DID_CREATION_FAILED`
- `DID_UPDATE_FAILED`
- `DID_DEACTIVATION_FAILED`

## Dependencies

- `common`: Core utilities, identifiers, exceptions
- `kotlinx-serialization`: JSON serialization
- `kotlinx-coroutines`: Async operations
- `kotlinx-datetime`: Timestamp handling
- `slf4j-api`: Logging (optional)

## W3C Compliance

This module follows the W3C DID Core specification:
- [DID Core](https://www.w3.org/TR/did-core/)
- [DID Resolution](https://www.w3.org/TR/did-spec-registries/#did-resolution)
- [DID Registration](https://identity.foundation/did-registration/)

## Documentation

For comprehensive documentation, see:
- **[Module Documentation](../../docs/modules/trustweave-did.md)** - Complete module reference
- **[Idiomatic Kotlin API Guide](../../docs/modules/did-core-idiomatic-api.md)** - Guide to idiomatic Kotlin features (DSLs, extensions, operators)

## License

See project root LICENSE file.

