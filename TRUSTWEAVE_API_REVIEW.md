# TrustWeave Kotlin SDK: Comprehensive API Review
## World-Class Reference Quality Assessment

**Reviewer:** Senior Kotlin Architect, Security Engineer, Web-of-Trust Specialist  
**Date:** 2024  
**Scope:** TrustWeave Trust Layer API (`trust` module)  
**Goal:** Transform into reference-quality Kotlin SDK for identity, trust, and secure data operations

---

## 1. High-Level Evaluation

### Strengths

✅ **Strong Foundation**
- Well-structured DSL-based API with fluent builders
- Consistent use of coroutines (`suspend` functions throughout)
- Clear separation of concerns (DID, Credential, Wallet, Trust)
- Good documentation with examples
- Recent refactoring has improved naming consistency

✅ **Modern Kotlin Patterns**
- Extension functions for ergonomic syntax
- DSL builders for complex operations
- Data classes for value types
- Proper use of sealed classes for error hierarchy (`TrustWeaveException`)

✅ **Security-Conscious Design**
- KMS abstraction prevents direct key exposure
- Proof generation is abstracted
- DID resolution is async and non-blocking

### Critical Weaknesses

❌ **Type Safety Erosion**
- Extensive use of `Any` types in configuration (`kms: Any`, `statusListManager: Any?`, `trustRegistry: Any?`)
- Loss of compile-time guarantees
- Forces unsafe casts throughout codebase
- Violates Kotlin's type safety principles

❌ **API Surface Bloat**
- Three-layer architecture (`TrustWeave` → `TrustWeaveContext` → `TrustWeaveConfig`) creates confusion
- Duplicate entry points (DSL vs direct methods for trust operations)
- Too many ways to do the same thing
- `getDslContext()` exposes internal implementation

❌ **Naming Inconsistencies**
- Vague "Provider" interfaces (`DidDslProvider`, `WalletDslProvider`, `CredentialDslProvider`)
- Method name `by()` is unclear (should be `signedBy()` or `withIssuer()`)
- "Enhanced" prefix removed but legacy patterns remain

❌ **Error Handling Inconsistency**
- Mix of exceptions and `Result<T>` types
- `IllegalStateException` used for configuration errors (should be domain-specific)
- No sealed result types for verification outcomes
- Silent failures in revocation setup (catch-all exception swallowing)

❌ **Cryptographic API Safety**
- Key IDs are raw strings (no type safety)
- No validation that key belongs to issuer DID
- Proof type is string-based (should be sealed enum)
- No compile-time prevention of algorithm mismatches

❌ **Reflection-Based Factory Resolution**
- Brittle, non-idiomatic Kotlin
- Runtime failures instead of compile-time errors
- Hard to test and mock
- Violates dependency injection principles

### Overall Assessment

**Current State:** Good foundation with significant room for improvement  
**Trustworthiness:** ⚠️ Moderate — type safety issues and error handling inconsistencies reduce confidence  
**Kotlin Idiomaticity:** ⚠️ Moderate — good patterns mixed with anti-patterns  
**Security Posture:** ⚠️ Moderate — abstraction is good but type safety gaps are concerning  
**Developer Experience:** ✅ Good — DSL is intuitive, but API surface is too large

**Verdict:** The API shows promise but needs significant refactoring to achieve reference-quality status. The type safety issues are the most critical concern for a trust-critical system.

---

## 2. Public API & Developer Experience

### Current State Analysis

**Positive Aspects:**
- DSL syntax is clean and readable
- Happy path is clear: `TrustWeave.build { }` → operations
- Good discoverability through IntelliJ autocomplete
- Consistent suspend function usage

**Critical Issues:**

#### A. Triple-Layer Architecture Confusion

**Problem:**
```kotlin
// Three ways to do the same thing:
val config = trustWeave { ... }  // Returns TrustWeaveConfig
val trustWeave = TrustWeave.build { ... }  // Returns TrustWeave
val context = trustWeave.getDslContext()  // Returns TrustWeaveContext (internal now, good!)

// Operations available on multiple layers:
config.issue { ... }  // ❌ Removed (good!)
trustWeave.issue { ... }  // ✅ Correct
context.issue { ... }  // ⚠️ Internal only (acceptable)
```

