# DSL Placement Recommendation: did-core vs trust module

## Executive Summary

**Recommendation: Split DSL across both modules with clear separation of concerns**

- **did-core**: Basic, standalone DSL extensions that work with DID types directly
- **trust module**: Higher-level orchestration DSL that requires context (KMS, registries, trust policies)

---

## Current Architecture Analysis

### Dependency Direction ✅
```
trust module → did-core (correct direction)
did-core → trust module (no dependencies - good!)
```

**Finding**: The dependency direction is correct. `did-core` is independent and can be used standalone.

### Current DSL Location
- **Current**: DSL is in `trust` module (`DidBuilder`, `DidDocumentBuilder`, etc.)
- **Dependency**: Trust module DSL depends on `DidDslProvider` interface which provides orchestration context

---

## Recommended DSL Placement Strategy

### 1. **did-core**: Basic, Standalone DSL Extensions

**Location**: `did/did-core/src/main/kotlin/org.trustweave/did/dsl/`

**Purpose**: Provide fluent, ergonomic extensions that work with DID types directly, without requiring orchestration context.

**What belongs here:**

#### A. Extension Functions on Core Types
```kotlin
// did-core DSL extensions
package org.trustweave.did.dsl

// Extension on Did type
suspend fun Did.resolveWith(resolver: DidResolver): DidResolutionResult {
    return resolver.resolve(this) ?: DidResolutionResult.Failure.NotFound(this)
}

// Extension on DidResolver
fun DidResolver.resolveOrThrow(did: Did): DidDocument {
    return when (val result = resolve(did)) {
        is DidResolutionResult.Success -> result.document
        else -> throw DidException.DidNotFound(did.value)
    }
}

// Extension on DidResolutionResult
fun DidResolutionResult.getOrThrow(): DidDocument = when (this) {
    is DidResolutionResult.Success -> document
    else -> throw DidException.DidNotFound(...)
}

fun DidResolutionResult.getOrNull(): DidDocument? = when (this) {
    is DidResolutionResult.Success -> document
    else -> null
}
```

#### B. Basic Builder DSL (Direct Method Usage)
```kotlin
// did-core DSL - works with DidMethod directly
suspend fun DidMethod.createDidWith(
    block: DidCreationOptionsBuilder.() -> Unit
): DidDocument {
    val options = didCreationOptions(block)
    return createDid(options)
}

// Usage (standalone, no orchestration needed):
val method = KeyDidMethod(kms)
val document = method.createDidWith {
    algorithm = KeyAlgorithm.ED25519
    purpose(KeyPurpose.AUTHENTICATION)
}
```

#### C. Infix Operators for DID Composition
```kotlin
// did-core: Basic DID composition operators
operator fun Did.plus(fragment: String): VerificationMethodId  // ✅ Already exists!

infix fun Did.with(fragment: String): VerificationMethodId  // ✅ Already exists!

// New: Resolution operators
infix fun Did.resolvedBy(resolver: DidResolver): DidResolutionResult {
    return resolver.resolve(this) ?: DidResolutionResult.Failure.NotFound(this)
}
```

**Benefits:**
- ✅ Can be used standalone (no trust module dependency)
- ✅ Works with just `did-core` types
- ✅ Minimal, focused on DID operations
- ✅ No orchestration context required

---

### 2. **trust module**: Higher-Level Orchestration DSL

**Location**: `trust/src/main/kotlin/org.trustweave/trust/dsl/did/` (keep current location)

**Purpose**: Provide orchestrated DSL that requires context (KMS, registries, trust policies, multiple services).

**What belongs here:**

#### A. Context-Aware DID Creation
```kotlin
// trust module DSL - requires orchestration context
suspend fun DidDslProvider.createDid(block: DidBuilder.() -> Unit): DidCreationResult {
    // Uses provider to get method, KMS, etc.
    // Returns sealed result type with rich error information
}

// Usage (requires TrustWeave context):
val trustWeave = TrustWeave.build { ... }
val result = trustWeave.createDid {
    method(KEY)
    algorithm(ED25519)
}
```

#### B. Trust-Aware Operations
```kotlin
// trust module: Trust-aware verification
suspend fun DidDslProvider.verifyDelegation(
    delegator: Did,
    delegate: Did
): DelegationVerificationResult {
    // Uses resolver from context
    // Checks trust relationships
    // Returns trust-aware result
}
```

#### C. Multi-Service Orchestration
```kotlin
// trust module: Operations requiring multiple services
suspend fun TrustWeave.createIdentityWithCredential(
    block: IdentityBuilder.() -> Unit
): IdentityCreationResult {
    // 1. Creates DID (needs KMS)
    // 2. Issues credential (needs credential service)
    // 3. Registers trust anchor (needs trust registry)
    // 4. Returns combined result
}
```

**Benefits:**
- ✅ Provides orchestration across multiple services
- ✅ Returns rich, context-aware result types
- ✅ Integrates with trust policies and registries
- ✅ Handles complex multi-step operations

---

## Decision Matrix

| DSL Feature | Location | Rationale |
|------------|---------|-----------|
| `Did.resolveWith(resolver)` | **did-core** | Works with just `Did` + `DidResolver` types |
| `DidResolver.resolveOrThrow()` | **did-core** | Extension on core interface, no context needed |
| `DidMethod.createDidWith { }` | **did-core** | Works with `DidMethod` directly |
| `Did + "fragment"` operator | **did-core** | ✅ Already there - pure type composition |
| `TrustWeave.createDid { }` | **trust module** | Requires orchestration context (KMS, registry) |
| `verifyDelegation()` | **trust module** | Requires resolver from context + trust policies |
| `createIdentityWithCredential()` | **trust module** | Multi-service orchestration |
| Trust policy DSL | **trust module** | Trust-specific, not core DID functionality |

