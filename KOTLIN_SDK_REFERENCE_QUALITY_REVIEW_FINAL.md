# TrustWeave Kotlin SDK: Reference-Quality API Review

**Review Date:** 2024-12  
**Reviewer:** Kotlin Software Architect, API Designer & Security Engineer  
**Goal:** Transform into industry gold standard for Kotlin identity, trust, and secure data libraries

---

## Executive Summary

The TrustWeave SDK demonstrates **strong architectural foundations** with excellent progress on type safety and error handling. The codebase shows **significant improvements** from previous reviews, with sealed result types and type-safe identifiers already implemented. However, several **critical refinements** are needed to achieve reference-quality status.

**Key Findings:**
- ✅ **Strengths:** Sealed result types implemented, type-safe identifiers exist, clean modular architecture
- ⚠️ **Critical Issues:** API surface bloat, inconsistent patterns, configuration complexity, missing trust model prominence
- 🎯 **Recommendation:** Focused refactoring in 4 key areas to achieve reference-quality status

**Overall Assessment:** **Production-ready** foundation requiring **architectural refinement** for reference-quality API. The SDK is approximately **75% of the way** to reference-quality status.

---

## 1. High-Level Evaluation

### Strengths ✅

**Architectural Excellence:**
- ✅ Clean module separation: `core`, `did`, `credentials`, `kms`, `trust`, `wallet`, `anchors`
- ✅ Plugin-based extensibility with well-designed registry patterns
- ✅ Context-based dependency injection (`TrustWeaveContext`) eliminates global state
- ✅ Composable capability interfaces (e.g., `Wallet` as composition of capabilities)

**Type Safety & Error Handling:**
- ✅ **Sealed result types implemented**: `VerificationResult`, `DidResolutionResult` (excellent!)
- ✅ **Type-safe identifiers**: `Did`, `KeyId`, `CredentialId` as inline value classes
- ✅ Validation at construction time for identifiers
- ✅ Exhaustive error handling with detailed error cases

**Modern Kotlin Usage:**
- ✅ Coroutines throughout (`suspend` functions)
- ✅ DSL builders for complex configurations
- ✅ Extension functions for ergonomics
- ✅ Inline value classes for type-safe identifiers
- ✅ Sealed classes for result types

**Security-Conscious Design:**
- ✅ Key material handled through KMS abstraction (never exposed)
- ✅ Proof generation/verification clearly separated
- ✅ DID resolution with validation and error handling
- ✅ Type-safe cryptographic operations

### Weaknesses ❌

**Critical: API Surface Bloat**
- Multiple ways to perform same operation (overloads, builders, direct methods)
- Service layer indirection (`DidService`, `CredentialService`) without clear benefit
- Too many builder classes (37+ builder classes found)
- Configuration DSL overly complex for common cases
- Redundant convenience methods

**Inconsistent Patterns:**
- Mixed exception/throwing patterns vs sealed result types
- Some APIs accept `String` where type-safe identifiers should be used
- Inconsistent use of sealed results (some operations still throw exceptions)
- No unified error model across all operations

**Naming & Domain Clarity:**
- Generic names obscure Web-of-Trust semantics (`Service`, `Manager`, `Registry`)
- Missing prominent trust/identity terminology in public API
- Some names hide cryptographic intent
- Trust concepts exist but not prominently exposed

**Configuration Complexity:**
- Configuration requires deep understanding of internal abstractions
- No simple factory for common cases
- Reflection-based factory resolution (unreliable, slow)
- Too many nested blocks for simple scenarios

**Developer Experience Issues:**
- Happy path is clear, but error handling requires extensive try-catch
- No clear "getting started" minimal API surface
- Documentation references multiple patterns (confusing)
- Some operations require understanding internal architecture

---

## 2. Public API & Developer Experience

### Current State Analysis

**Entry Point (Good!):**
```kotlin
// Single entry point - excellent!
val trustWeave = TrustWeave.build { ... }
```

**Issue: Redundant Overloads**

Multiple ways to create a DID:
```kotlin
// Current: 3+ ways to do the same thing
trustWeave.createDid()                                    // Simple
trustWeave.createDid(method = "key")                      // With method
trustWeave.createDid { method("key") }                    // With builder
```

**Recommendation:** Single method with optional builder:
```kotlin
suspend fun createDid(
    method: String = "key",
    configure: (DidCreationOptionsBuilder.() -> Unit)? = null
): Did {
    val options = configure?.let { didCreationOptions(it) } ?: DidCreationOptions()
    // ...
}
```

**Issue: Service Layer Indirection**

```kotlin
// Current: Inconsistent patterns
trustWeave.createDid()              // Direct method
trustWeave.issue { ... }            // Direct method
trustWeave.blockchains.anchor(...)  // Service property
trustWeave.contracts.draft(...)     // Service property
```

**Problem:** Inconsistent patterns. Some operations are direct methods, others are through service properties. Why the difference?

**Recommendation:** Clear principle:
- **Common operations** → Direct methods on facade
- **Complex operations with many methods** → Service property (only if truly complex)

**Issue: Configuration Complexity**

Current configuration requires deep understanding:
```kotlin
val trustWeave = TrustWeave.build {
    keys {
        provider("inMemory")
        algorithm("Ed25519")
    }
    did {
        method("key") {
            algorithm("Ed25519")
        }
    }
    // ... many more nested blocks
}
```

**Problems:**
- Too many nested blocks for simple cases
- Defaults not sufficient for common scenarios
- Reflection-based factory resolution (unreliable, slow)