**Impact:** Developers are confused about which entry point to use. The API should have ONE clear entry point.

**Recommendation:** ✅ Already addressed — extension functions on `TrustWeaveConfig` removed. Good progress.

#### B. Duplicate Trust Operations

**Problem:**
```kotlin
// Two ways to add trust anchor:
trustWeave.trust {
    addAnchor("did:key:issuer") { ... }
}

// OR
trustWeave.addTrustAnchor("did:key:issuer") { ... }
```

**Impact:** API surface bloat, maintenance burden, developer confusion.

**Recommendation:** Remove direct methods, keep only DSL. The DSL is more expressive and consistent.

#### C. Method Naming Issues

**Problem:**
```kotlin
// Unclear method name
by(issuerDid = "did:key:issuer", keyId = "key-1")

// What does "by" mean? Signed by? Issued by? Created by?
```

**Impact:** Poor discoverability, unclear intent.

**Recommendation:** Rename to `signedBy(issuerDid, keyId)` or `withIssuer(issuerDid, keyId)`.

#### D. Missing Type Safety for Key Operations

**Problem:**
```kotlin
// Key ID is just a string - no validation
by(issuerDid = "did:key:issuer", keyId = "key-1")

// No compile-time guarantee that:
// 1. Key exists
// 2. Key belongs to issuer
// 3. Key supports the required algorithm
```

**Impact:** Runtime errors, security vulnerabilities, poor developer experience.

**Recommendation:** Introduce `KeyId` inline class and `IssuerKey` sealed type.

### Happy Path Evaluation

**Current Happy Path:**
```kotlin
val trustWeave = TrustWeave.build {
    keys { provider("inMemory"); algorithm("Ed25519") }
    did { method("key") { algorithm("Ed25519") } }
}

val did = trustWeave.createDid { method("key") }
val credential = trustWeave.issue {
    credential { issuer(did); subject { id("did:key:holder") } }
    by(issuerDid = did, keyId = "key-1")  // ⚠️ Unclear
}
```

**Assessment:** ✅ Clear and intuitive, but `by()` naming is weak.

**Ideal Happy Path:**
```kotlin
val trustWeave = TrustWeave {
    keys { provider("inMemory"); algorithm("Ed25519") }
    did { method("key") { algorithm("Ed25519") } }
}

val issuer = trustWeave.createIdentity { method("key") }
val credential = trustWeave.issue {
    credential { issuer(issuer.did); subject { id("did:key:holder") } }
    signedBy(issuer.keyId)  // ✅ Clear, type-safe
}
```

---

## 3. Naming, Domain Modeling & Web-of-Trust Semantics

### Critical Naming Issues

#### A. "Provider" Interfaces Are Implementation-Focused

**Current:**
```kotlin
interface DidDslProvider { ... }
interface WalletDslProvider { ... }
interface CredentialDslProvider { ... }
```

**Problem:** "Provider" is an implementation detail, not a domain concept. In Web-of-Trust terminology, these are operations or services.

**Recommendation:**
```kotlin
// Option 1: Domain-focused names
interface DidOperations { ... }
interface WalletOperations { ... }
interface CredentialOperations { ... }

// Option 2: Service-oriented (if these represent services)
interface DidService { ... }
interface WalletService { ... }
interface CredentialService { ... }

// Option 3: Capability-based (most Web-of-Trust aligned)
interface DidCapabilities { ... }
interface WalletCapabilities { ... }
interface CredentialCapabilities { ... }
```

**Best Choice:** `DidOperations`, `WalletOperations`, `CredentialOperations` — these reflect what they do, not how they're implemented.

#### B. Vague Method Names

**Issues:**
1. `by()` → Should be `signedBy()` or `withIssuer()`
2. `getDslContext()` → Already internal (good), but name suggests it's for DSL when it's really the runtime
3. `rotateKey()` → Returns `Any` (type safety issue)

**Recommendation:**
```kotlin
// Before
fun by(issuerDid: String, keyId: String)

// After
fun signedBy(issuerDid: Did, keyId: KeyId)
// OR
fun withIssuer(issuer: IssuerIdentity)
```

