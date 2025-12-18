# Comprehensive Kotlin SDK Review: Reference-Quality API Assessment

**Review Date:** Current  
**Reviewer:** World-Class Kotlin Software Architect, API Designer, Security Engineer  
**Goal:** Transform into industry gold standard for Kotlin libraries dealing with identity, trust, and secure data

---

## Executive Summary

### Overall Assessment: **Excellent Foundation, Needs Refinement**

The API demonstrates **strong architectural foundations** with:
- ✅ Clean separation of concerns
- ✅ Type-safe sealed result hierarchies
- ✅ W3C VC Data Model alignment
- ✅ Explicit trust policy support
- ✅ Consistent error handling patterns

However, several areas need refinement to achieve **reference-quality** status:
- ⚠️ Some naming inconsistencies
- ⚠️ Parameter ordering could be more intuitive
- ⚠️ Missing some convenience patterns
- ⚠️ Some internal APIs still exposed
- ⚠️ Trust policy API could be more ergonomic

**Verdict:** The API is **85% of the way** to reference quality. With targeted refinements, it can become the industry gold standard.

---

## 1. High-Level Evaluation

### Strengths

1. **Clean Entry Point**
   - `CredentialServices.default()` - Simple, discoverable
   - All formats built-in - No plugin complexity exposed
   - Single factory method - No decision paralysis

2. **Type-Safe Results**
   - Sealed `IssuanceResult` and `VerificationResult` hierarchies
   - Exhaustive when expressions enforced by compiler
   - Rich error context in failure types

3. **W3C Alignment**
   - `VerifiableCredential` matches W3C VC Data Model
   - Proper use of `Issuer`, `CredentialSubject`, `CredentialStatus`
   - Format-agnostic proof handling

4. **Trust Model**
   - Explicit `TrustPolicy` interface
   - Configurable issuer trust validation
   - Clear separation: cryptographic validity vs. trust

5. **Developer Experience**
   - Convenience overloads for common cases
   - Format constants (`CredentialFormats.VC_LD`)
   - Type helpers (`credentialTypes()`)
   - String extensions for ergonomic usage

### Weaknesses

1. **Parameter Ordering**
   - `verify(credential, trustPolicy, options)` - `trustPolicy` between credential and options feels awkward
   - Should be: `verify(credential, options, trustPolicy)` or `verify(credential, options = options, trustPolicy = policy)`

2. **Naming Inconsistencies**
   - `statusListManager` parameter name doesn't match `CredentialRevocationManager` type
   - Some internal types still exposed (`ProofAdapterRegistry`, `ProofAdapters`)

3. **Missing Convenience**
   - No builder DSL for `IssuanceRequest` (mentioned in docs but not implemented)
   - `TrustPolicy` could have more ergonomic factory methods

4. **Error Handling Edge Cases**
   - `ProofAdapter.issue()` throws exceptions but should return `Result` for consistency
   - Some internal operations throw `IllegalArgumentException` instead of returning `Result`

5. **Documentation Gaps**
   - Missing examples for trust chain validation
   - No guidance on when to use which format
   - Limited examples for presentation creation

---

## 2. Public API & Developer Experience

### Current State Analysis

#### ✅ Excellent Aspects

**1. Factory Pattern**
```kotlin
val service = CredentialServices.default(didResolver)
```
- ✅ Single entry point
- ✅ Clear, discoverable
- ✅ Optional parameters with sensible defaults

**2. Convenience Overloads**
```kotlin
service.issue(
    format = CredentialFormats.VC_LD,
    issuerDid = issuerDid,
    subjectDid = subjectDid,
    type = "PersonCredential",
    claims = mapOf("name" to "Alice")
)
```
- ✅ Reduces boilerplate
- ✅ Auto-converts claims
- ✅ Auto-adds base type

