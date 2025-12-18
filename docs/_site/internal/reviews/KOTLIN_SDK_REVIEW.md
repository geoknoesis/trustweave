# Kotlin SDK API Review: TrustWeave Credential Service
## Reference-Quality API Transformation Plan

---

## 🎯 Executive Summary

**Key Architectural Insight**: All proof formats (VC-LD, VC-JWT, SD-JWT-VC) are **built-in and always available**. This means the current adapter registry/discovery system is **unnecessary complexity** that should be removed from the public API.

### Critical Simplification Needed

The API currently exposes adapter registration/discovery mechanisms that are **not needed** since proof formats are always available:

```kotlin
// ❌ Current: Unnecessary complexity
val registry = ProofRegistries.default()
ProofAdapters.autoRegister(registry, emptyMap())
val service = createCredentialService(registry, didResolver)

// ✅ Ideal: Simple, all formats built-in
val service = CredentialService.default(didResolver)
```

### Top 3 Priorities

1. **Remove adapter registry/discovery from public API** - Move to `internal` package
2. **Simplify factory to single method** - `CredentialService.default(didResolver)`
3. **Fix error handling consistency** - Use `Result` types instead of exceptions

---

## 1. High-Level Evaluation

### Strengths

✅ **Strong Type Safety**: Sealed classes for `IssuanceResult` and `VerificationResult` provide exhaustive, type-safe error handling  
✅ **W3C Alignment**: API correctly models W3C Verifiable Credentials Data Model  
✅ **Coroutine-First**: Proper use of `suspend` functions for async operations  
✅ **Domain Precision**: Types like `CredentialFormatId`, `Did`, `Iri` express domain concepts clearly  
✅ **Built-in Proof Formats**: All proof formats (VC-LD, VC-JWT, SD-JWT-VC) are built-in and always available  
✅ **Result Types**: Well-designed sealed hierarchies for success/failure cases  

### Critical Weaknesses

❌ **Factory Function Proliferation**: 4 different factory functions create cognitive overhead  
❌ **Exception-Based Error Handling**: DID extensions throw `IllegalArgumentException` instead of returning `Result` types  
❌ **Naming Inconsistencies**: Mix of "Service", "Manager", "Registry" patterns without clear semantics  
❌ **Over-Engineered Adapter System**: Registry/discovery patterns exposed when proof formats are built-in and always available  
❌ **Redundant Extensions**: `issueCredential()` extension duplicates `issue()` with less flexibility  
❌ **Missing Happy Path**: No simple "default" factory for common use cases  
❌ **Inconsistent Result Handling**: Some operations return results, others throw exceptions  

### Overall Assessment

The API is **well-architected** but **over-engineered** for common use cases. It prioritizes flexibility over ergonomics, making the "happy path" unnecessarily complex. The codebase shows strong understanding of Kotlin idioms and Web-of-Trust concepts, but needs simplification to achieve reference-quality status.

**Trustworthiness**: ⭐⭐⭐⭐ (4/5) - Secure, but error handling inconsistencies reduce trust  
**Modernity**: ⭐⭐⭐⭐ (4/5) - Uses modern Kotlin features, but could be more idiomatic  
**Kotlin-Idiomatic**: ⭐⭐⭐ (3/5) - Good use of sealed classes, but exception handling and factory patterns need work  

---

## 2. Public API & Developer Experience

### 2.0 Critical Simplification Opportunity: Built-in Proof Formats

**Key Insight**: Since all proof formats (VC-LD, VC-JWT, SD-JWT-VC) are **built-in and always available**, the current adapter registry/discovery system is **unnecessary complexity** in the public API.

#### Current Over-Engineering

The API currently exposes:
- `ProofAdapterRegistry` interface
- `ProofRegistries.default()` object
- `ProofAdapters.autoRegister()` with ServiceLoader discovery
- Multiple factory functions for adapter registration

**But**: If proof formats are always available, developers should never need to:
- Register adapters
- Discover adapters
- Manage registries
- Choose between auto-discovery vs explicit registration

#### Simplified Architecture

```kotlin
// Current (unnecessary complexity):
val registry = ProofRegistries.default()
ProofAdapters.autoRegister(registry, emptyMap())
val service = createCredentialService(registry, didResolver)

// Ideal (proof formats are built-in):
val service = CredentialService.default(didResolver)
// ✅ All formats (VC-LD, VC-JWT, SD-JWT-VC) are always available
```