#### C. Missing Domain Types

**Problem:** Core Web-of-Trust concepts are represented as primitives:
- `String` for DIDs (should be `Did` inline class)
- `String` for key IDs (should be `KeyId` inline class)
- `String` for proof types (should be `ProofType` sealed class)

**Recommendation:**
```kotlin
@JvmInline
value class Did(val value: String) {
    init {
        require(value.startsWith("did:")) { "Invalid DID format: $value" }
    }
}

@JvmInline
value class KeyId(val value: String)

sealed class ProofType(val value: String) {
    object Ed25519Signature2020 : ProofType("Ed25519Signature2020")
    object JsonWebSignature2020 : ProofType("JsonWebSignature2020")
    // ...
}
```

#### D. Trust Operations Naming

**Current:**
```kotlin
suspend fun isTrustedIssuer(issuerDid: String, credentialType: String? = null): Boolean
suspend fun getTrustPath(fromDid: String, toDid: String): TrustPathResult?
```

**Issues:**
- `isTrustedIssuer` is verbose
- `getTrustPath` doesn't indicate it might return null
- Missing trust policy abstraction

**Recommendation:**
```kotlin
// More concise
suspend fun isTrusted(issuer: Did, credentialType: CredentialType? = null): Boolean

// Clearer nullability
suspend fun findTrustPath(from: Did, to: Did): TrustPath?

// Add trust policy
data class TrustPolicy(
    val requiredCredentialTypes: Set<CredentialType>,
    val maxPathLength: Int = 3,
    val allowDelegation: Boolean = true
)
```

---

## 4. Idiomatic Kotlin & Architecture

### Type Safety Violations

#### Critical: `Any` Types in Configuration

**Current:**
```kotlin
class TrustWeaveConfig(
    val kms: Any,  // ❌ Lost type safety
    val statusListManager: Any? = null,  // ❌ Lost type safety
    val trustRegistry: Any? = null  // ❌ Lost type safety
)
```

**Problem:**
- No compile-time guarantees
- Forces unsafe casts: `config.kms as? KeyManagementService`
- Runtime failures instead of compile-time errors
- Violates Kotlin's type system

**Solution:**
```kotlin
// Use generics or sealed interface
interface KeyManagement {
    suspend fun sign(keyId: KeyId, data: ByteArray): ByteArray
    suspend fun getPublicKey(keyId: KeyId): PublicKey
}

interface RevocationManagement {
    suspend fun checkStatus(credentialId: String): RevocationStatus
}

interface TrustManagement {
    suspend fun isTrusted(issuer: Did, type: CredentialType?): Boolean
    suspend fun findTrustPath(from: Did, to: Did): TrustPath?
}

class TrustWeaveConfig(
    val kms: KeyManagement,  // ✅ Type-safe
    val revocation: RevocationManagement?,  // ✅ Type-safe
    val trust: TrustManagement?  // ✅ Type-safe
)
```

#### Reflection-Based Factory Resolution

**Current:**
```kotlin
private fun getDefaultKmsFactory(): KmsFactory {
    try {
        val factoryClass = Class.forName("com.trustweave.testkit.services.TestkitKmsFactory")
        val factory = factoryClass.getDeclaredConstructor().newInstance() as? KmsFactory
        if (factory != null) return factory
    } catch (e: Exception) {
        // Fall through
    }
    throw IllegalStateException("KMS factory not available...")
}
```

**Problem:**
- Brittle, runtime failures
- Hard to test
- Not idiomatic Kotlin
- Violates dependency injection principles

**Solution:**
```kotlin
// Use dependency injection or service loader
interface ServiceLoader<T> {
    fun load(): T?
}

// Or use Kotlin's ServiceLoader
val kmsFactory = ServiceLoader.load(KmsFactory::class.java)
    .firstOrNull()
    ?: throw TrustWeaveException.NoProviderFound("KMS factory")

// Or require explicit factory injection
class Builder {
    fun kmsFactory(factory: KmsFactory) { ... }
    // No reflection fallback
}
```

### Error Handling Inconsistencies

