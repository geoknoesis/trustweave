---
title: Code Example Style Guide
parent: Contributing to TrustWeave
nav_order: 30
---

# Code Example Style Guide

This guide establishes standards for code examples in TrustWeave documentation to ensure consistency, clarity, and correctness.

## General Principles

1. **Runnable**: All examples should be copy-paste ready and executable
2. **Complete**: Include all necessary imports and setup
3. **Idiomatic**: Use Kotlin best practices and idioms
4. **Contextual**: Show appropriate error handling for the context
5. **Clear**: Use descriptive variable names and comments

## Canonical Kotlin snippet contract (TrustWeave API)

Use this checklist for every ` ```kotlin ` fence so examples match the real public APIs (see `trust/.../IssuanceBuilder.kt`, `did/did-core/.../DidIdentifiers.kt` in the repo).

| Topic | Rule |
|--------|------|
| **Signing credentials** | `signedBy(issuerDid: Did)` or `signedBy(issuerDid, keyId)` where `keyId: String`. If you only have a DID string, wrap with `Did(didString)`. Never pass `issuerDid.value` (a `String`) as the first argument to `signedBy`. |
| **Key id from DID document** | Use `verificationMethod.firstOrNull()?.extractKeyId()` and `import org.trustweave.did.identifiers.extractKeyId`. Do not use `verificationMethod.first().id.substringAfter("#")` on `VerificationMethodId`. |
| **KMS key handles** | `KeyHandle.id` is a `KeyId`. When a `String` is required (e.g. `signedBy`’s `keyId`, or string parameters), use `generateKey(...).id.value`. |
| **`DidDocument.id`** | Type is `Did`. For APIs expecting `String`, use `document.id.value`. For `signedBy`, pass `document.id` (a `Did`) directly. |
| **DID creation results** | Quick examples: `createDid { ... }.getOrThrowDid()`. Production-oriented pages: exhaustive `when` on `DidCreationResult`. |
| **String templates** | Prefer `"${did.value}..."` when building URIs or key id strings; do not rely on implicit `toString()` unless the doc states it. |
| **Fence shape** | Each snippet is either **self-contained** (imports at top, valid top-level/`main` structure) or a **continuation fragment** with a first-line comment such as `// Continuation: assumes trustWeave, issuerDid from above`. Do not place `import` after indented body code. |

Intentional **wrong** examples (anti-patterns) must be inside a clearly labeled **Wrong** or **Anti-pattern** block, with a **Correct** block beside them.

### Mechanical audit (ripgrep)

Before large documentation PRs, search under `docs/**/*.md` (allowlist intentional hits in *this* file only):

- Legacy VM key extraction: `rg 'verificationMethod\..*substringAfter\("#"\)' docs --glob '*.md'`
- Non-existent API: `rg 'IssuerIdentity\.from' docs --glob '*.md'`