**3. Result Handling**
```kotlin
when (val result = service.issue(request)) {
    is IssuanceResult.Success -> { /* ... */ }
    is IssuanceResult.Failure -> { /* ... */ }
}
```
- ✅ Exhaustive when expressions
- ✅ Type-safe error handling
- ✅ Rich error context

#### ⚠️ Areas for Improvement

**1. Parameter Ordering**

**Current:**
```kotlin
suspend fun verify(
    credential: VerifiableCredential,
    trustPolicy: TrustPolicy? = null,  // ⚠️ Between credential and options
    options: VerificationOptions = VerificationOptions()
): VerificationResult
```

**Recommended:**
```kotlin
suspend fun verify(
    credential: VerifiableCredential,
    options: VerificationOptions = VerificationOptions(),
    trustPolicy: TrustPolicy? = null  // ✅ After options, more logical grouping
): VerificationResult
```

**Rationale:**
- Options and trust policy are both "configuration"
- Grouping related parameters improves readability
- Named parameters make this less critical, but still matters

**2. Missing Builder DSL**

**Current:**
```kotlin
val request = IssuanceRequest(
    format = CredentialFormats.VC_LD,
    issuer = Issuer.fromDid(issuerDid),
    credentialSubject = CredentialSubject.fromDid(subjectDid, claims),
    type = credentialTypes("PersonCredential"),
    issuedAt = Instant.now(),
    validUntil = Instant.now().plus(Duration.ofDays(365))
)
```

**Ideal:**
```kotlin
val request = issuanceRequest {
    format(CredentialFormats.VC_LD)
    issuer(issuerDid)
    subject(subjectDid) {
        claim("name", "Alice")
        claim("email", "alice@example.com")
    }
    type("PersonCredential")
    expiresIn(1, ChronoUnit.YEARS)
}
```

**3. Trust Policy Ergonomics**

**Current:**
```kotlin
val policy = TrustPolicy.allowlist(trustedIssuers = setOf(issuerDid))
```

**Could be better:**
```kotlin
// More discoverable
val policy = TrustPolicy.allowlist(issuerDid, otherIssuerDid)

// Or with vararg
val policy = TrustPolicy.allowlist(issuerDid, otherIssuerDid)
```

**4. Missing Happy Path Shortcuts**

For the most common case (issue and verify), could add:
```kotlin
// Issue and verify in one call (for testing/validation)
suspend fun CredentialService.issueAndVerify(
    request: IssuanceRequest,
    trustPolicy: TrustPolicy? = null
): Pair<VerifiableCredential, VerificationResult>
```

### Discoverability Score: **8/10**

**Strengths:**
- ✅ Constants are discoverable (`CredentialFormats.VC_LD`)
- ✅ Extension functions provide natural syntax
- ✅ Type-safe sealed classes guide usage

**Weaknesses:**
- ⚠️ Some operations require knowing exact parameter order
- ⚠️ Trust policy creation could be more discoverable
- ⚠️ Missing inline examples in KDoc

---

## 3. Naming, Domain Modeling & Web-of-Trust Semantics

### Current Naming Analysis

#### ✅ Excellent Names

1. **`VerifiableCredential`** ✅
   - W3C standard term
   - Clear and unambiguous
   - Industry-standard

2. **`CredentialRevocationManager`** ✅
   - Clear purpose (recently renamed from `StatusListManager`)
   - Explicit about domain (credential revocation)
   - Follows naming pattern

3. **`TrustPolicy`** ✅
   - Domain-precise
   - Clear Web-of-Trust semantics
   - Intuitive

4. **`Issuer`, `CredentialSubject`** ✅
   - W3C VC Data Model terms
   - Industry-standard
   - Clear domain meaning

#### ⚠️ Naming Issues

**1. Parameter Name Mismatch**

**Current:**
```kotlin
fun default(
    didResolver: DidResolver,
    schemaRegistry: SchemaRegistry? = null,
    statusListManager: CredentialRevocationManager? = null  // ⚠️ Name doesn't match type
)
```

