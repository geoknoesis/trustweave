---
title: Proof Purpose Validation
---

# Proof Purpose Validation

## Overview

**Proof Purpose Validation** ensures that proofs in verifiable credentials are used only for their intended purposes as defined in the DID Document's verification relationships. This is a critical security feature that prevents misuse of verification methods.

## Key Concepts

### Proof Purpose

A **proof purpose** indicates why a proof was created. Common proof purposes include:
- `assertionMethod`: For signing verifiable credentials
- `authentication`: For authenticating the DID controller
- `keyAgreement`: For establishing secure channels
- `capabilityInvocation`: For invoking capabilities
- `capabilityDelegation`: For delegating capabilities

### Verification Relationships

Each proof purpose corresponds to a verification relationship in the DID Document:
- `assertionMethod` → `assertionMethod` relationship
- `authentication` → `authentication` relationship
- `keyAgreement` → `keyAgreement` relationship
- `capabilityInvocation` → `capabilityInvocation` relationship
- `capabilityDelegation` → `capabilityDelegation` relationship

### Verification Method Matching

The proof's `verificationMethod` must be listed in the corresponding verification relationship of the issuer's DID Document.

## Usage

### Basic Validation

```kotlin
val result = trustLayer.verify {
    credential(credential)
    validateProofPurpose(true) // Enable proof purpose validation
}

if (result.proofPurposeValid) {
    println("Proof purpose is valid")
} else {
    println("Proof purpose validation failed: ${result.errors}")
}
```

### Complete Verification Example

```kotlin
val result = trustLayer.verify {
    credential(credential)
    validateProofPurpose(true)
    checkTrustRegistry(true)
    checkExpiration(true)
    verifyDelegation(true)
}

println("Verification Result:")
println("  Valid: ${result.valid}")
println("  Proof Purpose Valid: ${result.proofPurposeValid}")
println("  Proof Valid: ${result.proofValid}")
println("  Trust Registry Valid: ${result.trustRegistryValid}")
println("  Delegation Valid: ${result.delegationValid}")
```

## Proof Purposes

### assertionMethod

Used for signing verifiable credentials. The verification method must be in the issuer's `assertionMethod` list.

```kotlin
// DID Document must have:
{
  "assertionMethod": ["did:key:issuer#key-1"]
}

// Proof must have:
{
  "proofPurpose": "assertionMethod",
  "verificationMethod": "did:key:issuer#key-1"
}
```

### authentication

Used for authenticating the DID controller. The verification method must be in the DID's `authentication` list.

```kotlin
// DID Document must have:
{
  "authentication": ["did:key:user#key-1"]
}

// Proof must have:
{
  "proofPurpose": "authentication",
  "verificationMethod": "did:key:user#key-1"
}
```

### keyAgreement

Used for establishing secure channels. The verification method must be in the DID's `keyAgreement` list.

```kotlin
// DID Document must have:
{
  "keyAgreement": ["did:key:user#key-1"]
}

// Proof must have:
{
  "proofPurpose": "keyAgreement",
  "verificationMethod": "did:key:user#key-1"
}
```

### capabilityInvocation

Used for invoking capabilities on behalf of the DID. The verification method must be in the DID's `capabilityInvocation` list.

```kotlin
// DID Document must have:
{
  "capabilityInvocation": ["did:key:user#key-1"]
}

// Proof must have:
{
  "proofPurpose": "capabilityInvocation",
  "verificationMethod": "did:key:user#key-1"
}
```

### capabilityDelegation

Used for delegating capabilities to other DIDs. The verification method must be in the DID's `capabilityDelegation` list.

```kotlin
// DID Document must have:
{
  "capabilityDelegation": ["did:key:delegator#key-1"]
}

// Proof must have:
{
  "proofPurpose": "capabilityDelegation",
  "verificationMethod": "did:key:delegator#key-1"
}
```

## Verification Method References

### Full DID URLs

```kotlin
// Full DID URL
"did:key:issuer#key-1"
```

### Relative References

```kotlin
// Relative reference (resolved relative to issuer DID)
"#key-1" // Resolves to "did:key:issuer#key-1"
```

### Matching Logic

The validator matches verification methods using:
1. Exact match (full DID URL)
2. Relative reference match (resolved relative to issuer DID)
3. Fragment-only match (e.g., `#key-1` matches `did:key:issuer#key-1`)

## Examples

### Valid Proof Purpose

```kotlin
// DID Document
val issuerDoc = DidDocument(
    id = "did:key:issuer",
    assertionMethod = listOf("did:key:issuer#key-1")
)

// Credential with proof
val credential = VerifiableCredential(
    issuer = "did:key:issuer",
    proof = Proof(
        proofPurpose = "assertionMethod",
        verificationMethod = "did:key:issuer#key-1"
    )
)

// Validation succeeds
val result = validator.validateProofPurpose(
    proofPurpose = "assertionMethod",
    verificationMethod = "did:key:issuer#key-1",
    issuerDid = "did:key:issuer"
)
// result.valid == true
```

### Invalid Proof Purpose

```kotlin
// DID Document
val issuerDoc = DidDocument(
    id = "did:key:issuer",
    assertionMethod = listOf("did:key:issuer#key-1")
    // Note: capabilityInvocation is empty
)

// Credential with proof using wrong purpose
val credential = VerifiableCredential(
    issuer = "did:key:issuer",
    proof = Proof(
        proofPurpose = "capabilityInvocation", // Wrong purpose!
        verificationMethod = "did:key:issuer#key-1"
    )
)

// Validation fails
val result = validator.validateProofPurpose(
    proofPurpose = "capabilityInvocation",
    verificationMethod = "did:key:issuer#key-1",
    issuerDid = "did:key:issuer"
)
// result.valid == false
// result.errors contains "Proof purpose 'capabilityInvocation' does not match verification relationship"
```

## Best Practices

1. **Always Enable Validation**: Enable proof purpose validation in production
2. **Update DID Documents**: Keep DID documents up-to-date with correct verification relationships
3. **Use Correct Purposes**: Use the appropriate proof purpose for each operation
4. **Verify Before Issuing**: Ensure issuer DID document has correct relationships before issuing credentials
5. **Monitor Validation Failures**: Track proof purpose validation failures to identify issues

## Error Messages

Common validation errors:
- **Unknown proof purpose**: Proof purpose is not recognized
- **Verification method not found**: Verification method doesn't exist in DID Document
- **Proof purpose mismatch**: Proof purpose doesn't match verification relationship
- **DID resolution failed**: Cannot resolve issuer DID

## Integration with Credential Verification

Proof purpose validation is automatically performed when enabled in credential verification:

```kotlin
val result = trustLayer.verify {
    credential(credential)
    validateProofPurpose(true) // Enable validation
    checkTrustRegistry(true)
    checkExpiration(true)
}

if (!result.proofPurposeValid) {
    println("Proof purpose validation failed:")
    result.errors.forEach { println("  - $it") }
}
```

## See Also

- [DID Documentation](dids.md)
- [Delegation Documentation](delegation.md)
- [Web of Trust Scenario](../scenarios/web-of-trust-scenario.md)
- [W3C DID Core Specification](https://www.w3.org/TR/did-core/)