Or run `pwsh -File scripts/check-doc-snippets.ps1` from the repo root (requires [ripgrep](https://github.com/BurntSushi/ripgrep) on `PATH`).

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
val did = trustWeave.createDid { }.getOrThrowDid()
val credential = trustWeave.issue { /* credential { }; signedBy(did, "key-1") */ }.getOrThrow()
```

**Marking:**
Add comment when using this pattern:
```kotlin
// Quick start: using getOrThrow() / getOrThrowDid() for simplicity
// In production, prefer exhaustive `when` on DidCreationResult / IssuanceResult
val did = trustWeave.createDid { }.getOrThrowDid()
```

### Pattern 2: Production Code (sealed results)

**When to use:**
- Production code examples
- Error handling guides
- When demonstrating error recovery
- User-facing applications

**Example:**
```kotlin
// Production pattern: explicit handling
when (val dr = trustWeave.createDid { }) {
    is DidCreationResult.Success -> processDid(dr.did)
    is DidCreationResult.Failure -> handleDidFailure(dr)
}
```

### Pattern 3: Chaining issuance after DID success

**When to use:**
- Chaining multiple facade operations
- Functional style examples
- When demonstrating result transformation

**Example:**
```kotlin
val credential = when (val dr = trustWeave.createDid { }) {
    is DidCreationResult.Success -> when (
        val issued = trustWeave.issue {
            credential { issuer(dr.did); subject { id("did:key:holder") } }
            signedBy(dr.did, "key-1")
        }
    ) {
        is IssuanceResult.Success -> issued.credential
        is IssuanceResult.Failure -> throw IllegalStateException(issued.allErrors.joinToString())
    }
    is DidCreationResult.Failure -> throw IllegalStateException("DID creation failed: $dr")
}
```

## Import Statements

### Required Imports

Always include complete imports at the top of examples:

```kotlin
// TrustWeave imports
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.types.DidCreationResult
import org.trustweave.credential.results.IssuanceResult
import org.trustweave.credential.model.vc.VerifiableCredential

// Kotlinx imports
import kotlinx.coroutines.runBlocking
// Note: buildJsonObject is only needed for standalone JSON creation outside DSL
// Within DSL blocks (subject {}, credential {}, etc.), use DSL syntax instead
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
```

### Import Organization

1. TrustWeave imports (grouped by module)
2. Kotlinx imports
3. Standard library imports
4. Third-party imports

### DSL vs buildJsonObject

**Within DSL blocks**, use the DSL syntax:
```kotlin
subject {
    id("did:key:holder")
    "name" to "Alice"
    "address" {
        "street" to "123 Main St"
        "city" to "New York"
    }
    "grades" to arrayOfObjects(
        { "course" to "CS101"; "grade" to "A" },
        { "course" to "MATH101"; "grade" to "B" }
    )
}
```

**Outside DSL blocks** (for standalone JSON creation), use `buildJsonObject`:
```kotlin
val jsonPayload = buildJsonObject {
    put("id", "did:key:holder")
    put("name", "Alice")
}
```

## Code Comments

### When to Comment

- Explain non-obvious behavior
- Clarify why a pattern is used
- Note important considerations
- Warn about production vs testing

### Comment Style

```kotlin
// Good: Explains why
// Using getOrThrowDid() for quick start — in production use `when` on DidCreationResult
val did = trustWeave.createDid { }.getOrThrowDid()

// Good: Clarifies behavior
// This creates an in-memory wallet (testing only)
val wallet = trustWeave.wallet { holder(holderDid) }.getOrThrow()

// Bad: States the obvious
// Create a DID
val did = trustWeave.createDid { }.getOrThrowDid()
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
// Note: For DSL usage, build subject directly in credential block
// For standalone JSON, use: val credentialSubject = buildJsonObject { ... }

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
> **Version:** 0.6.0
> **API:** TrustWeave Facade

This example uses the TrustWeave facade API available in 1.0.0+.
```

## Context Indicators

### Marking Context

Add context indicators to examples:

```kotlin
import org.trustweave.trust.dsl.credential.DidMethods.KEY

// Quick start / tests — facade instance + extension (import org.trustweave.trust.types.getOrThrow / getOrThrowDid)
val trustWeave = TrustWeave.build { /* keys { }; did { } */ }
val (did, document) = trustWeave.createDid().getOrThrow()
val didOnly = trustWeave.createDid { method(KEY) }.getOrThrowDid()

// Production — sealed DidCreationResult (no kotlin.Result)
val created = trustWeave.createDid { method(KEY) }
when (created) {
    is DidCreationResult.Success -> { /* use created.did, created.document */ }
    is DidCreationResult.Failure -> { /* handle created */ }
}
```

## Complete Example Template

```kotlin
package com.example.TrustWeave.example

// TrustWeave imports
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.types.DidCreationResult
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.resolver.errorMessage

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
    val trustWeave = TrustWeave.quickStart()

    when (val dr = trustWeave.createDid { }) {
        is DidCreationResult.Success -> {
            println("Created DID: ${dr.did.value}")
            when (val res = trustWeave.resolveDid(dr.did)) {
                is DidResolutionResult.Success ->
                    println("Resolved document: ${res.document.id}")
                else ->
                    println("Resolve failed: ${res.errorMessage ?: res}")
            }
        }
        is DidCreationResult.Failure.MethodNotRegistered ->
            println("Method not registered: ${dr.method}")
        is DidCreationResult.Failure ->
            println("DID creation failed: $dr")
    }
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

1. **Avoid missing imports** — examples won't compile
2. **Avoid inconsistent error handling** — confuses readers
3. **Avoid unclear variable names** — hard to understand
4. **Avoid missing context** — readers don't know when to use a pattern
5. **Avoid incomplete examples** — can't run as-is
6. **Avoid version-specific code without tags** — may not work in all versions
7. **Avoid wrong types for `signedBy`** — first parameter must be `Did`, not `String` or `issuerDid.value`
8. **Avoid `substringAfter("#")` for key ids** — use `extractKeyId()` from the DID document
9. **Avoid non-existent helpers** — e.g. `IssuerIdentity.from(did, keyId)` is not an issuance API; use `signedBy(issuerDid, issuerKeyId)` with `issuerDid: Did`

## Related Documentation

- [Quick Start](../getting-started/quick-start.md) — examples using these patterns
- [Error Handling](../api-reference/advanced/error-handling.md) — error handling patterns
- [Code Style](code-style.md) — general code style guidelines

