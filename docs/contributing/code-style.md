---
title: Code Style
nav_exclude: true
---

# Code Style

This guide outlines the coding conventions and style guidelines for TrustWeave.

## Overview

TrustWeave follows Kotlin coding conventions with some project-specific guidelines:

- **Kotlin Style Guide** – follows official Kotlin style guide
- **ktlint** – automated formatting with ktlint
- **Naming Conventions** – clear, descriptive names
- **Documentation** – comprehensive KDoc comments

## Code Formatting

### ktlint Configuration

The project uses ktlint for code formatting. Format your code:

```bash
# Check formatting
./gradlew ktlintCheck

# Auto-format
./gradlew ktlintFormat
```

### Format on Save

Configure your IDE to format on save:

**IntelliJ IDEA:**
1. Install ktlint plugin
2. Enable "Reformat code on save"
3. Configure ktlint as formatter

## Naming Conventions

### Classes

Use PascalCase for class names:

```kotlin
class MyCustomDidMethod : DidMethod {
    // ...
}
```

### Functions

Use camelCase for function names:

```kotlin
suspend fun createDid(options: DidCreationOptions): DidDocument {
    // ...
}
```

### Variables

Use camelCase for variable names:

```kotlin
val issuerDid = "did:key:issuer"
val credentialSubject = buildJsonObject { }
```

### Constants

Use UPPER_SNAKE_CASE for constants:

```kotlin
companion object {
    const val DEFAULT_TIMEOUT = 30000L
    const val MAX_RETRIES = 3
}
```

## Code Structure

### File Organization

Organize files logically:

```kotlin
// Package declaration
package com.trustweave.example

// Imports
import com.trustweave.did.*
import kotlinx.coroutines.runBlocking

// Class/object definitions
class Example {
    // ...
}
```

### Function Length

Keep functions concise:

- **Short functions** – prefer functions under 20 lines
- **Complex logic** – extract to separate functions
- **Single responsibility** – each function should do one thing

### Class Size

Keep classes focused:

- **Single responsibility** – each class should have one purpose
- **Composition over inheritance** – prefer composition
- **Interfaces** – use interfaces for abstractions

## Documentation

### KDoc Comments

Document public APIs:

```kotlin
/**
 * Creates a DID using the specified method.
 *
 * @param method The DID method to use (e.g., "key", "web")
 * @param options Configuration options for DID creation
 * @return Result containing the created DID document or error
 */
suspend fun createDid(
    method: String,
    options: DidCreationOptions
): Result<DidDocument> {
    // ...
}
```

### Inline Documentation

Add comments for complex logic:

```kotlin
// Generate identifier from public key using multibase encoding
val identifier = multibase.encode(key.publicKey)
```

## Error Handling

### Result Types

Use `Result<T>` for operations that can fail:

```kotlin
suspend fun createDid(): Result<DidDocument> {
    return try {
        // Implementation
        Result.success(didDocument)
    } catch (e: Exception) {
        Result.failure(
            TrustWeaveError.DidCreationFailed(
                reason = e.message ?: "Unknown error"
            )
        )
    }
}
```

### Error Types

Use structured error types:

```kotlin
sealed class TrustWeaveError : Exception() {
    data class DidCreationFailed(val reason: String) : TrustWeaveError()
    data class DidResolutionFailed(val reason: String) : TrustWeaveError()
    // ...
}
```

## Testing

### Test Naming

Use descriptive test names:

```kotlin
@Test
fun testCreateDidWithEd25519Algorithm() = runBlocking {
    // ...
}

@Test
fun testResolveDidReturnsDocument() = runBlocking {
    // ...
}
```

### Test Organization

Organize tests logically:

```kotlin
class DidMethodTest {
    @Test
    fun testCreateDid() = runBlocking {
        // ...
    }

    @Test
    fun testResolveDid() = runBlocking {
        // ...
    }

    @Test
    fun testUpdateDid() = runBlocking {
        // ...
    }
}
```

## Best Practices

### Immutability

Prefer immutable data:

```kotlin
// Prefer immutable data classes
data class DidDocument(
    val id: String,
    val verificationMethod: List<VerificationMethod>
)

// Avoid mutable state
// ❌ var document: DidDocument? = null
// ✅ val document: DidDocument? = null
```

### Null Safety

Use Kotlin's null safety features:

```kotlin
// Use nullable types when appropriate
val didDocument: DidDocument? = resolveDid(did)

// Use safe calls
didDocument?.verificationMethod?.first()

// Use Elvis operator for defaults
val method = didDocument?.verificationMethod?.first() ?: throw NotFoundException()
```

### Extension Functions

Use extension functions for utilities:

```kotlin
fun DidDocument.toJson(): JsonObject {
    // Conversion logic
}
```

### Scope Functions

Use scope functions appropriately:

```kotlin
// Use `let` for null checks
didDocument?.let { document ->
    // Use document
}

// Use `apply` for configuration
val config = MyConfig().apply {
    endpoint = "https://example.com"
    timeout = 30000L
}
```

## Code Review Guidelines

### Checklist

Before submitting code for review:

- [ ] Code is formatted (`./gradlew ktlintCheck`)
- [ ] All tests pass (`./gradlew test`)
- [ ] Public APIs are documented
- [ ] Error handling is appropriate
- [ ] Code follows naming conventions
- [ ] No hardcoded values (use constants)
- [ ] No commented-out code

### Review Feedback

When reviewing code:

- Be constructive and respectful
- Focus on code quality, not style preferences
- Suggest improvements, don't just point out issues
- Explain why changes are needed

## Next Steps

- Review [Development Setup](development-setup.md) for environment setup
- See [Testing Guidelines](testing-guidelines.md) for testing practices
- Check [Pull Request Process](pull-request-process.md) for contribution workflow
- Explore existing code in TrustWeave modules for examples

## References

- [Kotlin Style Guide](https://kotlinlang.org/docs/coding-conventions.html)
- [ktlint Documentation](https://ktlint.github.io/)
- [KDoc Reference](https://kotlinlang.org/docs/kotlin-doc.html)