**Recommendation:** Provide a simple factory for common cases:
```kotlin
// Simple, opinionated defaults
suspend fun TrustWeave.Companion.inMemory(): TrustWeave = build {
    keys { provider("inMemory"); algorithm("Ed25519") }
    did { method("key") { algorithm("Ed25519") } }
    trust { provider("inMemory") }
}

// Usage
val trustWeave = TrustWeave.inMemory()
```

**Issue: Error Handling Forces Try-Catch Everywhere**

```kotlin
// Current: Must wrap everything in try-catch
try {
    val did = trustWeave.createDid()
    val credential = trustWeave.issue { ... }
} catch (e: DidException) { ... }
catch (e: CredentialException) { ... }
catch (e: Exception) { ... }
```

**Problem:** Exceptions for expected failures (e.g., DID resolution not found) force defensive programming everywhere.

**Current State (Good!):**
```kotlin
// Resolution returns sealed result (already does this - excellent!)
when (val result = trustWeave.resolveDid(did)) {
    is DidResolutionResult.Success -> { ... }
    is DidResolutionResult.Failure.NotFound -> { ... }
}
```

**Recommendation:** Extend sealed result pattern to more operations:
- ✅ `resolveDid()` → Already sealed result (keep!)
- ✅ `verifyCredential()` → Already sealed result (keep!)
- ⚠️ `createDid()` → Throws exception (OK for programming errors)
- ⚠️ `issueCredential()` → Throws exception (OK for programming errors)

**The hybrid approach is actually good!** Keep exceptions for programming errors, sealed results for expected failures.

### Recommended Public API Structure

**Single Entry Point with Clear Patterns:**

```kotlin
// ============================================
// SINGLE ENTRY POINT: com.trustweave.trust.TrustWeave
// ============================================

object TrustWeave {
    // Simple factory for common cases
    suspend fun inMemory(): TrustWeave = ...
    
    // Full configuration (for advanced cases)
    suspend fun build(configure: TrustWeaveConfig.Builder.() -> Unit): TrustWeave
}

class TrustWeave private constructor(...) {
    // ============================================
    // DID Operations (Direct Methods)
    // ============================================
    
    suspend fun createDid(
        method: String = "key",
        configure: (DidCreationOptionsBuilder.() -> Unit)? = null
    ): Did  // Type-safe identifier
    
    suspend fun resolveDid(
        did: Did,
        timeout: Duration = 30.seconds
    ): DidResolutionResult  // Sealed result (already good!)
    
    suspend fun updateDid(configure: DidDocumentBuilder.() -> Unit): DidDocument
    
    // ============================================
    // Credential Operations (Direct Methods)
    // ============================================
    
    suspend fun issue(configure: IssuanceBuilder.() -> Unit): VerifiableCredential
    
    suspend fun verify(
        credential: VerifiableCredential,
        configure: VerificationBuilder.() -> Unit = {}
    ): VerificationResult  // SEALED CLASS (already good!)
    
    suspend fun present(
        credentials: List<VerifiableCredential>,
        configure: PresentationBuilder.() -> Unit
    ): VerifiablePresentation
    
    // ============================================
    // Complex Services (Properties)
    // ============================================
    
    val blockchains: BlockchainService  // Only because it has 10+ methods
    val trust: TrustService              // Only because it has multiple operations
}
```

**Key Principles:**
1. **One way to do common operations** (fewer overloads)
2. **Sealed results for expected failures** (resolution, verification) ✅ Already implemented!
3. **Exceptions for programming errors** (creation, configuration) ✅ Already implemented!
4. **Clear separation**: Direct methods vs service properties

---

## 3. Naming, Domain Modeling & Web-of-Trust Semantics

### Current Naming Issues

**Generic/Implementation-Oriented Names:**

```kotlin
// Current: Generic names
class DidService                    // → What service? Creation? Resolution?
class CredentialService            // → Too generic
class WalletFactory                // → Factory pattern, but what does it create?
class ProviderChain                // → Implementation detail exposed
class PluginRegistry               // → Generic registry pattern
```

**Recommendation: Domain-Precise Names:**

```kotlin
// Better: Domain-precise names
// (But keep existing names if changing breaks too much - use aliases)

// Service names → More specific
typealias DidCreator = DidService           // If we keep service
typealias CredentialIssuer = CredentialService  // More specific

// Or: Remove service layer entirely, use direct methods
trustWeave.createDid()              // Clear intent
trustWeave.issueCredential()        // Clear intent
```

**Missing Web-of-Trust Terminology:**

The SDK has trust concepts but they're not prominent in the API:

```kotlin
// Current: Trust concepts exist but not prominent
trustRegistry.addAnchor(...)        // Hidden in registry
trustPolicy.isTrusted(...)          // Policy is separate concept

// Recommendation: Make trust first-class
trustWeave.trust {
    anchor("did:key:university") {  // Already exists - good!
        credentialTypes("EducationCredential")
    }
    
    val path = findPath(verifierDid, issuerDid)  // Trust path discovery
    val isTrusted = isTrusted(issuerDid, type)   // Trust check
}
```

**Good Examples of Domain-Precise Naming:**

```kotlin
// ✅ Good: Type-safe identifiers
value class Did(val value: String)
value class KeyId(val value: String)
value class CredentialId(val value: String)

// ✅ Good: Trust domain types
data class IssuerIdentity(val did: Did, val keyId: KeyId)
data class VerifierIdentity(val did: Did)
data class HolderIdentity(val did: Did)

// ✅ Good: Sealed result types
sealed class DidResolutionResult
sealed class VerificationResult
```

**Recommendations for Naming Improvements:**

1. **Keep type-safe identifiers** (already good: `Did`, `KeyId`, `CredentialId`)

