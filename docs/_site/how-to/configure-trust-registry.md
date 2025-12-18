# How to Configure Trust Registry

## Purpose

This guide shows you how to configure and use TrustWeave's trust registry to manage trust anchors and establish trust relationships. You'll learn how to add trusted issuers, check trust status, discover trust paths, and build a web of trust.

**What you'll accomplish:**
- Configure trust registry in TrustWeave
- Add trust anchors for trusted issuers
- Check if issuers are trusted for specific credential types
- Discover trust paths between verifiers and issuers
- Manage trust relationships dynamically

**Why this matters:**
Trust registries enable verifiers to determine which issuers they trust without requiring direct relationships. This is essential for decentralized identity systems where trust must be established across organizational boundaries.

---

## Prerequisites

- **Kotlin**: 2.2.0 or higher
- **Java**: 21 or higher
- **TrustWeave SDK**: Latest version
- **Dependencies**: `distribution-all` with trust registry support

**Required imports:**
```kotlin
import com.trustweave.trust.TrustWeave
import com.trustweave.trust.types.VerificationResult
import kotlinx.coroutines.runBlocking
```

**Configuration needed:**
- Trust registry provider (e.g., `inMemory` for testing)

---

## Before You Begin

A trust registry maintains a graph of trusted DIDs (trust anchors) and allows verification of whether an issuer is trusted, either directly or through a trust path. TrustWeave provides a fluent DSL for managing trust relationships.

**When to use this:**
- Establishing which issuers are trusted for credential verification
- Building web-of-trust systems
- Managing trust relationships in decentralized identity systems
- Implementing trust policies for credential acceptance

**How it fits in a workflow:**
```kotlin
// 1. Configure trust registry
val trustWeave = TrustWeave.build {
    trust { provider("inMemory") }
}

// 2. Add trust anchors
trustWeave.trust {
    addAnchor("did:key:university") {
        credentialTypes("EducationCredential")
    }
}

// 3. Check trust during verification
val result = trustWeave.verify {
    credential(credential)
    checkTrust(true)  // Verify issuer is trusted
}
```

---

## Step-by-Step Guide

### Step 1: Configure Trust Registry

Enable trust registry in your TrustWeave configuration:

```kotlin
val trustWeave = TrustWeave.build {
    keys {
        provider("inMemory")
        algorithm("Ed25519")
    }
    
    did {
        method("key") {
            algorithm("Ed25519")
        }
    }
    
    trust {
        provider("inMemory")  // For testing; use persistent provider in production
    }
}
```

**What this does:**
- Configures a trust registry for managing trust anchors
- Enables trust checking during credential verification
- Sets up the infrastructure for trust path discovery

> **Note:** For production, use a persistent provider (database-backed) instead of `inMemory`.

---

### Step 2: Add Trust Anchors

Add trusted issuers to the registry:

```kotlin
trustWeave.trust {
    // Add university as trusted anchor for education credentials
    addAnchor("did:key:university") {
        credentialTypes("EducationCredential", "DegreeCredential")
        description("Trusted university for academic credentials")
    }
    
    // Add company as trusted anchor for employment credentials
    addAnchor("did:key:company") {
        credentialTypes("EmploymentCredential")
        description("Trusted company for employment verification")
    }
    
    println("✅ Trust anchors added")
}
```

**What this does:**
- Registers a DID as a trust anchor
- Specifies which credential types the anchor is trusted for
- Adds metadata (description, etc.) for documentation
- Makes the issuer trusted for credential verification

**Key concepts:**
- **Trust Anchor**: A DID that is directly trusted by the verifier
- **Credential Types**: Specific credential types the anchor is trusted for (null = all types)
- **Trust Path**: Sequence of trust relationships connecting verifier to issuer

---

### Step 3: Check Trust Status

Verify if an issuer is trusted:

```kotlin
trustWeave.trust {
    val isTrusted = isTrusted("did:key:university", "EducationCredential")
    
    if (isTrusted) {
        println("✅ Issuer is trusted for EducationCredential")
    } else {
        println("❌ Issuer is not trusted")
    }
}
```

**What this does:**
- Checks if the issuer DID is a trust anchor
- Verifies the issuer is trusted for the specified credential type
- Returns `true` if trusted, `false` otherwise

---

### Step 4: Use Trust in Verification

Enable trust checking during credential verification:

```kotlin
val result = trustWeave.verify {
    credential(credential)
    checkTrust(true)  // Verify issuer is trusted
}

when (result) {
    is VerificationResult.Valid -> {
        println("✅ Credential is valid and issuer is trusted")
    }
    is VerificationResult.Invalid.UntrustedIssuer -> {
        println("❌ Credential issuer is not trusted: ${result.issuer}")
    }
    else -> {
        println("❌ Credential verification failed")
    }
}
```

**What this does:**
- Verifies the credential's cryptographic proof
- Checks if the issuer is in the trust registry
- Returns `UntrustedIssuer` if issuer is not trusted
- Only returns `Valid` if both proof and trust checks pass

---

## Complete Example

Here's a complete, runnable example:

```kotlin
import com.trustweave.trust.TrustWeave
import com.trustweave.trust.types.VerificationResult
import com.trustweave.did.resolver.DidResolutionResult
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.temporal.ChronoUnit

fun main() = runBlocking {
    // Step 1: Configure TrustWeave with trust registry
    val trustWeave = TrustWeave.build {
        keys {
            provider("inMemory")
            algorithm("Ed25519")
        }
        
        did {
            method("key") {
                algorithm("Ed25519")
            }
        }
        
        trust {
            provider("inMemory")
        }
    }
    
    // Step 2: Create DIDs
    val universityDid = trustWeave.createDid { method("key") }
    val studentDid = trustWeave.createDid { method("key") }
    
    // Step 3: Get key ID
    val resolutionResult = trustWeave.resolveDid(universityDid)
    val issuerDocument = when (resolutionResult) {
        is DidResolutionResult.Success -> resolutionResult.document
        else -> throw IllegalStateException("Failed to resolve issuer DID")
    }
    val keyId = issuerDocument.verificationMethod.firstOrNull()?.id?.substringAfter("#")
        ?: throw IllegalStateException("No verification method found")
    
    // Step 4: Add university as trust anchor
    trustWeave.trust {
        addAnchor(universityDid.value) {
            credentialTypes("EducationCredential", "DegreeCredential")
            description("Trusted university for academic credentials")
        }
        println("✅ Added university as trust anchor")
    }
    
    // Step 5: Issue credential
    val credential = trustWeave.issue {
        credential {
            id("https://example.edu/credentials/degree-123")
            type("DegreeCredential")
            issuer(universityDid.value)
            subject {
                id(studentDid.value)
                "degree" {
                    "name" to "Bachelor of Science"
                }
            }
            issued(Instant.now())
        }
        signedBy(issuerDid = universityDid.value, keyId = keyId)
    }
    
    // Step 6: Verify with trust checking
    val result = trustWeave.verify {
        credential(credential)
        checkTrust(true)  // Verify issuer is trusted
    }
    
    when (result) {
        is VerificationResult.Valid -> {
            println("✅ Credential is valid and issuer is trusted")
        }
        is VerificationResult.Invalid.UntrustedIssuer -> {
            println("❌ Issuer is not trusted: ${result.issuer}")
        }
        else -> {
            println("❌ Verification failed: ${result}")
        }
    }
    
    // Step 7: Check trust status directly
    trustWeave.trust {
        val isTrusted = isTrusted(universityDid.value, "DegreeCredential")
        println("University trusted for DegreeCredential: $isTrusted")
    }
}
```

---

## Visual Flow Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                  Trust Registry Configuration                │
└─────────────────────────────────────────────────────────────┘

