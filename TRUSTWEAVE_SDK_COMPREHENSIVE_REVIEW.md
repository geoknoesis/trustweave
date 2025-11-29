# TrustWeave Kotlin SDK: Comprehensive API Review

**Review Date:** 2024  
**Reviewer:** Kotlin Software Architect & Security Engineer  
**Goal:** Transform into reference-quality API for identity, trust, and secure data

---

## Executive Summary

The TrustWeave SDK demonstrates **solid architectural foundations** with a well-structured plugin system, comprehensive DID/credential support, and thoughtful separation of concerns. However, the API surface has **significant opportunities for refinement** to achieve reference-quality status. Key areas requiring attention:

- **API Minimalism**: Too many entry points, redundant overloads, and leaky abstractions
- **Error Handling**: Mixed exception/result patterns create confusion
- **Domain Modeling**: Some generic names obscure Web-of-Trust semantics
- **Type Safety**: Missing sealed result types for verification operations
- **Developer Experience**: Happy paths are clear, but error handling is inconsistent

**Overall Assessment:** The SDK is **production-ready** but needs **architectural refinement** to achieve reference-quality status. The foundation is excellent; the polish is needed.

---

## 1. High-Level Evaluation

### Strengths

✅ **Clean Architecture**
- Excellent separation: `core`, `did`, `credentials`, `kms`, `trust`, `wallet`
- Plugin system (`ProviderChain`, `PluginRegistry`) is well-designed
- Context-based dependency injection (`TrustWeaveContext`) avoids global state

✅ **Comprehensive Feature Set**
- Full DID lifecycle (create, resolve, update, deactivate)
- Verifiable Credentials (issue, verify, present)
- Trust registry with path discovery
- Blockchain anchoring
- Multiple KMS providers

✅ **Modern Kotlin Usage**
- Coroutines throughout (`suspend` functions)
- DSL builders for configuration
- Extension functions for ergonomics
- Data classes for value types

✅ **Security-Conscious Design**
- Key material handled through KMS abstraction
- Proof generation/verification separated
- DID resolution with validation

### Weaknesses

❌ **API Surface Bloat**
- Multiple ways to do the same thing (e.g., `createDid` with options vs builder)
- Service layer (`DidService`, `CredentialService`) adds indirection without clear benefit
- `ProviderChain` is powerful but exposes implementation details

❌ **Error Handling Inconsistency**
- Facade throws exceptions (`DidException`, `CredentialException`)
- Lower-level APIs return `Result<T>` or data classes (`CredentialVerificationResult`)
- No sealed result types for exhaustive handling
- Mixed patterns confuse developers

❌ **Naming & Domain Clarity**
- Generic names: `ProviderChain`, `PluginRegistry`, `Service`
- Missing Web-of-Trust terminology: "TrustAnchor", "Verifier", "Issuer" not prominent
- Some names hide cryptographic intent (e.g., `KeyManagementService` vs `SigningKeyService`)

❌ **Type Safety Gaps**
- `CredentialVerificationResult` is a data class (not sealed) → no exhaustive handling
- `DidResolutionResult` has nullable `document` → should be sealed
- String-based identifiers (`keyId: String`) instead of inline classes

❌ **Developer Experience Issues**
- Happy path is clear, but error handling requires try-catch everywhere
- No `Result<T>` wrapper for functional composition
- Configuration DSL is verbose for simple cases

---

## 2. Public API & Developer Experience

### Current State

**Entry Point:**
```kotlin
val trustweave = TrustWeave.create()
val did = trustweave.dids.create()
val credential = trustweave.credentials.issue(...)
```

**Strengths:**
- ✅ Single entry point (`TrustWeave`)
- ✅ Service-based organization (`dids`, `credentials`, `wallets`)
- ✅ DSL configuration support
- ✅ Sensible defaults

**Issues:**

#### A. Redundant Overloads

**Current:**
```kotlin
// DidService.kt
suspend fun create(method: String = "key", options: DidCreationOptions): DidDocument
suspend fun create(method: String = "key", configure: DidCreationOptionsBuilder.() -> Unit): DidDocument
```

