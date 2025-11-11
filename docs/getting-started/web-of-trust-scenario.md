# Web of Trust Scenario

This document provides a complete walkthrough of using VeriCore's web of trust features, including trust registries, delegation chains, and proof purpose validation.

## Overview

The web of trust scenario demonstrates how to:
1. Set up trust anchors
2. Issue trusted credentials
3. Verify trust paths
4. Delegate capabilities
5. Verify delegation chains
6. Use proof purpose validation
7. Integrate all features together

## Step-by-Step Walkthrough

### Step 1: Configure Trust Layer with Trust Registry

```kotlin
import com.geoknoesis.vericore.credential.dsl.*
import java.time.Instant
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
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
            provider("inMemory")
        }
    }
}
```

### Step 2: Create DIDs for Entities

```kotlin
val universityDid = trustLayer.createDid {
    method(DidMethods.KEY)
    algorithm(KeyAlgorithms.ED25519)
}

val companyDid = trustLayer.createDid {
    method(DidMethods.KEY)
    algorithm(KeyAlgorithms.ED25519)
}

val studentDid = trustLayer.createDid {
    method(DidMethods.KEY)
    algorithm(KeyAlgorithms.ED25519)
}

val hrDeptDid = trustLayer.createDid {
    method(DidMethods.KEY)
    algorithm(KeyAlgorithms.ED25519)
}
```

### Step 3: Set Up Trust Anchors

```kotlin
trustLayer.trust {
    // Add university as trusted anchor for education credentials
    addAnchor(universityDid) {
        credentialTypes("EducationCredential", "DegreeCredential")
        description("Trusted university for education credentials")
    }
    
    // Add company as trusted anchor for employment credentials
    addAnchor(companyDid) {
        credentialTypes("EmploymentCredential")
        description("Trusted company for employment credentials")
    }
    
    // Verify trust anchors were added
    val isUniversityTrusted = isTrusted(universityDid, "EducationCredential")
    println("University trusted for EducationCredential: $isUniversityTrusted")
}
```

### Step 4: Issue Credentials with Trust Verification

```kotlin
// Issue degree credential from university
val degreeCredential = trustLayer.issue {
    credential {
        id("https://university.edu/credentials/degree-123")
        type("EducationCredential", "DegreeCredential")
        issuer(universityDid)
        subject {
            id(studentDid)
            "degree" {
                "type" to "Bachelor"
                "field" to "Computer Science"
                "university" to "Example University"
            }
        }
        issued(Instant.now())
        expires(Instant.now().plusSeconds(31536000)) // 1 year
    }
    by(issuerDid = universityDid, keyId = "key-1")
}

// Verify with trust registry
val verification = trustLayer.verify {
    credential(degreeCredential)
    checkTrustRegistry(true)
    checkExpiration(true)
}

println("Verification Results:")
println("  Valid: ${verification.valid}")
println("  Trust Registry Valid: ${verification.trustRegistryValid}")
println("  Proof Valid: ${verification.proofValid}")
println("  Not Expired: ${verification.notExpired}")

if (verification.trustRegistryValid) {
    println("✅ Issuer is trusted!")
} else {
    println("❌ Issuer is not trusted")
}
```

### Step 5: Set Up Delegation

```kotlin
// Company delegates credential issuance to HR department
trustLayer.updateDid {
    did(companyDid)
    method(DidMethods.KEY)
    addCapabilityDelegation("$hrDeptDid#key-1")
}

// Verify delegation chain
val delegationResult = trustLayer.delegation {
    verifyChain(delegatorDid = companyDid, delegateDid = hrDeptDid)
}

if (delegationResult.valid) {
    println("✅ Delegation verified:")
    println("   Path: ${delegationResult.path.joinToString(" -> ")}")
} else {
    println("❌ Delegation failed:")
    delegationResult.errors.forEach { println("   - $it") }
}
```

### Step 6: Issue Credential Using Delegated Authority