#### Mixed Exception and Result Types

**Current:**
- `TrustWeave` facade throws exceptions
- Some lower-level APIs return `Result<T>`
- `CredentialVerificationResult` is a data class (not sealed)

**Problem:**
- Inconsistent error handling patterns
- Developers must know which pattern to use
- No exhaustive handling for verification results

**Solution:**
```kotlin
// Sealed result types for verification
sealed class VerificationResult {
    data class Valid(val credential: VerifiableCredential) : VerificationResult()
    sealed class Invalid : VerificationResult() {
        data class Expired(val expiredAt: Instant) : Invalid()
        data class Revoked(val revokedAt: Instant) : Invalid()
        data class InvalidProof(val reason: String) : Invalid()
        data class UntrustedIssuer(val issuer: Did) : Invalid()
        data class SchemaValidationFailed(val errors: List<String>) : Invalid()
    }
}

// Consistent exception hierarchy
sealed class TrustWeaveException : Exception() {
    // Already good structure, but needs more specific types
    data class KeyNotFound(val keyId: KeyId) : TrustWeaveException()
    data class DidResolutionFailed(val did: Did, val reason: String) : TrustWeaveException()
    // ...
}
```

### Builder Pattern Improvements

**Current:**
```kotlin
class IssuanceBuilder {
    private var credential: VerifiableCredential? = null
    private var issuerDid: String? = null
    private var keyId: String? = null
    // ...
}
```

**Issues:**
- Nullable fields require runtime validation
- No compile-time guarantee of required fields
- Builder can be in invalid state

**Better Approach:**
```kotlin
// Use sealed builder states
sealed class IssuanceBuilderState {
    object Empty : IssuanceBuilderState()
    data class WithCredential(val credential: VerifiableCredential) : IssuanceBuilderState()
    data class Ready(
        val credential: VerifiableCredential,
        val issuer: IssuerIdentity
    ) : IssuanceBuilderState()
}

class IssuanceBuilder {
    private var state: IssuanceBuilderState = IssuanceBuilderState.Empty
    
    fun credential(block: CredentialBuilder.() -> Unit): IssuanceBuilder {
        val cred = CredentialBuilder().apply(block).build()
        state = IssuanceBuilderState.WithCredential(cred)
        return this
    }
    
    fun signedBy(issuer: IssuerIdentity): IssuanceBuilder {
        state = when (val current = state) {
            is IssuanceBuilderState.WithCredential -> 
                IssuanceBuilderState.Ready(current.credential, issuer)
            else -> throw IllegalStateException("Credential must be set first")
        }
        return this
    }
    
    suspend fun issue(): VerifiableCredential {
        return when (val current = state) {
            is IssuanceBuilderState.Ready -> issueCredential(current.credential, current.issuer)
            else -> throw IllegalStateException("Builder not ready")
        }
    }
}
```

---

## 5. Trust Model & Crypto/API Safety Review

### Critical Security Issues

#### A. Key Material Handling

**Current:**
```kotlin
// Key ID is just a string
by(issuerDid = "did:key:issuer", keyId = "key-1")

// No validation that:
// 1. Key exists in KMS
// 2. Key belongs to issuer DID
// 3. Key supports required algorithm
```

**Security Risk:** High — Misuse can lead to signing with wrong keys or keys that don't exist.

**Solution:**
```kotlin
// Type-safe key identity
@JvmInline
value class KeyId(val value: String) {
    init {
        require(value.isNotBlank()) { "Key ID cannot be blank" }
    }
}

// Issuer identity bundles DID and key
data class IssuerIdentity(
    val did: Did,
    val keyId: KeyId
) {
    init {
        // Validate key belongs to DID (if possible at construction)
        require(keyId.value.startsWith("$did#") || keyId.value.startsWith(did.value)) {
            "Key ID must be associated with issuer DID"
        }
    }
}

// Usage
fun signedBy(issuer: IssuerIdentity) {
    // Type-safe, validated
}
```

#### B. Proof Type Safety

**Current:**
```kotlin
fun withProof(type: String)  // ❌ String-based, no validation
```

