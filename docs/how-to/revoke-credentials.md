# How to Revoke Credentials

## Purpose

This guide shows you how to revoke Verifiable Credentials using TrustWeave's revocation system. You'll learn how to create status lists, revoke credentials, check revocation status, and handle revocation lifecycle management.

**What you'll accomplish:**
- Configure revocation support in TrustWeave
- Create status lists for tracking revoked credentials
- Revoke credentials when needed
- Check if credentials are revoked
- Understand revocation status verification

**Why this matters:**
Revocation is essential for credential lifecycle management. It allows issuers to invalidate credentials that are no longer valid (e.g., expired licenses, terminated employment, compromised credentials) while maintaining cryptographic proof of the revocation.

---

## Prerequisites

- **Kotlin**: 2.2.21+ or higher
- **Java**: 21 or higher
- **TrustWeave SDK**: Latest version
- **Dependencies**: `distribution-all` with revocation support

**Required imports:**
```kotlin
import com.trustweave.trust.TrustWeave
import com.trustweave.credential.models.VerifiableCredential
import com.trustweave.credential.revocation.StatusPurpose
import com.trustweave.credential.revocation.RevocationStatus
import kotlinx.coroutines.runBlocking
```

**Configuration needed:**
- Status list manager provider (e.g., `inMemory` for testing)
- Revocation support enabled in credential issuance

---

## Before You Begin

Credential revocation uses **Status List 2021**—a compact, efficient method for tracking revoked credentials. TrustWeave handles the complexity of status list management, bit manipulation, and blockchain anchoring (if configured).

**When to use this:**
- Revoking credentials that are no longer valid
- Managing credential lifecycle (expiration, suspension, revocation)
- Complying with regulations requiring revocation capabilities
- Handling security incidents (compromised credentials)

**How it fits in a workflow:**
```kotlin
// 1. Configure revocation
val trustWeave = TrustWeave.build {
    revocation { provider("inMemory") }
}

// 2. Issue credential with revocation support
val credential = trustWeave.issue {
    credential { ... }
    withRevocation()  // Auto-creates status list
}

// 3. Revoke credential when needed
trustWeave.revoke {
    credential(credential.id)
    statusList(statusListId)
}

// 4. Check revocation status
val status = trustWeave.getDslContext().revocation {
    statusList(statusListId)
}.check(credential)
```

---

## Step-by-Step Guide

### Step 1: Configure Revocation Support

Enable revocation in your TrustWeave configuration:

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
    
    revocation {
        provider("inMemory")  // For testing; use persistent provider in production
    }
}
```

**What this does:**
- Configures a status list manager for tracking revoked credentials
- Enables revocation operations in TrustWeave
- Sets up the infrastructure for status list management

> **Note:** For production, use a persistent provider (database-backed) instead of `inMemory`.

---

### Step 2: Create a Status List

Create a status list for tracking revoked credentials. Status lists are typically created automatically when issuing credentials with `withRevocation()`, but you can also create them explicitly:

```kotlin
val statusList = trustWeave.getDslContext().revocation {
    forIssuer(issuerDid.value)
    purpose(StatusPurpose.REVOCATION)
    size(131072)  // Optional: default is 131072 credentials
}.createStatusList()

println("Status list created: ${statusList.id}")
```

**What this does:**
- Creates a status list credential for the issuer
- Allocates space for tracking revoked credentials (default: 131,072)
- Returns a `StatusListCredential` that can be referenced in credentials

**Key concepts:**
- **Status List**: A credential that contains a bitstring tracking revocation status
- **Status List Index**: Each credential gets an index (computed from credential ID hash)
- **Bitstring**: Compact representation (1 bit per credential)

---

### Step 3: Issue Credential with Revocation Support

Issue a credential that includes a reference to the status list:

```kotlin
val credential = trustWeave.issue {
    credential {
        id("https://example.edu/credentials/degree-123")
        type("DegreeCredential")
        issuer(issuerDid.value)
        subject {
            id("did:key:student")
            "degree" {
                "name" to "Bachelor of Science"
            }
        }
        issued(Instant.now())
    }
    signedBy(issuerDid = issuerDid.value, keyId = keyId)
    withRevocation()  // Auto-creates status list if needed
}
```

**What this does:**
- Automatically creates a status list if one doesn't exist
- Adds `credentialStatus` to the credential with status list reference
- Enables revocation checking during verification

The `credentialStatus` field will look like:
```json
{
  "id": "https://example.edu/statuslists/1#0",
  "type": "StatusList2021Entry",
  "statusPurpose": "revocation",
  "statusListIndex": "0",
  "statusListCredential": "https://example.edu/statuslists/1"
}
```

---

### Step 4: Revoke a Credential

Revoke a credential when it's no longer valid:

```kotlin
val revoked = trustWeave.revoke {
    credential(credential.id ?: throw IllegalStateException("Credential must have ID"))
    statusList(statusList.id)
}

