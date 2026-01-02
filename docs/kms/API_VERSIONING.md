# KMS API Versioning Strategy

**Module:** `kms-core`  
**Last Updated:** 2025-01-28

---

## Overview

This document outlines the versioning strategy for the `kms-core` module API, including deprecation policies, breaking change procedures, and migration guidelines.

---

## Versioning Policy

### Semantic Versioning

The KMS API follows [Semantic Versioning (SemVer)](https://semver.org/):
- **MAJOR** version: Breaking changes (incompatible API changes)
- **MINOR** version: New features (backward compatible)
- **PATCH** version: Bug fixes (backward compatible)

### API Stability Levels

1. **Stable API** (`1.x.x`)
   - Public interfaces that are fully supported
   - Backward compatibility guaranteed within major version
   - Deprecation warnings before removal

2. **Experimental API** (marked with `@Experimental` annotation)
   - APIs under active development
   - May change without deprecation
   - Not recommended for production use

3. **Internal API** (in `internal` package)
   - Implementation details
   - No compatibility guarantees
   - Can change at any time

---

## Deprecation Policy

### Deprecation Process

1. **Announcement Period**
   - Deprecated APIs are marked with `@Deprecated` annotation
   - Deprecation notice includes:
     - Reason for deprecation
     - Recommended replacement
     - Removal version (typically 2 major versions later)

2. **Deprecation Period**
   - Minimum 1 major version before removal
   - Deprecated APIs continue to work but emit warnings
   - Documentation clearly indicates deprecation status

3. **Removal**
   - Removed in next major version after deprecation period
   - Migration guide provided

### Example Deprecation

```kotlin
/**
 * @deprecated Use [newMethod] instead. This method will be removed in version 2.0.0.
 * @see newMethod
 */
@Deprecated(
    message = "Use newMethod() instead",
    replaceWith = ReplaceWith("newMethod()"),
    level = DeprecationLevel.WARNING
)
fun oldMethod() {
    // ...
}
```

---

## Breaking Changes

### Definition

A breaking change is any change that:
- Removes or renames public API elements
- Changes method signatures (parameter types, return types)
- Changes behavior in a way that breaks existing code
- Removes or changes sealed class subtypes
- Changes exception types thrown

### Breaking Change Process

1. **Planning**
   - Breaking changes planned for major versions only
   - Discussed in design proposals
   - Migration path designed before implementation

2. **Deprecation First**
   - Old API deprecated in current version
   - New API introduced alongside deprecated API
   - Deprecation period allows migration

3. **Documentation**
   - Migration guide provided
   - Examples updated
   - Release notes clearly document changes

4. **Implementation**
   - Breaking changes implemented in new major version
   - Old API removed
   - Clear error messages for removed APIs

### Breaking Change Examples

#### Interface Changes

**Before (v1.x):**
```kotlin
interface KeyManagementService {
    fun generateKey(algorithm: String): KeyHandle
}
```

**Deprecation (v1.x):**
```kotlin
interface KeyManagementService {
    @Deprecated("Use generateKey(Algorithm) instead", ReplaceWith("generateKey(Algorithm.parse(algorithm))"))
    fun generateKey(algorithm: String): KeyHandle
    
    fun generateKey(algorithm: Algorithm): GenerateKeyResult
}
```

**After (v2.0):**
```kotlin
interface KeyManagementService {
    fun generateKey(algorithm: Algorithm): GenerateKeyResult
    // String overload removed
}
```

---

## Backward Compatibility

### Guarantees

Within a major version (e.g., `1.x.x`), the API guarantees:
- ✅ No breaking changes
- ✅ Additive changes only (new methods, new result subtypes)
- ✅ Bug fixes that don't change behavior
- ✅ Performance improvements

### Additive Changes (Non-Breaking)

These changes are allowed in minor/patch versions:
- Adding new methods to interfaces (with default implementations if needed)
- Adding new sealed class subtypes
- Adding new algorithm types
- Adding optional parameters (with default values)
- Extending result types with new failure subtypes

---

## Migration Guidelines

### For API Consumers

1. **Monitor Deprecations**
   - Regularly check for `@Deprecated` annotations
   - Update code to use recommended replacements
   - Plan migration before major version upgrades

2. **Version Pinning**
   - Pin to specific major version in production
   - Test minor/patch updates before deployment
   - Plan major version upgrades separately

3. **Migration Steps**
   - Read migration guide for target version
   - Update code incrementally
   - Test thoroughly before deploying

### For Plugin Developers

1. **Implement Latest Interface**
   - Always implement the latest stable interface version
   - Handle deprecated methods if supporting older API versions
   - Test against multiple API versions if needed

2. **Algorithm Support**
   - Announce supported algorithms via `supportedAlgorithms` property
   - Handle new algorithm types gracefully
   - Return appropriate error results for unsupported algorithms

---

## API Evolution Examples

### Adding New Methods (Non-Breaking)

**v1.0:**
```kotlin
interface KeyManagementService {
    suspend fun generateKey(algorithm: Algorithm): GenerateKeyResult
}
```

**v1.1 (Additive):**
```kotlin
interface KeyManagementService {
    suspend fun generateKey(algorithm: Algorithm): GenerateKeyResult
    
    // New method - backward compatible
    suspend fun generateKeyWithOptions(
        algorithm: Algorithm,
        options: KeyGenerationOptions
    ): GenerateKeyResult
}
```

### Extending Result Types (Non-Breaking)

**v1.0:**
```kotlin
sealed class GenerateKeyResult {
    data class Success(val keyHandle: KeyHandle) : GenerateKeyResult()
    sealed class Failure : GenerateKeyResult() {
        data class UnsupportedAlgorithm(...) : Failure()
        data class Error(...) : Failure()
    }
}
```

**v1.1 (Additive):**
```kotlin
sealed class GenerateKeyResult {
    data class Success(val keyHandle: KeyHandle) : GenerateKeyResult()
    sealed class Failure : GenerateKeyResult() {
        data class UnsupportedAlgorithm(...) : Failure()
        data class InvalidOptions(...) : Failure() // New subtype
        data class Error(...) : Failure()
    }
}
```

---

## Version History

### Version 1.0.0 (Current)

**Stable APIs:**
- `KeyManagementService` interface
- `KeyManagementServices` factory
- `Algorithm` sealed class
- Result types (`GenerateKeyResult`, `SignResult`, etc.)
- SPI (`KeyManagementServiceProvider`)

**API Guarantees:**
- Interface stability guaranteed
- Result types are extensible (new failure subtypes allowed)
- Algorithm enum can be extended

---

## Best Practices

### For API Designers

1. **Design for Evolution**
   - Use sealed classes for extensibility
   - Prefer result types over exceptions
   - Use sealed interfaces for future extensions
   - Document extensibility points

2. **Minimize Breaking Changes**
   - Deprecate before removing
   - Provide migration paths
   - Use additive changes when possible
   - Consider impact on plugin ecosystem

3. **Clear Documentation**
   - Document stability guarantees
   - Clearly mark experimental APIs
   - Provide migration guides
   - Include examples

### For API Consumers

1. **Stay Current**
   - Regularly update to latest patch versions
   - Plan minor version updates
   - Monitor deprecation warnings

2. **Test Thoroughly**
   - Test after version updates
   - Verify plugin compatibility
   - Check migration guides

3. **Plan Major Upgrades**
   - Review breaking changes
   - Test migration path
   - Update dependencies

---

## Questions or Concerns

If you have questions about API versioning or encounter issues:

1. Check this document first
2. Review migration guides
3. Check GitHub issues
4. Contact maintainers

---

## References

- [Semantic Versioning](https://semver.org/)
- [Kotlin API Design Guidelines](https://kotlinlang.org/docs/coding-conventions.html)
- [TrustWeave Versioning Policy](../architecture/versioning-policy.md) (if exists)

