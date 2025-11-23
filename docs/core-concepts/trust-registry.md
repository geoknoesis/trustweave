# Trust Registry

## Overview

A **Trust Registry** is a system for managing trust anchors and discovering trust paths between Decentralized Identifiers (DIDs). It enables verifiers to determine whether an issuer is trusted, either directly or through a chain of trust relationships.

The Trust Registry implements a **Web of Trust** model where trust is established through relationships between DIDs, rather than relying on a single central authority.

## Trust Layer Setup Checklist

The trust registry sits on top of the rest of the issuance/verification stack. Before you plug it in, confirm the supporting primitives below are configured—without them the registry cannot evaluate trust paths.

### DID resolution
**Why it matters:** Trust checks must confirm that issuers resolve to valid DID documents.  
**What to wire:** Instantiate a `CredentialDidResolver` that routes lookups to your registered `DidMethod` implementations. When constructing `CredentialIssuer` or `CredentialVerifier`, pass `resolveDid = { did -> didResolver.resolve(did)?.isResolvable == true }` to satisfy the new constructor signature.  
**Related docs:** [DIDs](dids.md), [Verification Policies](../advanced/verification-policies.md).

### Key management
**Why it matters:** Issuers, holders, and verifiers need signing keys to produce and check proofs along the trust path.  
**What to wire:** Supply a `KeyManagementService` for every participant. For production, back the service with a Hardware Security Module (HSM) or remote Key Management Service (KMS) instead of `InMemoryKeyManagementService`.  
**Related docs:** [Key Management](key-management.md), [Quick Start – Step 4](../getting-started/quick-start.md#step-4-issue-a-credential-and-store-it).

### Proof generators
**Why it matters:** The registry does not create proofs itself, but it relies on credentials and presentations having cryptographic evidence attached.  
**What to wire:** Register an `Ed25519ProofGenerator` (or your preferred suite) in the `ProofGeneratorRegistry`, or inject one into `CredentialIssuer`. TrustWeave's built-in verifier currently performs structural checks; add dedicated signature validation if your policies require it.  
**Related docs:** [Wallet API Reference – CredentialPresentation](../api-reference/wallet-api.md#credentialpresentation).

### Schema and status services
**Why it matters:** Trust decisions often depend on schema validity or revocation state.  
**What to wire:** Register schemas with `SchemaRegistry` and expose status list endpoints before enabling `validateSchema` or `checkRevocation` during verification.  
**Related docs:** [Verification Policies](../advanced/verification-policies.md), scenario guides under `getting-started/`.

### Trust anchors
**Why it matters:** The registry only knows who to trust if you seed it with anchor definitions.  
**What to wire:** Load anchors via `addAnchor` before running verification. Anchors can come from configuration files, REST APIs, or on-chain sources.  
**Related docs:** [Adding Trust Anchors](#adding-trust-anchors), [Trust Paths](#trust-paths).

### Observability
**Why it matters:** Auditors and relying parties will ask *why* a credential was accepted or rejected.  
**What to wire:** Decide how to persist trust evaluations (structured logs, metrics, traces) so you can replay decisions. Consider exporting trust scores and path details to your observability stack.  
**Related docs:** Internal operations guide or your organisation’s logging standards.

## Key Concepts

### Trust Anchors

A **trust anchor** is a DID that is considered trustworthy for issuing specific types of credentials. Trust anchors form the foundation of the web of trust. They can be:
- **Universities** trusted for education credentials
- **Government agencies** trusted for identity credentials
- **Professional organizations** trusted for certification credentials
- **Companies** trusted for employment credentials

### Trust Paths

A **trust path** is a sequence of DIDs that connects a verifier to an issuer through trust relationships. Shorter paths indicate higher trust. The Trust Registry uses Breadth-First Search (BFS) to find the shortest trust path between two DIDs.

### Trust Scores

Trust scores range from 0.0 to 1.0, with higher scores indicating greater trust. Scores are calculated based on path length:
- **Direct trust** (path length 1): 1.0
- **Path length 2**: 0.8
- **Path length 3**: 0.6
- **Path length 4**: 0.4
- **Longer paths**: Decreasing score (minimum 0.1)

## Usage

### Configuring Trust Registry

```kotlin
val trustLayer = trustLayer {
    keys {
        provider("inMemory")
        algorithm(KeyAlgorithms.ED25519)
    }
    
    did {
        method(DidMethods.KEY) {
            algorithm(KeyAlgorithms.ED25519)
        }
    }
    
    credentials {
        defaultProofType(ProofTypes.ED25519)
    }
    
    trust {
        provider("inMemory") // or other provider
    }
}
```

**Outcome:** Creates a trust layer with in-memory providers so you can experiment with anchors and verification logic without external dependencies.

### Adding Trust Anchors

```kotlin
trustLayer.trust {
    // Add university as trusted anchor for education credentials
    addAnchor("did:key:university") {
        credentialTypes("EducationCredential", "DegreeCredential")
        description("Trusted university for academic credentials")
    }
    
    // Add company as trusted anchor for employment credentials
    addAnchor("did:key:company") {
        credentialTypes("EmploymentCredential")
        description("Trusted company for employment credentials")
    }
    
    // Add anchor that trusts all credential types (null credentialTypes)
    addAnchor("did:key:universal-trust") {
        description("Universal trust anchor")
        // credentialTypes is null, so trusts all types
    }
}
```

**Outcome:** Seeds the trust registry with multiple anchors so later verification calls know which issuers to trust.

### Checking Trust

```kotlin
trustLayer.trust {
    // Check if issuer is trusted for specific credential type
    val isTrusted = isTrusted("did:key:university", "EducationCredential")
    if (isTrusted) {
        println("Issuer is trusted for EducationCredential")
    }
    
    // Check if issuer is trusted for any credential type
    val isTrustedAny = isTrusted("did:key:university", null)
    
    // Check trust for different credential type (should fail)
    val notTrusted = isTrusted("did:key:university", "EmploymentCredential")
    // Returns false if university only trusts EducationCredential
}
```

**Outcome:** Demonstrates how to confirm trust decisions at runtime—useful for diagnostics or UI indicators.

### Finding Trust Paths

```kotlin
trustLayer.trust {
    val path = getTrustPath("did:key:verifier", "did:key:issuer")
    if (path != null) {
        println("Trust path found:")
        println("  Path: ${path.path.joinToString(" -> ")}")
        println("  Trust Score: ${path.trustScore}")
        println("  Valid: ${path.valid}")
    } else {
        println("No trust path found")
    }
}
```

**Outcome:** Returns the shortest trust path and score, helping you reason about transitive trust relationships.

### Getting Trusted Issuers

```kotlin
trustLayer.trust {
    // Get all trusted issuers for a specific credential type
    val educationIssuers = getTrustedIssuers("EducationCredential")
    educationIssuers.forEach { println("Trusted education issuer: $it") }
    
    // Get all trusted issuers (any credential type)
    val allIssuers = getTrustedIssuers(null)
    allIssuers.forEach { println("Trusted issuer: $it") }
}
```

**Outcome:** Queries provide a full list of trusted issuers, handy when building dashboards or audits.

### Removing Trust Anchors

```kotlin
trustLayer.trust {
    val removed = removeAnchor("did:key:university")
    if (removed) {
        println("Trust anchor removed")
    } else {
        println("Trust anchor not found")
    }
}
```

**Outcome:** Shows how to revoke trust anchors and confirm removal results.

## Trust Score Calculation

Trust scores are calculated based on path length:
- **Direct trust** (path length 1): 1.0
- **Path length 2**: 0.8
- **Path length 3**: 0.6
- **Path length 4**: 0.4
- **Longer paths**: Decreasing score (minimum 0.1)

The formula ensures that:
- Shorter paths have higher trust scores
- Trust decreases with path length
- Scores always remain between 0.0 and 1.0

## Integration with Credential Verification

Trust registry can be integrated into credential verification:

```kotlin
val result = trustLayer.verify {
    credential(credential)
    checkTrustRegistry(true) // Enable trust registry checking
}

if (result.trustRegistryValid) {
    println("Issuer is trusted")
} else {
    println("Issuer is not trusted: ${result.errors}")
}
```

**Outcome:** Integrates trust checks into credential verification so untrusted issuers are rejected automatically.

### Complete Verification Example

```kotlin
val result = trustLayer.verify {
    credential(credential)
    checkTrustRegistry(true)
    checkExpiration(true)
    validateSchema(true)
    verifyDelegation(true) // Also check delegation if needed
}

println("Verification Result:")
println("  Valid: ${result.valid}")
println("  Trust Registry Valid: ${result.trustRegistryValid}")
println("  Proof Valid: ${result.proofValid}")
println("  Not Expired: ${result.notExpired}")
println("  Schema Valid: ${result.schemaValid}")
```

## Advanced Usage

### Building Trust Networks

```kotlin
// Create a trust network with multiple anchors
trustLayer.trust {
    // Add multiple trust anchors
    addAnchor("did:key:university1") {
        credentialTypes("EducationCredential")
    }
    addAnchor("did:key:university2") {
        credentialTypes("EducationCredential")
    }
    addAnchor("did:key:company1") {
        credentialTypes("EmploymentCredential")
    }
    
    // Get registry to add trust relationships
    val registry = trustLayer.dsl().getTrustRegistry() as? InMemoryTrustRegistry
    registry?.addTrustRelationship("did:key:university1", "did:key:university2")
    
    // Now find trust path between universities
    val path = getTrustPath("did:key:university1", "did:key:university2")
}
```

### Trust Anchor Metadata

```kotlin
trustLayer.trust {
    addAnchor("did:key:issuer") {
        credentialTypes("CredentialType1", "CredentialType2")
        description("Detailed description of the trust anchor")
        addedAt(Instant.now()) // Optional: specify when added
    }
}
```

## Best Practices

1. **Use Credential Type Filtering**: Always specify credential types when adding trust anchors to limit trust scope
2. **Regular Updates**: Keep trust anchors up-to-date as organizations change
3. **Verify Trust Paths**: Check trust paths before accepting credentials from unknown issuers
4. **Monitor Trust Scores**: Use trust scores to make informed decisions about credential acceptance
5. **Document Trust Decisions**: Keep records of why trust anchors were added or removed

## Implementation Details

### In-Memory Trust Registry

The `InMemoryTrustRegistry` implementation:
- Stores trust anchors in memory
- Uses BFS (Breadth-First Search) for path discovery
- Calculates trust scores based on path length
- Supports trust relationships between anchors
- Suitable for testing and small-scale deployments

### Future Implementations

Future implementations may include:
- **Persistent Trust Registry**: Store trust anchors in a database
- **Distributed Trust Registry**: Share trust anchors across networks
- **Blockchain-based Registry**: Anchor trust relationships on blockchain
- **Federated Trust Registry**: Multiple registries working together

## See Also

- [Delegation Documentation](delegation.md)
- [DID Documentation](dids.md)
- [Web of Trust Scenario](../scenarios/web-of-trust-scenario.md)