**Recommended:**
```kotlin
fun default(
    didResolver: DidResolver,
    schemaRegistry: SchemaRegistry? = null,
    revocationManager: CredentialRevocationManager? = null  // ✅ Matches type name
)
```

**2. Internal API Exposure**

**Current:**
- `ProofAdapterRegistry` - Marked as `@InternalTrustWeaveApi` but still public
- `ProofAdapters` - Exposed for "backward compatibility" but deprecated code removed
- `ProofRegistries` - Similar issue

**Recommendation:**
- Move to `internal` package or make truly internal
- If backward compatibility needed, use typealias with deprecation

**3. Generic Names**

**Current:**
- `CredentialService` - Could be more specific
- `CredentialServices` - Factory object name is fine

**Analysis:**
- `CredentialService` is actually fine - it's in the `credential` package
- Context is clear, brevity is valuable
- **No change needed**

**4. Inconsistent Naming Patterns**

**Current:**
- `CredentialRevocationManager` - Manager suffix
- `SchemaRegistry` - Registry suffix
- `TemplateService` - Service suffix

**Analysis:**
- These are actually consistent with their roles:
  - Manager = actively manages state
  - Registry = stores/retrieves items
  - Service = provides operations
- **No change needed**

### Domain Modeling Score: **9/10**

**Strengths:**
- ✅ W3C VC terminology used correctly
- ✅ Clear trust boundaries
- ✅ Explicit cryptographic operations
- ✅ Web-of-Trust semantics well-represented

**Weaknesses:**
- ⚠️ One parameter name mismatch
- ⚠️ Some internal APIs still exposed

---

## 4. Idiomatic Kotlin & Architecture

### Kotlin Idioms Analysis

#### ✅ Excellent Kotlin Usage

**1. Sealed Classes for Results**
```kotlin
sealed class IssuanceResult {
    data class Success(val credential: VerifiableCredential) : IssuanceResult()
    sealed class Failure : IssuanceResult() { /* ... */ }
}
```
- ✅ Exhaustive when expressions
- ✅ Type-safe error handling
- ✅ Compiler-enforced completeness

**2. Data Classes for Requests**
```kotlin
data class IssuanceRequest(
    val format: CredentialFormatId,
    val issuer: Issuer,
    // ...
)
```
- ✅ Immutable by default
- ✅ Value semantics
- ✅ Copy support

**3. Value Classes for Identifiers**
```kotlin
@JvmInline
value class CredentialFormatId(val value: String)
```
- ✅ Type-safe without allocation overhead
- ✅ Prevents string misuse

**4. Extension Functions**
```kotlin
fun String.toFormatId(): CredentialFormatId
fun Did.asIssuer(): Issuer
```
- ✅ Ergonomic syntax
- ✅ Discoverable via autocomplete

**5. Companion Object Factories**
```kotlin
object CredentialFormats {
    val VC_LD = CredentialFormatId("vc-ld")
}
```
- ✅ Namespace organization
- ✅ Discoverable constants

#### ⚠️ Non-Idiomatic Patterns

**1. Exception Throwing in SPI**

**Current:**
```kotlin
interface ProofAdapter {
    suspend fun issue(request: IssuanceRequest): VerifiableCredential
    // @throws IllegalArgumentException if request is invalid
    // @throws IllegalStateException if adapter is not ready
}
```

**Issue:**
- SPI throws exceptions but public API returns `Result`
- Inconsistent error handling model
- Makes error handling harder for adapter implementers

**Recommended:**
```kotlin
interface ProofAdapter {
    suspend fun issue(request: IssuanceRequest): IssuanceResult
    // ✅ Consistent with public API
}
```

**2. Missing Inline Functions**

**Current:**
```kotlin
fun IssuanceResult.getOrReturn(onFailure: (Failure) -> R): VerifiableCredential?
```

