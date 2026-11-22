---
title: How to Revoke Credentials
parent: How-To Guides
nav_order: 90
---
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

- **Kotlin**: 2.4.21+
- **Java**: 21 or higher
- **TrustWeave SDK**: 0.7.0
- **Dependencies**: `distribution-all` (or `trust` + `credentials-credential-api` + a persistent status list plugin such as `credentials-plugins-status-list-bitstring` or `credentials-plugins-status-list-token`)

**Required imports:**
```kotlin
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.types.RevocationResult
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.model.StatusPurpose
import org.trustweave.credential.revocation.RevocationStatus
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
import org.trustweave.trust.dsl.credential.RevocationProviders.IN_MEMORY
// 1. Configure revocation
val trustWeave = TrustWeave.build {
    revocation { provider(IN_MEMORY) }
}

// 2. Issue credential with revocation support
val credential = trustWeave.issue {
    credential { ... }
    withRevocation()  // Auto-creates status list
}

// 3. Revoke credential when needed (sealed result)
when (val result = trustWeave.revokeResult {
    credential(credential.id)
    statusList(statusListId)
}) {
    is RevocationResult.Revoked -> println("Revoked")
    is RevocationResult.NotRevoked -> println("Already revoked or entry unchanged")
    is RevocationResult.TimedOut -> println("Timed out — re-check status before retrying")
    is RevocationResult.NotConfigured -> println("Revocation manager not configured: ${result.reason}")
    is RevocationResult.InvalidRequest -> println("Invalid request: ${result.reason}")
    is RevocationResult.Failure -> println("Failed: ${result.reason}")
}

// 4. Check revocation status
val status = trustWeave.revocation {
    statusList(statusListId)
}.check(credential)
```

---

## Step-by-Step Guide

### Step 1: Configure Revocation Support

Enable revocation in your TrustWeave configuration:

```kotlin
import org.trustweave.trust.dsl.credential.KmsProviders
import org.trustweave.trust.dsl.credential.RevocationProviders
import org.trustweave.trust.dsl.credential.KeyAlgorithms.ED25519
import org.trustweave.trust.dsl.credential.DidMethods.KEY
val trustWeave = TrustWeave.build {
    keys {
        provider(KmsProviders.IN_MEMORY)
        algorithm(ED25519)
    }
    
    did {
        method(KEY) {
            algorithm(ED25519)
        }
    }
    
    revocation {
        provider(RevocationProviders.IN_MEMORY)  // For testing; use persistent provider in production
    }
}
```

**What this does:**
- Configures a status list manager for tracking revoked credentials
- Enables revocation operations in TrustWeave
- Sets up the infrastructure for status list management