```kotlin
// HR department issues credential using delegated authority
val employmentCredential = trustLayer.issue {
    credential {
        id("https://company.com/credentials/employment-456")
        type("EmploymentCredential")
        issuer(hrDeptDid) // HR issues on behalf of company
        subject {
            id(studentDid)
            "employment" {
                "company" to "Tech Corp"
                "role" to "Software Engineer"
                "startDate" to "2024-01-01"
            }
        }
        issued(Instant.now())
    }
    by(issuerDid = hrDeptDid, keyId = "key-1")
}

// Verify credential with delegation check
val employmentVerification = trustLayer.verify {
    credential(employmentCredential)
    checkTrustRegistry(true)
    verifyDelegation(true)
    checkExpiration(true)
}

println("Employment Credential Verification:")
println("  Valid: ${employmentVerification.valid}")
println("  Trust Registry Valid: ${employmentVerification.trustRegistryValid}")
println("  Delegation Valid: ${employmentVerification.delegationValid}")
```

### Step 7: Find Trust Paths

```kotlin
trustLayer.trust {
    // Get all trusted issuers for a credential type
    val educationIssuers = getTrustedIssuers("EducationCredential")
    println("Trusted education issuers: ${educationIssuers.joinToString(", ")}")
    
    // Find trust path between two DIDs (if trust relationships exist)
    val trustPath = getTrustPath(universityDid, companyDid)
    if (trustPath != null) {
        println("Trust path found:")
        println("  Path: ${trustPath.path.joinToString(" -> ")}")
        println("  Trust Score: ${trustPath.trustScore}")
        println("  Valid: ${trustPath.valid}")
    } else {
        println("No trust path found between university and company")
    }
}
```

### Step 8: Update DID Documents with New Fields

```kotlin
trustLayer.updateDid {
    did(studentDid)
    method(DidMethods.KEY)
    
    // Add capability invocation for signing documents
    addCapabilityInvocation("$studentDid#key-1")
    
    // Add capability delegation for delegating to assistants
    addCapabilityDelegation("$studentDid#key-2")
    
    // Set JSON-LD context
    context("https://www.w3.org/ns/did/v1", "https://example.com/context/v1")
}

println("✅ DID document updated with capability relationships and context")
```

### Step 9: Use Proof Purpose Validation

```kotlin
// Update issuer DID to have assertionMethod relationship
trustLayer.updateDid {
    did(universityDid)
    method(DidMethods.KEY)
    addAssertionMethod("$universityDid#key-1")
}

// Issue credential with assertionMethod proof purpose
val validatedCredential = trustLayer.issue {
    credential {
        id("https://university.edu/credentials/validated-789")
        type("EducationCredential")
        issuer(universityDid)
        subject {
            id(studentDid)
            "certification" {
                "name" to "Certified Developer"
                "level" to "Advanced"
            }
        }
        issued(Instant.now())
    }
    by(issuerDid = universityDid, keyId = "key-1")
    proofPurpose(ProofPurposes.ASSERTION_METHOD)
}

// Verify with proof purpose validation
val proofPurposeVerification = trustLayer.verify {
    credential(validatedCredential)
    validateProofPurpose(true)
    checkTrustRegistry(true)
}

println("Proof Purpose Validation:")
println("  Valid: ${proofPurposeVerification.valid}")
println("  Proof Purpose Valid: ${proofPurposeVerification.proofPurposeValid}")
println("  Trust Registry Valid: ${proofPurposeVerification.trustRegistryValid}")
```

### Step 10: Complete Integration Example