**Problem:**
- Typos lead to runtime errors
- No compile-time guarantee of valid proof types
- Algorithm mismatches not caught

**Solution:**
```kotlin
sealed class ProofType(val value: String) {
    object Ed25519Signature2020 : ProofType("Ed25519Signature2020")
    object JsonWebSignature2020 : ProofType("JsonWebSignature2020")
    object EcdsaSecp256k1Signature2019 : ProofType("EcdsaSecp256k1Signature2019")
    
    companion object {
        fun fromString(value: String): ProofType? = when (value) {
            Ed25519Signature2020.value -> Ed25519Signature2020
            JsonWebSignature2020.value -> JsonWebSignature2020
            EcdsaSecp256k1Signature2019.value -> EcdsaSecp256k1Signature2019
            else -> null
        }
    }
}

// Usage
fun withProof(type: ProofType) { ... }
```

#### C. Silent Failure in Revocation Setup

**Current:**
```kotlin
try {
    val statusList = statusListManager.createStatusList(...)
    // ...
} catch (e: Exception) {
    // Status list creation failed - continue without revocation
    // In production, you might want to log this or throw
}
```

**Security Risk:** Medium — Credentials may be issued without revocation support silently.

**Solution:**
```kotlin
// Explicit error handling
when (val result = statusListManager.createStatusList(...)) {
    is Result.Success -> { /* use status list */ }
    is Result.Failure -> {
        throw TrustWeaveException.RevocationSetupFailed(
            reason = result.error.message ?: "Unknown error"
        )
    }
}

// OR use sealed result
sealed class RevocationSetupResult {
    data class Success(val statusList: StatusList) : RevocationSetupResult()
    data class Failure(val reason: String) : RevocationSetupResult()
}
```

#### D. Trust Boundary Clarity

**Current:**
```kotlin
// Trust operations are mixed with other operations
trustWeave.trust { ... }
trustWeave.addTrustAnchor(...)  // Direct method
```

**Issue:** Trust operations should be clearly separated and require explicit trust policy.

**Solution:**
```kotlin
// Separate trust domain
class TrustOperations(
    private val registry: TrustRegistry,
    private val policy: TrustPolicy
) {
    suspend fun isTrusted(issuer: Did, type: CredentialType?): Boolean
    suspend fun findTrustPath(from: Did, to: Did): TrustPath?
    suspend fun addAnchor(anchor: TrustAnchor, metadata: TrustAnchorMetadata): Boolean
}

// Usage
val trust = trustWeave.trust(policy = TrustPolicy.default())
trust.isTrusted(issuer, CredentialType.Education)
```

### Cryptographic API Safety

**Current Issues:**
1. Algorithm selection is string-based
2. No validation of algorithm-key compatibility
3. Key operations don't verify key existence

**Recommendations:**
```kotlin
// Algorithm as sealed class
sealed class SignatureAlgorithm(val name: String) {
    object Ed25519 : SignatureAlgorithm("Ed25519")
    object Secp256k1 : SignatureAlgorithm("secp256k1")
    object P256 : SignatureAlgorithm("P-256")
}

// Key type validation
data class KeySpec(
    val algorithm: SignatureAlgorithm,
    val keyId: KeyId,
    val publicKey: PublicKey
) {
    fun supports(required: SignatureAlgorithm): Boolean {
        return algorithm == required
    }
}

// Usage with validation
fun signedBy(issuer: IssuerIdentity, algorithm: SignatureAlgorithm) {
    val keySpec = kms.getKeySpec(issuer.keyId)
    require(keySpec.supports(algorithm)) {
        "Key ${issuer.keyId} does not support algorithm ${algorithm.name}"
    }
    // Proceed with signing
}
```

---

## 6. Concurrency & Coroutine Design

### Current State

**Strengths:**
- ✅ All public APIs are `suspend` functions
- ✅ Proper use of `withContext(Dispatchers.IO)` for I/O operations
- ✅ No `GlobalScope` usage observed

**Issues:**

#### A. Scattered Dispatcher Usage

**Current:**
```kotlin
suspend fun execute(): List<VerifiableCredential> = withContext(Dispatchers.IO) {
    // ...
}
```