#### Recommendations

1. **Move registry/discovery to `internal` package**
   - `ProofAdapterRegistry` → `internal`
   - `ProofRegistries` → `internal`
   - `ProofAdapters` → `internal` (or remove ServiceLoader discovery)

2. **Simplify factory to single method**
   ```kotlin
   object CredentialService {
       fun default(
           didResolver: DidResolver,
           schemaRegistry: SchemaRegistry? = null,
           statusListManager: StatusListManager? = null
       ): CredentialService
   }
   ```

3. **Internal implementation can still use registry pattern**
   - Keep registry pattern internally for code organization
   - But pre-register all built-in adapters at construction time
   - No public API exposure needed

4. **Remove adapter-related factory overloads**
   - Remove `createCredentialService(adapterRegistry: ...)`
   - Remove `createCredentialService(vararg adapters: ...)`
   - Remove `createCredentialServiceWithAutoDiscovery(...)`

### Current API Surface Issues

#### 2.1 Factory Function Overload (Made Worse by Built-in Formats)

**Problem**: Four factory functions create decision paralysis, even though proof formats are built-in:

```kotlin
// Current - too many choices, unnecessary complexity
fun createCredentialService(adapterRegistry: ProofAdapterRegistry, ...)
fun createCredentialService(didResolver: DidResolver, vararg adapters: ProofAdapter, ...)
fun createCredentialServiceWithAutoDiscovery(didResolver: DidResolver, ...)
fun createCredentialServiceWithAutoDiscovery(didResolver: DidResolver, formatIds: List<CredentialFormatId>, ...)
```

**Impact**: Developers must understand registry patterns and adapters even though all proof formats are always available. This is unnecessary complexity.

**Recommendation**: Since proof formats are built-in, provide a simple factory:

```kotlin
// Ideal - simple, all formats always available
object CredentialService {
    fun default(
        didResolver: DidResolver,
        schemaRegistry: SchemaRegistry? = null,
        statusListManager: StatusListManager? = null
    ): CredentialService {
        // All proof formats built-in, no registration needed
        return DefaultCredentialService(didResolver, schemaRegistry, statusListManager)
    }
}
```

#### 2.2 Missing Happy Path

**Problem**: No simple way to get started:

```kotlin
// What developers want to write:
val service = CredentialService.default(didResolver)

// What they currently must write (unnecessary complexity):
val registry = ProofRegistries.default()
ProofAdapters.autoRegister(registry, emptyMap())
val service = createCredentialService(registry, didResolver)
// ❌ This is over-engineered - proof formats are built-in!
```

**Recommendation**: Since proof formats are built-in, simplify to:

```kotlin
object CredentialService {
    /**
     * Creates a credential service with all built-in proof formats.
     * 
     * All proof formats (VC-LD, VC-JWT, SD-JWT-VC) are always available.
     * No registration or discovery needed.
     */
    fun default(
        didResolver: DidResolver,
        schemaRegistry: SchemaRegistry? = null,
        statusListManager: StatusListManager? = null
    ): CredentialService {
        // All proof adapters are built-in - no registry/discovery needed
        return DefaultCredentialService(didResolver, schemaRegistry, statusListManager)
    }
}
```

#### 2.3 Redundant Extension Functions

**Problem**: `issueCredential()` extension duplicates `issue()` with less power:

```kotlin
// Current - redundant and less flexible
suspend fun CredentialService.issueCredential(
    format: CredentialFormatId,
    issuer: Issuer,
    credentialSubject: CredentialSubject,
    type: List<CredentialType>,
    ...
): IssuanceResult

// vs the more powerful
suspend fun CredentialService.issue(request: IssuanceRequest): IssuanceResult
```

**Recommendation**: Remove `issueCredential()` extension. If needed, provide a builder DSL instead:

```kotlin
// Better - DSL for ergonomic building
suspend fun CredentialService.issue(
    format: CredentialFormatId,
    block: IssuanceRequestBuilder.() -> Unit
): IssuanceResult
```

#### 2.4 Inconsistent Error Handling

**Problem**: DID extensions throw exceptions instead of returning results:

```kotlin
// Current - throws exception
suspend fun CredentialService.issueForDid(
    didResolver: DidResolver,
    subjectDid: Did,
    ...
): IssuanceResult {
    val resolutionResult = didResolver.resolve(subjectDid)
    when (resolutionResult) {
        is DidResolutionResult.Success -> { /* OK */ }
        else -> throw IllegalArgumentException("Subject DID not found")  // ❌ Exception
    }
}
```