**Could be:**
```kotlin
inline fun <R> IssuanceResult.getOrReturn(
    crossinline onFailure: (Failure) -> R
): VerifiableCredential?
```
- ✅ Inline for zero-cost abstractions
- ✅ Crossinline for lambda parameters

**3. Null Safety**

**Current:**
```kotlin
val issuerDid: Did?
    get() = if (issuerIri.isDid) try { Did(issuerIri.value) } catch (e: IllegalArgumentException) { null } else null
```

**Issue:**
- Try-catch in property getter is awkward
- Could use `runCatching` for cleaner code

**Recommended:**
```kotlin
val issuerDid: Did?
    get() = issuerIri.takeIf { it.isDid }
        ?.let { runCatching { Did(it.value) }.getOrNull() }
```

### Architecture Score: **8.5/10**

**Strengths:**
- ✅ Clean separation: public API / internal / SPI
- ✅ Modular structure
- ✅ Testable design

**Weaknesses:**
- ⚠️ Some internal APIs still public
- ⚠️ SPI error handling inconsistent
- ⚠️ Some non-idiomatic patterns

---

## 5. Trust Model & Crypto/API Safety Review

### Trust Model Analysis

#### ✅ Excellent Trust Design

**1. Explicit Trust Policy**
```kotlin
interface TrustPolicy {
    suspend fun isTrusted(issuer: Did): Boolean
}
```
- ✅ Makes trust checking explicit
- ✅ Configurable without exposing internals
- ✅ Clear trust boundaries

**2. Default Safe Behavior**
```kotlin
suspend fun verify(
    credential: VerifiableCredential,
    trustPolicy: TrustPolicy? = null  // ✅ Default: accept all (explicit choice)
)
```
- ✅ Default is explicit (null = accept all)
- ✅ Forces developers to think about trust
- ✅ No hidden trust assumptions

**3. Trust Failure Type**
```kotlin
data class UntrustedIssuer(
    val issuerDid: Did,
    // ...
) : Invalid()
```
- ✅ Type-safe trust failure
- ✅ Clear error messaging
- ✅ Actionable error information

#### ⚠️ Trust Model Issues

**1. Missing Trust Chain Validation**

**Current:**
- `TrustPolicy.isTrusted()` only checks direct issuer
- No support for trust chains or delegation

**Recommended:**
```kotlin
interface TrustPolicy {
    suspend fun isTrusted(issuer: Did): Boolean
    
    // Add trust chain validation
    suspend fun verifyTrustChain(
        credential: VerifiableCredential
    ): TrustChainResult
}

sealed class TrustChainResult {
    data class Valid(val chain: List<Did>) : TrustChainResult()
    data class Invalid(val reason: String) : TrustChainResult()
}
```

**2. Trust Policy Factory Methods**

**Current:**
```kotlin
fun allowlist(trustedIssuers: Set<Did>): TrustPolicy
```

**Could be more ergonomic:**
```kotlin
// Vararg support
fun allowlist(vararg trustedIssuers: Did): TrustPolicy =
    allowlist(trustedIssuers.toSet())

// Builder pattern
fun allowlist(block: MutableSet<Did>.() -> Unit): TrustPolicy {
    val issuers = mutableSetOf<Did>().apply(block)
    return allowlist(issuers)
}
```

**3. Missing Trust Context**

**Current:**
- Trust policy doesn't receive credential context
- Can't make trust decisions based on credential type or claims

**Recommended:**
```kotlin
interface TrustPolicy {
    suspend fun isTrusted(
        issuer: Did,
        credential: VerifiableCredential  // ✅ Context for trust decisions
    ): Boolean
}
```

### Cryptographic Safety Analysis

#### ✅ Safe Patterns

**1. Opaque Identifiers**
```kotlin
value class CredentialFormatId(val value: String)
```
- ✅ Type-safe format selection
- ✅ Prevents string typos