**Problem:** Dispatcher selection is scattered, hard to change, and not configurable.

**Solution:**
```kotlin
// Centralized dispatcher configuration
class TrustWeaveConfig {
    val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    // Allow override for testing
}

// Or use CoroutineScope injection
class TrustWeaveContext(
    private val config: TrustWeaveConfig,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
)
```

#### B. Missing Structured Concurrency Guarantees

**Current:**
- No explicit `CoroutineScope` management
- No cancellation propagation guarantees
- No timeout handling for long-running operations

**Recommendation:**
```kotlin
// Add timeout support
suspend fun resolveDid(
    did: Did,
    timeout: Duration = 30.seconds
): DidDocument = withTimeout(timeout) {
    // Resolution logic
}

// Explicit scope for operations
class TrustWeave(
    private val scope: CoroutineScope
) {
    suspend fun issue(block: IssuanceBuilder.() -> Unit): VerifiableCredential {
        return scope.async { /* issue logic */ }.await()
    }
}
```

#### C. Blocking Operations Not Clearly Marked

**Current:**
- Cryptographic operations may be blocking but not clearly marked
- KMS operations may block but appear as suspend

**Recommendation:**
```kotlin
// Clearly mark blocking operations
@Blocking
suspend fun sign(keyId: KeyId, data: ByteArray): ByteArray

// Or use separate interface
interface BlockingKeyManagement {
    fun signBlocking(keyId: KeyId, data: ByteArray): ByteArray
}
```

---

## 7. Legacy / Deprecated Code

### Already Addressed ✅
- ✅ Removed `EnhancedQueryBuilder` → `QueryBuilder`
- ✅ Removed `queryEnhanced()` → `query()`
- ✅ Removed extension functions on `TrustWeaveConfig`
- ✅ Made `getDslContext()` internal
- ✅ Removed deprecated `capability()` method
- ✅ Removed duplicate `TrustLayer.kt`

### Remaining Issues

#### A. Reflection-Based Factory Resolution

**Location:** `TrustWeaveConfig.Builder`

**Issue:** Uses `Class.forName()` and reflection to load factories.

**Impact:** Brittle, runtime failures, hard to test.

**Removal Plan:**
1. Require explicit factory injection
2. Remove all reflection code
3. Provide clear error messages if factories not provided
4. Update documentation with factory setup examples

#### B. `Any` Types in Configuration

**Location:** `TrustWeaveConfig`, `TrustWeaveContext`

**Issue:** Type safety lost through `Any` usage.

**Migration Plan:**
1. Create sealed interfaces for each service type
2. Migrate `Any` to specific interfaces
3. Update all casts to use new types
4. Add migration guide for custom implementations

#### C. String-Based Identifiers

**Location:** Throughout API (DIDs, Key IDs, Proof Types)

**Issue:** No type safety, validation, or domain modeling.

**Migration Plan:**
1. Introduce inline classes for `Did`, `KeyId`
2. Introduce sealed classes for `ProofType`, `CredentialType`
3. Provide extension functions for backward compatibility
4. Deprecate string overloads, remove in next major version

---

## 8. Concrete Refactor Suggestions

### Refactor 1: Type-Safe Identity and Key Management

**Before:**
```kotlin
val credential = trustWeave.issue {
    credential { issuer("did:key:issuer"); subject { id("did:key:holder") } }
    by(issuerDid = "did:key:issuer", keyId = "key-1")  // ❌ Strings, unclear
}
```

**After:**
```kotlin
// Type-safe identity
val issuer = trustWeave.createIdentity {
    method("key")
    algorithm(SignatureAlgorithm.Ed25519)
}

// Type-safe issuance
val credential = trustWeave.issue {
    credential {
        issuer(issuer.did)
        subject { id(holderDid) }
    }
    signedBy(issuer)  // ✅ Clear, type-safe, validated
}
```

**Changes:**
- `Did` inline class replaces `String`
- `KeyId` inline class replaces `String`
- `IssuerIdentity` data class bundles DID + key
- `signedBy()` replaces `by()`
- Compile-time validation

### Refactor 2: Sealed Result Types for Verification

