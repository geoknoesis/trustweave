# DID-Core Module Code Review

## Executive Summary

The DID-core module is well-structured but has **API design inconsistencies** that create unnecessary verbosity and deviate from documented design principles. The primary issue is the use of `Did.invoke()` instead of a direct public constructor.

## Critical Issues

### 1. ❌ **Overly Verbose API: `Did.invoke()` Pattern**

**Current Implementation:**
```kotlin
class Did private constructor(value: String) : Iri(...) {
    companion object {
        operator fun invoke(didString: String): Did { ... }
    }
}
```

**Usage Pattern (144 instances across codebase):**
```kotlin
val did = Did.invoke("did:key:z6Mk...")
val doc = DidDocument(id = Did.invoke("did:key:123"))
```

**Problems:**
1. **Verbose**: `Did.invoke("...")` is longer than `Did("...")`
2. **Inconsistent**: Other identifiers (`CredentialId`, `IssuerId`) use direct constructors
3. **Contradicts Documentation**: `docs/advanced/identifier-design.md` explicitly recommends direct constructors
4. **Not Idiomatic**: Kotlin convention is to use constructors directly, not companion object `invoke()`
5. **Overkill**: The private constructor + `invoke()` pattern adds complexity without clear benefit

**Evidence from Documentation:**
> **Key Design Decision**: Use direct constructors instead of factory methods like `parse()`.
> 
> **Rationale**:
> - ✅ **Simpler API**: `Did("did:key:...")` is more concise than `Did.parse("did:key:...")`
> - ✅ **Validation in constructor**: All validation happens in `init` blocks, so constructor is sufficient
> - ✅ **Consistent with Kotlin idioms**: Direct constructors are the standard approach

**Comparison with Other Identifiers:**
```kotlin
// CredentialId - uses direct constructor ✅
class CredentialId(value: String) : Iri(value) { ... }
val credId = CredentialId("https://example.com/cred/123")

// IssuerId - uses direct constructor ✅
class IssuerId(value: String) : Iri(value) { ... }
val issuerId = IssuerId("did:key:...")

// Did - uses invoke() ❌
class Did private constructor(value: String) : Iri(...) { ... }
val did = Did.invoke("did:key:...")  // Verbose!
```

### 2. ⚠️ **Inconsistent API Surface**

The codebase has:
- `Did.invoke()` - 144 usages (verbose)
- `VerificationMethodId.parse()` - reasonable for parsing complex strings
- Extension functions like `String.toDidOrNull()` - good for safe parsing

**Recommendation**: Standardize on direct constructor for `Did`, keep `parse()` for `VerificationMethodId` (since it has complex parsing logic with optional baseDid).

## Recommended Solution

### Make Constructor Public

**Change:**
```kotlin
// Current (overkill)
class Did private constructor(value: String) : Iri(...) {
    companion object {
        operator fun invoke(didString: String): Did { ... }
    }
}

// Recommended (simple, idiomatic)
class Did(value: String) : Iri(value.substringBefore("#")), Comparable<Did> {
    init {
        require(value.startsWith("did:")) {
            "Invalid DID format: '$value'. DIDs must start with 'did:'"
        }
        require(value.split(":").size >= 3) {
            "Invalid DID format: '$value'. Expected format: did:method:identifier"
        }
    }
    // ... rest of implementation
}
```

**Benefits:**
1. ✅ **Simpler**: `Did("did:key:...")` instead of `Did.invoke("did:key:...")`
2. ✅ **Consistent**: Matches `CredentialId`, `IssuerId`, and other identifiers
3. ✅ **Idiomatic**: Follows Kotlin conventions
4. ✅ **Documented**: Aligns with design documentation
5. ✅ **Less code**: Removes companion object `invoke()` method

**Migration Path:**
1. Make constructor public
2. Move validation to `init` block
3. Remove `companion object { invoke() }`
4. Update serializer to use constructor directly
5. Find/replace `Did.invoke(` → `Did(` across codebase (144 instances)

### Keep `VerificationMethodId.parse()` 

**Rationale**: `VerificationMethodId` has complex parsing logic:
- Handles full IDs: `"did:key:...#key-1"`
- Handles relative fragments: `"#key-1"` (requires optional `baseDid`)
- Has multiple parsing strategies