2. **Rename generic services** (if breaking change acceptable):
   ```kotlin
   // Old → New (add typealias for migration)
   typealias DidResolver = DidService
   typealias CredentialIssuer = CredentialService
   ```

3. **Make trust terminology prominent:**
   ```kotlin
   // Already exists in DSL - make it more visible
   trustWeave.trust { ... }  // Good!
   
   // Consider adding to top-level API
   val trustAnchor: TrustAnchor = trustWeave.getTrustAnchor(did)
   val trustPath: TrustPath = trustWeave.findTrustPath(verifier, issuer)
   ```

4. **Eliminate "Manager" and "Helper" suffixes:**
   ```kotlin
   // Current:
   StatusListManager
   
   // Better:
   StatusListRegistry  // Or just: StatusList (if it's the primary concept)
   ```

### Domain Model Clarity

**Current Domain Model:**

The SDK correctly models:
- ✅ DIDs (`Did`, `DidDocument`)
- ✅ Verifiable Credentials (`VerifiableCredential`, `VerifiablePresentation`)
- ✅ Proofs (`Proof`, proof types)
- ✅ Trust (`TrustRegistry`, `TrustAnchor`)

**Missing or Unclear:**

- ⚠️ **Trust paths** exist but not as first-class type
- ⚠️ **Delegation chains** exist but not prominently
- ⚠️ **Issuer/Verifier/Holder** concepts exist but could be more prominent

**Recommendation:**

```kotlin
// Add first-class trust path type
data class TrustPath(
    val from: VerifierIdentity,
    val to: IssuerIdentity,
    val anchors: List<TrustAnchor>,
    val verified: Boolean
)

// Make delegation explicit
sealed class DelegationChain {
    data class Valid(val chain: List<DidDocument>) : DelegationChain()
    data class Invalid(val reason: String) : DelegationChain()
}

// Result type for delegation
suspend fun verifyDelegation(
    from: Did,
    to: Did
): DelegationChain
```

---

## 4. Idiomatic Kotlin & Architecture

### Strengths ✅

**Good Kotlin Usage:**

```kotlin
// ✅ Inline value classes for type safety
@JvmInline value class Did(val value: String)

// ✅ Sealed classes for result types
sealed class DidResolutionResult

// ✅ DSL builders for complex operations
trustWeave.issue {
    credential { ... }
}

// ✅ Extension functions for ergonomics
fun Did.method(): String = value.substringAfter("did:").substringBefore(":")
```

**Good Architecture Patterns:**

```kotlin
// ✅ Context-based dependency injection
class TrustWeaveContext(val config: TrustWeaveConfig)

// ✅ Capability-based interfaces
interface Wallet : CredentialStorage {
    fun <T : Any> supports(capability: KClass<T>): Boolean
}

// ✅ Plugin system with SPI
interface DidMethodProvider {
    fun create(config: DidMethodConfig): DidMethod
}
```

### Issues ❌

**1. Overuse of DSL Builders**

Found **37+ builder classes**. Many are unnecessary:

```kotlin
// Current: Builder for everything
class WalletBuilder { ... }
class CredentialBuilder { ... }
class SubjectBuilder { ... }
class JsonObjectBuilder { ... }
class VerificationMethodBuilder { ... }
class ServiceBuilder { ... }
// ... 30+ more
```

**Problem:** Builders should be used sparingly. Simple cases should have direct APIs.

**Recommendation:** Use builders only for complex configurations:

```kotlin
// Simple case: Direct API
val did = trustWeave.createDid(method = "key")

// Complex case: Builder
val did = trustWeave.createDid {
    method("key")
    algorithm("Ed25519")
    purpose(KeyPurpose.AUTHENTICATION)
    purpose(KeyPurpose.ASSERTION)
    service { ... }
}
```

**2. Inconsistent Null Safety**

```kotlin
// Current: Mixed nullable/non-nullable patterns
fun resolveDid(did: String): DidResolutionResult  // Good: sealed result

fun getCredential(id: String): VerifiableCredential?  // Nullable - should be sealed result

// Recommendation: Use sealed results for expected failures
sealed class CredentialResult {
    data class Found(val credential: VerifiableCredential) : CredentialResult()
    data class NotFound(val id: String) : CredentialResult()
}

fun getCredential(id: CredentialId): CredentialResult
```

**3. Reflection-Based Factory Resolution**

```kotlin
// Current: Reflection in configuration (TrustWeaveConfig.kt)
private fun getDefaultKmsFactory(): KmsFactory {
    try {
        val factoryClass = Class.forName("com.trustweave.testkit.services.TestkitKmsFactory")
        val factory = factoryClass.getDeclaredConstructor().newInstance() as? KmsFactory
        if (factory != null) return factory
    } catch (e: Exception) {
        // Fall through
    }
    throw IllegalStateException(...)
}
```

**Problem:** Reflection is slow, unreliable, and breaks with ProGuard/R8.

**Recommendation:** Use dependency injection or explicit factory parameters:

```kotlin
// Option 1: Explicit factories
val trustWeave = TrustWeave.build {
    kmsFactory = TestkitKmsFactory()
    didMethodFactory = TestkitDidMethodFactory()
}

// Option 2: Service loader (SPI)
val kmsFactory = ServiceLoader.load(KmsFactory::class.java).firstOrNull()
```

**4. KMS Abstraction Uses `Any` Type**

```kotlin
// Current: Using Any to avoid dependencies
class TrustWeaveConfig(
    val kms: Any,  // ❌ Should be KeyManagementService
    // ...
)
```

**Problem:** Weakens type safety, forces unsafe casts.