**Recommendation**: Return `Result` or sealed failure types:

```kotlin
// Better - returns Result
suspend fun CredentialService.issueForDid(
    didResolver: DidResolver,
    subjectDid: Did,
    ...
): Result<VerifiableCredential, IssuanceFailure> {
    val resolutionResult = didResolver.resolve(subjectDid)
    return when (resolutionResult) {
        is DidResolutionResult.Success -> {
            val request = IssuanceRequest(...)
            issue(request).fold(
                onFailure = { Result.failure(it) },
                onSuccess = { Result.success(it.credential) }
            )
        }
        else -> Result.failure(IssuanceFailure.SubjectDidNotResolved(subjectDid))
    }
}
```

### Developer Experience Improvements

#### 2.5 Discoverability

**Current**: Developers must read documentation to understand:
- What is a `ProofAdapter`?
- What is a `ProofAdapterRegistry`?
- When to use auto-discovery vs explicit adapters?

**Recommendation**: 
- Hide registry details behind a simple factory
- Provide clear examples in function documentation
- Use type-safe builders for complex operations

#### 2.6 Type Safety Gaps

**Problem**: Some operations accept `Map<String, Any?>` for configuration:

```kotlin
fun createCredentialServiceWithAutoDiscovery(
    didResolver: DidResolver,
    options: Map<String, Any?> = emptyMap()  // ❌ Type-unsafe
)
```

**Recommendation**: Use data classes for configuration:

```kotlin
data class CredentialServiceConfig(
    val schemaRegistry: SchemaRegistry? = null,
    val statusListManager: StatusListManager? = null,
    val adapterOptions: Map<CredentialFormatId, AdapterConfig> = emptyMap()
)

fun CredentialService.create(
    didResolver: DidResolver,
    config: CredentialServiceConfig = CredentialServiceConfig()
): CredentialService
```

---

## 3. Naming, Domain Modeling & Web-of-Trust Semantics

### 3.1 Naming Issues

#### ❌ Generic/Implementation Names