if (revoked) {
    println("✅ Credential revoked successfully")
} else {
    println("⚠️ Credential was already revoked")
}
```

**What this does:**
- Computes the credential's index in the status list (from credential ID hash)
- Sets the corresponding bit in the status list bitstring
- Updates the status list credential
- Returns `true` if revocation succeeded, `false` if already revoked

**What happens internally:**
1. Hash the credential ID to get index
2. Set the bit at that index in the status list
3. Update the status list credential's bitstring
4. Optionally anchor the updated status list to blockchain (if configured)

---

### Step 5: Check Revocation Status

Verify if a credential is revoked:

```kotlin
val status = trustWeave.getDslContext().revocation {
    statusList(statusList.id)
}.check(credential)

when (status) {
    is RevocationStatus.Revoked -> {
        println("❌ Credential is revoked")
        println("   Revoked at: ${status.revokedAt}")
    }
    is RevocationStatus.Active -> {
        println("✅ Credential is active (not revoked)")
    }
    is RevocationStatus.Unknown -> {
        println("⚠️ Revocation status unknown")
        println("   Reason: ${status.reason}")
    }
}
```

**What this does:**
- Extracts the status list reference from the credential
- Checks the bit at the credential's index in the status list
- Returns a sealed result type with revocation status

---

## Complete Example

Here's a complete, runnable example:

```kotlin
import com.trustweave.trust.TrustWeave
import com.trustweave.trust.types.ProofType
import com.trustweave.did.resolver.DidResolutionResult
import com.trustweave.credential.revocation.RevocationStatus
import com.trustweave.credential.revocation.StatusPurpose
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.temporal.ChronoUnit

fun main() = runBlocking {
    // Step 1: Configure TrustWeave with revocation
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
        
        revocation {
            provider("inMemory")
        }
    }
    
    // Step 2: Create issuer DID
    val issuerDid = trustWeave.createDid { method("key") }
    
    // Step 3: Get key ID
    val resolutionResult = trustWeave.resolveDid(issuerDid)
    val issuerDocument = when (resolutionResult) {
        is DidResolutionResult.Success -> resolutionResult.document
        else -> throw IllegalStateException("Failed to resolve issuer DID")
    }
    val keyId = issuerDocument.verificationMethod.firstOrNull()?.id?.substringAfter("#")
        ?: throw IllegalStateException("No verification method found")
    
    // Step 4: Issue credential with revocation support
    val credential = trustWeave.issue {
        credential {
            id("https://example.edu/credentials/degree-123")
            type("DegreeCredential")
            issuer(issuerDid.value)
            subject {
                id("did:key:student")
                "degree" {
                    "name" to "Bachelor of Science"
                }
            }
            issued(Instant.now())
            expires(365 * 10, ChronoUnit.DAYS)
        }
        signedBy(issuerDid = issuerDid.value, keyId = keyId)
        withRevocation()  // Auto-creates status list
    }
    
    println("✅ Credential issued: ${credential.id}")
    println("   Status list: ${credential.credentialStatus?.statusListCredential}")
    
    // Step 5: Check initial status
    val statusListId = credential.credentialStatus?.statusListCredential
        ?: throw IllegalStateException("Credential has no status list")
    
    val initialStatus = trustWeave.getDslContext().revocation {
        statusList(statusListId)
    }.check(credential)
    
    when (initialStatus) {
        is RevocationStatus.Active -> println("✅ Credential is active")
        else -> println("Status: $initialStatus")
    }
    
    // Step 6: Revoke credential
    val revoked = trustWeave.revoke {
        credential(credential.id ?: throw IllegalStateException("Credential must have ID"))
        statusList(statusListId)
    }
    
    if (revoked) {
        println("✅ Credential revoked successfully")
    }
    
    // Step 7: Verify revocation
    val revokedStatus = trustWeave.getDslContext().revocation {
        statusList(statusListId)
    }.check(credential)
    
    when (revokedStatus) {
        is RevocationStatus.Revoked -> {
            println("✅ Revocation confirmed: Credential is revoked")
        }
        else -> {
            println("⚠️ Unexpected status: $revokedStatus")
        }
    }
}
```

---

## Visual Flow Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                  Credential Revocation Flow                  │
└─────────────────────────────────────────────────────────────┘

┌──────────────┐
│   Step 1     │  Configure Revocation
│  Configure   │  • Status List Manager (inMemory/database)
└──────┬───────┘  • Enable revocation support
       │
       ▼
┌──────────────┐
│   Step 2     │  Create Status List
│ Create List  │  • For issuer DID
└──────┬───────┘  • Purpose: REVOCATION
       │          • Size: 131,072 (default)
       ▼
┌──────────────┐
│   Step 3     │  Issue Credential
│ Issue with   │  • Include credentialStatus
│ Revocation   │  • Reference status list
└──────┬───────┘  • Auto-create list if needed
       │
       ▼
┌──────────────┐
│   Step 4     │  Revoke Credential
│   Revoke     │  • Hash credential ID → index
└──────┬───────┘  • Set bit at index
       │          • Update status list
       ▼
┌──────────────┐
│   Step 5     │  Check Status
│   Verify     │  • Read status list
└──────┬───────┘  • Check bit at index
       │          • Return RevocationStatus
       ▼
    ✅ Active
    or
    ❌ Revoked
```