**2. Immutable Data Classes**
```kotlin
data class VerifiableCredential(/* ... */)
```
- ✅ Immutable by default
- ✅ Prevents accidental modification

**3. Explicit Proof Handling**
```kotlin
val proof: CredentialProof? = null  // Optional in data model, required when verified
```
- ✅ Clear proof requirements
- ✅ Format-agnostic proof abstraction

#### ⚠️ Safety Concerns

**1. ProofAdapter Exception Throwing**

**Current:**
```kotlin
suspend fun issue(request: IssuanceRequest): VerifiableCredential
// @throws IllegalArgumentException
```

**Issue:**
- Exceptions can be missed
- Inconsistent with public API
- Makes error handling harder

**2. Missing Algorithm Policy**

**Current:**
- No way to restrict cryptographic algorithms
- Could accept weak algorithms

**Recommended:**
```kotlin
data class VerificationOptions(
    // ... existing fields ...
    val allowedAlgorithms: Set<String>? = null,  // ✅ Algorithm allowlist
    val minimumKeyStrength: Int? = null  // ✅ Minimum key size
)
```

**3. Key Material Handling**

**Current:**
- Key handling is abstracted away (good)
- But no explicit key lifecycle management API

**Recommendation:**
- Keep key handling abstracted (current approach is correct)
- Document key lifecycle expectations

### Trust & Crypto Safety Score: **8/10**

**Strengths:**
- ✅ Explicit trust policies
- ✅ Type-safe operations
- ✅ Clear trust boundaries

**Weaknesses:**
- ⚠️ Missing trust chain support
- ⚠️ No algorithm policy
- ⚠️ SPI exception throwing

---

## 6. Concurrency & Coroutine Design

### Coroutine Usage Analysis

#### ✅ Excellent Patterns

**1. Suspend Functions**
```kotlin
suspend fun issue(request: IssuanceRequest): IssuanceResult
suspend fun verify(credential: VerifiableCredential): VerificationResult
```
- ✅ All async operations are suspend
- ✅ No blocking operations exposed
- ✅ Cancellable by default

**2. Structured Concurrency**
```kotlin
suspend fun verify(
    credentials: List<VerifiableCredential>,
    // ...
): List<VerificationResult> = coroutineScope {
    credentials.map { async { verify(it, trustPolicy, options) } }.awaitAll()
}
```
- ✅ Uses `coroutineScope` for structured concurrency
- ✅ Parallel execution with proper scoping
- ✅ Cancellation propagates correctly

**3. Cancellation Documentation**
```kotlin
/**
 * This operation is cancellable and will respect coroutine cancellation.
 * Typical duration: 50-200ms depending on proof generation.
 */
```
- ✅ Explicit cancellation semantics
- ✅ Performance expectations documented

#### ⚠️ Concurrency Issues

**1. Missing Cancellation Context**

**Current:**
- No explicit cancellation token parameter
- Relies on coroutine context

**Analysis:**
- Actually fine - Kotlin coroutines handle this automatically
- **No change needed**

**2. Blocking Operations**

**Current:**
- No obvious blocking operations in public API
- ✅ Good

**3. Concurrent Access**

**Current:**
- `CredentialService` implementations should be thread-safe
- Not explicitly documented

**Recommendation:**
- Document thread-safety expectations
- Consider making service instances immutable/stateless

### Concurrency Score: **9/10**

**Strengths:**
- ✅ Proper suspend usage
- ✅ Structured concurrency
- ✅ Cancellation support

**Weaknesses:**
- ⚠️ Thread-safety not explicitly documented
- ⚠️ Could add more cancellation context documentation

---

## 7. Legacy / Deprecated Code

### Deprecated Code Analysis

#### ✅ Clean State

**Good News:**
- ✅ Deprecated factory functions removed
- ✅ No deprecated annotations found in public API
- ✅ Clean codebase

#### ⚠️ Remaining Issues

**1. Internal API Exposure**