**Problem:** Two ways to do the same thing. The builder overload is just syntactic sugar.

**Recommendation:**
```kotlin
// Single method with optional builder
suspend fun create(
    method: String = "key",
    configure: (DidCreationOptionsBuilder.() -> Unit)? = null
): DidDocument {
    val options = configure?.let { didCreationOptions(it) } ?: DidCreationOptions()
    // ...
}
```

#### B. Service Layer Indirection

**Current:**
```kotlin
trustweave.dids.create()  // → DidService.create() → DidMethod.createDid()
```

**Problem:** Extra layer adds no value. Why not `trustweave.createDid()` directly?

**Recommendation:**
```kotlin
// Option 1: Direct methods on TrustWeave
class TrustWeave {
    suspend fun createDid(method: String = "key", ...): DidDocument
    suspend fun resolveDid(did: String): DidResolutionResult
    suspend fun issueCredential(...): VerifiableCredential
    suspend fun verifyCredential(...): VerificationResult
}

// Option 2: Keep services but make them more focused
// Only expose services for complex operations (e.g., trust registry)
```

#### C. Missing Happy Path Simplification

**Current:**
```kotlin
val credential = trustweave.credentials.issue(
    issuer = issuerDid,
    subject = buildJsonObject { ... },
    config = IssuanceConfig(
        proofType = ProofType.Ed25519Signature2020,
        keyId = keyId,
        issuerDid = issuerDid
    )
)
```

**Problem:** Too many parameters. `issuerDid` appears twice.

**Recommendation:**
```kotlin
// Builder pattern for complex operations
val credential = trustweave.issue {
    issuer(issuerDid)
    subject { 
        put("name", "Alice")
    }
    signWith(keyId)
    proofType(ProofType.Ed25519Signature2020)
}

// Or: Extract common patterns
val credential = trustweave.issueSimple(
    issuer = issuerDid,
    keyId = keyId,
    subject = mapOf("name" to "Alice")
)
```

#### D. Error Handling Forces Try-Catch Everywhere

**Current:**
```kotlin
try {
    val did = trustweave.dids.create()
} catch (e: DidException.DidMethodNotRegistered) {
    // Handle
} catch (e: DidException.InvalidDidFormat) {
    // Handle
}
```

**Problem:** Exceptions for expected failures (e.g., DID not found) force defensive programming.

**Recommendation:**
```kotlin
// Return sealed result types
sealed class DidResult {
    data class Success(val document: DidDocument) : DidResult()
    sealed class Failure : DidResult() {
        data class MethodNotRegistered(val method: String) : Failure()
        data class InvalidFormat(val reason: String) : Failure()
        data class NotFound(val did: String) : Failure()
    }
}

// Usage
when (val result = trustweave.createDid()) {
    is DidResult.Success -> println(result.document.id)
    is DidResult.Failure.MethodNotRegistered -> // Handle
    is DidResult.Failure.InvalidFormat -> // Handle
}
```

### Recommended API Surface

**Minimal Public API:**
```kotlin
// Core operations (direct methods)
class TrustWeave {
    // DIDs
    suspend fun createDid(method: String = "key", configure: (DidBuilder.() -> Unit)? = null): DidDocument
    suspend fun resolveDid(did: String): DidResolutionResult
    suspend fun updateDid(did: String, updater: (DidDocument) -> DidDocument): DidDocument
    suspend fun deactivateDid(did: String): Boolean
    
    // Credentials
    suspend fun issueCredential(configure: CredentialIssuanceBuilder.() -> Unit): VerifiableCredential
    suspend fun verifyCredential(credential: VerifiableCredential, config: VerificationConfig = default): VerificationResult
    
    // Wallets
    suspend fun createWallet(holderDid: String, config: WalletConfig = default): Wallet
    
    // Trust (only expose if needed)
    val trust: TrustRegistry // Access trust operations via property
}

// Sealed result types for exhaustive handling
sealed class VerificationResult {
    data class Valid(val credential: VerifiableCredential) : VerificationResult()
    sealed class Invalid : VerificationResult() {
        data class Expired(val expiredAt: Instant) : Invalid()
        data class Revoked(val revokedAt: Instant) : Invalid()
        data class InvalidProof(val reason: String) : Invalid()
        data class UntrustedIssuer(val issuer: String) : Invalid()
    }
}
```