**Before:**
```kotlin
val result: CredentialVerificationResult = trustWeave.verify {
    credential(credential)
}

// Result is data class, requires manual checking
if (result.valid) { ... } else { ... }
```

**After:**
```kotlin
val result: VerificationResult = trustWeave.verify {
    credential(credential)
}

// Exhaustive when expression
when (result) {
    is VerificationResult.Valid -> {
        println("Credential is valid: ${result.credential.id}")
    }
    is VerificationResult.Invalid.Expired -> {
        println("Credential expired at ${result.expiredAt}")
    }
    is VerificationResult.Invalid.Revoked -> {
        println("Credential revoked at ${result.revokedAt}")
    }
    is VerificationResult.Invalid.UntrustedIssuer -> {
        println("Issuer ${result.issuer} is not trusted")
    }
    // Compiler ensures all cases handled
}
```

**Benefits:**
- Exhaustive handling enforced by compiler
- Clear error types
- Better error messages
- Type-safe error recovery

### Refactor 3: Type-Safe Configuration

**Before:**
```kotlin
val trustWeave = TrustWeave.build {
    keys {
        provider("inMemory")  // ❌ String-based
        algorithm("Ed25519")  // ❌ String-based
    }
    did {
        method("key") {  // ❌ String-based
            algorithm("Ed25519")  // ❌ String-based
        }
    }
}
```

**After:**
```kotlin
val trustWeave = TrustWeave {
    keys {
        provider(KeyManagementProvider.InMemory)  // ✅ Type-safe
        algorithm(SignatureAlgorithm.Ed25519)  // ✅ Type-safe
    }
    did {
        method(DidMethod.Key) {  // ✅ Type-safe
            algorithm(SignatureAlgorithm.Ed25519)  // ✅ Type-safe
        }
    }
}
```

**Changes:**
- Sealed classes for providers, algorithms, methods
- Compile-time validation
- Better autocomplete
- No typos possible

### Refactor 4: Simplified Trust Operations

**Before:**
```kotlin
// Two ways to do the same thing
trustWeave.trust {
    addAnchor("did:key:issuer") { ... }
}

trustWeave.addTrustAnchor("did:key:issuer") { ... }
```

**After:**
```kotlin
// Single, clear API
val trust = trustWeave.trust(policy = TrustPolicy.default())

trust.addAnchor(
    anchor = TrustAnchor(did = Did("did:key:issuer")),
    metadata = TrustAnchorMetadata {
        credentialTypes(CredentialType.Education)
        description("Trusted university")
    }
)

// Type-safe trust checking
val isTrusted = trust.isTrusted(
    issuer = Did("did:key:issuer"),
    type = CredentialType.Education
)
```

**Benefits:**
- Single entry point
- Type-safe DIDs and credential types
- Clear trust policy
- Better separation of concerns

### Refactor 5: Ideal Reference-Quality API

