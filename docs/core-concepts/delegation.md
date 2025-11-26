---
title: Delegation
nav_exclude: true
---

# Delegation

## Overview

**Delegation** allows a DID (delegator) to grant capabilities to another DID (delegate). This enables hierarchical authority structures where DIDs can delegate credential issuance or other capabilities to subordinate DIDs.

Delegation is essential for:
- **Corporate hierarchies**: CEOs delegating to departments
- **Government structures**: Agencies delegating to regional offices
- **Educational institutions**: Universities delegating to departments
- **Service providers**: Main providers delegating to sub-providers

## Key Concepts

### Capability Delegation

**Capability Delegation** is a verification relationship in a DID Document that indicates which verification methods can be used to delegate capabilities to other DIDs. When a DID includes another DID's verification method in its `capabilityDelegation` list, it grants that DID the authority to perform operations on its behalf.

### Capability Invocation

**Capability Invocation** is a verification relationship that indicates which verification methods can be used to invoke capabilities on behalf of the DID. This is typically used for:
- Signing documents
- Performing actions
- Invoking services
- Executing operations

### Delegation Chain

A **delegation chain** is a sequence of DIDs where each DID delegates authority to the next, forming a hierarchical structure. For example:
- CEO → HR Director → HR Manager → HR Assistant

Each link in the chain must be verified to ensure the entire delegation is valid.

## Usage

### Setting Up Delegation

```kotlin
// Step 1: Update delegator DID document to include capability delegation
trustLayer.updateDid {
    did(delegatorDid)
    method(DidMethods.KEY)
    addCapabilityDelegation("$delegateDid#key-1")
}

// Step 2: Verify the delegation was set up correctly
val delegationResult = trustLayer.delegate {
    from(delegatorDid)
    to(delegateDid)
    verify()
}

if (delegationResult.valid) {
    println("Delegation set up successfully")
}
```

### Verifying Delegation Chain

```kotlin
val result = trustLayer.delegate {
    from(delegatorDid)
    to(delegateDid)
    capability("issueCredentials") // Optional: specify capability
    verify()
}

if (result.valid) {
    println("Delegation verified:")
    println("  Path: ${result.path.joinToString(" -> ")}")
} else {
    println("Delegation failed:")
    result.errors.forEach { println("  - $it") }
}
```

### Multi-Hop Delegation

```kotlin
// Set up multi-hop delegation chain
trustLayer.updateDid {
    did(ceoDid)
    method(DidMethods.KEY)
    addCapabilityDelegation("$directorDid#key-1")
}

trustLayer.updateDid {
    did(directorDid)
    method(DidMethods.KEY)
    addCapabilityDelegation("$managerDid#key-1")
}

// Verify the entire chain
val result = trustLayer.delegate {
    capability("issueCredentials")
    verifyChain(listOf(ceoDid, directorDid, managerDid))
}

if (result.valid) {
    println("Full delegation chain verified: ${result.path.joinToString(" -> ")}")
}
```

### Using Delegated Credentials

When a credential is issued by a delegate, the verification can check the delegation chain:

```kotlin
// Issue credential using delegated authority
val credential = trustLayer.issue {
    credential {
        id("https://company.com/credential-123")
        type("EmploymentCredential")
        issuer(delegateDid) // Delegate issues on behalf of delegator
        subject {
            id(employeeDid)
            "employment" {
                "company" to "Tech Corp"
                "role" to "Software Engineer"
            }
        }
        issued(Instant.now())
    }
    by(issuerDid = delegateDid, keyId = "key-1")
}

// Verify credential with delegation check
val result = trustLayer.verify {
    credential(credential)
    verifyDelegation(true) // Enable delegation verification
    checkExpiration(true)
}

if (result.delegationValid) {
    println("Delegation chain is valid")
} else {
    println("Delegation verification failed: ${result.errors}")
}
```

## Verification Relationships

### Capability Delegation

Used when a DID grants authority to another DID. The delegate can then use this authority to perform operations.

**Example**: A CEO delegates credential issuance to an HR Director.

```kotlin
trustLayer.updateDid {
    did(ceoDid)
    addCapabilityDelegation("$hrDirectorDid#key-1")
}
```

### Capability Invocation

Used when a DID invokes capabilities on its own behalf. This is typically used for signing documents or performing actions.

**Example**: An employee signs a document using their capability invocation key.

```kotlin
trustLayer.updateDid {
    did(employeeDid)
    addCapabilityInvocation("$employeeDid#key-1")
}
```

## Example: Corporate Hierarchy