**Current:**
```kotlin
@InternalTrustWeaveApi
interface ProofAdapterRegistry {
    // ...
}
```

**Issue:**
- Still public, just annotated
- Could be truly internal

**Recommendation:**
- Move to `internal` package if possible
- Or use typealias with deprecation for migration

**2. Backward Compatibility Comments**

**Current:**
```kotlin
/**
 * This is only exposed for backward compatibility with deprecated factory functions.
 */
object ProofAdapters {
    // ...
}
```

**Issue:**
- Deprecated functions removed, but compatibility code remains
- Should be removed or made internal

**3. Duplicate Factory Files**

**Found:**
- `CredentialServiceFactory.kt` and `CredentialServices.kt` - appear to be duplicates

**Recommendation:**
- Consolidate into single file
- Remove duplicate

### Legacy Code Score: **9/10**

**Strengths:**
- ✅ No deprecated public APIs
- ✅ Clean codebase

**Weaknesses:**
- ⚠️ Some internal APIs still public
- ⚠️ Compatibility code for removed features

---

## 8. Concrete Refactor Suggestions

### Priority 1: Critical Improvements

#### 1. Fix Parameter Ordering

**Before:**
```kotlin
suspend fun verify(
    credential: VerifiableCredential,
    trustPolicy: TrustPolicy? = null,
    options: VerificationOptions = VerificationOptions()
): VerificationResult
```

**After:**
```kotlin
suspend fun verify(
    credential: VerifiableCredential,
    options: VerificationOptions = VerificationOptions(),
    trustPolicy: TrustPolicy? = null
): VerificationResult
```

**Rationale:**
- Options and trust policy are both configuration
- Logical grouping improves readability
- Named parameters make this less critical but still matters

#### 2. Fix Parameter Name Mismatch

**Before:**
```kotlin
fun default(
    statusListManager: CredentialRevocationManager? = null
)
```

**After:**
```kotlin
fun default(
    revocationManager: CredentialRevocationManager? = null
)
```

**Rationale:**
- Parameter name should match type name
- More intuitive and discoverable

#### 3. Enhance Trust Policy API

**Before:**
```kotlin
val policy = TrustPolicy.allowlist(trustedIssuers = setOf(issuerDid))
```

**After:**
```kotlin
// Vararg support
val policy = TrustPolicy.allowlist(issuerDid, otherIssuerDid)

// Or with credential context
suspend fun isTrusted(issuer: Did, credential: VerifiableCredential): Boolean
```

### Priority 2: Enhancements

#### 4. Add Builder DSL for IssuanceRequest

**Before:**
```kotlin
val request = IssuanceRequest(
    format = CredentialFormats.VC_LD,
    issuer = Issuer.fromDid(issuerDid),
    credentialSubject = CredentialSubject.fromDid(subjectDid, claims),
    type = credentialTypes("PersonCredential"),
    issuedAt = Instant.now(),
    validUntil = Instant.now().plus(Duration.ofDays(365))
)
```

**After:**
```kotlin
val request = issuanceRequest {
    format(CredentialFormats.VC_LD)
    issuer(issuerDid)
    subject(subjectDid) {
        claim("name", "Alice")
        claim("email", "alice@example.com")
    }
    type("PersonCredential")
    expiresIn(1, ChronoUnit.YEARS)
}
```

#### 5. Make SPI Consistent with Public API

**Before:**
```kotlin
interface ProofAdapter {
    suspend fun issue(request: IssuanceRequest): VerifiableCredential
    // @throws IllegalArgumentException
}
```

**After:**
```kotlin
interface ProofAdapter {
    suspend fun issue(request: IssuanceRequest): IssuanceResult
    // ✅ Consistent error handling
}
```

### Priority 3: Nice-to-Have

#### 6. Add Algorithm Policy

```kotlin
data class VerificationOptions(
    // ... existing fields ...
    val allowedAlgorithms: Set<String>? = null,
    val minimumKeyStrength: Int? = null
)
```