```kotlin
// Complete workflow combining all features
fun completeWebOfTrustWorkflow() = runBlocking {
    val trustLayer = trustLayer {
        keys { provider("inMemory") }
        did { method(DidMethods.KEY) }
        trust { provider("inMemory") }
    }
    
    // 1. Create DIDs
    val issuerDid = trustLayer.createDid { method(DidMethods.KEY) }
    val holderDid = trustLayer.createDid { method(DidMethods.KEY) }
    
    // 2. Set up trust anchor
    trustLayer.trust {
        addAnchor(issuerDid) {
            credentialTypes("TestCredential")
        }
    }
    
    // 3. Update issuer DID with assertionMethod
    trustLayer.updateDid {
        did(issuerDid)
        method(DidMethods.KEY)
        addAssertionMethod("$issuerDid#key-1")
    }
    
    // 4. Issue credential
    val credential = trustLayer.issue {
        credential {
            id("https://example.com/credential-1")
            type("TestCredential")
            issuer(issuerDid)
            subject {
                id(holderDid)
                "test" to "value"
            }
            issued(Instant.now())
        }
        by(issuerDid = issuerDid, keyId = "key-1")
        proofPurpose(ProofPurposes.ASSERTION_METHOD)
    }
    
    // 5. Verify with all checks enabled
    val result = trustLayer.verify {
        credential(credential)
        checkTrustRegistry(true)
        validateProofPurpose(true)
        checkExpiration(true)
    }
    
    // 6. Check results
    println("Complete Verification Results:")
    println("  Valid: ${result.valid}")
    println("  Trust Registry Valid: ${result.trustRegistryValid}")
    println("  Proof Purpose Valid: ${result.proofPurposeValid}")
    println("  Proof Valid: ${result.proofValid}")
    println("  Not Expired: ${result.notExpired}")
    
    if (result.valid && result.trustRegistryValid && result.proofPurposeValid) {
        println("✅ Credential verified successfully with all checks!")
    }
}
```

## Real-World Use Cases

### University Credential Verification

Universities can be added as trust anchors, allowing verifiers to automatically trust credentials issued by recognized institutions.

```kotlin
trustLayer.trust {
    addAnchor(universityDid) {
        credentialTypes("EducationCredential", "DegreeCredential", "CertificateCredential")
        description("Accredited university")
    }
}
```

### Corporate Delegation

Companies can delegate credential issuance to HR departments, creating a hierarchical authority structure while maintaining centralized trust.

```kotlin
// CEO delegates to HR Director
trustLayer.updateDid {
    did(ceoDid)
    addCapabilityDelegation("$hrDirectorDid#key-1")
}

// HR Director delegates to HR Manager
trustLayer.updateDid {
    did(hrDirectorDid)
    addCapabilityDelegation("$hrManagerDid#key-1")
}
```

### Multi-Party Trust Networks

Multiple organizations can form trust networks where credentials issued by one trusted party are automatically trusted by others in the network.

```kotlin
// Create trust network
trustLayer.trust {
    addAnchor(organization1Did) {
        credentialTypes("PartnershipCredential")
    }
    addAnchor(organization2Did) {
        credentialTypes("PartnershipCredential")
    }
    addAnchor(organization3Did) {
        credentialTypes("PartnershipCredential")
    }
    
    // Get all trusted partners
    val partners = getTrustedIssuers("PartnershipCredential")
    println("Trusted partners: ${partners.joinToString(", ")}")
}
```

## Best Practices

1. **Use Credential Type Filtering**: Specify credential types when adding trust anchors to limit trust scope
2. **Verify Delegation Chains**: Always verify delegation chains when accepting delegated credentials
3. **Check Trust Paths**: Verify trust paths before accepting credentials from unknown issuers
4. **Update DID Documents**: Keep DID documents up-to-date with capability relationships
5. **Use Proof Purpose Validation**: Enable proof purpose validation to ensure proofs are used correctly
6. **Monitor Trust Scores**: Use trust scores to make informed decisions about credential acceptance
7. **Regular Audits**: Periodically review and update trust anchors and delegation relationships

## Error Handling

```kotlin
try {
    val result = trustLayer.verify {
        credential(credential)
        checkTrustRegistry(true)
        validateProofPurpose(true)
        verifyDelegation(true)
    }
    
    if (!result.valid) {
        println("Verification failed:")
        result.errors.forEach { println("  - $it") }
        result.warnings.forEach { println("  Warning: $it") }
    }
} catch (e: Exception) {
    println("Verification error: ${e.message}")
}
```

## See Also

- [Trust Registry Documentation](../core-concepts/trust-registry.md)
- [Delegation Documentation](../core-concepts/delegation.md)
- [Proof Purpose Validation Documentation](../core-concepts/proof-purpose-validation.md)
- [DID Documentation](../core-concepts/dids.md)
- [Web of Trust Example](../../vericore-examples/src/main/kotlin/io/geoknoesis/vericore/examples/trust/WebOfTrustExample.kt)
- [Delegation Chain Example](../../vericore-examples/src/main/kotlin/io/geoknoesis/vericore/examples/delegation/DelegationChainExample.kt)