This is a legitimate use case for a `parse()` method.

## Additional Observations

### ✅ **Good Design Decisions**

1. **Type Safety**: Using value classes and sealed classes for identifiers
2. **Inheritance Model**: `Did extends Iri` is semantically correct
3. **Validation**: Proper validation in constructors
4. **Serialization**: Custom serializers handle errors gracefully
5. **Extension Functions**: `String.toDidOrNull()` provides safe parsing

### ⚠️ **Minor Issues**

1. **Inconsistent Error Types**: Some places throw `IllegalArgumentException`, others use `DidException.InvalidDidFormat`
   - **Recommendation**: Standardize on `IllegalArgumentException` for constructor validation, `DidException` for domain errors

2. **Documentation Mismatch**: Code doesn't match documented design
   - **Recommendation**: Update code to match documentation (make constructor public)

3. **Redundant Validation**: `RegistryBasedResolver` validates DID format even though `Did` constructor already validates
   - **Recommendation**: Trust the type system - if you have a `Did`, it's already validated

## Code Examples

### Before (Current - Verbose)
```kotlin
// Creating DIDs
val did = Did.invoke("did:key:z6Mk...")
val doc = DidDocument(id = Did.invoke("did:key:123"))

// In resolvers
val parsed = Did.invoke(didString)

// In tests
val did = Did.invoke("did:web:example.com")
```

### After (Recommended - Clean)
```kotlin
// Creating DIDs
val did = Did("did:key:z6Mk...")
val doc = DidDocument(id = Did("did:key:123"))

// In resolvers
val parsed = Did(didString)

// In tests
val did = Did("did:web:example.com")

// Safe parsing (when needed)
val did = "did:key:...".toDidOrNull()
val did = try { Did("did:key:...") } catch (e: IllegalArgumentException) { null }
```

## Impact Assessment

### Files Affected
- **Main source**: `DidIdentifiers.kt` (1 file)
- **Tests**: All test files using `Did.invoke()` (15+ files, 144 instances)
- **Production code**: ~10 files using `Did.invoke()`
- **Serializers**: `DidSerializer.kt` (already uses `Did.invoke()`, just change to constructor)

### Breaking Changes
- **None**: This is a simplification, not a breaking change
- All existing code continues to work (just needs find/replace)
- API surface becomes simpler, not more complex

### Benefits
1. **Reduced Verbosity**: 144 instances become shorter
2. **Better Consistency**: Matches other identifier classes
3. **Improved Readability**: `Did("...")` is more intuitive than `Did.invoke("...")`
4. **Documentation Alignment**: Code matches documented design
5. **Easier Onboarding**: New developers expect `Did("...")`, not `Did.invoke("...")`

## Recommendation Priority

**🔴 HIGH PRIORITY**: Make `Did` constructor public and remove `invoke()` pattern

This is a simple change with high impact:
- Low risk (no breaking changes)
- High benefit (cleaner API, better consistency)
- Quick to implement (find/replace + validation move)
- Aligns with documented design principles

## Implementation Steps

1. **Update `DidIdentifiers.kt`**:
   - Change `private constructor` → `constructor`
   - Move validation from `invoke()` to `init` block
   - Remove `companion object { invoke() }`

2. **Update `DidSerializer.kt`**:
   - Change `Did.invoke(string)` → `Did(string)`

3. **Update Extension Functions**:
   - Change `Did.invoke(this)` → `Did(this)` in `DidIdentifiersExtensions.kt`

4. **Bulk Replace**:
   - Find: `Did.invoke(`
   - Replace: `Did(`
   - Verify compilation

5. **Update Tests**:
   - All test files will automatically benefit from simpler syntax

6. **Update Documentation**:
   - Ensure examples use `Did("...")` not `Did.invoke("...")`

## Conclusion

The `Did.invoke()` pattern is unnecessarily verbose and contradicts the documented design philosophy. Making the constructor public aligns the implementation with:
- ✅ Kotlin idioms
- ✅ Other identifier classes in the codebase
- ✅ Documented design principles
- ✅ Developer expectations

This is a **low-risk, high-value** refactoring that significantly improves API ergonomics.