| Current | Issue | Recommendation |
|---------|-------|----------------|
| `CredentialService` | "Service" is generic | Keep (it's the main interface) |
| `DefaultCredentialService` | "Default" is vague | `CredentialServiceImpl` (internal) |
| `ProofAdapterRegistry` | "Registry" is implementation detail | Keep but make internal |
| `StatusListManager` | "Manager" is vague | `CredentialStatusChecker` or `RevocationChecker` |
| `SchemaRegistry` | "Registry" is implementation detail | Keep but consider `SchemaValidator` |

#### ❌ Inconsistent Patterns

- `ProofRegistries.default()` - object with method
- `ProofAdapters.autoRegister()` - object with method  
- `createCredentialService()` - top-level function
- `CredentialService.create()` - would be more consistent

**Recommendation**: Standardize on object-based factories:

```kotlin
object CredentialService {
    fun default(...): CredentialService
    fun create(...): CredentialService
    fun withAdapters(...): CredentialService
}
```

### 3.2 Domain Model Clarity

#### ✅ Good Domain Names

- `VerifiableCredential` - Clear, W3C-aligned
- `CredentialFormatId` - Opaque identifier, type-safe
- `IssuanceResult` / `VerificationResult` - Clear operation results
- `Did` / `Iri` - Domain-precise identifiers

#### ⚠️ Could Be Clearer

- `IssuanceRequest` - Good, but consider `CredentialIssuanceRequest` for clarity
- `PresentationRequest` - Good
- `VerificationOptions` - Good, but consider `CredentialVerificationOptions`
- `CredentialStatusInfo` - Good

### 3.3 Web-of-Trust Semantics

#### Missing Trust Concepts

The API doesn't explicitly model:
- **Trust policies** (which issuers are trusted?)
- **Trust chains** (credential delegation)
- **Trust anchors** (root of trust)

**Recommendation**: Add trust-aware types:

```kotlin
interface TrustPolicy {
    suspend fun isTrusted(issuer: Did): Boolean
    suspend fun verifyTrustChain(credential: VerifiableCredential): TrustChainResult
}

sealed class TrustChainResult {
    data class Trusted(val chain: List<VerifiableCredential>) : TrustChainResult()
    data class Untrusted(val reason: String) : TrustChainResult()
    data class ChainBroken(val missingLink: Did) : TrustChainResult()
}
```

#### Proof Semantics

**Current**: `CredentialProof` is format-agnostic, which is good, but the API doesn't clearly express:
- What cryptographic properties the proof provides?
- What trust assumptions are made?

**Recommendation**: Add proof metadata:

```kotlin
data class ProofMetadata(
    val algorithm: ProofAlgorithm,
    val verificationMethod: VerificationMethodId,
    val purpose: ProofPurpose,  // authentication, assertion, etc.
    val created: Instant
)
```

---

## 4. Idiomatic Kotlin & Architecture

### 4.1 Kotlin Idioms - What's Good

✅ **Sealed Classes**: Excellent use for result types  
✅ **Value Classes**: `CredentialFormatId` as `@JvmInline value class`  
✅ **Extension Functions**: Good use for ergonomic APIs  
✅ **Data Classes**: Proper use for data types  
✅ **Coroutines**: Correct `suspend` usage  

### 4.2 Kotlin Idioms - What Needs Work

#### ❌ Exception Handling

**Problem**: Mix of exceptions and sealed results:

```kotlin
// Throws exception
suspend fun CredentialService.issueForDid(...): IssuanceResult {
    if (resolutionResult !is Success) {
        throw IllegalArgumentException(...)  // ❌
    }
}

// Returns result (good)
suspend fun CredentialService.issue(...): IssuanceResult
```

**Recommendation**: Use `Result<T, E>` or sealed results consistently:

```kotlin
// Better - consistent Result type
suspend fun CredentialService.issueForDid(...): Result<VerifiableCredential, IssuanceFailure>
```

#### ❌ Null Safety

**Problem**: Some operations return nullable types when they should return `Result`:

```kotlin
// Current - nullable return
suspend fun CredentialService.resolveSubjectDid(
    didResolver: DidResolver,
    credential: VerifiableCredential
): Did?  // ❌ Nullable, loses error information

// Better - Result type
suspend fun CredentialService.resolveSubjectDid(
    didResolver: DidResolver,
    credential: VerifiableCredential
): Result<Did, SubjectResolutionFailure>
```

#### ❌ Companion Object Usage

**Problem**: Inconsistent use of companion objects vs top-level functions:

```kotlin
// Current - mixed patterns
fun createCredentialService(...)  // Top-level
object ProofRegistries { ... }    // Object
companion object { fun fromDid(...) }  // Companion

// Better - consistent object pattern
object CredentialService {
    fun create(...)
    fun default(...)
}
```

### 4.3 Architecture Issues

#### ❌ Leaky Abstractions

**Problem**: Internal implementation details exposed:

```kotlin
// Current - registry exposed in public API
fun createCredentialService(
    adapterRegistry: ProofAdapterRegistry,  // ❌ Internal detail
    ...
)

// Better - hide registry
fun CredentialService.create(
    didResolver: DidResolver,
    adapters: List<ProofAdapter> = emptyList()  // ✅ Higher-level abstraction
)
```

#### ❌ Module Boundaries

**Current Structure**:
```
credential-api/
  - CredentialService (interface)
  - CredentialServices (factory functions)
  - internal/
    - DefaultCredentialService
  - proof/
    - ProofAdapterRegistry (public)  // ❌ Should be internal
```

**Recommendation**:
```
credential-api/
  - CredentialService (interface)
  - CredentialService.kt (object factory)
  - internal/
    - DefaultCredentialService
    - ProofAdapterRegistry
    - ProofRegistries
```

---

## 5. Trust Model & Crypto/API Safety Review

### 5.1 Cryptographic Safety

#### ✅ Good Practices

- Opaque format IDs prevent format confusion
- Type-safe identifiers (`Did`, `Iri`, `CredentialFormatId`)
- Sealed result types prevent ignoring errors

#### ⚠️ Safety Concerns

**Problem**: No explicit cryptographic algorithm validation:

```kotlin
// Current - no algorithm validation
data class VerificationOptions(
    val checkRevocation: Boolean = true,
    val checkExpiration: Boolean = true,
    // ❌ No algorithm whitelist/blacklist
)
```

**Recommendation**: Add algorithm policy:

```kotlin
data class VerificationOptions(
    val checkRevocation: Boolean = true,
    val checkExpiration: Boolean = true,
    val allowedAlgorithms: Set<ProofAlgorithm>? = null,  // null = all allowed
    val minimumKeyStrength: KeyStrength = KeyStrength.MEDIUM
)
```

**Problem**: Key material handling not explicit:

```kotlin
// Current - key handling hidden in adapters
suspend fun issue(request: IssuanceRequest): IssuanceResult
// Where does the key come from? How is it protected?

// Better - explicit key management
interface KeyProvider {
    suspend fun getSigningKey(methodId: VerificationMethodId): SigningKey
}

suspend fun CredentialService.issue(
    request: IssuanceRequest,
    keyProvider: KeyProvider
): IssuanceResult
```

### 5.2 Trust Boundaries

#### Missing Trust Policies

**Problem**: No explicit trust model:

```kotlin
// Current - no trust policy
suspend fun verify(
    credential: VerifiableCredential,
    options: VerificationOptions = VerificationOptions()
): VerificationResult
// ❌ Doesn't check if issuer is trusted

// Better - explicit trust policy
suspend fun verify(
    credential: VerifiableCredential,
    trustPolicy: TrustPolicy,
    options: VerificationOptions = VerificationOptions()
): VerificationResult
```

#### Missing Trust Chain Validation

**Problem**: No support for credential delegation:

```kotlin
// Current - no delegation support
// If issuer A delegates to issuer B, how do we verify?

// Better - trust chain support
sealed class TrustChainResult {
    data class Trusted(val chain: List<VerifiableCredential>) : TrustChainResult()
    data class Untrusted(val reason: String) : TrustChainResult()
}

suspend fun CredentialService.verifyTrustChain(
    credential: VerifiableCredential,
    trustPolicy: TrustPolicy
): TrustChainResult
```

### 5.3 API Safety

#### ✅ Type Safety

- Strong typing prevents many errors
- Sealed classes ensure exhaustive handling
- Value classes prevent primitive obsession

#### ⚠️ Runtime Safety

**Problem**: Some operations can fail silently or throw unexpected exceptions:

```kotlin
// Current - can throw
suspend fun CredentialService.issueForDid(...): IssuanceResult {
    throw IllegalArgumentException(...)  // ❌ Unexpected exception
}

// Better - explicit failure type
suspend fun CredentialService.issueForDid(...): Result<VerifiableCredential, IssuanceFailure>
```

---

## 6. Concurrency & Coroutine Design

### 6.1 Current State

#### ✅ Good Practices

- All async operations use `suspend`
- Batch operations use `coroutineScope` and `async`/`awaitAll`
- No `GlobalScope` usage observed

#### ⚠️ Issues

**Problem**: No explicit cancellation handling:

```kotlin
// Current - no cancellation documentation
suspend fun verify(
    credential: VerifiableCredential,
    options: VerificationOptions = VerificationOptions()
): VerificationResult
// Is this cancellable? How long does it take?

// Better - document cancellation
/**
 * Verifies a credential.
 * 
 * This operation is cancellable and will respect coroutine cancellation.
 * Typical duration: 100-500ms depending on DID resolution and proof verification.
 */
suspend fun verify(...): VerificationResult
```

**Problem**: Blocking operations not clearly marked:

```kotlin
// Current - unclear if blocking
fun supports(format: CredentialFormatId): Boolean
// Is this a registry lookup (fast) or does it do I/O?

// Better - document clearly
/**
 * Checks if a format is supported.
 * 
 * This is a fast, non-blocking registry lookup. No I/O is performed.
 */
fun supports(format: CredentialFormatId): Boolean
```

### 6.2 Recommendations

1. **Document cancellation semantics** for all `suspend` functions
2. **Mark blocking operations** clearly (if any exist)
3. **Provide timeout utilities** for long-running operations:

```kotlin
suspend fun CredentialService.verifyWithTimeout(
    credential: VerifiableCredential,
    timeout: Duration = Duration.ofSeconds(5),
    options: VerificationOptions = VerificationOptions()
): VerificationResult = withTimeout(timeout) {
    verify(credential, options)
}
```

---

## 7. Legacy / Deprecated Code

### 7.1 Identified Issues

#### ❌ Redundant Extensions

**File**: `CredentialServiceExtensions.kt`

```kotlin
// ❌ Remove - redundant with issue()
suspend fun CredentialService.issueCredential(...): IssuanceResult
```

**Action**: Remove and provide DSL builder instead.

#### ❌ Exception-Based DID Extensions

**File**: `CredentialServiceDidExtensions.kt`

```kotlin
// ❌ Refactor - throws exceptions
suspend fun CredentialService.issueForDid(...): IssuanceResult {
    throw IllegalArgumentException(...)
}
```

**Action**: Refactor to return `Result` types.

#### ❌ Multiple Factory Functions

**File**: `CredentialServices.kt`

```kotlin
// ❌ Consolidate - too many factory functions
fun createCredentialService(...)  // 4 overloads
```

**Action**: Consolidate into single `CredentialService` object with clear methods.

#### ❌ Unnecessary Registry/Discovery System

**Files**: `ProofAdapterRegistry.kt`, `ProofRegistries.kt`, `ProofAdapters.kt`

```kotlin
// ❌ Remove from public API - proof formats are built-in
interface ProofAdapterRegistry { ... }
object ProofRegistries { ... }
object ProofAdapters { ... }  // ServiceLoader discovery unnecessary
```

**Action**: Since proof formats are built-in:
- Move all registry/discovery code to `internal` package
- Remove `ProofAdapterRegistry` from public API
- Remove `ProofAdapters.autoRegister()` from public API
- Simplify factory to just create service with built-in adapters

### 7.2 Migration Plan

#### Phase 1: Add New API (Non-Breaking)

1. Add `CredentialService.default()` factory (uses built-in proof formats)
2. Add `Result`-based DID extensions alongside existing ones
3. Add DSL builders for issuance requests
4. Move registry/discovery APIs to `internal` package

#### Phase 2: Deprecate Old API

1. Mark old factory functions as `@Deprecated`
2. Mark exception-throwing extensions as `@Deprecated`
3. Provide migration guides

#### Phase 3: Remove Legacy Code

1. Remove deprecated functions
2. Move registry interfaces to `internal`
3. Consolidate factory functions

---

## 8. Concrete Refactor Suggestions

### 8.1 Factory Simplification

#### Before

```kotlin
// Too many choices, unnecessary complexity
val registry = ProofRegistries.default()
ProofAdapters.autoRegister(registry, emptyMap())
val service = createCredentialService(registry, didResolver)
// ❌ Over-engineered - proof formats are built-in!
```

#### After

```kotlin
// Simple - all proof formats always available
val service = CredentialService.default(didResolver)

// Or with optional customization (schemas, revocation)
val service = CredentialService.default(
    didResolver = didResolver,
    schemaRegistry = mySchemaRegistry,
    statusListManager = myStatusListManager
)
// ✅ All proof formats (VC-LD, VC-JWT, SD-JWT-VC) are built-in
```

### 8.2 Error Handling Consistency

#### Before

```kotlin
// Throws exception - inconsistent
suspend fun CredentialService.issueForDid(
    didResolver: DidResolver,
    subjectDid: Did,
    ...
): IssuanceResult {
    val result = didResolver.resolve(subjectDid)
    if (result !is DidResolutionResult.Success) {
        throw IllegalArgumentException("Subject DID not found")
    }
    // ...
}
```

#### After

```kotlin
// Returns Result - consistent
suspend fun CredentialService.issueForDid(
    didResolver: DidResolver,
    subjectDid: Did,
    issuerDid: Did,
    type: List<CredentialType>,
    claims: Claims,
    format: CredentialFormatId
): Result<VerifiableCredential, IssuanceFailure> {
    val resolutionResult = didResolver.resolve(subjectDid)
    return when (resolutionResult) {
        is DidResolutionResult.Success -> {
            val request = IssuanceRequest(
                format = format,
                issuer = Issuer.fromDid(issuerDid),
                credentialSubject = CredentialSubject.fromDid(subjectDid, claims),
                type = type,
                issuedAt = Instant.now()
            )
            issue(request).fold(
                onFailure = { Result.failure(IssuanceFailure.from(it)) },
                onSuccess = { Result.success(it.credential) }
            )
        }
        is DidResolutionResult.NotFound -> 
            Result.failure(IssuanceFailure.SubjectDidNotFound(subjectDid))
        is DidResolutionResult.Invalid -> 
            Result.failure(IssuanceFailure.SubjectDidInvalid(subjectDid, resolutionResult.reason))
        is DidResolutionResult.Error -> 
            Result.failure(IssuanceFailure.ResolutionError(resolutionResult.error))
    }
}
```

### 8.3 Trust-Aware Verification

#### Before

```kotlin
// No trust policy
val result = service.verify(credential)
when (result) {
    is VerificationResult.Valid -> {
        // ❌ But is the issuer trusted?
    }
}
```

#### After

```kotlin
// Explicit trust policy
val trustPolicy = TrustPolicy.allowlist(
    trustedIssuers = setOf(
        Did("did:web:example.com"),
        Did("did:key:z6Mk...")
    )
)

val result = service.verify(
    credential = credential,
    trustPolicy = trustPolicy
)

when (result) {
    is VerificationResult.Valid -> {
        // ✅ Issuer is explicitly trusted
        processCredential(result.credential)
    }
    is VerificationResult.Invalid.UntrustedIssuer -> {
        // ✅ Clear trust failure
        log.warn("Untrusted issuer: ${result.issuerDid}")
    }
}
```

### 8.4 Reference-Quality API Example

Here's what a **reference-quality** API would look like:

```kotlin
// ============================================
// REFERENCE-QUALITY API DESIGN
// ============================================

/**
 * Credential Service - Main entry point for credential operations.
 * 
 * Provides a simple, type-safe API for issuing and verifying
 * W3C Verifiable Credentials.
 * 
 * All proof formats (VC-LD, VC-JWT, SD-JWT-VC) are built-in and always available.
 */
object CredentialService {
    /**
     * Creates a credential service with all built-in proof formats.
     * 
     * All proof formats are always available - no registration or discovery needed.
     * 
     * @param didResolver Required DID resolver for issuer/subject resolution
     * @param schemaRegistry Optional schema registry for credential validation
     * @param statusListManager Optional status list manager for revocation checking
     */
    fun default(
        didResolver: DidResolver,
        schemaRegistry: SchemaRegistry? = null,
        statusListManager: StatusListManager? = null
    ): CredentialService {
        // All proof adapters are built-in - no registry/discovery needed
        return DefaultCredentialService(
            didResolver = didResolver,
            schemaRegistry = schemaRegistry,
            statusListManager = statusListManager
        )
    }
}

/**
 * Credential service interface.
 */
interface CredentialService {
    /**
     * Issues a verifiable credential.
     * 
     * @param request Issuance request with all credential details
     * @return Result containing either the issued credential or failure details
     */
    suspend fun issue(request: IssuanceRequest): IssuanceResult
    
    /**
     * Verifies a verifiable credential.
     * 
     * @param credential The credential to verify
     * @param trustPolicy Optional trust policy (default: accept all issuers)
     * @param options Verification options
     * @return Verification result with detailed success/failure information
     */
    suspend fun verify(
        credential: VerifiableCredential,
        trustPolicy: TrustPolicy = TrustPolicy.acceptAll(),
        options: VerificationOptions = VerificationOptions()
    ): VerificationResult
    
    /**
     * Verifies multiple credentials in parallel.
     */
    suspend fun verify(
        credentials: List<VerifiableCredential>,
        trustPolicy: TrustPolicy = TrustPolicy.acceptAll(),
        options: VerificationOptions = VerificationOptions()
    ): List<VerificationResult> = coroutineScope {
        credentials.map { async { verify(it, trustPolicy, options) } }.awaitAll()
    }
    
    /**
     * Creates a verifiable presentation from credentials.
     */
    suspend fun createPresentation(
        credentials: List<VerifiableCredential>,
        request: PresentationRequest
    ): VerifiablePresentation
    
    /**
     * Verifies a verifiable presentation.
     */
    suspend fun verifyPresentation(
        presentation: VerifiablePresentation,
        trustPolicy: TrustPolicy = TrustPolicy.acceptAll(),
        options: VerificationOptions = VerificationOptions()
    ): VerificationResult
    
    /**
     * Checks credential status (revocation, expiration).
     */
    suspend fun checkStatus(credential: VerifiableCredential): CredentialStatusInfo
    
    /**
     * Checks if a format is supported.
     */
    fun supports(format: CredentialFormatId): Boolean
    
    /**
     * Gets all supported formats.
     */
    fun supportedFormats(): List<CredentialFormatId>
}

/**
 * Trust policy for issuer validation.
 */
interface TrustPolicy {
    /**
     * Checks if an issuer is trusted.
     */
    suspend fun isTrusted(issuer: Did): Boolean
    
    /**
     * Verifies a trust chain (for delegated credentials).
     */
    suspend fun verifyTrustChain(credential: VerifiableCredential): TrustChainResult
    
    companion object {
        /**
         * Accepts all issuers (no trust validation).
         */
        fun acceptAll(): TrustPolicy = AcceptAllTrustPolicy
        
        /**
         * Only accepts issuers in the allowlist.
         */
        fun allowlist(trustedIssuers: Set<Did>): TrustPolicy = AllowlistTrustPolicy(trustedIssuers)
        
        /**
         * Rejects issuers in the blocklist.
         */
        fun blocklist(blockedIssuers: Set<Did>): TrustPolicy = BlocklistTrustPolicy(blockedIssuers)
    }
}

/**
 * Configuration for credential service.
 */
data class CredentialServiceConfig(
    val schemaRegistry: SchemaRegistry? = null,
    val statusListManager: StatusListManager? = null,
    val adapterOptions: Map<String, Any?> = emptyMap()
)

// ============================================
// USAGE EXAMPLES
// ============================================

// Simple usage - happy path
val service = CredentialService.default(didResolver)

// Issue a credential
val result = service.issue(
    IssuanceRequest(
        format = CredentialFormatId("vc-ld"),
        issuer = Issuer.fromDid(Did("did:key:issuer")),
        credentialSubject = CredentialSubject.fromDid(
            did = Did("did:key:subject"),
            claims = mapOf("name" to JsonPrimitive("Alice"))
        ),
        type = listOf(CredentialType("VerifiableCredential"), CredentialType("Person")),
        issuedAt = Instant.now()
    )
)

when (result) {
    is IssuanceResult.Success -> {
        println("Issued credential: ${result.credential.id}")
    }
    is IssuanceResult.Failure -> {
        println("Failed: ${result.allErrors.joinToString()}")
    }
}

// Verify with trust policy
val trustPolicy = TrustPolicy.allowlist(
    trustedIssuers = setOf(Did("did:web:example.com"))
)

val verification = service.verify(
    credential = credential,
    trustPolicy = trustPolicy
)

when (verification) {
    is VerificationResult.Valid -> {
        println("Credential is valid and issuer is trusted")
    }
    is VerificationResult.Invalid.UntrustedIssuer -> {
        println("Issuer ${verification.issuerDid} is not trusted")
    }
    is VerificationResult.Invalid -> {
        println("Verification failed: ${verification.allErrors.joinToString()}")
    }
}
```

---

## Summary of Key Recommendations

### Immediate Actions (High Priority)

1. ✅ **Simplify factory to single method** - `CredentialService.default()` (proof formats are built-in)
2. ✅ **Remove registry/discovery from public API** - move to `internal` since formats are always available
3. ✅ **Refactor DID extensions** to return `Result` instead of throwing
4. ✅ **Add trust policy support** to verification
5. ✅ **Remove adapter registration complexity** - not needed for built-in formats

### Medium Priority

6. ⚠️ **Remove redundant extensions** (`issueCredential`)
7. ⚠️ **Add DSL builders** for issuance requests
8. ⚠️ **Document cancellation semantics** for all suspend functions
9. ⚠️ **Add algorithm policy** to verification options
10. ⚠️ **Simplify internal implementation** - remove ServiceLoader/SPI since formats are built-in

### Long-Term Improvements

10. 🔄 **Add trust chain validation** support
11. 🔄 **Add explicit key management** API
12. 🔄 **Provide migration guide** for deprecated APIs

---

## Conclusion

The TrustWeave Credential Service API is **well-architected** with strong type safety and W3C alignment, but needs **dramatic simplification** to achieve reference-quality status. Since proof formats are built-in and always available, the main issues are:

1. **Unnecessary adapter registry/discovery** - remove from public API since formats are built-in
2. **Too many factory functions** - consolidate into single `CredentialService.default()` method
3. **Inconsistent error handling** - standardize on `Result` types
4. **Missing trust model** - add explicit trust policies
5. **Over-engineered for built-in formats** - simplify internal implementation

With these changes, the API will be:
- ✅ **Sleek and minimal** - single happy path
- ✅ **Intuitive** - clear factory methods
- ✅ **Trustworthy** - explicit trust policies
- ✅ **Idiomatic Kotlin** - consistent Result types
- ✅ **Secure** - algorithm policies and key management

The foundation is solid; these refinements will elevate it to reference-quality status.

