# Phase 3 Implementation Summary: Rename Generic Types

**Date:** 2024-12-04  
**Phase:** Phase 3 - Rename Generic Types  
**Status:** ✅ Completed

## Overview

Made `ProviderChain` and `PluginRegistry` internal infrastructure components. This addresses the concern from the SDK review that generic names hide domain intent. Domain-specific registries already exist and should be used instead.

**⚠️ Breaking Changes:** This phase introduces breaking changes as generic types are now internal and domain-specific classes must be used instead.

## Changes Implemented

### 1. Made ProviderChain Internal

**File:** `common/src/main/kotlin/com/trustweave/core/plugin/ProviderChain.kt`

**Changes:**
- Made `ProviderChain` class `internal` - no longer part of public API
- Made `createProviderChain()` function `internal`
- Made `createProviderChainFromConfig()` function `internal`
- Updated documentation to indicate internal API status

**Breaking Change:** External code using `ProviderChain` directly will no longer compile. The internal `ProviderChain` can still be used by framework code that needs failover functionality.

### 2. Made PluginRegistry Extension Internal

**File:** `common/src/main/kotlin/com/trustweave/core/plugin/PluginRegistry.kt`

- Made `PluginRegistry.getInstance()` extension function `internal`

**Breaking Change:** External code using this extension will no longer compile. Use domain-specific registries instead.

**Note:** Domain-specific chain classes were initially created but removed due to module dependency constraints. Since `ProviderChain` is now internal (not part of public API), domain-specific chain wrappers are not needed. The internal `ProviderChain` can still be used internally by framework code that has access to domain types.

### 3. Made PluginRegistry Internal

**File:** `common/src/main/kotlin/com/trustweave/core/plugin/PluginRegistry.kt`

**Changes:**
- Made `PluginRegistry` interface `internal`
- Made `DefaultPluginRegistry` class `internal`
- Updated documentation to indicate internal API status

**Breaking Change:** External code using `PluginRegistry` directly will no longer compile. Domain-specific registries already exist and should be used instead:
- `DidMethodRegistry` for DID methods
- `BlockchainAnchorRegistry` for blockchain anchors
- `TrustRegistry` for trust anchors
- `CredentialServiceRegistry` for credential services

**Key Documentation Addition:**
```kotlin
/**
 * Note on Domain-Specific Naming:
 * This is a generic infrastructure registry that handles all plugin types.
 * TrustWeave also provides domain-specific registries with clearer semantics:
 * - DID operations: `DidMethodRegistry` for DID method registration
 * - Credential operations: Domain-specific credential service registries
 * - Trust operations: `TrustRegistry` for trust anchor management
 * - Blockchain anchors: `BlockchainAnchorRegistry` for anchor client registration
 *
 * When to Use PluginRegistry:
 * Use this interface for low-level plugin management, testing, or when you need
 * to register plugins across multiple domains. For domain-specific operations,
 * prefer the domain-specific registries mentioned above.
 */
```

## Rationale

### Why Make Generic Types Internal?

Both `ProviderChain` and `PluginRegistry` are intentionally generic infrastructure components that:

1. **Support Multiple Domains**: They work with any provider/plugin type, making them reusable across DID, credential, KMS, and other domains
2. **Enable Failover**: `ProviderChain` provides failover functionality that is domain-agnostic
3. **Support Testing**: Generic types enable dependency injection and test isolation
4. **Follow Single Responsibility**: They handle infrastructure concerns (registration, discovery, failover) not domain logic

However, generic names hide domain intent. Making them internal:

1. **Removes Generic Types from Public API** - Public API only exposes domain-specific registries with clear names
2. **Maintains Infrastructure** - Internal generic types still provide reusable infrastructure for framework internals
3. **Better Developer Experience** - Developers use domain-specific registries that make purpose explicit
4. **Follows Best Practices** - Aligns with SDK review recommendation to avoid generic names in public API

### Domain-Specific Registries Already Exist

TrustWeave already provides domain-specific registries with clearer semantics:

- **`DidMethodRegistry`** - For DID method registration and resolution
- **`BlockchainAnchorRegistry`** - For blockchain anchor client registration
- **`CredentialServiceRegistry`** - For credential service registration
- **`ProofGeneratorRegistry`** - For proof generator registration
- **`TrustRegistry`** - For trust anchor management

These registries are the preferred way to work with domain-specific operations.

## Benefits

1. **Clearer Intent**: Generic types are now internal - domain-specific registries have clearer, domain-precise names
2. **Better Developer Experience**: Domain-specific registries make purpose explicit (e.g., `DidMethodRegistry` vs generic `PluginRegistry`)
3. **Improved API Surface**: Public API no longer exposes generic infrastructure types
4. **Maintains Infrastructure**: Internal generic types still provide reusable infrastructure for framework internals
5. **Follows Best Practices**: Aligns with recommendation from SDK review to use domain-precise names

## Domain-Specific Registries Reference

### DID Operations
- **`DidMethodRegistry`** - Register and resolve DID methods
- **`DidResolver`** - Functional interface for DID resolution

### Credential Operations
- **`CredentialServiceRegistry`** - Register credential services
- **`CredentialIssuer`** - Domain-specific credential issuer
- **`CredentialVerifier`** - Domain-specific credential verifier

### Trust Operations
- **`TrustRegistry`** - Manage trust anchors and trust paths

### Blockchain Operations
- **`BlockchainAnchorRegistry`** - Register blockchain anchor clients

### Key Management
- **KMS Services** - Domain-specific key management services and factories

## Usage Guidelines

### Use Generic Types When:
- Building infrastructure-level plugin management
- Creating test utilities that work across domains
- Implementing failover across providers of any type
- Working with low-level plugin registration

### Use Domain-Specific Registries When:
- Working with DID methods → Use `DidMethodRegistry`
- Working with credentials → Use `CredentialServiceRegistry`, `CredentialIssuer`
- Working with trust anchors → Use `TrustRegistry`
- Working with blockchain anchors → Use `BlockchainAnchorRegistry`

## Files Modified

1. `common/src/main/kotlin/com/trustweave/core/plugin/ProviderChain.kt`
   - Made class `internal`
   - Made factory functions `internal`
   - Updated documentation

2. `common/src/main/kotlin/com/trustweave/core/plugin/PluginRegistry.kt`
   - Made interface `internal`
   - Made implementation `internal`
   - Updated documentation

## Files Created

None - Chain classes were initially created but removed due to module dependency constraints. Since `ProviderChain` is now internal, domain-specific wrappers are not needed in the public API.

## Migration Guide

### Before (Generic Types)

```kotlin
// Generic ProviderChain - no longer available
val chain = ProviderChain(listOf(issuer1, issuer2))
val credential = chain.execute { issuer ->
    issuer.issue(credential, issuerDid, keyId, options)
}

// Generic PluginRegistry - no longer available
val registry: PluginRegistry = DefaultPluginRegistry()
```

### After (Internal Infrastructure)

```kotlin
// Generic types are now internal - use domain-specific registries instead
// Domain-specific registries (already exist)
val didRegistry = DidMethodRegistry()
val trustRegistry = TrustRegistry()
val blockchainRegistry = BlockchainAnchorRegistry()
```

**Note:** If you need chain/failover functionality, implement it using domain-specific registries or create domain-specific chain wrappers in modules that have access to both `common` (for internal `ProviderChain`) and domain types.

## Testing

All changes compile successfully. Generic types are now internal and can only be used by framework code. Domain-specific registries remain the public API.

## Next Steps (Phase 4)

The next phase will focus on simplifying the API surface:
- Remove redundant overloads
- Consolidate service layer
- Streamline public API

## Related Documentation

- [SDK Comprehensive Review](../TRUSTWEAVE_SDK_COMPREHENSIVE_REVIEW.md) - Original review document
- [Phase 2 Summary](./PHASE_2_IMPLEMENTATION_SUMMARY.md) - Previous phase implementation