#### 7. Add Trust Chain Support

```kotlin
interface TrustPolicy {
    suspend fun verifyTrustChain(
        credential: VerifiableCredential
    ): TrustChainResult
}
```

---

## Ideal Reference-Quality API Example

Here's what a **reference-quality** API usage would look like:

```kotlin
// 1. Initialize (simple, discoverable)
val service = CredentialServices.default(
    didResolver = didResolver,
    revocationManager = revocationManager
)

// 2. Issue credential (ergonomic, type-safe)
val result = service.issue(
    format = CredentialFormats.VC_LD,
    issuerDid = issuerDid,
    subjectDid = subjectDid,
    type = "PersonCredential",
    claims = mapOf(
        "name" to "Alice",
        "email" to "alice@example.com"
    ),
    expiresIn = Duration.ofDays(365)
)

// 3. Handle result (exhaustive, type-safe)
val credential = when (result) {
    is IssuanceResult.Success -> result.credential
    is IssuanceResult.Failure -> {
        log.error("Issuance failed: ${result.allErrors}")
        return@function
    }
}

// 4. Verify with trust policy (explicit, clear)
val trustPolicy = TrustPolicy.allowlist(issuerDid, trustedIssuer2)
val verification = service.verify(
    credential = credential,
    options = VerificationOptionPresets.strict(),
    trustPolicy = trustPolicy
)

// 5. Handle verification (exhaustive, actionable)
when (verification) {
    is VerificationResult.Valid -> {
        println("✅ Valid credential from trusted issuer")
        // Use credential
    }
    is VerificationResult.Invalid.UntrustedIssuer -> {
        println("❌ Issuer not trusted: ${verification.issuerDid}")
        // Handle untrusted issuer
    }
    is VerificationResult.Invalid -> {
        println("❌ Invalid: ${verification.allErrors.joinToString()}")
        // Handle other failures
    }
}
```

**Key Qualities:**
- ✅ **Discoverable** - Autocomplete guides usage
- ✅ **Type-Safe** - Compiler enforces correctness
- ✅ **Explicit** - Trust decisions are visible
- ✅ **Ergonomic** - Minimal boilerplate
- ✅ **Actionable** - Errors provide context

---

## Summary & Recommendations

### Overall Score: **8.5/10** (Excellent, approaching reference quality)

### Critical Actions (Must Fix)

1. ✅ Fix parameter ordering in `verify()` methods
2. ✅ Fix `statusListManager` → `revocationManager` parameter name
3. ✅ Make SPI error handling consistent (return `Result` instead of throwing)

### High Priority (Should Fix)

4. ⚠️ Add vararg support to `TrustPolicy.allowlist()` and `blocklist()`
5. ⚠️ Add credential context to `TrustPolicy.isTrusted()`
6. ⚠️ Move internal APIs to `internal` package or deprecate properly
7. ⚠️ Add builder DSL for `IssuanceRequest`

### Medium Priority (Nice to Have)

8. ⚠️ Add algorithm policy to `VerificationOptions`
9. ⚠️ Add trust chain validation support
10. ⚠️ Document thread-safety expectations

### Strengths to Preserve

- ✅ Clean factory pattern
- ✅ Sealed result hierarchies
- ✅ Type-safe identifiers
- ✅ Explicit trust policies
- ✅ W3C VC alignment
- ✅ Convenience overloads

---

## Conclusion

The API is **excellent** and very close to reference quality. The main improvements needed are:

1. **Parameter ordering** - Minor but important for ergonomics
2. **Naming consistency** - One parameter name fix
3. **SPI consistency** - Make error handling consistent
4. **Trust policy ergonomics** - Add vararg and context support

With these changes, the API will be a **reference-quality** Kotlin SDK for Verifiable Credentials and Web-of-Trust systems.

**Estimated effort:** 2-3 days of focused refactoring to achieve reference quality.

