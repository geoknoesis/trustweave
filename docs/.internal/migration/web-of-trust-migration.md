---
title: Web of Trust Migration Guide
---

# Web of Trust Migration Guide

This guide helps you migrate existing TrustWeave code to use the new web of trust features and updated DID Document structure.

## Breaking Changes

### DidResolutionResult.documentMetadata

**Before:**
```kotlin
val didRegistry = DidMethodRegistry()
// register methods during setup
val result = didRegistry.resolve("did:key:...")
val created = result.documentMetadata["created"] as? String
```

**After:**
```kotlin
val didRegistry = DidMethodRegistry()
val result = didRegistry.resolve("did:key:...")
val created = result.documentMetadata.created // Returns Instant?
```

The `documentMetadata` field has changed from `Map<String, Any?>` to `DidDocumentMetadata` with structured fields:
- `created: Instant?`
- `updated: Instant?`
- `versionId: String?`
- `nextUpdate: Instant?`
- `canonicalId: String?`
- `equivalentId: List<String>`

### Migration Steps

1. **Update code accessing documentMetadata:**
   ```kotlin
   // Old
   val createdStr = result.documentMetadata["created"] as? String
   val created = createdStr?.let { Instant.parse(it) }

   // New
   val created = result.documentMetadata.created
   ```

2. **Update test code:**
   ```kotlin
   // Old
   DidResolutionResult(
       document = doc,
       documentMetadata = mapOf("created" to "2024-01-01T00:00:00Z")
   )

   // New
   DidResolutionResult(
       document = doc,
       documentMetadata = DidDocumentMetadata(
           created = Instant.parse("2024-01-01T00:00:00Z")
       )
   )
   ```

## New Features

### DID Document Extensions

DID Documents now include new W3C DID Core fields:
- `context: List<String>` - JSON-LD contexts
- `capabilityInvocation: List<String>` - Capability invocation relationships
- `capabilityDelegation: List<String>` - Capability delegation relationships

These fields have default values, so existing code continues to work.

### Trust Registry

New trust registry functionality for managing trust anchors:

```kotlin
trustLayer.trust {
    addAnchor("did:key:issuer") {
        credentialTypes("EducationCredential")
    }

    val isTrusted = isTrusted("did:key:issuer", "EducationCredential")
}
```

### Delegation

New delegation chain verification:

```kotlin
val result = trustLayer.delegate {
    from(delegatorDid)
    to(delegateDid)
    verify()
}
```

### Proof Purpose Validation

New proof purpose validation in credential verification:

```kotlin
val result = trustLayer.verify {
    credential(credential)
    validateProofPurpose(true)
}
```

## Deprecated APIs

No APIs have been deprecated. All new features are additive and backward compatible.

## Migration Checklist

- [ ] Update code accessing `documentMetadata` to use structured type
- [ ] Update test code to use `DidDocumentMetadata`
- [ ] Add trust registry configuration if using trust verification
- [ ] Update DID document creation to include new fields if needed
- [ ] Enable proof purpose validation if needed
- [ ] Update credential verification to use new options

## Examples

See the following examples for migration patterns:
- [Web of Trust Example](../../distribution/TrustWeave-examples/src/main/kotlin/com/geoknoesis/TrustWeave/examples/trust/WebOfTrustExample.kt)
- [Delegation Chain Example](../../distribution/TrustWeave-examples/src/main/kotlin/com/geoknoesis/TrustWeave/examples/delegation/DelegationChainExample.kt)