**Complete Example:**
```kotlin
// ============================================
// INITIALIZATION
// ============================================

val trustWeave = TrustWeave {
    keys {
        provider(KeyManagementProvider.InMemory)
        algorithm(SignatureAlgorithm.Ed25519)
    }
    
    did {
        method(DidMethod.Key) {
            algorithm(SignatureAlgorithm.Ed25519)
        }
    }
    
    trust {
        provider(TrustProvider.InMemory)
        policy(TrustPolicy.default())
    }
}

// ============================================
// IDENTITY CREATION (Type-Safe)
// ============================================

val issuer = trustWeave.createIdentity {
    method(DidMethod.Key)
    algorithm(SignatureAlgorithm.Ed25519)
}

val holder = trustWeave.createIdentity {
    method(DidMethod.Key)
    algorithm(SignatureAlgorithm.Ed25519)
}

// ============================================
// CREDENTIAL ISSUANCE (Type-Safe, Clear)
// ============================================

val credential = trustWeave.issue {
    credential {
        id(CredentialId("https://example.edu/credentials/123"))
        type(CredentialType.Education)
        issuer(issuer.did)
        subject {
            id(holder.did)
            claim("degree", "Bachelor of Science")
            claim("institution", "Example University")
        }
        issued(Instant.now())
        expires(Instant.now().plus(1, ChronoUnit.YEARS))
    }
    signedBy(issuer)  // ✅ Type-safe, validated
    proofType(ProofType.Ed25519Signature2020)
    withRevocation()  // Explicit, not silent
}

// ============================================
// CREDENTIAL VERIFICATION (Exhaustive Handling)
// ============================================

val verification = trustWeave.verify {
    credential(credential)
    checkRevocation()
    checkExpiration()
    checkTrust()
}

when (verification) {
    is VerificationResult.Valid -> {
        println("✅ Credential is valid")
        useCredential(verification.credential)
    }
    is VerificationResult.Invalid.Expired -> {
        println("❌ Credential expired: ${verification.expiredAt}")
    }
    is VerificationResult.Invalid.UntrustedIssuer -> {
        println("❌ Issuer not trusted: ${verification.issuer}")
    }
    // Compiler ensures all cases handled
}

// ============================================
// TRUST MANAGEMENT (Type-Safe, Policy-Based)
// ============================================

val trust = trustWeave.trust(
    policy = TrustPolicy {
        maxPathLength = 3
        allowDelegation = true
        requiredTypes = setOf(CredentialType.Education)
    }
)

trust.addAnchor(
    anchor = TrustAnchor(did = issuer.did),
    metadata = TrustAnchorMetadata {
        credentialTypes(CredentialType.Education)
        description("Trusted educational institution")
    }
)

val isTrusted = trust.isTrusted(
    issuer = issuer.did,
    type = CredentialType.Education
)

// ============================================
// WALLET OPERATIONS (Clean Query API)
// ============================================

val wallet = trustWeave.wallet {
    holder(holder.did)
    enableOrganization()
}

wallet.store(credential)

val results = wallet.query {
    type(CredentialType.Education)
    notExpired()
    valid()
    tag("degree")
    collection("Education")
}
```

**Key Improvements:**
1. ✅ All identifiers are type-safe (`Did`, `KeyId`, `CredentialType`, etc.)
2. ✅ Clear, domain-focused naming (`signedBy`, `createIdentity`)
3. ✅ Exhaustive error handling with sealed classes
4. ✅ Single entry point (`TrustWeave`)
5. ✅ No `Any` types
6. ✅ No reflection
7. ✅ Compile-time safety throughout
8. ✅ Web-of-Trust terminology respected

---

## Summary of Priority Actions

### Critical (Security & Type Safety)
1. **Replace `Any` types** with sealed interfaces or generics
2. **Introduce type-safe identifiers** (`Did`, `KeyId`, `ProofType`, `CredentialType`)
3. **Fix silent revocation failures** — make errors explicit
4. **Add key validation** — ensure keys belong to issuer DIDs

### High Priority (API Quality)
5. **Remove duplicate trust methods** — keep only DSL
6. **Rename `by()` to `signedBy()`** or `withIssuer()`
7. **Rename Provider interfaces** to domain names
8. **Introduce sealed result types** for verification

### Medium Priority (Developer Experience)
9. **Remove reflection-based factories** — require explicit injection
10. **Centralize dispatcher configuration**
11. **Add timeout support** for long-running operations
12. **Improve error messages** with actionable guidance

### Low Priority (Polish)
13. **Add builder state machine** for compile-time validation
14. **Improve documentation** with security considerations
15. **Add more examples** for common workflows

---

## Conclusion

The TrustWeave API has a **solid foundation** with good DSL design and modern Kotlin patterns. However, **critical type safety issues** and **API surface bloat** prevent it from being reference-quality.

**Most Critical Issues:**
1. `Any` types erode type safety
2. String-based identifiers lack validation
3. Reflection-based factories are brittle
4. Silent failures in security-critical paths

**Path to Reference Quality:**
1. Introduce type-safe identifiers (inline classes)
2. Replace `Any` with sealed interfaces
3. Remove reflection, require explicit dependencies
4. Add sealed result types for exhaustive handling
5. Simplify API surface (remove duplicates)

With these changes, TrustWeave can become a **reference-quality Kotlin SDK** for identity and trust operations, setting the standard for secure, type-safe, Web-of-Trust-aligned APIs.