┌──────────────┐
│   Step 1     │  Configure Trust Registry
│  Configure   │  • Provider (inMemory/database)
└──────┬───────┘  • Enable trust checking
       │
       ▼
┌──────────────┐
│   Step 2     │  Add Trust Anchors
│ Add Anchors  │  • Issuer DID
└──────┬───────┘  • Credential types
       │          • Metadata
       ▼
┌──────────────┐
│   Step 3     │  Check Trust Status
│ Check Trust  │  • isTrusted(issuerDid, type)
└──────┬───────┘  • Returns true/false
       │
       ▼
┌──────────────┐
│   Step 4     │  Verify with Trust
│  Verify VC   │  • checkTrust(true)
└──────┬───────┘  • Valid if trusted
       │          • UntrustedIssuer if not
       ▼
    ✅ Valid
    or
    ❌ Untrusted
```

---

## Verification Step

After configuring trust registry, verify it works:

```kotlin
// Quick trust check
trustWeave.trust {
    val isTrusted = isTrusted("did:key:university", "EducationCredential")
    println("Trust status: $isTrusted")
}

// Verify credential with trust
val result = trustWeave.verify {
    credential(credential)
    checkTrust(true)
}

val isValidAndTrusted = result is VerificationResult.Valid
println("Credential is ${if (isValidAndTrusted) "valid and trusted" else "invalid or untrusted"}")
```

**Expected output:**
```
✅ Added university as trust anchor
✅ Credential is valid and issuer is trusted
University trusted for DegreeCredential: true
Credential is valid and trusted
```

**What to check:**
- ✅ Trust anchor was added successfully
- ✅ `isTrusted()` returns `true` for trusted issuers
- ✅ Verification with `checkTrust(true)` succeeds for trusted issuers
- ✅ Verification fails with `UntrustedIssuer` for untrusted issuers

---

## Common Errors & Troubleshooting

### Error: "Trust registry is not configured"

**Problem:** Trust provider wasn't configured in TrustWeave.

**Solution:**
```kotlin
// ✅ Ensure trust registry is configured
val trustWeave = TrustWeave.build {
    trust {
        provider("inMemory")  // or your provider
    }
}
```

---

### Error: Issuer not trusted during verification

**Problem:** The issuer wasn't added as a trust anchor, or credential type doesn't match.

**Solution:**
```kotlin
// ✅ Add issuer as trust anchor
trustWeave.trust {
    addAnchor(issuerDid) {
        credentialTypes("EducationCredential")  // Match credential type
    }
}

// ✅ Or trust for all types
trustWeave.trust {
    addAnchor(issuerDid) {
        // No credentialTypes = trusted for all types
    }
}
```

---

### Error: Trust path not found

**Problem:** No trust path exists between verifier and issuer.

**Solution:**
```kotlin
// ✅ Check if direct trust exists
trustWeave.trust {
    val isDirectlyTrusted = isTrusted(issuerDid, credentialType)
    
    if (!isDirectlyTrusted) {
        // Try to find trust path
        val path = getTrustPath(verifierDid, issuerDid)
        if (path != null) {
            println("Trust path found: ${path.path}")
        } else {
            println("No trust path found - add trust anchor")
        }
    }
}
```

---

## Advanced Patterns

### Pattern 1: Trust Path Discovery

Find trust paths between verifiers and issuers:

```kotlin
trustWeave.trust {
    val path = getTrustPath("did:key:verifier", "did:key:issuer")
    
    when (path) {
        is TrustPathResult.Found -> {
            println("✅ Trust path found:")
            println("   Path: ${path.path.joinToString(" → ")}")
            println("   Length: ${path.path.size}")
            println("   Score: ${path.score}")
        }
        is TrustPathResult.NotFound -> {
            println("❌ No trust path found")
        }
        else -> {
            println("⚠️ Trust path discovery failed")
        }
    }
}
```

---

### Pattern 2: Conditional Trust

Trust issuers conditionally based on credential type:

```kotlin
fun isTrustedForType(issuerDid: String, credentialType: String): Boolean {
    return trustWeave.getDslContext().getTrustRegistry()?.let { registry ->
        kotlinx.coroutines.runBlocking {
            registry.isTrustedIssuer(issuerDid, credentialType)
        }
    } ?: false
}