---

## Implementation Strategy

### Phase 1: Add Basic DSL to did-core

```kotlin
// did/did-core/src/main/kotlin/org.trustweave/did/dsl/DidExtensions.kt
package org.trustweave.did.dsl

import org.trustweave.did.identifiers.Did
import org.trustweave.did.resolver.DidResolver
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.exception.DidException

/**
 * Extension functions for fluent DID operations.
 * These work standalone without orchestration context.
 */

/**
 * Resolves this DID using the provided resolver.
 */
suspend fun Did.resolveWith(resolver: DidResolver): DidResolutionResult {
    return resolver.resolve(this) ?: DidResolutionResult.Failure.NotFound(
        did = this,
        reason = "DID not found"
    )
}

/**
 * Resolves this DID and returns the document, or throws if not found.
 */
suspend fun Did.resolveOrThrow(resolver: DidResolver): DidDocument {
    return when (val result = resolver.resolve(this)) {
        is DidResolutionResult.Success -> result.document
        else -> throw DidException.DidNotFound(
            did = this.value,
            availableMethods = emptyList()
        )
    }
}

/**
 * Resolves this DID and returns the document, or null if not found.
 */
suspend fun Did.resolveOrNull(resolver: DidResolver): DidDocument? {
    return when (val result = resolver.resolve(this)) {
        is DidResolutionResult.Success -> result.document
        else -> null
    }
}
```

```kotlin
// did/did-core/src/main/kotlin/org.trustweave/did/dsl/ResolverExtensions.kt
package org.trustweave.did.dsl

import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument
import org.trustweave.did.resolver.DidResolver
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.exception.DidException

/**
 * Extension functions for DidResolver to improve ergonomics.
 */

/**
 * Resolves a DID and returns the document, or throws if not found.
 */
suspend fun DidResolver.resolveOrThrow(did: Did): DidDocument {
    return when (val result = resolve(did)) {
        is DidResolutionResult.Success -> result.document
        else -> throw DidException.DidNotFound(
            did = did.value,
            availableMethods = emptyList()
        )
    }
}

/**
 * Resolves a DID and returns the document, or null if not found.
 */
suspend fun DidResolver.resolveOrNull(did: Did): DidDocument? {
    return when (val result = resolve(did)) {
        is DidResolutionResult.Success -> result.document
        else -> null
    }
}
```

```kotlin
// did/did-core/src/main/kotlin/org.trustweave/did/dsl/DidMethodExtensions.kt
package org.trustweave.did.dsl

import org.trustweave.did.DidMethod
import org.trustweave.did.DidCreationOptions
import org.trustweave.did.DidCreationOptionsBuilder
import org.trustweave.did.model.DidDocument
import org.trustweave.did.didCreationOptions

/**
 * Extension functions for DidMethod to provide fluent DSL.
 */

/**
 * Creates a DID using a builder DSL.
 */
suspend fun DidMethod.createDidWith(
    block: DidCreationOptionsBuilder.() -> Unit
): DidDocument {
    val options = didCreationOptions(block)
    return createDid(options)
}
```

### Phase 2: Keep Trust Module DSL (Orchestration)

The trust module DSL remains for context-aware operations:

```kotlin
// trust/src/main/kotlin/org.trustweave/trust/dsl/did/DidBuilder.kt
// Keep existing - provides orchestration context
```

---

## Benefits of This Approach

### ✅ Modularity
- `did-core` DSL can be used standalone
- Users can use DID operations without pulling in trust module
- Clear separation: basic operations vs orchestration

### ✅ Discoverability
- Basic operations are in `did-core` where developers expect them
- Advanced orchestration is in `trust` module where it belongs

### ✅ Dependency Management
- `did-core` remains lightweight and independent
- `trust` module provides value-added orchestration
- No circular dependencies

### ✅ Use Cases

**Standalone DID Operations (did-core only):**
```kotlin
// User only needs did-core
dependencies {
    implementation("org.trustweave:trustweave-did:1.0.0")
}

val resolver = RegistryBasedResolver(registry)
val document = Did("did:key:...").resolveWith(resolver).getOrThrow()
```

**Orchestrated Operations (trust module):**
```kotlin
// User needs orchestration
dependencies {
    implementation("org.trustweave:trustweave-trust:1.0.0")
}

val trustWeave = TrustWeave.build { ... }
val result = trustWeave.createDid { method(KEY) }
```

---

## Migration Path

1. **Add basic DSL to did-core** (non-breaking)
   - Add extension functions
   - Add basic builder DSL
   - Document in did-core module docs

2. **Keep trust module DSL** (no changes needed)
   - Trust module DSL continues to work
   - Can delegate to did-core DSL where appropriate

3. **Update documentation**
   - Document when to use did-core DSL vs trust module DSL
   - Show examples of both approaches

---

## Conclusion

**Split the DSL across both modules:**

- **did-core**: Basic, standalone extensions for direct DID operations
- **trust module**: Higher-level orchestration DSL requiring context

This approach:
- ✅ Maintains modularity
- ✅ Allows standalone usage of did-core
- ✅ Provides orchestration when needed
- ✅ Follows dependency direction correctly
- ✅ Improves discoverability

The key principle: **If it works with just DID types, it belongs in did-core. If it requires orchestration context, it belongs in trust module.**