> **Note:** `TrustWeave.build { revocation { provider(...) } }` supports `"inMemory"` for development and testing. For production, construct a persistent status list manager from `credentials:plugins:status-list:bitstring` or `credentials:plugins:status-list:token` and wire it programmatically (see [Choosing a Status List Manager](#choosing-a-status-list-manager) below).

---

### Step 2: Create a Status List

Create a status list for tracking revoked credentials. Status lists are typically created automatically when issuing credentials with `withRevocation()`, but you can also create them explicitly:

```kotlin
val statusList = trustWeave.revocation {
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
import org.trustweave.did.identifiers.Did
import kotlin.time.Clock

val credential = trustWeave.issue {
    credential {
        id("https://example.edu/credentials/degree-123")
        type("DegreeCredential")
        issuer(issuerDid)
        subject {
            id(Did("did:key:student"))
            "degree" {
                "name" to "Bachelor of Science"
            }
        }
        issued(Clock.System.now())
    }
    signedBy(issuerDid = issuerDid, keyId = keyId)
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

Revoke a credential when it's no longer valid using `revokeResult { }` (which returns a sealed `RevocationResult` instead of throwing on configuration, validation, or timeout errors):

```kotlin
import org.trustweave.trust.types.RevocationResult

when (val result = trustWeave.revokeResult {
    credential(credential.id?.value ?: throw IllegalStateException("Credential must have ID"))
    statusList(statusList.value)
}) {
    is RevocationResult.Revoked -> println("✅ Credential revoked successfully")
    is RevocationResult.NotRevoked -> println("⚠️ Credential entry unchanged (already revoked or unknown index)")
    is RevocationResult.TimedOut -> println("⏱️ Revocation timed out after ${result.timeout} — re-check status before retrying")
    is RevocationResult.NotConfigured -> println("❌ Revocation manager not configured: ${result.reason}")
    is RevocationResult.InvalidRequest -> println("❌ Invalid revocation request: ${result.reason}")
    is RevocationResult.Failure -> println("❌ Revocation failed: ${result.reason}")
}
```

**What this does:**
- Computes the credential's index in the status list (from credential ID hash)
- Sets the corresponding bit in the status list bitstring
- Updates the status list credential
- Returns a sealed `RevocationResult` (`Revoked`, `NotRevoked`, `NotConfigured`, `InvalidRequest`, `TimedOut`, or `Failure`)

**What happens internally:**
1. Hash the credential ID to get index
2. Set the bit at that index in the status list
3. Update the status list credential's bitstring
4. Optionally anchor the updated status list to blockchain (if configured)

---

### Step 5: Check Revocation Status

Verify if a credential is revoked:

```kotlin
val status = trustWeave.revocation {
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
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.types.RevocationResult
import org.trustweave.credential.model.ProofType
import org.trustweave.trust.dsl.credential.KmsProviders
import org.trustweave.trust.dsl.credential.RevocationProviders
import org.trustweave.trust.dsl.credential.KeyAlgorithms.ED25519
import org.trustweave.trust.dsl.credential.DidMethods.KEY
import org.trustweave.trust.types.getOrThrowDid
import org.trustweave.credential.results.getOrThrow
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.resolver.errorMessage
import org.trustweave.did.identifiers.extractKeyId
import org.trustweave.credential.revocation.RevocationStatus
import org.trustweave.credential.model.StatusPurpose
import kotlinx.coroutines.runBlocking
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

fun main() = runBlocking {
    // Step 1: Configure TrustWeave with revocation
    val trustWeave = TrustWeave.build {
        keys {
            provider(KmsProviders.IN_MEMORY)
            algorithm(ED25519)
        }
        
        did {
            method(KEY) {
                algorithm(ED25519)
            }
        }
        
        revocation {
            provider(RevocationProviders.IN_MEMORY)
        }
    }
    
    // Step 2: Create issuer DID
    val issuerDid = trustWeave.createDid { method(KEY) }.getOrThrowDid()

    // Step 3: Get key ID
    val issuerDocument = when (val res = trustWeave.resolveDid(issuerDid)) {
    is DidResolutionResult.Success -> res.document
    else -> throw IllegalStateException(res.errorMessage ?: "Failed to resolve DID")
}
    val keyId = issuerDocument.verificationMethod.firstOrNull()?.extractKeyId()
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
            issued(Clock.System.now())
            expires((365 * 10).days)
        }
        signedBy(issuerDid = issuerDid, keyId = keyId)
        withRevocation()  // Auto-creates status list
    }
    
    println("✅ Credential issued: ${credential.id}")
    println("   Status list: ${credential.credentialStatus?.statusListCredential}")
    
    // Step 5: Check initial status
    val statusListId = credential.credentialStatus?.statusListCredential
        ?: throw IllegalStateException("Credential has no status list")
    
    val initialStatus = trustWeave.revocation {
        statusList(statusListId)
    }.check(credential)
    
    when (initialStatus) {
        is RevocationStatus.Active -> println("✅ Credential is active")
        else -> println("Status: $initialStatus")
    }
    
    // Step 6: Revoke credential using revokeResult
    when (val result = trustWeave.revokeResult {
        credential(credential.id?.value ?: throw IllegalStateException("Credential must have ID"))
        statusList(statusListId)
    }) {
        is RevocationResult.Revoked -> println("✅ Credential revoked successfully")
        is RevocationResult.NotRevoked -> println("⚠️ Credential entry unchanged")
        else -> println("❌ Revocation did not complete: $result")
    }
    
    // Step 7: Verify revocation
    val revokedStatus = trustWeave.revocation {
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
val status = trustWeave.revocation {
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
- Credential has `credentialStatus` field
- Status list ID is valid
- Revocation operation returns `true`
- Status check returns `RevocationStatus.Revoked`

---

## Common Errors & Troubleshooting

### Error: "Status list manager is not configured"

**Problem:** Revocation provider wasn't configured in TrustWeave.

**Solution:**
```kotlin
import org.trustweave.trust.dsl.credential.RevocationProviders.IN_MEMORY
// ✅ Ensure revocation is configured
val trustWeave = TrustWeave.build {
    revocation {
        provider(IN_MEMORY)  // or your provider
    }
}
```

---

### Error: "Status list ID is required"

**Problem:** The `statusList()` method wasn't called in the revocation builder.

**Solution:**
```kotlin
// ❌ Missing status list ID (returns RevocationResult.InvalidRequest)
trustWeave.revokeResult {
    credential(credentialId)
    // Missing: statusList(statusListId)
}

// ✅ Correct
trustWeave.revokeResult {
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
    signedBy(issuerDid = issuerDid, keyId = keyId)
    withRevocation()  // Required for revocation support
}
```

---

### Warning: Status list not found

**Problem:** The status list ID doesn't exist or was deleted.

**Solution:**
```kotlin
// ✅ Verify status list exists
val statusList = trustWeave.revocation {
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
val statusList = trustWeave.revocation {
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
    val result = trustWeave.revokeResult {
        credential(credId)
        statusList(statusListId)
    }
    credId to (result is RevocationResult.Revoked)
}

val successCount = results.count { it.second }
println("Revoked $successCount out of ${credentialIds.size} credentials")
```

---

### Pattern 2: Suspension (Temporary Revocation)

Suspend a credential temporarily:

```kotlin
// Create suspension status list
val suspensionList = trustWeave.revocation {
    forIssuer(issuerDid.value)
    purpose(StatusPurpose.SUSPENSION)  // Different purpose
}.createStatusList()

// Suspend credential
trustWeave.revocation {
    credential(credential.id?.value ?: throw IllegalStateException("Credential must have ID"))
    statusList(suspensionList.value)
}.suspend()
```

---

### Pattern 3: Revocation with Verification

Revoke and immediately verify:

```kotlin
// Revoke
val result = trustWeave.revokeResult {
    credential(credential.id?.value ?: throw IllegalStateException("Credential must have ID"))
    statusList(statusListId)
}

if (result is RevocationResult.Revoked) {
    // Immediately verify revocation
    val status = trustWeave.revocation {
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

## Choosing a Status List Manager

The `revocation { provider(IN_MEMORY) }` block in the examples above is intended for development and tests. In `TrustWeave.build { ... }`, `revocation { provider(...) }` only resolves the built-in `"inMemory"` provider — passing another name throws `ConfigException.UnsupportedValue`.

For production deployments, choose one of the two persistent status-list plugins (`credentials:plugins:status-list:bitstring` or `credentials:plugins:status-list:token`), construct its manager directly using a JDBC `DataSource` and your KMS/signing configuration, and wire that `CredentialRevocationManager` programmatically into `CredentialServices.createCredentialService(..., revocationManager = manager)` (then pass it via `credentialService(...)` in `TrustWeave.build { ... }`) or invoke the manager directly from your issuer/verifier services.

### W3C Bitstring Status List

The default W3C format. Status lists are themselves Verifiable Credentials containing a gzipped, base64url-encoded bitstring. Best for VC-LD / VC-JWT credentials.

- **Module**: `credentials:plugins:status-list:bitstring` ([credentials/plugins/status-list/bitstring](https://github.com/geoknoesis/trustweave/tree/main/credentials/plugins/status-list/bitstring)) — see the plugin [README](https://github.com/geoknoesis/trustweave/blob/main/credentials/plugins/status-list/bitstring/README.md)
- **Artifact**: `org.trustweave:credentials-plugins-status-list-bitstring:0.7.0`
- **Spec**: [W3C Bitstring Status List v1.0](https://www.w3.org/TR/vc-bitstring-status-list/)
- **Use when**: issuing W3C VCs (`vc-ld`, `vc-jwt`).

```kotlin
import org.trustweave.credential.CredentialServices
import org.trustweave.did.identifiers.VerificationMethodId
import org.trustweave.revocation.bitstring.BitstringStatusListManagerFactory
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource

val dataSource = HikariDataSource(HikariConfig().apply {
    jdbcUrl = "jdbc:postgresql://localhost:5432/trustweave"
    username = "trustweave"
    password = System.getenv("DB_PASSWORD")
})

val manager = BitstringStatusListManagerFactory.create(
    dataSource = dataSource,
    kms = kms,
    issuerDid = "did:web:issuer.example.com",
    bitsPerEntry = 1,
    proofEngine = vcLdProofEngine, // required to sign status list VCs
    issuerKeyId = VerificationMethodId.parse("did:web:issuer.example.com#key-1"),
    baseUrl = "https://issuer.example.com/statuslists",
)

// Wire into CredentialService and pass into TrustWeave.build { credentialService(...) }
val credentialService = CredentialServices.createCredentialService(
    kms = kms,
    didResolver = didResolver,
    revocationManager = manager,
)
```

### IETF Token Status List

JWT/CWT-based status list designed for SD-JWT VC and ISO mdoc. Status is encoded as a signed token rather than a VC, so it fits the SD-JWT VC `status` claim and mdoc flows naturally.

- **Module**: `credentials:plugins:status-list:token` ([credentials/plugins/status-list/token](https://github.com/geoknoesis/trustweave/tree/main/credentials/plugins/status-list/token)) — see the plugin [README](https://github.com/geoknoesis/trustweave/blob/main/credentials/plugins/status-list/token/README.md)
- **Artifact**: `org.trustweave:credentials-plugins-status-list-token:0.7.0`
- **Spec**: [IETF Token Status List](https://datatracker.ietf.org/doc/draft-ietf-oauth-status-list/)
- **Use when**: issuing SD-JWT VCs or mdocs.

```kotlin
import org.trustweave.did.identifiers.VerificationMethodId
import org.trustweave.revocation.token.TokenStatusListManagerFactory

val manager = TokenStatusListManagerFactory.create(
    dataSource = dataSource,
    kms = kms,
    issuerDid = "did:web:issuer.example.com",
    statusListUri = "https://issuer.example.com/statuslists/1",
    bitsPerEntry = 1,
    issuerKeyId = VerificationMethodId.parse("did:web:issuer.example.com#key-1"), // Ed25519 or P-256 key in kms
)
// Wire `manager` into CredentialServices.createCredentialService(..., revocationManager = manager)
// or use it directly as a CredentialRevocationManager.
```

> **Note:** Both `BitstringStatusListManager` and `TokenStatusListManager` persist state in PostgreSQL/H2 via the supplied `DataSource`. Provide `proofEngine` + `issuerKeyId` (Bitstring) or `issuerKeyId` (Token) so published status lists are cryptographically signed with your KMS key.

---

## Publishing Status Lists

For verifiers to check revocation, the signed status-list credential (or token) must be reachable at the URL embedded in the credential's `credentialStatus` entry. The [status-list/publishing](https://github.com/geoknoesis/trustweave/tree/main/credentials/plugins/status-list/publishing) module provides four backends behind a common `StatusListPublisher` interface — see the plugin [README](https://github.com/geoknoesis/trustweave/blob/main/credentials/plugins/status-list/publishing/README.md) for full details.

| Backend | Use when |
|---|---|
| `LocalFileStatusListPublisher` | Local dev / testing, or behind your own web server |
| `S3StatusListPublisher` | Hosting on AWS S3 (optionally fronted by CloudFront) |
| `AzureBlobStatusListPublisher` | Hosting on Azure Blob Storage |
| `GcsStatusListPublisher` | Hosting on Google Cloud Storage |

The interface is:

```kotlin
interface StatusListPublisher {
    suspend fun publish(statusListId: String, content: ByteArray, contentType: String): String
    suspend fun delete(statusListId: String)
    suspend fun getUrl(statusListId: String): String?
}
```

Example — set up an S3 publisher and push a serialized status list:

```kotlin
import org.trustweave.revocation.publishing.S3StatusListPublisher
import org.trustweave.revocation.publishing.S3PublisherConfig
import kotlinx.coroutines.runBlocking

val publisher = S3StatusListPublisher(
    S3PublisherConfig(
        bucket = "trust.example.com",
        region = "us-east-1",
        keyPrefix = "statuslists/",
        publicUrlPattern = "https://trust.example.com/statuslists/{id}"
    )
)

runBlocking {
    // `bytes` is the signed status list VC (Bitstring) or status token (Token),
    // produced by the corresponding manager.
    val url = publisher.publish(
        statusListId = "sl-001",
        content = bytes,
        contentType = "application/json"  // or "application/jwt" for Token Status List
    )
    println("Published at: $url")
}
```

> **Tip:** Republish whenever a status list changes. Verifiers fetch the URL with normal HTTP caching, so use short `Cache-Control` headers on the published object.

---

## Verifier-Side Status Checking

On the verifier side, the [`CredentialStatusChecker`](https://github.com/geoknoesis/trustweave/blob/main/credentials/credential-models-mp/src/commonMain/kotlin/org/trustweave/credential/spi/status/CredentialStatusChecker.kt) SPI gates verification on a live status fetch — proof engines call it before returning a `Valid` result.

```kotlin
import org.trustweave.credential.spi.status.CredentialStatusChecker
import org.trustweave.credential.spi.status.CredentialStatusCheckResult
import org.trustweave.credential.model.vc.VerifiableCredential

class HttpStatusChecker : CredentialStatusChecker {
    override suspend fun checkStatus(
        credential: VerifiableCredential
    ): CredentialStatusCheckResult {
        val entry = credential.credentialStatus
            ?: return CredentialStatusCheckResult.NoStatus
        // Fetch entry.statusListCredential, decode the bitstring,
        // check the bit at entry.statusListIndex, then return one of:
        //   Valid | Revoked(reason) | Suspended(reason) | CheckFailed(reason)
        return CredentialStatusCheckResult.Valid
    }
}
```

Inject it via the proof engine config under the `"statusChecker"` key:

```kotlin
import org.trustweave.credential.spi.proof.ProofEngineConfig

val config = ProofEngineConfig(
    properties = mapOf("statusChecker" to HttpStatusChecker())
)
```

The sealed result type covers every outcome a verifier needs to surface:

| Result | Meaning |
|---|---|
| `Valid` | Status list confirms the credential is active |
| `Revoked(reason)` | Credential has been permanently revoked |
| `Suspended(reason)` | Credential is temporarily on hold |
| `CheckFailed(reason)` | Status check itself failed (network, malformed list) |
| `NoStatus` | Credential has no `credentialStatus` field |

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

See: [How to Verify Credentials](verify-credentials.md)

---

### 2. Blockchain-Anchored Revocation

Anchor status lists to blockchain for tamper evidence:

```kotlin
import org.trustweave.trust.dsl.credential.RevocationProviders.IN_MEMORY
// Configure blockchain anchoring
val trustWeave = TrustWeave.build {
    anchor {
        chain("algorand:testnet") {
            inMemory()
        }
    }
    revocation {
        provider(IN_MEMORY)
    }
}

// Status lists will be anchored automatically when threshold is reached
```

See: [How to Anchor to Blockchain](blockchain-anchoring.md)

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
val result = trustWeave.revokeResult {
    credential(credentialId)
    statusList(statusListId)
}

if (result is RevocationResult.Revoked) {
    handleRevocation(credentialId)
}
```

---

### 4. Revocation Analytics

Track revocation patterns:

```kotlin
// Get status list statistics
val statusList = trustWeave.revocation {
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