---

## 3. Naming, Domain Modeling & Web-of-Trust Semantics

### Current Issues

#### A. Generic Names Hide Domain Intent

**Current:**
- `ProviderChain` → What does it provide? Trust? Credentials? Keys?
- `PluginRegistry` → Implementation detail, not domain concept
- `CredentialService` → "Service" is vague

**Recommendation:**
```kotlin
// Domain-precise names
class TrustChain<T>  // For trust-related chains
class CredentialIssuerChain  // For credential issuance chains
class DidResolverRegistry  // For DID resolvers
class CredentialIssuer  // Already exists, use it!
class CredentialVerifier  // Already exists, use it!
```

#### B. Missing Web-of-Trust Terminology

**Current:**
```kotlin
interface TrustRegistry {
    suspend fun isTrustedIssuer(issuerDid: String, credentialType: String?): Boolean
    suspend fun addTrustAnchor(...)
}
```

**Good:** Uses "TrustAnchor" and "Issuer" terminology.

**Missing:**
- "Verifier" not prominent (it's in `CredentialVerifier` but not in public API)
- "Holder" vs "Subject" confusion
- "Proof" vs "Signature" terminology

**Recommendation:**
```kotlin
// Make Web-of-Trust concepts first-class
interface TrustRegistry {
    suspend fun isTrustedIssuer(issuer: IssuerIdentity, credentialType: CredentialType?): Boolean
    suspend fun addTrustAnchor(anchor: TrustAnchor): Boolean
    suspend fun findTrustPath(from: VerifierIdentity, to: IssuerIdentity): TrustPath?
}

// Type-safe identities
@JvmInline value class IssuerIdentity(val did: String)
@JvmInline value class VerifierIdentity(val did: String)
@JvmInline value class HolderIdentity(val did: String)
```

#### C. Cryptographic Clarity

**Current:**
```kotlin
interface KeyManagementService {
    suspend fun generateKey(algorithm: Algorithm, ...): KeyHandle
    suspend fun sign(keyId: String, data: ByteArray, ...): ByteArray
}
```

**Issues:**
- `KeyManagementService` is too generic (manages keys, but also signs)
- `keyId: String` should be `KeyId` (inline class)
- `Algorithm` is good, but `SignatureAlgorithm` would be clearer

**Recommendation:**
```kotlin
// Separate concerns
interface SigningKeyService {
    suspend fun generateSigningKey(algorithm: SignatureAlgorithm): SigningKeyId
    suspend fun sign(keyId: SigningKeyId, data: ByteArray): Signature
    suspend fun getPublicKey(keyId: SigningKeyId): PublicKey
}

interface EncryptionKeyService {
    suspend fun generateEncryptionKey(algorithm: EncryptionAlgorithm): EncryptionKeyId
    suspend fun encrypt(keyId: EncryptionKeyId, data: ByteArray): EncryptedData
}

// Or: Keep unified but rename
interface CryptographicKeyService {
    suspend fun generateKey(algorithm: KeyAlgorithm, purpose: KeyPurpose): KeyId
    suspend fun sign(keyId: KeyId, data: ByteArray): Signature
    suspend fun encrypt(keyId: KeyId, data: ByteArray): EncryptedData
}
```

#### D. Data-Type Correctness

**Current:**
```kotlin
data class CredentialVerificationResult(
    val valid: Boolean,
    val errors: List<String> = emptyList(),
    // ... many boolean flags
)
```

**Problem:** Data class with boolean flags → no exhaustive handling, easy to misuse.

**Recommendation:**
```kotlin
// Sealed class for exhaustive handling
sealed class VerificationResult {
    data class Valid(
        val credential: VerifiableCredential,
        val warnings: List<VerificationWarning> = emptyList()
    ) : VerificationResult()
    
    sealed class Invalid : VerificationResult() {
        data class Expired(val expiredAt: Instant) : Invalid()
        data class Revoked(val revokedAt: Instant) : Invalid()
        data class InvalidProof(val reason: String, val proof: Proof?) : Invalid()
        data class UntrustedIssuer(val issuer: IssuerIdentity) : Invalid()
        data class SchemaValidationFailed(val errors: List<SchemaError>) : Invalid()
    }
}
```

### Recommended Naming Conventions

| Current | Recommended | Rationale |
|---------|-------------|-----------|
| `ProviderChain` | `TrustChain` / `IssuerChain` | Domain-precise |
| `PluginRegistry` | `DidResolverRegistry` / `CredentialIssuerRegistry` | Domain-precise |
| `CredentialService` | `CredentialIssuer` / `CredentialVerifier` | Domain-precise |
| `KeyManagementService` | `SigningKeyService` / `CryptographicKeyService` | Clearer purpose |
| `keyId: String` | `keyId: KeyId` | Type safety |
| `CredentialVerificationResult` (data class) | `VerificationResult` (sealed class) | Exhaustive handling |

---

## 4. Idiomatic Kotlin & Architecture

### Current Strengths

✅ **Coroutines:** All public APIs are `suspend`  
✅ **Data Classes:** Used for value types  
✅ **Extension Functions:** Good ergonomics  
✅ **DSL Builders:** Configuration DSLs are well-designed

### Issues

#### A. Missing Sealed Classes for States

**Current:**
```kotlin
data class DidResolutionResult(
    val document: DidDocument?,
    val documentMetadata: DidDocumentMetadata = DidDocumentMetadata(),
    val resolutionMetadata: Map<String, Any?> = emptyMap()
)
```

**Problem:** Nullable `document` → no exhaustive handling, easy to forget null check.

**Recommendation:**
```kotlin
sealed class DidResolutionResult {
    data class Success(
        val document: DidDocument,
        val metadata: DidDocumentMetadata = DidDocumentMetadata()
    ) : DidResolutionResult()
    
    sealed class Failure : DidResolutionResult() {
        data class NotFound(val did: String, val reason: String) : Failure()
        data class InvalidFormat(val did: String, val reason: String) : Failure()
        data class MethodNotRegistered(val method: String) : Failure()
    }
}
```

#### B. Missing Inline Classes for Identifiers

**Current:**
```kotlin
suspend fun sign(keyId: String, data: ByteArray): ByteArray
suspend fun resolveDid(did: String): DidResolutionResult
```

**Problem:** String-based identifiers → no type safety, easy to mix up.

**Recommendation:**
```kotlin
@JvmInline value class KeyId(val value: String)
@JvmInline value class Did(val value: String)
@JvmInline value class CredentialId(val value: String)

suspend fun sign(keyId: KeyId, data: ByteArray): ByteArray
suspend fun resolveDid(did: Did): DidResolutionResult
```

#### C. Error Handling: Exceptions vs Result

**Current:**
- Facade throws exceptions
- Lower-level APIs return `Result<T>`
- Verification returns data class

**Problem:** Inconsistent patterns.

**Recommendation:**
```kotlin
// Option 1: Result<T> everywhere (functional)
suspend fun createDid(...): Result<DidDocument, DidError>
suspend fun verifyCredential(...): Result<VerificationResult, VerificationError>

// Option 2: Sealed result types (Kotlin-idiomatic)
suspend fun createDid(...): DidResult
suspend fun verifyCredential(...): VerificationResult

// Option 3: Exceptions for exceptional cases, Result for expected failures
suspend fun createDid(...): DidDocument  // Throws on config errors
suspend fun resolveDid(...): DidResolutionResult  // Returns sealed result (expected to fail)
```

**Recommendation:** Use **Option 2** (sealed result types) for operations that commonly fail (resolution, verification). Use exceptions for programming errors (invalid config).

#### D. DSL Overuse

**Current:**
```kotlin
trustweave.dids.create {
    algorithm = KeyAlgorithm.ED25519
    purpose(KeyPurpose.AUTHENTICATION)
}
```

**Good:** DSL is clear and ergonomic.

**Issue:** Some DSLs are verbose for simple cases.

**Recommendation:**
```kotlin
// Simple case: direct parameters
val did = trustweave.createDid(algorithm = KeyAlgorithm.ED25519)

// Complex case: DSL
val did = trustweave.createDid {
    algorithm = KeyAlgorithm.ED25519
    purpose(KeyPurpose.AUTHENTICATION)
    purpose(KeyPurpose.ASSERTION_METHOD)
}
```

### Recommended Patterns

```kotlin
// 1. Sealed classes for states
sealed class VerificationResult { ... }

// 2. Inline classes for identifiers
@JvmInline value class KeyId(val value: String)

// 3. Extension functions for ergonomics
fun TrustWeave.createDidSimple(algorithm: KeyAlgorithm = KeyAlgorithm.ED25519): DidDocument

// 4. Result types for expected failures
sealed class DidResolutionResult { ... }

// 5. Exceptions for programming errors
class InvalidConfigurationException(...) : TrustWeaveException(...)
```

---

## 5. Trust Model & Crypto/API Safety Review

### Current Strengths

✅ **KMS Abstraction:** Keys never exposed in public API  
✅ **Proof Generation:** Separated from credential creation  
✅ **DID Resolution:** Validated before use  
✅ **Trust Registry:** Configurable trust policies

### Issues

#### A. Key Material Safety

**Current:**
```kotlin
interface KeyManagementService {
    suspend fun sign(keyId: String, data: ByteArray): ByteArray
}
```

**Good:** Private keys never exposed.

**Issue:** `keyId: String` is easy to misuse (wrong key, wrong algorithm).

**Recommendation:**
```kotlin
// Type-safe key IDs with algorithm binding
@JvmInline value class KeyId(val value: String)

data class KeySpec(
    val id: KeyId,
    val algorithm: SignatureAlgorithm,
    val purpose: KeyPurpose
)

interface SigningKeyService {
    suspend fun sign(keyId: KeyId, data: ByteArray, algorithm: SignatureAlgorithm? = null): Signature
    // Algorithm parameter allows override, but validates against key spec
}
```

#### B. Proof Purpose Validation

**Current:**
```kotlin
data class CredentialVerificationResult(
    val proofPurposeValid: Boolean = false,
    // ...
)
```

**Issue:** Boolean flag → no details about why it failed.

**Recommendation:**
```kotlin
sealed class VerificationResult {
    sealed class Invalid : VerificationResult() {
        data class InvalidProofPurpose(
            val required: ProofPurpose,
            val actual: ProofPurpose?,
            val verificationMethod: String
        ) : Invalid()
    }
}
```

#### C. Trust Boundary Clarity

**Current:**
```kotlin
suspend fun verify(
    credential: VerifiableCredential,
    config: VerificationConfig = VerificationConfig()
): CredentialVerificationResult
```

**Issue:** Trust registry check is optional (`checkTrustRegistry: Boolean`). Should be explicit.

**Recommendation:**
```kotlin
// Make trust checks explicit
data class VerificationConfig(
    val trustPolicy: TrustPolicy = TrustPolicy.NoTrustRequired,
    val expirationPolicy: ExpirationPolicy = ExpirationPolicy.Strict,
    val revocationPolicy: RevocationPolicy = RevocationPolicy.CheckStatusList
)

sealed class TrustPolicy {
    object NoTrustRequired : TrustPolicy()
    data class RequireTrustAnchor(val registry: TrustRegistry) : TrustPolicy()
    data class RequireTrustPath(val maxPathLength: Int, val registry: TrustRegistry) : TrustPolicy()
}
```

#### D. Algorithm Safety

**Current:**
```kotlin
suspend fun sign(keyId: String, data: ByteArray, algorithm: Algorithm? = null): ByteArray
```

**Issue:** Algorithm parameter is optional → easy to use wrong algorithm.

**Recommendation:**
```kotlin
// Validate algorithm against key spec
suspend fun sign(keyId: KeyId, data: ByteArray, algorithm: SignatureAlgorithm? = null): Signature {
    val keySpec = getKeySpec(keyId)
    val effectiveAlgorithm = algorithm ?: keySpec.algorithm
    require(keySpec.supports(effectiveAlgorithm)) {
        "Key $keyId does not support algorithm ${effectiveAlgorithm.name}"
    }
    // ...
}
```

### Recommended Safety Patterns

```kotlin
// 1. Type-safe identifiers
@JvmInline value class KeyId(val value: String)

// 2. Algorithm validation
data class KeySpec(val algorithm: SignatureAlgorithm, ...) {
    fun supports(required: SignatureAlgorithm): Boolean = algorithm == required
}

// 3. Explicit trust policies
sealed class TrustPolicy { ... }

// 4. Sealed result types with detailed errors
sealed class VerificationResult {
    sealed class Invalid {
        data class InvalidProofPurpose(...) : Invalid()
        data class UntrustedIssuer(...) : Invalid()
    }
}
```

---

## 6. Concurrency & Coroutine Design

### Current State

**Strengths:**
- ✅ All public APIs are `suspend` functions
- ✅ Proper use of `withContext(Dispatchers.IO)` for I/O
- ✅ No `GlobalScope` usage observed

**Issues:**

#### A. Dispatcher Selection

**Current:**
```kotlin
suspend fun execute(): List<VerifiableCredential> = withContext(Dispatchers.IO) {
    // ...
}
```

**Problem:** Hardcoded dispatchers → not configurable, hard to test.

**Recommendation:**
```kotlin
class TrustWeaveContext(
    val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
)

// Or: Use CoroutineScope injection
class TrustWeave(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
)
```

#### B. Missing Timeout Support

**Current:**
```kotlin
suspend fun resolveDid(did: String): DidResolutionResult
```

**Issue:** No timeout → can hang indefinitely.

**Recommendation:**
```kotlin
suspend fun resolveDid(
    did: String,
    timeout: Duration = 30.seconds
): DidResolutionResult = withTimeout(timeout) {
    // Resolution logic
}
```

#### C. No Structured Concurrency Guarantees

**Current:**
- No explicit `CoroutineScope` management
- No cancellation propagation documentation

**Recommendation:**
```kotlin
class TrustWeave(
    private val scope: CoroutineScope
) {
    suspend fun issueCredential(...): VerifiableCredential {
        // Operations run in scope, cancellation propagates
        return scope.async { /* issue logic */ }.await()
    }
}
```

#### D. Blocking Operations Not Marked

**Current:**
```kotlin
suspend fun sign(keyId: String, data: ByteArray): ByteArray
```

**Issue:** Cryptographic operations may be CPU-bound (blocking). Should be marked.

**Recommendation:**
```kotlin
// Mark blocking operations
suspend fun sign(keyId: KeyId, data: ByteArray): Signature = 
    withContext(Dispatchers.Default) {  // CPU-bound
        // Signing logic
    }

// Or: Separate blocking API
fun signBlocking(keyId: KeyId, data: ByteArray): Signature
```

### Recommended Concurrency Patterns

```kotlin
// 1. Configurable dispatchers
class TrustWeaveContext(
    val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
)

// 2. Timeout support
suspend fun resolveDid(did: String, timeout: Duration = 30.seconds): DidResolutionResult

// 3. Structured concurrency
class TrustWeave(private val scope: CoroutineScope)

// 4. Mark blocking operations
suspend fun sign(...) = withContext(Dispatchers.Default) { ... }
```

---

## 7. Legacy / Deprecated Code

### Identified Issues

#### A. Mixed Error Handling Patterns

**Current:**
- Exceptions in facade
- `Result<T>` in lower-level APIs
- Data classes for verification results

**Action:** Standardize on sealed result types for expected failures, exceptions for programming errors.

#### B. Generic Names

**Current:**
- `ProviderChain` → Should be domain-specific
- `PluginRegistry` → Should be domain-specific
- `Service` suffix → Overused

**Action:** Rename to domain-precise names.

#### C. Missing Type Safety

**Current:**
- String-based identifiers
- Data classes instead of sealed classes

**Action:** Introduce inline classes and sealed result types.

### Cleanup Plan

1. **Phase 1: Introduce Sealed Result Types**
   - Create `DidResolutionResult` (sealed)
   - Create `VerificationResult` (sealed)
   - Deprecate old data classes

2. **Phase 2: Introduce Inline Classes**
   - `KeyId`, `Did`, `CredentialId`
   - Deprecate String overloads

3. **Phase 3: Rename Generic Types**
   - `ProviderChain` → Domain-specific names
   - `PluginRegistry` → Domain-specific names

4. **Phase 4: Simplify API Surface**
   - Remove redundant overloads
   - Consolidate service layer

---

## 8. Concrete Refactor Suggestions

### Scenario 1: Creating and Resolving a DID

**Current:**
```kotlin
val trustweave = TrustWeave.create()
try {
    val did = trustweave.dids.create()
    val result = trustweave.dids.resolve(did.id)
    if (result.document != null) {
        println("Resolved: ${result.document.id}")
    } else {
        println("Not found")
    }
} catch (e: DidException.DidMethodNotRegistered) {
    // Handle
}
```

**Recommended:**
```kotlin
val trustweave = TrustWeave.create()

// Create: Simple case
val did = trustweave.createDid()  // Direct method, throws on error

// Resolve: Expected to fail → sealed result
when (val result = trustweave.resolveDid(did.id)) {
    is DidResolutionResult.Success -> println("Resolved: ${result.document.id}")
    is DidResolutionResult.Failure.NotFound -> println("Not found: ${result.did}")
    is DidResolutionResult.Failure.InvalidFormat -> println("Invalid: ${result.reason}")
    is DidResolutionResult.Failure.MethodNotRegistered -> println("Method not registered: ${result.method}")
}
```

### Scenario 2: Issuing a Credential

**Current:**
```kotlin
val credential = trustweave.credentials.issue(
    issuer = issuerDid,
    subject = buildJsonObject { put("name", "Alice") },
    config = IssuanceConfig(
        proofType = ProofType.Ed25519Signature2020,
        keyId = keyId,
        issuerDid = issuerDid  // Redundant
    )
)
```

**Recommended:**
```kotlin
// Option 1: Builder pattern
val credential = trustweave.issueCredential {
    issuer(issuerDid)
    subject {
        put("name", "Alice")
    }
    signWith(keyId)
    proofType(ProofType.Ed25519Signature2020)
}

// Option 2: Simple overload
val credential = trustweave.issueCredential(
    issuer = issuerDid,
    keyId = keyId,
    subject = mapOf("name" to "Alice")
)
```

### Scenario 3: Verifying a Credential

**Current:**
```kotlin
val result = trustweave.credentials.verify(credential)
if (result.valid) {
    println("Valid")
} else {
    println("Errors: ${result.errors}")
    // No exhaustive handling
}
```

**Recommended:**
```kotlin
when (val result = trustweave.verifyCredential(credential)) {
    is VerificationResult.Valid -> {
        println("Valid: ${result.credential.id}")
        result.warnings.forEach { println("Warning: $it") }
    }
    is VerificationResult.Invalid.Expired -> {
        println("Expired at ${result.expiredAt}")
    }
    is VerificationResult.Invalid.Revoked -> {
        println("Revoked at ${result.revokedAt}")
    }
    is VerificationResult.Invalid.InvalidProof -> {
        println("Invalid proof: ${result.reason}")
    }
    is VerificationResult.Invalid.UntrustedIssuer -> {
        println("Untrusted issuer: ${result.issuer}")
    }
    // Compiler ensures all cases handled
}
```

### Scenario 4: Trust Registry Usage

**Current:**
```kotlin
val registry: TrustRegistry = InMemoryTrustRegistry()
registry.addTrustAnchor(anchorDid, metadata)
val isTrusted = registry.isTrustedIssuer(issuerDid, credentialType)
```

**Recommended:**
```kotlin
// Make trust registry part of TrustWeave
val trustweave = TrustWeave.create {
    trustRegistry = InMemoryTrustRegistry()
}

// Add trust anchor
trustweave.trust.addAnchor(anchorDid) {
    credentialTypes("EducationCredential")
    description("Trusted university")
}

// Check trust (with type safety)
val isTrusted = trustweave.trust.isTrustedIssuer(
    issuer = IssuerIdentity(issuerDid),
    credentialType = CredentialType("EducationCredential")
)
```

### Ideal Reference-Quality API Snippet

```kotlin
// ============================================
// Reference-Quality API Example
// ============================================

// 1. Simple initialization
val trustweave = TrustWeave.create()

// 2. Create DID (simple case)
val did = trustweave.createDid()

// 3. Issue credential (builder pattern)
val credential = trustweave.issueCredential {
    issuer(did.id)
    subject {
        put("name", "Alice")
        put("email", "alice@example.com")
    }
    signWith(did.verificationMethod.first().id)
    proofType(ProofType.Ed25519Signature2020)
}

// 4. Verify credential (sealed result)
when (val result = trustweave.verifyCredential(credential)) {
    is VerificationResult.Valid -> {
        println("✅ Valid: ${result.credential.id}")
    }
    is VerificationResult.Invalid.Expired -> {
        println("❌ Expired: ${result.expiredAt}")
    }
    is VerificationResult.Invalid.UntrustedIssuer -> {
        println("❌ Untrusted: ${result.issuer}")
    }
    // Compiler ensures exhaustive handling
}

// 5. Trust registry (explicit trust policy)
trustweave.trust.addAnchor(did.id) {
    credentialTypes("PersonCredential")
    description("Trusted identity provider")
}

val isTrusted = trustweave.trust.isTrustedIssuer(
    issuer = IssuerIdentity(did.id),
    credentialType = CredentialType("PersonCredential")
)

// 6. Type-safe identifiers
val keyId: KeyId = trustweave.generateSigningKey(SignatureAlgorithm.Ed25519)
val signature: Signature = trustweave.sign(keyId, data.toByteArray())
```

---

## Summary of Recommendations

### High Priority

1. **Introduce Sealed Result Types**
   - `DidResolutionResult` (sealed)
   - `VerificationResult` (sealed)
   - Replace data classes with boolean flags

2. **Introduce Inline Classes**
   - `KeyId`, `Did`, `CredentialId`
   - Type-safe identifiers

3. **Simplify API Surface**
   - Remove redundant overloads
   - Consolidate service layer
   - Direct methods on `TrustWeave`

4. **Standardize Error Handling**
   - Sealed result types for expected failures
   - Exceptions for programming errors

### Medium Priority

5. **Rename Generic Types**
   - `ProviderChain` → Domain-specific names
   - `PluginRegistry` → Domain-specific names

6. **Improve Type Safety**
   - Algorithm validation
   - Proof purpose validation
   - Trust policy types

7. **Enhance Developer Experience**
   - Builder patterns for complex operations
   - Simple overloads for common cases
   - Better error messages

### Low Priority

8. **Concurrency Improvements**
   - Configurable dispatchers
   - Timeout support
   - Structured concurrency

9. **Documentation**
   - Web-of-Trust terminology
   - Trust boundary documentation
   - Security best practices

---

## Conclusion

The TrustWeave SDK has **excellent foundations** but needs **architectural refinement** to achieve reference-quality status. The plugin system, separation of concerns, and security-conscious design are strengths. The main areas for improvement are:

1. **API Minimalism**: Reduce surface area, remove redundancy
2. **Type Safety**: Sealed result types, inline classes
3. **Error Handling**: Consistent patterns
4. **Domain Modeling**: Web-of-Trust terminology, precise names

With these changes, TrustWeave can become the **gold standard** for Kotlin identity and trust libraries.

---

**Next Steps:**
1. Review and prioritize recommendations
2. Create migration plan for breaking changes
3. Implement sealed result types
4. Introduce inline classes
5. Simplify API surface