// Use in verification
val result = trustWeave.verify {
    credential(credential)
    checkTrust(isTrustedForType(credential.issuer, "EducationCredential"))
}
```

---

### Pattern 3: Dynamic Trust Management

Add and remove trust anchors dynamically:

```kotlin
// Add trust anchor
trustWeave.trust {
    val added = addAnchor("did:key:new-issuer") {
        credentialTypes("NewCredentialType")
    }
    println("Trust anchor added: $added")
}

// Remove trust anchor
trustWeave.trust {
    val removed = removeAnchor("did:key:old-issuer")
    println("Trust anchor removed: $removed")
}

// List all trusted issuers
trustWeave.trust {
    val trustedIssuers = getTrustedIssuers("EducationCredential")
    println("Trusted issuers for EducationCredential: ${trustedIssuers.size}")
}
```

---

## Next Steps

Now that you can configure trust registries, here are ways to extend your implementation:

### 1. Integrate with Verification

Use trust checking in credential verification:

```kotlin
val result = trustWeave.verify {
    credential(credential)
    checkTrust(true)  // Verify issuer is trusted
}
```

See: [How to Verify Credentials](./verify-credentials.md)

---

### 2. Build Web of Trust

Create trust relationships between multiple entities:

```kotlin
// University trusts department
trustWeave.trust {
    addAnchor(departmentDid.value) {
        credentialTypes("DepartmentCredential")
    }
}

// Department trusts individual professor
trustWeave.trust {
    addAnchor(professorDid.value) {
        credentialTypes("ProfessorCredential")
    }
}

// Find trust path: verifier → university → department → professor
val path = trustWeave.trust {
    getTrustPath(verifierDid.value, professorDid.value)
}
```

---

### 3. Trust Policies

Implement custom trust policies:

```kotlin
fun shouldTrustIssuer(issuerDid: String, credentialType: String): Boolean {
    return trustWeave.getDslContext().getTrustRegistry()?.let { registry ->
        kotlinx.coroutines.runBlocking {
            // Direct trust
            if (registry.isTrustedIssuer(issuerDid, credentialType)) {
                return@runBlocking true
            }
            
            // Trust path with max length
            val path = registry.getTrustPath(verifierDid, issuerDid)
            when (path) {
                is TrustPathResult.Found -> path.path.size <= 3  // Max 3 hops
                else -> false
            }
        }
    } ?: false
}
```

---

### 4. Trust Registry Analytics

Track trust relationships:

```kotlin
trustWeave.trust {
    val allAnchors = getTrustedIssuers(null)  // All types
    println("Total trust anchors: ${allAnchors.size}")
    
    val educationAnchors = getTrustedIssuers("EducationCredential")
    println("Education credential anchors: ${educationAnchors.size}")
}
```

---

## Summary

You've learned how to configure and use trust registries with TrustWeave:

✅ **Configured trust registry** with provider support  
✅ **Added trust anchors** for trusted issuers  
✅ **Checked trust status** for issuers and credential types  
✅ **Integrated trust checking** into credential verification  
✅ **Discovered trust paths** between verifiers and issuers  

**Key takeaways:**
- Trust registries enable decentralized trust management
- Trust anchors specify which credential types issuers are trusted for
- Trust checking can be enabled during credential verification
- Trust paths allow indirect trust relationships

**What's next:**
- Integrate trust checking into verification workflows
- Build web-of-trust systems with multiple trust relationships
- Implement custom trust policies
- Explore trust path discovery for complex trust graphs

For more examples, see the [scenarios documentation](../scenarios/README.md).