**Recommendation:** Make KMS a proper interface in core:

```kotlin
// In kms-core module (minimal interface)
interface KeyManagementService {
    suspend fun sign(keyId: KeyId, data: ByteArray): ByteArray
    suspend fun getPublicKey(keyId: KeyId): PublicKey
}

// TrustWeave depends on kms-core (minimal dependency)
class TrustWeaveConfig(
    val kms: KeyManagementService,  // ✅ Type-safe
    // ...
)
```

**5. Mixed Error Handling Patterns**

```kotlin
// Current: Inconsistent patterns
suspend fun createDid(): DidDocument  // Throws exception

suspend fun resolveDid(): DidResolutionResult  // Returns sealed result (good!)

suspend fun verifyCredential(): VerificationResult  // Returns sealed result (good!)
```

**Status:** Actually, the current hybrid approach is **good**! Keep:
- **Programming errors** (misconfiguration, invalid input) → Exceptions
- **Expected failures** (not found, expired, invalid) → Sealed result types

### Recommended Kotlin Patterns

**1. Use Sealed Classes for Result Types** ✅ Already implemented!

```kotlin
// ✅ Good: Exhaustive error handling
sealed class VerificationResult {
    data class Valid(val credential: VerifiableCredential) : VerificationResult()
    sealed class Invalid : VerificationResult() {
        data class Expired(val expiredAt: Instant) : Invalid()
        data class Revoked(val revokedAt: Instant?) : Invalid()
        data class InvalidProof(val reason: String) : Invalid()
        data class UntrustedIssuer(val issuer: Did) : Invalid()
    }
}
```

**2. Use Inline Value Classes for Type Safety** ✅ Already implemented!

```kotlin
// ✅ Already good: Type-safe identifiers
@JvmInline value class Did(val value: String)
@JvmInline value class KeyId(val value: String)
@JvmInline value class CredentialId(val value: String)
```

**3. Use Extension Functions for Ergonomics** ✅ Already implemented!

```kotlin
// ✅ Good: Convenient extensions
fun Did.method(): String = value.substringAfter("did:").substringBefore(":")

fun IssuerIdentity.verificationMethodId(): String = 
    "${did.value}#${keyId.value}"
```

**4. Minimize Builders** ⚠️ Needs improvement

```kotlin
// ❌ Avoid: Builder for simple cases
class SimpleBuilder {
    var value: String? = null
    fun build(): String = value ?: throw IllegalStateException()
}

// ✅ Prefer: Direct API or data class
data class SimpleConfig(val value: String)

// ✅ Use builder only for complex configurations
class ComplexBuilder {
    // Many optional parameters
    fun build(): ComplexConfig { ... }
}
```

---

## 5. Trust Model & Crypto/API Safety Review

### Cryptographic Safety ✅

**Strengths:**
- ✅ Key material never exposed (KMS abstraction)
- ✅ Proof generation uses proper canonicalization (JCS)
- ✅ Signature algorithms clearly defined (`Ed25519Signature2020`, etc.)
- ✅ Key rotation supported
- ✅ Proper separation of concerns (signing vs verification)

### Trust Model Safety ⚠️

**Current Trust Model:**

```kotlin
// Trust registry exists but could be more prominent
trustWeave.trust {
    addAnchor("did:key:university") {
        credentialTypes("EducationCredential")
    }
    
    val isTrusted = isTrusted("did:key:university", "EducationCredential")
}
```

**Issues:**

1. **Trust paths not first-class**
   - Path discovery exists but result type is unclear
   - No validation of trust path integrity

2. **Delegation chains not prominently exposed**
   - Delegation exists but API is buried in DSL
   - No clear verification of delegation chains

3. **Missing trust policy types**
   - Trust policies exist but not as clear types
   - No way to define custom trust policies

**Recommendations:**

```kotlin
// 1. Make trust paths first-class
data class TrustPath(
    val from: VerifierIdentity,
    val to: IssuerIdentity,
    val anchors: List<TrustAnchor>,
    val verified: Boolean,
    val verifiedAt: Instant
)

// 2. Explicit trust verification
sealed class TrustVerification {
    data class Trusted(
        val path: TrustPath,
        val policy: TrustPolicy
    ) : TrustVerification()
    
    data class Untrusted(
        val reason: String,
        val path: TrustPath?
    ) : TrustVerification()
}

suspend fun verifyTrust(
    verifier: VerifierIdentity,
    issuer: IssuerIdentity,
    credentialType: String
): TrustVerification

// 3. Trust policies as types
sealed class TrustPolicy {
    object AnyIssuer : TrustPolicy()
    data class TrustedAnchorsOnly(val anchors: Set<Did>) : TrustPolicy()
    data class WithDelegation(val maxDepth: Int) : TrustPolicy()
    data class Custom(val check: (Did, String) -> Boolean) : TrustPolicy()
}
```

### API Safety Issues ❌

**1. Type Safety Gaps**

```kotlin
// Current: Some String-based identifiers
fun issueCredential(
    issuer: String,        // ❌ Should be Did
    keyId: String,         // ❌ Should be KeyId
    subject: Map<String, Any>  // ❌ Should be type-safe
)

// Recommendation: Type-safe parameters
fun issueCredential(
    issuer: Did,
    keyId: KeyId,
    subject: CredentialSubject
): VerifiableCredential
```

**2. Unsafe KMS Configuration**

```kotlin
// Current: Using Any type
class TrustWeaveConfig(val kms: Any)  // ❌ Type-unsafe

// Recommendation: Proper interface
interface KeyManagementService {
    suspend fun sign(keyId: KeyId, data: ByteArray): ByteArray
    suspend fun getPublicKey(keyId: KeyId): PublicKey
}

class TrustWeaveConfig(val kms: KeyManagementService)  // ✅ Type-safe
```