---

## Verification Step

After revoking, verify the revocation status:

```kotlin
// Quick verification
val status = trustWeave.getDslContext().revocation {
    statusList(statusListId)
}.check(credential)

val isRevoked = status is RevocationStatus.Revoked
println("Credential is ${if (isRevoked) "revoked" else "active"}")
```

**Expected output:**
```
✅ Credential issued: https://example.edu/credentials/degree-123
   Status list: https://example.edu/statuslists/1
✅ Credential is active
✅ Credential revoked successfully
✅ Revocation confirmed: Credential is revoked
```

**What to check:**
- ✅ Credential has `credentialStatus` field
- ✅ Status list ID is valid
- ✅ Revocation operation returns `true`
- ✅ Status check returns `RevocationStatus.Revoked`

---

## Common Errors & Troubleshooting

### Error: "Status list manager is not configured"

**Problem:** Revocation provider wasn't configured in TrustWeave.

**Solution:**
```kotlin
// ✅ Ensure revocation is configured
val trustWeave = TrustWeave.build {
    revocation {
        provider("inMemory")  // or your provider
    }
}
```

---

### Error: "Status list ID is required"

**Problem:** The `statusList()` method wasn't called in the revocation builder.

**Solution:**
```kotlin
// ❌ Missing status list ID
trustWeave.revoke {
    credential(credentialId)
    // Missing: statusList(statusListId)
}

// ✅ Correct
trustWeave.revoke {
    credential(credentialId)
    statusList(statusListId)  // Required
}
```

---

### Error: "Credential has no status list"

**Problem:** The credential was issued without revocation support.

**Solution:**
```kotlin
// ✅ Issue credential with revocation
val credential = trustWeave.issue {
    credential { ... }
    signedBy(issuerDid = issuerDid.value, keyId = keyId)
    withRevocation()  // Required for revocation support
}
```

---

### Warning: Status list not found

**Problem:** The status list ID doesn't exist or was deleted.

**Solution:**
```kotlin
// ✅ Verify status list exists
val statusList = trustWeave.getDslContext().revocation {
    statusList(statusListId)
}.getStatusList()

if (statusList == null) {
    println("Status list not found: $statusListId")
    // Create new status list or use existing one
}
```

---

### Error: Revocation check returns Unknown

**Problem:** The credential doesn't have a status list reference, or the status list can't be accessed.

**Solution:**
```kotlin
// ✅ Check credential has status list reference
if (credential.credentialStatus == null) {
    println("Credential has no revocation support")
    // Re-issue credential with withRevocation()
}

// ✅ Verify status list is accessible
val statusList = trustWeave.getDslContext().revocation {
    statusList(statusListId)
}.getStatusList()

if (statusList == null) {
    println("Status list not accessible: $statusListId")
}
```

