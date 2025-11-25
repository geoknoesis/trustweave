---
title: Code Example Style Guide
---

# Code Example Style Guide

This guide establishes standards for code examples in TrustWeave documentation to ensure consistency, clarity, and correctness.

## General Principles

1. **Runnable**: All examples should be copy-paste ready and executable
2. **Complete**: Include all necessary imports and setup
3. **Idiomatic**: Use Kotlin best practices and idioms
4. **Contextual**: Show appropriate error handling for the context
5. **Clear**: Use descriptive variable names and comments

## Error Handling Patterns

### Pattern 1: Quick Start / Prototypes (getOrThrow)

**When to use:**
- Quick start examples
- Prototypes and demos
- Test code
- Simple scripts
- Documentation examples where error handling would obscure the main point

**Example:**
```kotlin
// Quick start pattern
val did = TrustWeave.createDid().getOrThrow()
val credential = TrustWeave.issueCredential(...).getOrThrow()
```

**Marking:**
Add comment when using this pattern:
```kotlin
// Quick start: using getOrThrow() for simplicity
// In production, use fold() for proper error handling
val did = TrustWeave.createDid().getOrThrow()
```

### Pattern 2: Production Code (fold)

**When to use:**
- Production code examples
- Error handling guides
- When demonstrating error recovery
- User-facing applications

**Example:**
```kotlin
// Production pattern: explicit error handling
val result = TrustWeave.createDid()
result.fold(
    onSuccess = { did -> 
        processDid(did)
    },
    onFailure = { error ->
        handleError(error)
    }
)
```

### Pattern 3: Result Chaining (map/flatMap)

**When to use:**
- Chaining multiple operations
- Functional style examples
- When demonstrating result transformation

**Example:**
```kotlin
// Chaining pattern
val credential = TrustWeave.createDid()
    .map { did -> 
        TrustWeave.issueCredential(issuerDid = did.id, ...)
    }
    .flatMap { it }  // Unwrap nested Result
    .getOrThrow()
```

## Import Statements

### Required Imports

Always include complete imports at the top of examples:

```kotlin
// TrustWeave imports
import com.trustweave.TrustWeave
import com.trustweave.core.*
import com.trustweave.did.*
import com.trustweave.credential.models.VerifiableCredential

// Kotlinx imports
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
```

### Import Organization

1. TrustWeave imports (grouped by module)
2. Kotlinx imports
3. Standard library imports
4. Third-party imports

## Code Comments

### When to Comment

- Explain non-obvious behavior
- Clarify why a pattern is used
- Note important considerations
- Warn about production vs testing

### Comment Style

```kotlin
// Good: Explains why
// Using getOrThrow() for quick start - in production use fold()
val did = TrustWeave.createDid().getOrThrow()

// Good: Clarifies behavior
// This creates an in-memory wallet (testing only)
val wallet = TrustWeave.createWallet(holderDid).getOrThrow()

// Bad: States the obvious
// Create a DID
val did = TrustWeave.createDid().getOrThrow()
```

## Variable Naming

### Naming Conventions

- Use descriptive names: `issuerDid`, `credentialSubject`, `walletId`
- Avoid abbreviations: `vc` → `credential`, `didDoc` → `didDocument`
- Use domain terms: `holderDid`, `issuerKeyId`, `credentialId`

### Examples

```kotlin
// Good: Descriptive names
val issuerDid = "did:key:issuer"
val issuerKeyId = "did:key:issuer#key-1"
val credentialSubject = buildJsonObject { ... }

// Bad: Abbreviations
val iDid = "did:key:issuer"
val kId = "did:key:issuer#key-1"
val cs = buildJsonObject { ... }
```

## Package Declarations

### When to Include

- Complete, runnable examples
- Examples in getting-started guides
- Tutorial examples

### When to Omit

- API reference snippets
- Inline code examples
- Quick reference examples

### Format

```kotlin
package com.example.TrustWeave.quickstart

// ... imports ...

fun main() = runBlocking {
    // ... code ...
}
```

## Version Tags

### When to Tag

- Examples using version-specific features
- Examples that may not work in all versions
- Breaking change examples

### Format

```markdown
> **Version:** 1.0.0-SNAPSHOT  
> **API:** TrustWeave Facade

This example uses the TrustWeave facade API available in 1.0.0+.
```

## Context Indicators

### Marking Context

Add context indicators to examples:

```kotlin
// Quick start example
val did = TrustWeave.createDid().getOrThrow()

// Production example
val result = TrustWeave.createDid()
result.fold(
    onSuccess = { did -> /* handle */ },
    onFailure = { error -> /* handle */ }
)

// Testing example
val testDid = TrustWeave.createDid().getOrThrow()
```

## Complete Example Template

```kotlin
package com.example.TrustWeave.example

// TrustWeave imports
import com.trustweave.TrustWeave
import com.trustweave.core.*
import com.trustweave.did.*

// Kotlinx imports
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Example: Creating and using a DID
 * 
 * This example demonstrates:
 * - Creating a DID with default options
 * - Resolving a DID
 * - Error handling patterns
 */
fun main() = runBlocking {
    // Create TrustWeave instance
    val TrustWeave = TrustWeave.create()
    
    // Create DID with error handling
    val didResult = TrustWeave.createDid()
    didResult.fold(
        onSuccess = { did ->
            println("Created DID: ${did.id}")
            
            // Resolve the DID
            val resolution = TrustWeave.resolveDid(did.id).getOrThrow()
            if (resolution.document != null) {
                println("Resolved DID document")
            }
        },
        onFailure = { error ->
            when (error) {
                is TrustWeaveError.DidMethodNotRegistered -> {
                    println("Method not registered: ${error.method}")
                }
                else -> {
                    println("Error: ${error.message}")
                }
            }
        }
    )
}
```

## Best Practices

1. **Always include imports** for complete examples
2. **Use descriptive variable names** that match domain terminology
3. **Add context comments** when using patterns that might not be obvious
4. **Show error handling** appropriate to the context
5. **Include expected output** for complete examples
6. **Tag version-specific** examples
7. **Use consistent formatting** (ktlint-compliant)

## Anti-Patterns to Avoid

1. ❌ **Missing imports** - Examples won't compile
2. ❌ **Inconsistent error handling** - Confuses readers
3. ❌ **Unclear variable names** - Hard to understand
4. ❌ **Missing context** - Readers don't know when to use pattern
5. ❌ **Incomplete examples** - Can't run as-is
6. ❌ **Version-specific code without tags** - May not work in all versions

## Related Documentation

- [Quick Start](../getting-started/quick-start.md) - Examples using these patterns
- [Error Handling](../advanced/error-handling.md) - Error handling patterns
- [Code Style](../contributing/code-style.md) - General code style guidelines