**3. Missing Algorithm Validation**

```kotlin
// Current: Algorithm passed as string
fun sign(keyId: KeyId, data: ByteArray, algorithm: String? = null)

// Recommendation: Type-safe algorithm
sealed class SignatureAlgorithm {
    object Ed25519 : SignatureAlgorithm()
    object ES256K : SignatureAlgorithm()
    object BBS : SignatureAlgorithm()
}

fun sign(
    keyId: KeyId,
    data: ByteArray,
    algorithm: SignatureAlgorithm? = null  // ✅ Type-safe
): ByteArray
```

### Recommended Safety Improvements

**1. Type-Safe Identifiers Everywhere** ✅ Mostly done!

```kotlin
// Replace all String identifiers with type-safe versions
suspend fun resolveDid(did: Did): DidResolutionResult  // ✅ Already uses Did

suspend fun issueCredential(
    issuer: Did,              // ✅ Not String
    keyId: KeyId,             // ✅ Not String
    subject: CredentialSubject  // ✅ Type-safe subject
): VerifiableCredential
```

**2. Algorithm Type Safety**

```kotlin
// Define algorithms as sealed class
sealed class SignatureAlgorithm(val name: String) {
    object Ed25519 : SignatureAlgorithm("Ed25519")
    object ES256K : SignatureAlgorithm("ES256K")
    object BBS : SignatureAlgorithm("BBS")
    
    companion object {
        fun fromString(name: String): SignatureAlgorithm? = when (name) {
            "Ed25519" -> Ed25519
            "ES256K" -> ES256K
            "BBS" -> BBS
            else -> null
        }
    }
}

// Use in APIs
suspend fun sign(
    keyId: KeyId,
    data: ByteArray,
    algorithm: SignatureAlgorithm? = null
): ByteArray
```

**3. Validation at Type Boundaries** ✅ Already implemented!

```kotlin
// Validate at construction
@JvmInline value class Did(val value: String) {
    init {
        require(value.startsWith("did:")) {
            "Invalid DID format: '$value'. DIDs must start with 'did:'"
        }
        require(value.split(":").size >= 3) {
            "Invalid DID format: '$value'. Expected format: did:method:identifier"
        }
    }
}

// ✅ Already does this - excellent!
```

---

## 6. Concurrency & Coroutine Design

### Current State ✅

**Strengths:**
- ✅ All public APIs are `suspend` functions
- ✅ Proper use of `withContext(Dispatchers.IO)` for I/O operations
- ✅ No `GlobalScope` usage observed
- ✅ Coroutines used throughout

### Issues ⚠️

**1. Dispatcher Selection Not Configurable**

```kotlin
// Current: Hardcoded dispatchers
suspend fun execute(): List<VerifiableCredential> = withContext(Dispatchers.IO) {
    // ...
}
```

**Problem:** Not configurable for testing or custom dispatchers.

**Recommendation:** Make dispatchers configurable:

```kotlin
class TrustWeaveConfig(
    val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
)

// Use in operations
suspend fun execute(): List<VerifiableCredential> = 
    withContext(config.ioDispatcher) {
        // ...
    }
```

**2. Missing Timeout Support** ⚠️ Partially implemented

```kotlin
// Current: Some operations have timeout, others don't
suspend fun resolveDid(did: Did, timeout: Duration = 30.seconds): DidResolutionResult  // ✅ Has timeout

suspend fun verifyCredential(credential: VerifiableCredential): VerificationResult  // ❌ No timeout
```

**Recommendation:** Add timeout parameter to all I/O operations:

```kotlin
suspend fun resolveDid(
    did: Did,
    timeout: Duration = 30.seconds
): DidResolutionResult = withTimeout(timeout) {
    // Resolution logic
}

suspend fun verifyCredential(
    credential: VerifiableCredential,
    timeout: Duration = 10.seconds
): VerificationResult = withTimeout(timeout) {
    // Verification logic
}
```

**3. No Structured Concurrency Guarantees**

**Current:** No explicit `CoroutineScope` management.

**Recommendation:** Add scope for lifecycle management:

```kotlin
class TrustWeave(
    private val config: TrustWeaveConfig,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    suspend fun issue(configure: IssuanceBuilder.() -> Unit): VerifiableCredential {
        // Operations run in scope, cancellation propagates
        return scope.async {
            // Issue logic
        }.await()
    }
    
    fun close() {
        scope.cancel()
    }
}
```

**4. Blocking Operations Not Clearly Marked**

```kotlin
// Current: Cryptographic operations may be blocking
suspend fun sign(keyId: KeyId, data: ByteArray): ByteArray

// Recommendation: Mark CPU-bound operations
suspend fun sign(keyId: KeyId, data: ByteArray): ByteArray = 
    withContext(Dispatchers.Default) {  // CPU-bound
        // Signing logic
    }

// Or: Separate blocking API
fun signBlocking(keyId: KeyId, data: ByteArray): ByteArray
```

### Recommended Concurrency Patterns

**1. Timeout Support for All I/O Operations**

```kotlin
suspend fun resolveDid(
    did: Did,
    timeout: Duration = 30.seconds
): DidResolutionResult = withTimeout(timeout) {
    // Resolution logic
}

suspend fun verifyCredential(
    credential: VerifiableCredential,
    timeout: Duration = 10.seconds
): VerificationResult = withTimeout(timeout) {
    // Verification logic
}
```

**2. Configurable Dispatchers**

