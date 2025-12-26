# Idiomatic Kotlin API Guide

This document describes the idiomatic Kotlin features added to the `did-core` module.

## Overview

The module now includes:
- **Builder DSLs** for creating resolvers and registries
- **Extension functions** for fluent, functional-style operations
- **Operator overloads** for intuitive API usage
- **Retry logic** with exponential backoff

## Builder DSLs

### Universal Resolver Builder

```kotlin
import org.trustweave.did.resolver.universalResolver

val resolver = universalResolver("https://dev.uniresolver.io") {
    timeout = 60
    apiKey = "my-api-key"
    retry {
        maxRetries = 3
        initialDelayMs = 200
        maxDelayMs = 2000
    }
    protocolAdapter = StandardUniversalResolverAdapter()
}
```

### Registry Builder

```kotlin
import org.trustweave.did.registry.didMethodRegistry

val registry = didMethodRegistry {
    register(KeyDidMethod(kms))
    register(WebDidMethod())
    registerAll(OtherMethod1(), OtherMethod2())
}
```

## Operator Overloads

### Registry Operators

```kotlin
val registry = DefaultDidMethodRegistry()

// Bracket notation for access
val method = registry["key"]

// `in` operator for checking existence
if ("key" in registry) {
    // Method is registered
}

// Assignment operator
registry["new"] = NewDidMethod()
```

## Extension Functions

### DidResolutionResult Extensions

```kotlin
import org.trustweave.did.resolver.DidResolutionResult

val result: DidResolutionResult = // ... resolution result

// Safe access
val document = result.getOrNull()
val doc = result.getOrThrow()
val defaultDoc = result.getOrDefault(defaultDocument)

// Callbacks
result.onSuccess { document ->
    println("Resolved: ${document.id}")
}

result.onFailure { failure ->
    println("Failed: ${failure.reason}")
}

// Functional transformation
val mapped = result.map { doc -> doc.copy(id = otherDid) }

// Fold for complete transformation
val message = result.fold(
    onSuccess = { "Success: ${it.id.value}" },
    onFailure = { "Failed: ${it.reason}" }
)

// Properties
if (result.isSuccessResult) { /* ... */ }
if (result.isFailureResult) { /* ... */ }
val failure = result.failureOrNull()
```

### Did Extensions

```kotlin
import org.trustweave.did.identifiers.Did
import org.trustweave.did.dsl.resolveWith
import org.trustweave.did.dsl.resolveOrNull
import org.trustweave.did.dsl.resolveOrThrow
import org.trustweave.did.dsl.resolveOrDefault

val did = Did("did:key:123")
val resolver: DidResolver = // ... resolver

// Fluent resolution
val document = did.resolveWith(resolver).getOrThrow()
val doc = did.resolveOrNull(resolver)
val docOrDefault = did.resolveOrDefault(resolver, defaultDocument)

// With callback
did.resolveWith(resolver) { document ->
    println("Resolved: ${document.id}")
}
```

## Retry Configuration

### Default Retry

```kotlin
val resolver = DefaultUniversalResolver(
    baseUrl = "https://dev.uniresolver.io",
    retryConfig = RetryConfig.default()  // 3 retries, 100ms initial delay
)
```

### No Retry

```kotlin
val resolver = DefaultUniversalResolver(
    baseUrl = "https://dev.uniresolver.io",
    retryConfig = RetryConfig.noRetry()
)
```

### Aggressive Retry

```kotlin
val resolver = DefaultUniversalResolver(
    baseUrl = "https://dev.uniresolver.io",
    retryConfig = RetryConfig.aggressive()  // 5 retries, 200ms initial delay
)
```

### Custom Retry

```kotlin
val retryConfig = RetryConfig(
    maxRetries = 5,
    initialDelayMs = 200,
    maxDelayMs = 5000,
    retryableStatusCodes = setOf(500, 502, 503, 504),
    retryableExceptions = setOf(
        java.net.ConnectException::class.java,
        java.net.SocketTimeoutException::class.java
    )
)

val resolver = DefaultUniversalResolver(
    baseUrl = "https://dev.uniresolver.io",
    retryConfig = retryConfig
)
```

## Performance Improvements

### Cached Validation

The `baseUrl` validation is now cached during initialization, improving performance for repeated resolutions.

### Retry Logic

Automatic retry with exponential backoff for transient failures:
- Network errors (ConnectException, SocketTimeoutException)
- HTTP 5xx errors (configurable)
- Exponential backoff with jitter to avoid thundering herd

## Migration Guide

### Before

```kotlin
val registry = DefaultDidMethodRegistry()
registry.register(KeyDidMethod(kms))
val method = registry.get("key")

val resolver = DefaultUniversalResolver("https://dev.uniresolver.io")
val result = resolver.resolveDid("did:key:123")
val document = when (result) {
    is DidResolutionResult.Success -> result.document
    else -> null
}
```

### After

```kotlin
val registry = didMethodRegistry {
    register(KeyDidMethod(kms))
}
val method = registry["key"]  // Operator overload

val resolver = universalResolver("https://dev.uniresolver.io") {
    timeout = 60
    retry { maxRetries = 3 }
}
val document = Did("did:key:123")
    .resolveWith(resolver)
    .getOrNull()  // Extension function
```

## Best Practices

1. **Use builder DSLs** for complex configurations
2. **Use extension functions** for fluent, readable code
3. **Use operator overloads** for intuitive API usage
4. **Configure retry logic** for production environments
5. **Use safe access patterns** (`getOrNull`, `getOrDefault`) when appropriate