```kotlin
// Step 1: Create DIDs for corporate hierarchy
val ceoDid = trustLayer.createDid {
    method(DidMethods.KEY)
    algorithm(KeyAlgorithms.ED25519)
}

val hrDirectorDid = trustLayer.createDid {
    method(DidMethods.KEY)
    algorithm(KeyAlgorithms.ED25519)
}

val hrManagerDid = trustLayer.createDid {
    method(DidMethods.KEY)
    algorithm(KeyAlgorithms.ED25519)
}

// Step 2: Set up delegation chain
// CEO delegates to HR Director
trustLayer.updateDid {
    did(ceoDid)
    method(DidMethods.KEY)
    addCapabilityDelegation("$hrDirectorDid#key-1")
}

// HR Director delegates to HR Manager
trustLayer.updateDid {
    did(hrDirectorDid)
    method(DidMethods.KEY)
    addCapabilityDelegation("$hrManagerDid#key-1")
}

// Step 3: Verify the full chain
val result = trustLayer.delegate {
    capability("issueCredentials")
    verifyChain(listOf(ceoDid, hrDirectorDid, hrManagerDid))
}

if (result.valid) {
    println("Corporate delegation chain verified:")
    println("  ${result.path.joinToString(" -> ")}")
} else {
    println("Delegation chain verification failed:")
    result.errors.forEach { println("  - $it") }
}

// Step 4: Issue credential using delegated authority
val credential = trustLayer.issue {
    credential {
        id("https://company.com/credential-123")
        type("EmploymentCredential")
        issuer(hrManagerDid) // HR Manager issues on behalf of company
        subject {
            id(employeeDid)
            "employment" {
                "company" to "Tech Corp"
                "role" to "Software Engineer"
                "startDate" to "2024-01-01"
            }
        }
        issued(Instant.now())
    }
    by(issuerDid = hrManagerDid, keyId = "key-1")
}

// Step 5: Verify credential with delegation check
val verification = trustLayer.verify {
    credential(credential)
    verifyDelegation(true)
    checkExpiration(true)
}

println("Credential Verification:")
println("  Valid: ${verification.valid}")
println("  Delegation Valid: ${verification.delegationValid}")
println("  Proof Valid: ${verification.proofValid}")
```

## Advanced Usage

### Removing Delegation

```kotlin
trustLayer.updateDid {
    did(delegatorDid)
    method(DidMethods.KEY)
    removeCapabilityDelegation("$delegateDid#key-1")
}
```

### Multiple Delegates

A delegator can delegate to multiple DIDs:

```kotlin
trustLayer.updateDid {
    did(ceoDid)
    method(DidMethods.KEY)
    addCapabilityDelegation("$hrDirectorDid#key-1")
    addCapabilityDelegation("$financeDirectorDid#key-1")
    addCapabilityDelegation("$itDirectorDid#key-1")
}
```

### Self-Delegation

A DID can delegate to itself (useful for key rotation):

```kotlin
trustLayer.updateDid {
    did(did)
    method(DidMethods.KEY)
    addCapabilityDelegation("$did#key-2") // Delegate to new key
}
```

## Best Practices

1. **Verify Delegation Chains**: Always verify delegation chains when accepting delegated credentials
2. **Limit Delegation Scope**: Only delegate necessary capabilities
3. **Monitor Delegation**: Regularly audit delegation relationships
4. **Document Delegation**: Keep records of why delegations were granted
5. **Revoke When Needed**: Remove delegations when they're no longer needed

## Implementation Details

### Delegation Service

The `DelegationService`:
- Verifies single-hop delegation chains
- Supports multi-hop delegation verification
- Validates capability delegation relationships in DID Documents
- Returns detailed error messages for failed verifications

### Verification Process

1. Resolve delegator DID document
2. Check if `capabilityDelegation` list contains delegate
3. Resolve delegate DID document
4. Verify delegate exists and is valid
5. (Future) Verify delegation credential/proof if present

## Error Handling

Common delegation verification errors:
- **Delegator not found**: Delegator DID cannot be resolved
- **Delegate not found**: Delegate DID cannot be resolved
- **No capability delegation**: Delegator has no `capabilityDelegation` relationships
- **Delegate not in list**: Delegate is not in delegator's `capabilityDelegation` list
- **Broken chain**: One link in multi-hop chain is invalid

## See Also

- [Trust Registry Documentation](trust-registry.md)
- [DID Documentation](dids.md)
- [Delegation Chain Example](../../distribution/TrustWeave-examples/src/main/kotlin/com/geoknoesis/TrustWeave/examples/delegation/DelegationChainExample.kt)
- [Web of Trust Scenario](../scenarios/web-of-trust-scenario.md)