```kotlin
data class ConcurrencyConfig(
    val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    val cpuDispatcher: CoroutineDispatcher = Dispatchers.Default,
    val defaultTimeout: Duration = 30.seconds
)

class TrustWeaveConfig(
    val concurrency: ConcurrencyConfig = ConcurrencyConfig()
)
```

**3. Structured Concurrency**

```kotlin
class TrustWeave(
    private val config: TrustWeaveConfig,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    // All operations use scope
    suspend fun issue(...): VerifiableCredential = coroutineScope {
        // Issue logic
    }
    
    // Lifecycle management
    suspend fun close() {
        scope.cancel()
    }
}
```

---

## 7. Legacy / Deprecated Code

### Deprecated APIs

**1. Duplicate Entry Points** ✅ Already resolved!

- ✅ Single `TrustWeave` class in `trust` module
- ✅ No duplicate entry points found

**2. Service Layer Properties**

**Current:**
```kotlin
trustWeave.dids.create()      // Service layer
trustWeave.credentials.issue() // Service layer
```

**Status:** Already marked internal in some places, but still accessible.

**Recommendation:**
- Make all service classes `internal`
- Keep only complex services as properties (`blockchains`, `trust`)
- Move common operations to direct methods

**3. Reflection-Based Factory Resolution**

**Location:** `TrustWeaveConfig.Builder.getDefaultKmsFactory()`

**Problem:** Uses reflection, breaks with ProGuard/R8, slow.

**Recommendation:**
- Remove reflection-based resolution
- Require explicit factory parameters
- Use ServiceLoader (SPI) for optional discovery

**4. Mixed Error Handling Patterns**

**Current:** Some APIs throw exceptions, others return results.

**Status:** Actually, the hybrid approach is **good**! Keep:
- Exceptions for programming errors
- Sealed results for expected failures

### Migration Plan

**Phase 1: Deprecation (Non-Breaking)**
1. Add `@Deprecated` annotations to old APIs
2. Add migration guides
3. Provide typealiases for compatibility

**Phase 2: Internalization (Non-Breaking)**
1. Make service classes `internal`
2. Move common operations to facade methods
3. Keep only complex services as properties

**Phase 3: Standardization (Potentially Breaking)**
1. Standardize error handling patterns (already mostly done!)
2. Replace reflection with explicit dependencies
3. Remove deprecated APIs

---

## 8. Concrete Refactor Suggestions

### Before vs After: Key Scenarios

#### Scenario 1: Creating and Resolving a DID

**Before:**
```kotlin
// Multiple ways to do the same thing
val did1 = trustWeave.createDid()                                    // Option 1
val did2 = trustWeave.createDid(method = "key")                      // Option 2
val did3 = trustWeave.createDid { method("key") }                    // Option 3

// Resolution throws exception or returns result? Unclear.
try {
    val doc = trustWeave.resolveDid(did1)  // Returns DidResolutionResult - good
} catch (e: Exception) {
    // But sometimes throws? Inconsistent
}
```

**After:**
```kotlin
// Single clear way
val did = trustWeave.createDid(method = "key")  // Simple case
val did = trustWeave.createDid {                // Complex case
    method("key")
    algorithm("Ed25519")
    purpose(KeyPurpose.AUTHENTICATION)
}

// Consistent sealed result pattern
when (val result = trustWeave.resolveDid(did, timeout = 30.seconds)) {
    is DidResolutionResult.Success -> {
        val doc = result.document
        // Use document
    }
    is DidResolutionResult.Failure.NotFound -> {
        println("DID not found: ${result.did}")
    }
    is DidResolutionResult.Failure.InvalidFormat -> {
        println("Invalid format: ${result.reason}")
    }
    // Compiler ensures exhaustive handling
}
```

#### Scenario 2: Issuing a Credential

**Before:**
```kotlin
// Too many parameters, issuerDid repeated
val credential = trustWeave.issueCredential(
    issuer = issuerDid,
    keyId = keyId,
    subject = mapOf(
        "id" to "did:key:subject",
        "name" to "Alice"
    ),
    credentialType = "PersonCredential",
    expirationDate = "2025-12-31T23:59:59Z"
)

// Or: Complex builder
val credential = trustWeave.issue {
    credential {
        type("PersonCredential")
        issuer(issuerDid)
        subject {
            id("did:key:subject")
            claim("name", "Alice")
        }
        expires(Instant.parse("2025-12-31T23:59:59Z"))
    }
    by(issuerDid = issuerDid, keyId = keyId)
}
```

**After:**
```kotlin
// Simple case: Type-safe, minimal parameters
val credential = trustWeave.issue(
    issuer = IssuerIdentity.from(issuerDid, keyId),
    subject = CredentialSubject(
        id = Did("did:key:subject"),
        claims = mapOf("name" to "Alice")
    ),
    type = "PersonCredential",
    expires = Instant.parse("2025-12-31T23:59:59Z")
)

// Complex case: Builder for advanced options
val credential = trustWeave.issue {
    credential {
        type("PersonCredential")
        issuer(issuerDid)
        subject {
            id("did:key:subject")
            claim("name", "Alice")
        }
        expires(Instant.parse("2025-12-31T23:59:59Z"))
    }
    signedBy(IssuerIdentity.from(issuerDid, keyId))
}
```

#### Scenario 3: Verifying a Credential

**Before:**
```kotlin
// Returns data class, not sealed - can't do exhaustive handling
val result = trustWeave.verifyCredential(credential)
if (result.valid) {
    // Success
} else {
    // But what went wrong? Need to check multiple properties
    if (result.expired) { ... }
    if (result.revoked) { ... }
    if (!result.proofValid) { ... }
    // Easy to miss cases
}
```