---

## Advanced Patterns

### Pattern 1: Batch Revocation

Revoke multiple credentials at once:

```kotlin
val credentialIds = listOf("cred-1", "cred-2", "cred-3")
val results = credentialIds.map { credId ->
    try {
        val revoked = trustWeave.revoke {
            credential(credId)
            statusList(statusListId)
        }
        credId to revoked
    } catch (error: Exception) {
        credId to false
    }
}

val successCount = results.count { it.second }
println("Revoked $successCount out of ${credentialIds.size} credentials")
```

---

### Pattern 2: Suspension (Temporary Revocation)

Suspend a credential temporarily:

```kotlin
// Create suspension status list
val suspensionList = trustWeave.getDslContext().revocation {
    forIssuer(issuerDid.value)
    purpose(StatusPurpose.SUSPENSION)  // Different purpose
}.createStatusList()

// Suspend credential
trustWeave.getDslContext().revocation {
    credential(credential.id ?: throw IllegalStateException("Credential must have ID"))
    statusList(suspensionList.id)
}.suspend()
```

---

### Pattern 3: Revocation with Verification

Revoke and immediately verify:

```kotlin
// Revoke
val revoked = trustWeave.revoke {
    credential(credential.id ?: throw IllegalStateException("Credential must have ID"))
    statusList(statusListId)
}

if (revoked) {
    // Immediately verify revocation
    val status = trustWeave.getDslContext().revocation {
        statusList(statusListId)
    }.check(credential)
    
    when (status) {
        is RevocationStatus.Revoked -> {
            println("✅ Revocation confirmed")
        }
        else -> {
            println("⚠️ Revocation not yet reflected: $status")
        }
    }
}
```

---

## Next Steps

Now that you can revoke credentials, here are ways to extend your implementation:

### 1. Integrate with Verification

Check revocation during credential verification:

```kotlin
val result = trustWeave.verify {
    credential(credential)
    checkRevocation()  // Automatically checks status list
}
```

See: [How to Verify Credentials](./verify-credentials.md)

---

### 2. Blockchain-Anchored Revocation

Anchor status lists to blockchain for tamper evidence:

```kotlin
// Configure blockchain anchoring
val trustWeave = TrustWeave.build {
    anchor {
        chain("algorand:testnet") {
            inMemory()
        }
    }
    revocation {
        provider("inMemory")
    }
}

// Status lists will be anchored automatically when threshold is reached
```

See: [How to Anchor to Blockchain](./blockchain-anchoring.md)

---

### 3. Revocation Webhooks

Set up notifications when credentials are revoked:

```kotlin
// Custom revocation handler
fun handleRevocation(credentialId: String) {
    // Notify holders, update database, etc.
    webhookService.notifyRevocation(credentialId)
}

// Revoke with notification
val revoked = trustWeave.revoke {
    credential(credentialId)
    statusList(statusListId)
}

if (revoked) {
    handleRevocation(credentialId)
}
```

---

### 4. Revocation Analytics

Track revocation patterns:

```kotlin
// Get status list statistics
val statusList = trustWeave.getDslContext().revocation {
    statusList(statusListId)
}.getStatusList()

// Analyze revocation patterns
// (Implementation depends on status list manager)
```

---

## Summary

You've learned how to revoke credentials with TrustWeave:

✅ **Configured revocation support** with status list manager  
✅ **Created status lists** for tracking revoked credentials  
✅ **Issued credentials with revocation** using `withRevocation()`  
✅ **Revoked credentials** when no longer valid  
✅ **Checked revocation status** to verify credentials  

**Key takeaways:**
- Status List 2021 provides efficient, compact revocation tracking
- TrustWeave handles bit manipulation and status list management automatically
- Revocation is checked during verification when `checkRevocation()` is enabled
- Status lists can be anchored to blockchain for additional tamper evidence

**What's next:**
- Integrate revocation checking into verification workflows
- Set up blockchain anchoring for status lists
- Implement revocation notifications and analytics
- Explore suspension for temporary revocation

For more examples, see the [scenarios documentation](../scenarios/README.md).