**After:**
```kotlin
// Sealed result type - exhaustive error handling ✅ Already implemented!
when (val result = trustWeave.verifyCredential(credential)) {
    is VerificationResult.Valid -> {
        println("Credential is valid: ${result.credential.id}")
        // Use credential safely
    }
    is VerificationResult.Invalid.Expired -> {
        println("Credential expired at ${result.expiredAt}")
    }
    is VerificationResult.Invalid.Revoked -> {
        println("Credential revoked at ${result.revokedAt}")
    }
    is VerificationResult.Invalid.InvalidProof -> {
        println("Proof invalid: ${result.reason}")
    }
    is VerificationResult.Invalid.UntrustedIssuer -> {
        println("Issuer ${result.issuer} is not trusted")
    }
    // Compiler ensures all cases handled
}
```

#### Scenario 4: Trust Management

**Before:**
```kotlin
// Trust concepts exist but not prominent
trustWeave.trust {
    addAnchor("did:key:university") {
        credentialTypes("EducationCredential")
    }
    
    val isTrusted = isTrusted("did:key:university", "EducationCredential")
    // But trust path discovery is unclear
}
```

**After:**
```kotlin
// Trust is first-class with clear types
trustWeave.trust {
    // Add trust anchor
    addAnchor("did:key:university") {
        credentialTypes("EducationCredential")
        description("Trusted university")
    }
    
    // Check trust
    when (val verification = verifyTrust(
        verifier = VerifierIdentity(Did("did:key:employer")),
        issuer = IssuerIdentity.from("did:key:university", "key-1"),
        credentialType = "EducationCredential"
    )) {
        is TrustVerification.Trusted -> {
            val path = verification.path
            println("Trusted via path: ${path.anchors.map { it.did }}")
        }
        is TrustVerification.Untrusted -> {
            println("Not trusted: ${verification.reason}")
        }
    }
}
```

#### Scenario 5: Configuration

**Before:**
```kotlin
// Complex configuration with many nested blocks
val trustWeave = TrustWeave.build {
    keys {
        provider("inMemory")
        algorithm("Ed25519")
    }
    did {
        method("key") {
            algorithm("Ed25519")
        }
    }
    anchor {
        chain("algorand:testnet") {
            provider("algorand")
        }
    }
    trust {
        provider("inMemory")
    }
}
```

**After:**
```kotlin
// Simple factory for common cases
val trustWeave = TrustWeave.inMemory()  // Everything in-memory for testing

// Or: Progressive enhancement
val trustWeave = TrustWeave.inMemory()
    .withDidMethod("key")
    .withKms("aws", kmsConfig)
    .withTrustRegistry("inMemory")
    .build()

// Or: Full configuration for advanced cases
val trustWeave = TrustWeave.build {
    keys {
        provider("aws")
        algorithm("Ed25519")
    }
    did {
        method("key") {
            algorithm("Ed25519")
        }
    }
    // ... only configure what you need
}
```

### Ideal Reference-Quality API Example

Here's what a reference-quality API would look like:

```kotlin
// ============================================
// SINGLE ENTRY POINT
// ============================================

object TrustWeave {
    // Simple factory
    suspend fun inMemory(): TrustWeave = ...
    
    // Full configuration
    suspend fun build(configure: TrustWeaveConfig.Builder.() -> Unit): TrustWeave
}

class TrustWeave private constructor(
    private val config: TrustWeaveConfig,
    private val scope: CoroutineScope
) {
    // ============================================
    // DID Operations (Type-safe, sealed results)
    // ============================================
    
    suspend fun createDid(
        method: String = "key",
        configure: (DidCreationOptionsBuilder.() -> Unit)? = null
    ): Did  // Type-safe identifier
    
    suspend fun resolveDid(
        did: Did,
        timeout: Duration = 30.seconds
    ): DidResolutionResult  // Sealed result ✅ Already implemented!
    
    suspend fun updateDid(configure: DidDocumentBuilder.() -> Unit): DidDocument
    
    // ============================================
    // Credential Operations (Type-safe, sealed results)
    // ============================================
    
    suspend fun issue(configure: IssuanceBuilder.() -> Unit): VerifiableCredential
    
    suspend fun verify(
        credential: VerifiableCredential,
        configure: VerificationBuilder.() -> Unit = {}
    ): VerificationResult  // Sealed result ✅ Already implemented!
    
    suspend fun present(
        credentials: List<VerifiableCredential>,
        configure: PresentationBuilder.() -> Unit
    ): VerifiablePresentation
    
    // ============================================
    // Trust Operations (First-class trust model)
    // ============================================
    
    suspend fun verifyTrust(
        verifier: VerifierIdentity,
        issuer: IssuerIdentity,
        credentialType: String
    ): TrustVerification  // Sealed result
    
    suspend fun findTrustPath(
        verifier: VerifierIdentity,
        issuer: IssuerIdentity
    ): TrustPath?
    
    // Trust DSL for configuration
    suspend fun trust(configure: TrustBuilder.() -> Unit)
    
    // ============================================
    // Complex Services (Only for truly complex operations)
    // ============================================
    
    val blockchains: BlockchainService  // 10+ methods
    val trust: TrustService              // Multiple trust operations
    
    // ============================================
    // Lifecycle
    // ============================================
    
    suspend fun close() {
        scope.cancel()
    }
}

// ============================================
// TYPE-SAFE IDENTIFIERS ✅ Already implemented!
// ============================================

@JvmInline value class Did(val value: String) {
    init { /* validation */ }
    val method: String get() = ...
}

@JvmInline value class KeyId(val value: String) {
    init { /* validation */ }
}

@JvmInline value class CredentialId(val value: String) {
    init { /* validation */ }
}

// ============================================
// DOMAIN TYPES ✅ Already implemented!
// ============================================

data class IssuerIdentity(
    val did: Did,
    val keyId: KeyId
) {
    val verificationMethodId: String get() = "${did.value}#${keyId.value}"
}

data class VerifierIdentity(val did: Did)
data class HolderIdentity(val did: Did)

// ============================================
// SEALED RESULT TYPES ✅ Already implemented!
// ============================================

sealed class DidResolutionResult {
    data class Success(val document: DidDocument, val metadata: ResolutionMetadata) : DidResolutionResult()
    sealed class Failure : DidResolutionResult() {
        data class NotFound(val did: Did) : Failure()
        data class InvalidFormat(val did: Did, val reason: String) : Failure()
        data class MethodNotRegistered(val method: String) : Failure()
        data class ResolutionError(val did: Did, val reason: String) : Failure()
    }
}

sealed class VerificationResult {
    data class Valid(val credential: VerifiableCredential, val warnings: List<String> = emptyList()) : VerificationResult()
    sealed class Invalid : VerificationResult() {
        data class Expired(val credential: VerifiableCredential, val expiredAt: Instant) : Invalid()
        data class Revoked(val credential: VerifiableCredential, val revokedAt: Instant?) : Invalid()
        data class InvalidProof(val credential: VerifiableCredential, val reason: String) : Invalid()
        data class UntrustedIssuer(val credential: VerifiableCredential, val issuer: Did) : Invalid()
        data class SchemaValidationFailed(val credential: VerifiableCredential, val errors: List<String>) : Invalid()
    }
}

sealed class TrustVerification {
    data class Trusted(val path: TrustPath, val policy: TrustPolicy) : TrustVerification()
    data class Untrusted(val reason: String, val path: TrustPath?) : TrustVerification()
}

// ============================================
// USAGE EXAMPLE
// ============================================

suspend fun main() {
    // Simple initialization
    val trustWeave = TrustWeave.inMemory()
    
    // Create DID (type-safe)
    val issuerDid = trustWeave.createDid(method = "key")
    
    // Resolve DID (sealed result - exhaustive handling)
    when (val result = trustWeave.resolveDid(issuerDid, timeout = 30.seconds)) {
        is DidResolutionResult.Success -> {
            val doc = result.document
            val keyId = KeyId("${doc.id}#key-1")
            
            // Issue credential (type-safe issuer identity)
            val credential = trustWeave.issue {
                credential {
                    type("PersonCredential")
                    issuer(issuerDid)
                    subject {
                        id(Did("did:key:holder"))
                        claim("name", "Alice")
                    }
                }
                signedBy(IssuerIdentity(issuerDid, keyId))
            }
            
            // Verify credential (sealed result - exhaustive handling)
            when (val verification = trustWeave.verify(credential)) {
                is VerificationResult.Valid -> {
                    println("Credential valid: ${verification.credential.id}")
                }
                is VerificationResult.Invalid.Expired -> {
                    println("Expired at ${verification.expiredAt}")
                }
                is VerificationResult.Invalid.Revoked -> {
                    println("Revoked at ${verification.revokedAt}")
                }
                // Compiler ensures all cases handled
            }
        }
        is DidResolutionResult.Failure.NotFound -> {
            println("DID not found: ${result.did}")
        }
        // Compiler ensures all cases handled
    }
    
    trustWeave.close()
}
```

---

## Summary: Priority Actions

### Critical (Must Fix)

1. **Reduce API Surface Bloat** - Eliminate redundant overloads, consolidate builders
2. **Simplify Configuration** - Add simple factory for common cases (`TrustWeave.inMemory()`)
3. **Remove Reflection** - Replace with explicit dependencies or ServiceLoader
4. **Type-Safe KMS** - Replace `Any` with proper interface

### High Priority (Should Fix)

5. **Promote Trust Model** - Make trust concepts first-class in API
6. **Add Timeout Support** - All I/O operations should have timeouts
7. **Configurable Dispatchers** - Allow custom dispatchers for testing
8. **First-Class Trust Paths** - Make trust paths a prominent type

### Medium Priority (Nice to Have)

9. **Reduce Builder Count** - Use builders only for complex configurations
10. **Service Layer Cleanup** - Make all services internal except truly complex ones
11. **Naming Improvements** - Rename generic services to domain-precise names
12. **Algorithm Type Safety** - Replace string algorithms with sealed class

### Low Priority (Future Enhancement)

13. **Structured Concurrency** - Add explicit scope management
14. **Blocking Operations** - Mark CPU-bound operations clearly
15. **Documentation** - Improve getting started guides

---

## Conclusion

The TrustWeave SDK has **excellent foundations** and is **production-ready**. Significant progress has been made on type safety and error handling. To achieve **reference-quality** status, focus on:

1. **API Minimalism** - Single way to do common operations, fewer overloads
2. **Configuration Simplicity** - Simple factory for common cases
3. **Trust Model Prominence** - Make trust concepts first-class, not hidden
4. **Consistency** - Unified patterns across all operations

The recommended refactorings are **focused and actionable**. Most can be done incrementally without breaking changes, using deprecation and migration guides.

**Current Status:** Approximately **75% of the way** to reference-quality status. The remaining 25% focuses on:
- API surface reduction
- Configuration simplification
- Trust model prominence
- Minor type safety improvements

With these changes, TrustWeave can become the **industry gold standard** for Kotlin identity and trust libraries.

---

**End of Review**

