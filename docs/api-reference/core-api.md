---
title: Core API Reference
nav_order: 4
parent: API Reference
keywords:
  - api
  - core api
  - trustweave
  - dids
  - credentials
  - wallets
  - blockchain
  - trust
---

# Core API Reference

Complete API reference for TrustWeave's TrustWeave API.

> **Version:** 0.6.0
> **Kotlin:** 2.2.21+ | **Java:** 21+
> See [CHANGELOG.md](../../CHANGELOG.md) for version history and migration guides.
>
> **Note:** This API reference documents the `TrustWeave` API, which is the primary interface for trust and identity operations in TrustWeave. The TrustWeave provides a DSL-based API for creating DIDs, issuing credentials, managing wallets, and more.

```kotlin
dependencies {
    implementation("org.trustweave:distribution-all:0.6.0")
}
```

## Overview

The TrustWeave provides a unified, DSL-based API for decentralized identity and trust operations. The API uses suspend functions and DSL builders for type-safe, fluent operations.

**Key Concepts:**
- **TrustWeave**: The main entry point for trust and identity operations
- **DSL Builders**: Fluent builders for configuring and performing operations (e.g., `issue { }`, `createDid { }`)
- **Configuration**: Use `TrustWeave.build { }` for KMS, DID methods, anchors, and trust; use `trustWeave.configuration` when you need registries and clients directly

**Main Operations:**
- **`issue { }`**: Issue verifiable credentials using DSL
- **`verify { }`**: Verify verifiable credentials
- **`createDid { }`**: Create DIDs using DSL
- **`updateDid { }`**: Update DID documents
- **`wallet { }`**: Create and configure wallets
- **`delegate { }`**: Delegate authority between DIDs
- **`rotateKey { }`**: Rotate keys in DID documents

## Quick Reference

| Operation | Method | Returns |
|-----------|--------|---------|
| Create TrustWeave | `TrustWeave.build { }` | `TrustWeave` |
| Create DID | `trustWeave.createDid { }` | `DidCreationResult` |
| Resolve DID | `trustWeave.resolveDid(did)` | `DidResolutionResult` |
| Update DID | `trustWeave.updateDid { }` | `DidDocument` |
| Delegate DID | `trustWeave.delegate { }` | `DelegationChainResult` |
| Rotate Key | `trustWeave.rotateKey { }` | `DidDocument` |
| Issue Credential | `trustWeave.issue { }` | `IssuanceResult` |
| Verify Credential | `trustWeave.verify { }` / `verify(credential)` | `VerificationResult` |
| Create Wallet | `trustWeave.wallet { }` | `WalletCreationResult` |
| Trust operations | `trustWeave.trust { }` (anchors, paths, queries inside DSL) | `Unit` |
| Trust path (typed) | `trustWeave.findTrustPath(verifier, issuer)` | `TrustPath` |
| Revoke credential | `trustWeave.revoke { }` | `Boolean` |
| Revocation DSL | `trustWeave.revocation { }` | `RevocationBuilder` (status lists, checks) |
| Get Configuration | `trustWeave.configuration` | `TrustWeaveConfig` |
| Create from Config | `TrustWeave.from(config)` | `TrustWeave` |

**Throwing helpers (imports):** `DidCreationResult.getOrThrow()` / `getOrThrowDid()` / `getOrThrowDocument()` are extensions in **`org.trustweave.trust.types`**. `IssuanceResult.getOrThrow()` and related credential helpers are in **`org.trustweave.credential.results`**. These are **not** `kotlin.Result`—prefer exhaustive **`when`** in production; use **`getOrThrow`** only at deliberate boundaries.

## TrustWeave Class

### Creating TrustWeave Instances

```kotlin
import org.trustweave.trust.TrustWeave
import kotlinx.coroutines.runBlocking
import org.trustweave.testkit.services.*

fun main() = runBlocking {
    // Create with defaults (in-memory KMS, did:key method)
    val trustWeave = TrustWeave.build {
        keys {
            provider(IN_MEMORY)
            algorithm(ED25519)
        }
        did {
            method(KEY) {
                algorithm(ED25519)
            }
        }
    }

    // Create with custom configuration
    val trustWeave2 = TrustWeave.build {
        keys {
            provider(IN_MEMORY)
            algorithm(ED25519)
        }
        did {
            method(KEY) {
                algorithm(ED25519)
            }
            method(WEB) {
                domain("example.com")
            }
        }
        anchor {
            chain("algorand:testnet") {
                provider(ALGORAND)
            }
        }
        trust {
            provider(IN_MEMORY)
        }
    }
}
```

### Main Operations

The TrustWeave provides DSL-based operations:

- **`issue { }`**: Issue verifiable credentials
- **`verify { }`**: Verify verifiable credentials
- **`createDid { }`**: Create DIDs
- **`updateDid { }`**: Update DID documents
- **`delegate { }`**: Delegate authority between DIDs
- **`rotateKey { }`**: Rotate keys in DID documents
- **`wallet { }`**: Create wallets
- **`trust { }`**: Manage trust anchors and queries (DSL)
- **`findTrustPath()`**: Find a typed trust path between verifier and issuer identities
- **`revoke { }`**: Revoke a credential (boolean success)
- **`revocation { }`**: Status lists, suspension, revocation checks
- **`configuration`**: `TrustWeaveConfig` (registries, KMS, services)

**Example:**
```kotlin
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.types.DidCreationResult
import org.trustweave.credential.results.IssuanceResult
import kotlinx.coroutines.runBlocking
import org.trustweave.testkit.services.*

fun main() = runBlocking {
    val trustWeave = TrustWeave.build { ... }

    // Create DID
    val didResult = trustWeave.createDid {
        method(KEY)
        algorithm(ED25519)
    }
    
    val issuerDid = when (didResult) {
        is DidCreationResult.Success -> didResult.did
        else -> throw IllegalStateException("Failed to create DID")
    }

    // Issue credential
    val issuanceResult = trustWeave.issue {
        credential { ... }
        signedBy(issuerDid = issuerDid, keyId = "key-1")
    }
    
    val credential = when (issuanceResult) {
        is IssuanceResult.Success -> issuanceResult.credential
        else -> throw IllegalStateException("Failed to issue credential")
    }
}

// Create wallet
val walletResult = trustWeave.wallet {
    holder("did:key:holder")
}
val wallet = when (walletResult) {
    is WalletCreationResult.Success -> walletResult.wallet
    else -> throw IllegalStateException("Failed to create wallet")
}
```

### DID Operations

#### create

Creates a new DID using the default or specified method.

```kotlin
suspend fun createDid(block: DidBuilder.() -> Unit): DidCreationResult
```

**Access via:** `trustWeave.createDid { }`

**Parameters:**

- **`method`** (String, optional): DID method identifier
  - **Default**: `"key"` (did:key method)
  - **Format**: Method name without `did:` prefix (e.g., `"key"`, `"web"`, `"ion"`)
  - **Available Methods**: Use `getAvailableDidMethods()` to see registered methods
  - **Example**: `"web"` for did:web, `"ion"` for did:ion
  - **Validation**: Automatically validated - must be registered method
  - **Common Values**: `"key"`, `"web"`, `"ion"`, `"ethr"`, `"polygon"`

- **`options`** (DidCreationOptions, optional): DID creation options
  - **Default**: `DidCreationOptions()` with ED25519 algorithm
  - **Type**: Data class with typed properties
  - **Properties**:
    - `algorithm`: Key algorithm (ED25519, SECP256K1, RSA)
    - `purposes`: List of key purposes (AUTHENTICATION, ASSERTION_METHOD, etc.)
    - `additionalProperties`: Method-specific options
  - **Example**: `DidCreationOptions(algorithm = KeyAlgorithm.ED25519)`

- **`configure`** (DidCreationOptionsBuilder.() -> Unit, optional): Builder function
  - **Alternative to**: `options` parameter
  - **Type**: DSL builder function
  - **Example**: `{ algorithm = KeyAlgorithm.ED25519; purpose(KeyPurpose.AUTHENTICATION) }`

**Returns:** `DidCreationResult` - Sealed result type containing:

**Success Case:**
- `DidCreationResult.Success` containing:
  - `did`: The created `Did` object
  - `document`: W3C-compliant DID document containing:
    - `id`: The DID string (e.g., `"did:key:z6Mk..."`)
    - `verificationMethod`: Array of verification methods with public keys
    - `authentication`: Authentication key references
    - `assertionMethod`: Assertion key references (for signing)
    - `service`: Optional service endpoints

**Failure Cases:**
- `DidCreationResult.Failure.MethodNotRegistered` - Method is not registered (includes available methods)
- `DidCreationResult.Failure.KeyGenerationFailed` - Key generation failed
- `DidCreationResult.Failure.DocumentCreationFailed` - Document creation failed
- `DidCreationResult.Failure.InvalidConfiguration` - Configuration validation failed
- `DidCreationResult.Failure.Other` - Other error with reason and optional cause

**Note:** This method returns sealed results instead of throwing exceptions. Use `when` expressions for exhaustive error handling.

**Default Behavior:**
- Uses `did:key` method if not specified
- Generates ED25519 key pair
- Creates verification method with `#key-1` fragment
- Adds key to `authentication` and `assertionMethod` arrays

**Edge Cases:**
- If method not registered → `DidException.DidMethodNotRegistered` with available methods list
- If algorithm not supported by method → `TrustWeaveException.ValidationFailed` with reason
- If KMS fails to generate key → `TrustWeaveException.InvalidOperation` with context
- If method-specific validation fails → `TrustWeaveException.ValidationFailed` with field details

**Performance:**
- Time complexity: O(1) for key generation (if cached)
- Network calls: 0 (local key generation for did:key)
- Thread-safe: ✅ Yes (suspend function, thread-safe KMS operations)

**Example:**
```kotlin
import org.trustweave.testkit.services.*
import org.trustweave.trust.types.getOrThrowDid

// Simple usage (uses defaults: did:key, ED25519)

val didResult = trustWeave.createDid {
    method(KEY)
    algorithm(ED25519)
}

when (didResult) {
    is DidCreationResult.Success -> {
        println("Created DID: ${didResult.did.value}")
        println("Document: ${didResult.document.id}")
    }
    is DidCreationResult.Failure.MethodNotRegistered -> {
        println("Method not registered: ${didResult.method}")
        println("Available methods: ${didResult.availableMethods.joinToString()}")
    }
    is DidCreationResult.Failure.KeyGenerationFailed -> {
        println("Key generation failed: ${didResult.reason}")
    }
    is DidCreationResult.Failure.DocumentCreationFailed -> {
        println("Document creation failed: ${didResult.reason}")
    }
    is DidCreationResult.Failure.InvalidConfiguration -> {
        println("Invalid configuration: ${didResult.reason}")
    }
    is DidCreationResult.Failure.Other -> {
        println("Error: ${didResult.reason}")
        didResult.cause?.printStackTrace()
    }
}

// For tests and examples, use getOrThrowDid()

val did = trustWeave.createDid {
    method(KEY)
    algorithm(ED25519)
}.getOrThrowDid() // Requires `import org.trustweave.trust.types.getOrThrowDid`; throws IllegalStateException on failure

// With custom method
val webDidResult = trustWeave.createDid {
    method(WEB)
    domain("example.com")
}
when (webDidResult) {
    is DidCreationResult.Success -> println("Created: ${webDidResult.did.value}")
    is DidCreationResult.Failure -> println("Error creating DID")
}
```

**Error Types:**
- `DidCreationResult.Failure.MethodNotRegistered` - Method is not registered (includes available methods)
- `DidCreationResult.Failure.KeyGenerationFailed` - Key generation failed (includes reason)
- `DidCreationResult.Failure.DocumentCreationFailed` - Document creation failed (includes reason)
- `DidCreationResult.Failure.InvalidConfiguration` - Configuration validation failed (includes reason and details)
- `DidCreationResult.Failure.Other` - Other error (includes reason and optional cause)

#### createDidWithKey

Creates a DID and returns the first verification **key id** in one step (same DSL as `createDid`).

```kotlin
suspend fun createDidWithKey(
    method: String? = null,
    timeout: Duration = 10.seconds,
    block: DidBuilder.() -> Unit = {}
): DidCreationWithKeyResult
```

**Access via:** `trustWeave.createDidWithKey { }`

**Returns:** [`DidCreationWithKeyResult`](../../trust/src/main/kotlin/org/trustweave/trust/types/DidResult.kt) — `Success(did, keyId)` or `Failure` (either wraps `DidCreationResult.Failure` or describes key extraction failure).

**Tests / examples:** use exhaustive `when` or `import org.trustweave.trust.types.getOrThrow` and call `.getOrThrow()` for a `Pair<Did, String>` (throws on failure).

#### resolveDid / resolve

Resolves a DID to its document.

```kotlin
suspend fun resolveDid(did: String, timeout: Duration = 30.seconds): DidResolutionResult
suspend fun resolveDid(did: Did, timeout: Duration = 30.seconds): DidResolutionResult
```

**Access via:** `trustWeave.resolveDid(...)` (implements `DidResolver.resolve` for `Did`).

**Returns:** Sealed `DidResolutionResult` — use `DidResolutionResult.Success` for the `DidDocument` and metadata; failures are separate variants (not a nullable `document`).

**Example:**
```kotlin
import org.trustweave.did.resolver.DidResolutionResult

when (val res = trustWeave.resolveDid("did:key:z6Mk...")) {
    is DidResolutionResult.Success -> println("Resolved: ${res.document.id}")
    is DidResolutionResult.Failure -> println("Resolution failed")
}
```

#### updateDid

Updates a DID document via the `DidDocument` DSL.

```kotlin
suspend fun updateDid(
    timeout: Duration = 30.seconds,
    block: DidDocumentBuilder.() -> Unit
): DidDocument
```

**Access via:** `trustWeave.updateDid { }`

**Parameters:** Builder methods such as **`did(...)`**, **`addService { }`**, **`addVerificationMethod { }`**, etc., depending on your DID method.

**Returns:** The updated `DidDocument`. Failures are thrown as `TrustWeaveException` / `DidException` from the underlying DID method.

**Example:**
```kotlin
import org.trustweave.did.identifiers.Did

val did = Did("did:key:example")
val updated = trustWeave.updateDid {
    did(did.value)
    addService {
        id("${did.value}#service-1")
        type("LinkedDomains")
        endpoint("https://example.com/service")
    }
}
println("Updated: ${updated.id}")
```

#### DID deactivation

There is no `TrustWeave.deactivateDid` API. Deactivation is **method-specific** (e.g. registrar or `DidMethod` implementation). Use your DID method’s documented lifecycle operations.

#### availableMethods

Gets a list of available DID method names.

```kotlin
fun availableMethods(): List<String>
```

**Access via:** `trustWeave.configuration.didMethods.keys` or access registry directly

**Returns:** `List<String>` - List of registered DID method names

**Example:**
```kotlin
val methods = trustWeave.configuration.didMethods.keys
println("Available methods: $methods") // ["key", "web", "ion"]
```

### Credential Operations

#### issue

Issues a verifiable credential with cryptographic proof using the DSL.

```kotlin
suspend fun issue(block: IssuanceBuilder.() -> Unit): IssuanceResult
```

**Parameters:**

The DSL builder provides a fluent API for configuring the credential:

- **`credential { }`**: Configure the credential structure
  - `id(String)`: Set credential ID (optional, auto-generated if not provided)
  - `type(String...)`: Add credential types (first should be "VerifiableCredential")
  - `issuer(String)`: Set issuer DID
  - `subject { }`: Build credential subject with claims
  - `issued(Instant)`: Set issuance date
  - `expires(Instant)` or `expires(Long, ChronoUnit)`: Set expiration
  - `schema(String)`: Set credential schema
  - `status { }`: Configure revocation status

- **`signedBy(issuerDid: Did)`**: Specify issuer DID (key ID auto-extracted)
  - **`issuerDid`**: The DID of the credential issuer (type-safe `Did` object)
  - Key ID will be automatically extracted from the DID document during build
  
- **`signedBy(issuerDid: Did, keyId: String)`**: Specify issuer and explicit key ID for signing
  - **`issuerDid`**: The DID of the credential issuer (type-safe `Did` object)
  - **`keyId`**: The key ID fragment (e.g., `"key-1"`, not the full `"$issuerDid#key-1"`)

**Returns:** `IssuanceResult` - Sealed result type containing:

**Success Case:**
- `IssuanceResult.Success` containing:
  - `credential`: Signed `VerifiableCredential` with:
    - `id`: Auto-generated credential ID (UUID)
    - `issuer`: Issuer DID
    - `issuanceDate`: Current timestamp
    - `credentialSubject`: Provided subject data
    - `type`: Credential types
    - `proof`: Cryptographic proof (signature)

**Failure cases** (see `org.trustweave.credential.results.IssuanceResult`):

- **`IssuanceResult.Failure.UnsupportedFormat`** — No proof adapter for the requested proof suite (lists supported suites when known).
- **`IssuanceResult.Failure.AdapterNotReady`** — Credential service / format adapter not initialized.
- **`IssuanceResult.Failure.InvalidRequest`** — Invalid or incomplete issuance input (`field`, `reason`).
- **`IssuanceResult.Failure.AdapterError`** — Underlying adapter failure while issuing (`format`, `reason`, optional `cause`).
- **`IssuanceResult.Failure.MultipleFailures`** — Several failures aggregated.

Use **`issuanceResult.allErrors`** for a single string list, or **`getOrThrow()`** to unwrap success in tests.

**Edge cases (typical outcomes):**

- Missing signing key, resolver, or misconfigured DID method → often surfaces as **`AdapterError`** or **`InvalidRequest`** (check **`allErrors`**).
- Misconfigured **`TrustWeave.build { … }`** (no credential service) → **`AdapterNotReady`** from **`trustWeave.issue { }`**.
- Thrown exceptions (**`DidException`**, **`TrustWeaveException.ValidationFailed`**, …) can still occur from DID/KMS layers during the issuance pipeline; handle **`IssuanceResult`** for expected issuance failures and use **`try`/`catch`** where the stack may throw.

**Performance Characteristics:**

- **Time Complexity**:
  - O(1) for key lookup (if cached)
  - O(n) for proof generation where n = credential size
  - O(1) for DID resolution (if cached)
- **Network Calls**:
  - 1 call for DID resolution (unless cached)
  - 0 calls for signing (uses local KMS)
- **Thread Safety**:
  - Thread-safe (all operations are suspend functions)
  - Safe for concurrent use
- **Resource Usage**:
  - Memory: O(n) where n = credential size
  - CPU: Moderate (cryptographic operations)

**Example:**
```kotlin
import org.trustweave.did.identifiers.Did
import org.trustweave.credential.results.getOrThrow

// Simple usage with error handling

val issuerDid = Did("did:key:issuer")
val issuanceResult = trustWeave.issue {
    credential {
        type("VerifiableCredential", "PersonCredential")
        issuer(issuerDid)
        subject {
            id("did:key:subject")
            "name" to "Alice"
        }
    }
    signedBy(issuerDid = issuerDid, keyId = "key-1")
}

when (issuanceResult) {
    is IssuanceResult.Success -> {
        println("Issued credential: ${issuanceResult.credential.id}")
    }
    is IssuanceResult.Failure.UnsupportedFormat -> {
        println("Unsupported format: ${issuanceResult.format.value}")
        println("Supported: ${issuanceResult.supportedFormats.joinToString { it.value }}")
    }
    is IssuanceResult.Failure.AdapterNotReady -> {
        println("Adapter not ready (${issuanceResult.format.value}): ${issuanceResult.reason}")
    }
    is IssuanceResult.Failure.InvalidRequest -> {
        println("Invalid request: field '${issuanceResult.field}' — ${issuanceResult.reason}")
    }
    is IssuanceResult.Failure.AdapterError -> {
        println("Adapter error (${issuanceResult.format.value}): ${issuanceResult.reason}")
        issuanceResult.cause?.printStackTrace()
    }
    is IssuanceResult.Failure.MultipleFailures -> {
        println("Multiple failures: ${issuanceResult.allErrors.joinToString("; ")}")
    }
}

// For tests and examples, use getOrThrow()

val credential = trustWeave.issue {
    credential {
        type("VerifiableCredential", "PersonCredential")
        issuer(issuerDid)
        subject {
            id("did:key:subject")
            "name" to "Alice"
        }
    }
    signedBy(issuerDid = issuerDid, keyId = "key-1")
}.getOrThrow() // Requires `import org.trustweave.credential.results.getOrThrow`; throws IllegalStateException on failure
```

**Error types:** `UnsupportedFormat`, `AdapterNotReady`, `InvalidRequest`, `AdapterError`, `MultipleFailures` (see **`IssuanceResult`**).

#### verify

Verifies a verifiable credential by checking proof, issuer resolution, expiration, revocation, and optional trust or schema rules.

**Access via:** `trustWeave.verify(credential, …)` or `trustWeave.verify { … }`

```kotlin
suspend fun verify(
    credential: VerifiableCredential,
    checkRevocation: Boolean = true,
    checkExpiration: Boolean = true,
    timeout: Duration = 10.seconds
): VerificationResult

suspend fun verify(
    timeout: Duration = 10.seconds,
    block: VerificationBuilder.() -> Unit
): VerificationResult
```

**Package:** `org.trustweave.credential.results.VerificationResult` (sealed hierarchy).

**DSL builder highlights (`VerificationBuilder`):**

- **`credential(VerifiableCredential)`** — credential to verify (required in DSL overload)
- **`checkRevocation()` / `skipRevocation()`** — revocation checks (on by default)
- **`checkExpiration()` / `skipExpiration()`** — expiration checks (on by default)
- **`validateSchema(schemaId)` / `skipSchema()`** — optional JSON Schema / SHACL validation
- **`validateProofPurpose()`** — optional proof-purpose checks
- **`requireTrust(registry)` / `withTrustPolicy(policy)` / `allowUntrusted()`** — issuer trust via `TrustRegistry` or `TrustEvaluator`

**Returns:** A **`VerificationResult`**: use **`result.isValid`** or an exhaustive **`when`** on **`VerificationResult.Valid`** vs **`VerificationResult.Invalid`** (and its subclasses such as **`Invalid.Expired`**, **`Invalid.Revoked`**, **`Invalid.InvalidProof`**, **`Invalid.UntrustedIssuer`**, …). Successful results expose issuer/subject IRIs, issuance and expiry instants, and optional warnings.

**Performance characteristics:** Similar to before—cryptographic verification is O(1) per check; DID resolution may hit the network unless cached; revocation checks may fetch status lists.

**Edge cases (typical outcomes):**

- Malformed credential, unsupported proof suite, or adapter not configured → **`VerificationResult.Invalid.*`** or **`AdapterNotReady`**
- Expired / not-yet-valid / revoked / untrusted issuer → corresponding **`Invalid`** subtype
- Optional schema or trust failures → **`Invalid.SchemaValidationFailed`**, **`Invalid.UntrustedIssuer`**, etc.

**Example:**
```kotlin
import org.trustweave.credential.results.VerificationResult

// Convenience overload (default expiration + revocation checks)
when (val result = trustWeave.verify(credential)) {
    is VerificationResult.Valid -> {
        println("Valid: ${result.credential.id}")
        result.warnings.forEach { println("Warning: $it") }
    }
    is VerificationResult.Invalid -> {
        println("Invalid: ${result.allErrors.joinToString()}")
    }
}

// DSL overload (custom checks, trust policy, timeout)
when (
    val result = trustWeave.verify(timeout = 30.seconds) {
        credential(credential)
        checkRevocation()
        checkExpiration()
    }
) {
    is VerificationResult.Valid -> { /* … */ }
    is VerificationResult.Invalid.Expired -> { /* … */ }
    is VerificationResult.Invalid.Revoked -> { /* … */ }
    // … exhaustive handling
}
```

**Errors:**
- `TrustWeaveException.ValidationFailed` - Credential validation failed (missing fields, invalid structure)
- `DidException.DidMethodNotRegistered` - Issuer DID method not registered
- `DidException.DidNotFound` - Issuer DID cannot be resolved

### Revocation and Status List Management

TrustWeave provides comprehensive revocation management with blockchain anchoring support. See [Blockchain-Anchored Revocation](../core-concepts/blockchain-anchored-revocation.md) for detailed documentation.

#### createStatusList

Creates a new status list for managing credential revocation or suspension.

```kotlin
suspend fun createStatusList(
    issuerDid: String,
    purpose: StatusPurpose,
    size: Int = 131072,
    customId: String? = null
): Result<StatusListCredential>
```

**Parameters:**
- `issuerDid`: The DID of the issuer who owns this status list (required, must be resolvable)
- `purpose`: `StatusPurpose.REVOCATION` or `StatusPurpose.SUSPENSION` (required)
- `size`: Initial size of the status list in entries (default: 131072 = 16KB, must be power of 2)
- `customId`: Optional custom ID for the status list (auto-generated UUID if null)

**Returns:** `Result<StatusListCredential>` containing:
- `id`: Unique identifier for the status list
- `issuer`: DID of the issuer
- `purpose`: REVOCATION or SUSPENSION
- `size`: Number of entries in the status list
- `credential`: The status list credential document

**Performance Characteristics:**
- **Time Complexity:** O(1) for status list creation
- **Space Complexity:** O(N) where N is the size parameter
- **Network Calls:** 0 (local operation)
- **Thread Safety:** Thread-safe, can be called concurrently

**Edge Cases:**
- If `issuerDid` is not resolvable, returns `DidNotFound` error
- If `size` is not a power of 2, it may be rounded up to the nearest power of 2
- If `customId` conflicts with existing status list, a new ID is generated

**Example:**
```kotlin
val statusList = TrustWeave.createStatusList(
    issuerDid = "did:key:issuer",
    purpose = StatusPurpose.REVOCATION,
    size = 65536  // 8KB status list
).fold(
    onSuccess = { list ->
        println("Status List ID: ${list.id}")
        println("Size: ${list.size} entries")
    },
    onFailure = { error ->
        when (error) {
            is DidException.DidNotFound -> {
                println("Issuer DID not found: ${error.did}")
            }
            else -> println("Error: ${error.message}")
        }
    }
)
```

**Errors:**
- `DidException.DidNotFound` - Issuer DID cannot be resolved
- `DidException.InvalidDidFormat` - Invalid issuer DID format
- `TrustWeaveException.ValidationFailed` - Invalid size or purpose value

#### revokeCredential

Revokes a credential by adding it to a status list. The credential's index in the status list is determined by hashing the credential ID.

```kotlin
suspend fun revokeCredential(
    credentialId: String,
    statusListId: String
): Result<Boolean>
```

**Parameters:**
- `credentialId`: The ID of the credential to revoke (required, must be a valid URI or UUID)
- `statusListId`: The ID of the status list to add the credential to (required, must exist)

**Returns:** `Result<Boolean>` - `true` if revocation succeeded, `false` if already revoked

**Performance Characteristics:**
- **Time Complexity:** O(1) for bit manipulation (hash + bit set)
- **Space Complexity:** O(1) (in-place bit update)
- **Network Calls:** 0 (local operation, unless using blockchain-anchored registry)
- **Thread Safety:** Thread-safe, can be called concurrently

**Edge Cases:**
- If credential is already revoked, returns `true` (idempotent operation)
- If status list is full (all bits set), returns error
- If `statusListId` doesn't exist, returns error
- Hash collisions are extremely rare but possible (1 in 2^64 for default size)

**Example:**
```kotlin
val revoked = TrustWeave.revokeCredential(
    credentialId = "urn:uuid:credential-123",
    statusListId = statusList.id
).fold(
    onSuccess = { success ->
        if (success) {
            println("Credential revoked successfully")
        } else {
            println("Credential was already revoked")
        }
    },
    onFailure = { error ->
        when (error) {
            is TrustWeaveException.ValidationFailed -> {
                println("Invalid credential ID or status list ID")
            }
            else -> println("Revocation error: ${error.message}")
        }
    }
)
```

**Errors:**
- `TrustWeaveException.ValidationFailed` - Invalid credential ID or status list ID format
- `TrustWeaveException.InvalidState` - Status list not found or full

#### suspendCredential

Suspends a credential (temporarily disables it). The credential's index in the status list is determined by hashing the credential ID.

```kotlin
suspend fun suspendCredential(
    credentialId: String,
    statusListId: String
): Result<Boolean>
```

**Parameters:**
- `credentialId`: The ID of the credential to suspend (required, must be a valid URI or UUID)
- `statusListId`: The ID of the suspension status list (required, must exist)

**Returns:** `Result<Boolean>` - `true` if suspension succeeded, `false` if already suspended

**Performance Characteristics:**
- **Time Complexity:** O(1) for bit manipulation (hash + bit set)
- **Space Complexity:** O(1) (in-place bit update)
- **Network Calls:** 0 (local operation, unless using blockchain-anchored registry)
- **Thread Safety:** ✅ Thread-safe, can be called concurrently

**Edge Cases:**
- If credential is already suspended, returns `true` (idempotent operation)
- If status list is full (all bits set), returns error
- If `statusListId` doesn't exist, returns error
- Hash collisions are extremely rare but possible (1 in 2^64 for default size)

**Example:**
```kotlin
val suspended = TrustWeave.suspendCredential(
    credentialId = "urn:uuid:credential-123",
    statusListId = suspensionList.id
).fold(
    onSuccess = { success ->
        if (success) {
            println("✅ Credential suspended successfully")
        } else {
            println("⚠️ Credential was already suspended")
        }
    },
    onFailure = { error ->
        when (error) {
            is TrustWeaveException.ValidationFailed -> {
                println("❌ Invalid credential ID or status list ID")
            }
            is TrustWeaveException.InvalidState -> {
                println("❌ Status list not found or full")
            }
            else -> {
                println("❌ Suspension error: ${error.message}")
            }
        }
    }
)
```

**Errors:**
- `TrustWeaveException.ValidationFailed` - Invalid credential ID or status list ID format
- `TrustWeaveException.InvalidState` - Status list not found or full

#### checkRevocationStatus

Checks if a credential is revoked or suspended by examining its status list entry.

```kotlin
suspend fun checkRevocationStatus(
    credential: VerifiableCredential
): Result<RevocationStatus>
```

**Parameters:**
- `credential`: The credential to check (required, must have `credentialStatus` field if status list is used)

**Returns:** `Result<RevocationStatus>` containing:
- `revoked`: Whether the credential is revoked (`true` if revoked, `false` otherwise)
- `suspended`: Whether the credential is suspended (`true` if suspended, `false` otherwise)
- `statusListId`: The status list ID if applicable (from `credential.credentialStatus.id`)
- `reason`: Optional revocation reason (if provided during revocation)

**Performance Characteristics:**
- **Time Complexity:** O(1) for bit lookup (hash + bit check)
- **Space Complexity:** O(1) (no additional storage)
- **Network Calls:** 0 for local status lists, 1+ for blockchain-anchored status lists (if not cached)
- **Thread Safety:** ✅ Thread-safe, can be called concurrently

**Edge Cases:**
- If credential has no `credentialStatus` field → Returns `revoked = false`, `suspended = false`
- If status list not found → Returns `TrustWeaveException.InvalidState`
- If credential ID hash collision → Extremely rare (1 in 2^64), may return incorrect status
- If status list not accessible → Returns `TrustWeaveException.InvalidOperation`

**Example:**
```kotlin
val status = TrustWeave.checkRevocationStatus(credential).fold(
    onSuccess = { status ->
        when {
            status.revoked -> {
                println("❌ Credential is revoked")
                println("   Status List: ${status.statusListId}")
                status.reason?.let { println("   Reason: $it") }
            }
            status.suspended -> {
                println("⚠️ Credential is suspended")
                println("   Status List: ${status.statusListId}")
            }
            else -> {
                println("✅ Credential is valid (not revoked or suspended)")
            }
        }
    },
    onFailure = { error ->
        when (error) {
            is TrustWeaveException.InvalidState -> {
                println("❌ Status list not found")
            }
            is TrustWeaveException.InvalidOperation -> {
                println("❌ Cannot access status list: ${error.message}")
            }
            else -> {
                println("❌ Error checking revocation status: ${error.message}")
            }
        }
    }
)
```

**Errors:**
- `TrustWeaveException.InvalidState` - Status list not found or not accessible
- `TrustWeaveException.InvalidOperation` - Cannot access status list (network error, etc.)
- `TrustWeaveException.ValidationFailed` - Credential structure invalid (missing required fields)

#### Using BlockchainRevocationRegistry

For blockchain-anchored revocation, use `BlockchainRevocationRegistry` with an anchoring strategy:

```kotlin
import org.trustweave.credential.revocation.*
import java.time.Duration

// Create status list manager
val statusListManager = InMemoryStatusListManager()

// Create blockchain anchor client
val anchorClient = /* your blockchain anchor client */

// Create registry with periodic anchoring strategy
val registry = BlockchainRevocationRegistry(
    anchorClient = anchorClient,
    statusListManager = statusListManager,
    anchorStrategy = PeriodicAnchorStrategy(
        interval = Duration.ofHours(1),
        maxUpdates = 100
    ),
    chainId = "algorand:testnet"
)

// Create status list
val statusList = registry.createStatusList(
    issuerDid = "did:key:issuer",
    purpose = StatusPurpose.REVOCATION
)

// Revoke credential (automatic anchoring if threshold reached)
registry.revokeCredential("cred-123", statusList.id)

// Check pending anchors
val pending = registry.getPendingAnchor(statusList.id)
if (pending != null) {
    println("Pending updates: ${pending.updateCount}")
}

// Manual anchoring
val anchorRef = registry.anchorRevocationList(statusList, "algorand:testnet")
```

**Anchoring Strategies:**

1. **PeriodicAnchorStrategy** - Anchor on schedule or after N updates
   ```kotlin
   PeriodicAnchorStrategy(
       interval = Duration.ofHours(1),
       maxUpdates = 100
   )
   ```

2. **LazyAnchorStrategy** - Anchor only when verification is requested
   ```kotlin
   LazyAnchorStrategy(
       maxStaleness = Duration.ofDays(1)
   )
   ```

3. **HybridAnchorStrategy** - Combine periodic and lazy approaches
   ```kotlin
   HybridAnchorStrategy(
       periodicInterval = Duration.ofHours(1),
       maxUpdates = 100,
       forceAnchorOnVerify = true
   )
   ```

See [Blockchain-Anchored Revocation](../core-concepts/blockchain-anchored-revocation.md) for complete documentation and examples.

### Trust Operations

Trust operations allow you to manage trust anchors and verify issuer trust relationships. The trust registry must be configured during `TrustWeave.build { }`.

#### trust (DSL Style)

Performs trust operations using the trust DSL. Provides a fluent API for managing trust anchors and discovering trust paths.

```kotlin
suspend fun trust(block: suspend TrustBuilder.() -> Unit)
```

**Access via:** `trustWeave.trust { }`

**Parameters:**

The DSL builder provides a fluent API for trust operations:
- **`addAnchor(String) { }`**: Add a trust anchor with metadata
- **`removeAnchor(String)`**: Remove a trust anchor
- **`isTrusted(String, String?)`**: Check if an issuer is trusted
- **`findTrustPath(Did, Did)`** (extension in `trust` DSL): Find a sealed **`TrustPath`** between two DIDs
- **`getTrustedIssuers(String?)`**: Get all trusted issuers

**Returns:** `Unit`

**Edge Cases:**
- If trust registry is not configured → `IllegalStateException` with configuration instructions
- If anchor already exists → Returns without error (idempotent)
- If anchor doesn't exist when removing → Returns without error (idempotent)

**Example:**
```kotlin
import org.trustweave.did.identifiers.Did
import org.trustweave.testkit.services.*
import org.trustweave.trust.types.TrustPath

val trustWeave = TrustWeave.build {
    trust { provider(IN_MEMORY) }
}

trustWeave.trust {
    addAnchor("did:key:university") {
        credentialTypes("EducationCredential")
        description("Trusted university")
    }

    val isTrusted = isTrusted("did:key:university", "EducationCredential")
    when (val path = findTrustPath(Did("did:key:verifier"), Did("did:key:issuer"))) {
        is TrustPath.Verified -> println("Path: ${path.fullPath.joinToString(" -> ") { it.value }}")
        is TrustPath.NotFound -> println("No path")
    }
}
```

**Errors:**
- `IllegalStateException` - Trust registry is not configured

#### addTrustAnchor

Adds a trust anchor to the registry. Convenience method for adding a trust anchor without using the DSL.

```kotlin
suspend fun addTrustAnchor(
    anchorDid: String,
    block: TrustAnchorMetadataBuilder.() -> Unit = {}
): Boolean
```

**Access via:** `trustWeave.addTrustAnchor(anchorDid) { }`

**Parameters:**
- **`anchorDid`** (String, required): The DID of the trust anchor
- **`block`** (TrustAnchorMetadataBuilder.() -> Unit, optional): Configuration block for trust anchor metadata
  - `credentialTypes(String...)`: Credential types this anchor can issue
  - `description(String)`: Human-readable description
  - `metadata(Map<String, Any>)`: Additional metadata

**Returns:** `Boolean` - `true` if the anchor was added successfully, `false` if it already exists

**Example:**
```kotlin
val added = trustWeave.addTrustAnchor("did:key:university") {
    credentialTypes("EducationCredential", "TranscriptCredential")
    description("Trusted university")
}

if (added) {
    println("Trust anchor added")
} else {
    println("Trust anchor already exists")
}
```

**Errors:**
- `IllegalStateException` - Trust registry is not configured

#### removeTrustAnchor

Removes a trust anchor from the registry.

```kotlin
suspend fun removeTrustAnchor(anchorDid: String): Boolean
```

**Access via:** `trustWeave.removeTrustAnchor(anchorDid)`

**Parameters:**
- **`anchorDid`** (String, required): The DID of the trust anchor to remove

**Returns:** `Boolean` - `true` if the anchor was removed, `false` if it didn't exist

**Example:**
```kotlin
val removed = trustWeave.removeTrustAnchor("did:key:university")
if (removed) {
    println("Trust anchor removed")
}
```

**Errors:**
- `IllegalStateException` - Trust registry is not configured

#### isTrustedIssuer

Checks if an issuer is trusted for a specific credential type.

```kotlin
suspend fun isTrustedIssuer(
    issuerDid: String,
    credentialType: String? = null
): Boolean
```

**Access via:** `trustWeave.isTrustedIssuer(issuerDid, credentialType)`

**Parameters:**
- **`issuerDid`** (String, required): The DID of the issuer to check
- **`credentialType`** (String?, optional): The credential type (null means check for any type)

**Returns:** `Boolean` - `true` if the issuer is trusted, `false` otherwise

**Example:**
```kotlin
val isTrusted = trustWeave.isTrustedIssuer(
    issuerDid = "did:key:university",
    credentialType = "EducationCredential"
)

if (isTrusted) {
    println("Issuer is trusted for EducationCredential")
}
```

**Errors:**
- `IllegalStateException` - Trust registry is not configured

#### findTrustPath

Finds a trust path between verifier and issuer identities. Returns the sealed type **`TrustPath`** (**`Verified`** or **`NotFound`**).

```kotlin
suspend fun findTrustPath(
    verifier: VerifierIdentity,
    issuer: IssuerIdentity,
    timeout: Duration = 10.seconds
): TrustPath
```

**Access via:** `trustWeave.findTrustPath(verifier, issuer)` or, inside **`trustWeave.trust { }`**, **`findTrustPath(Did, Did)`** (extension).

**Example:**
```kotlin
import org.trustweave.did.identifiers.Did
import org.trustweave.trust.types.IssuerIdentity
import org.trustweave.trust.types.TrustPath
import org.trustweave.trust.types.VerifierIdentity

when (
    val path = trustWeave.findTrustPath(
        VerifierIdentity(Did("did:key:verifier")),
        IssuerIdentity(Did("did:key:issuer"))
    )
) {
    is TrustPath.Verified -> println("Trust path: ${path.fullPath.joinToString(" -> ") { it.value }}")
    is TrustPath.NotFound -> println("No trust path found")
}
```

**Errors:**
- `IllegalStateException` - Trust registry is not configured

#### getTrustedIssuers

Gets all trusted issuers for a specific credential type.

```kotlin
suspend fun getTrustedIssuers(credentialType: String? = null): List<String>
```

**Access via:** `trustWeave.getTrustedIssuers(credentialType)`

**Parameters:**
- **`credentialType`** (String?, optional): The credential type (null means all types)

**Returns:** `List<String>` - List of trusted issuer DIDs

**Example:**
```kotlin
val trustedIssuers = trustWeave.getTrustedIssuers("EducationCredential")
println("Trusted issuers: $trustedIssuers")
```

**Errors:**
- `IllegalStateException` - Trust registry is not configured

### Wallet Operations

#### wallet

Creates a wallet for storing credentials using the DSL. Wallets provide secure storage and management of verifiable credentials for a specific holder.

```kotlin
suspend fun wallet(block: WalletBuilder.() -> Unit): Wallet
```

**Access via:** `trustWeave.wallet { }`

**Parameters:**

The DSL builder provides a fluent API for configuring the wallet:

- **`holder(String)`**: The DID of the credential holder (required)
- **`id(String)`**: Unique wallet identifier (optional, auto-generated if not provided)
- **`enableOrganization()`**: Enable collections, tags, and metadata features
- **`enablePresentation()`**: Enable presentation and selective disclosure support

**Returns:** `Wallet` - The created wallet instance with:
- `walletId`: Unique wallet identifier
- `holderDid`: The holder's DID
- `capabilities`: Available wallet features (organization, presentation, etc.)

**Wallet Options:**
- `enableOrganization`: Enable collections, tags, and metadata features (default: `false`)
- `enablePresentation`: Enable presentation and selective disclosure support (default: `false`)
- `storagePath`: File system or bucket path for persistent storage (required for `FileSystem`/`S3` providers)
- `encryptionKey`: Secret material for at-rest encryption (optional, recommended for production)

**Performance Characteristics:**
- **Time Complexity:** O(1) for in-memory, O(1) for database (with index), O(1) for file system
- **Space Complexity:** O(1) for wallet creation (storage grows with credentials)
- **Network Calls:** 0 for `InMemory`, 1 for `Database`/`S3` (connection check)
- **Thread Safety:** Thread-safe creation, wallet instance may have provider-specific thread safety

**Edge Cases:**
- If `walletId` already exists for the provider, returns `WalletCreationFailed` error
- If `storagePath` is invalid or inaccessible, returns `WalletCreationFailed` error
- If `holderDid` format is invalid, returns `InvalidDidFormat` error
- If provider is not registered, returns `WalletCreationFailed` error

**Example:**
```kotlin
// Simple usage (in-memory, for testing)
val wallet = trustWeave.wallet {
    holder("did:key:holder")
}
println("Created wallet: ${wallet.walletId}")
println("Holder: ${wallet.holderDid}")

// With organization and presentation enabled
val wallet = trustWeave.wallet {
    holder("did:key:holder")
    id("my-wallet-id")
    enableOrganization()
    enablePresentation()
}

// Use wallet directly
wallet.store(credential)
val retrieved = wallet.get(credentialId)
val allCredentials = wallet.list()
```

**Errors:**
- `WalletException.WalletCreationFailed` - Wallet creation failed (provider not found, configuration invalid, storage unavailable, duplicate wallet ID)
- `DidException.InvalidDidFormat` - Invalid holder DID format
- `TrustWeaveException.ValidationFailed` - Invalid wallet options or configuration

### Advanced TrustWeave Methods

#### Lower-level access (no `getDslContext`)

The facade does not expose `getDslContext()`. For registries and clients, use **`trustWeave.configuration`** and facade helpers such as **`resolveDid`**, **`revocation { }`**, and **`revoke { }`**.

**Example:**
```kotlin
import org.trustweave.did.resolver.DidResolutionResult

val registry = trustWeave.configuration.didRegistry
val methods = registry.getAllMethodNames()

when (val res = trustWeave.resolveDid("did:key:example")) {
    is DidResolutionResult.Success -> println(res.document.id)
    is DidResolutionResult.Failure -> println("Failed")
}
```

#### configuration

Gets the underlying configuration object. Provides access to lower-level configuration details if needed.

```kotlin
val configuration: TrustWeaveConfig
```

**Access via:** `trustWeave.configuration`

**Returns:** `TrustWeaveConfig` - The configuration object

**When to use:**
- Inspecting registered DID methods
- Checking configured providers
- Advanced configuration access

**Example:**
```kotlin
val config = trustWeave.configuration
val didMethods = config.didMethods.keys
println("Registered DID methods: $didMethods")
```

**Note:** Most operations should be done through `TrustWeave` methods. Only use this for inspection or advanced use cases.

#### from (Companion Method)

Creates a TrustWeave from an existing TrustWeaveConfig. Useful when you already have a configuration object and want to create the facade wrapper.

```kotlin
fun from(config: TrustWeaveConfig): TrustWeave
```

**Access via:** `TrustWeave.from(config)`

**Parameters:**
- **`config`** (TrustWeaveConfig, required): The existing configuration object

**Returns:** `TrustWeave` - A TrustWeave instance wrapping the provided config

**When to use:**
- Reusing a configuration object
- Creating multiple TrustWeave instances from the same config
- Advanced configuration scenarios

**Example:**
```kotlin
import kotlinx.coroutines.runBlocking
import org.trustweave.testkit.services.*
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.dsl.credential.DidMethods.KEY
import org.trustweave.trust.dsl.credential.KeyAlgorithms.ED25519
import org.trustweave.trust.dsl.trustWeave

val config = runBlocking {
    trustWeave("my-instance") {
        keys { provider(IN_MEMORY); algorithm(ED25519) }
        did { method(KEY) { algorithm(ED25519) } }
    }
}

// Create multiple TrustWeave instances from the same config
val trustWeave1 = TrustWeave.from(config)
val trustWeave2 = TrustWeave.from(config)
```

**Note:** In most cases, you should use `TrustWeave.build { }` instead of creating a config first. This method is primarily for advanced scenarios where you need to reuse a configuration object.

**Note:** The configuration is shared between instances. Changes to one may affect others.

### Blockchain Anchoring

#### anchor

Anchors data to a blockchain for tamper evidence and timestamping. The data is serialized to JSON, canonicalized, and only the digest is stored on-chain (not the full data).

```kotlin
// TrustWeave.blockchains: BlockchainService
suspend fun <T : Any> anchor(
    data: T,
    serializer: KSerializer<T>,
    chainId: String
): AnchorResult
```

**Parameters:**
- `data`: The data to anchor (any serializable type, must be JSON-serializable)
- `serializer`: Kotlinx Serialization serializer for the data type (required)
- `chainId`: Chain ID in CAIP-2 format (e.g., `"algorand:testnet"`, `"polygon:mainnet"`)

**Returns:** `AnchorResult` (see `org.trustweave.anchor.AnchorResult`) with chain reference and payload metadata. On failure, throws `org.trustweave.anchor.exceptions.BlockchainException` (e.g. `ChainNotRegistered`).

**Performance Characteristics:**
- **Time Complexity:** O(N) for serialization where N is data size, O(1) for blockchain write
- **Space Complexity:** O(N) for serialization buffer
- **Network Calls:** 1-2 (blockchain transaction submission + confirmation, if required)
- **Blockchain Latency:** Varies by chain (Algorand: ~4s, Polygon: ~2s, Ethereum: ~15s)
- **Thread Safety:** Thread-safe, can be called concurrently (each call creates separate transaction)

**Edge Cases:**
- If data cannot be serialized, returns `ValidationFailed` error
- If chain is not registered, returns `ChainNotRegistered` error
- If blockchain transaction fails (network issue, insufficient funds), returns `Unknown` error with cause
- Large data (>1MB) may require chunking or off-chain storage (implementation-dependent)

**Note:** The full data is NOT stored on-chain. Only a cryptographic digest (hash) is stored. To retrieve the original payload, use `trustWeave.blockchains.read()` with the `AnchorRef` returned from anchoring (behavior depends on the anchor client implementation).

**Example:**
```kotlin
import org.trustweave.anchor.exceptions.BlockchainException

val myData = MyData(id = "123", value = "test")
try {
    val anchor = trustWeave.blockchains.anchor(
        data = myData,
        serializer = MyData.serializer(),
        chainId = "algorand:testnet"
    )
    println("Anchored at: ${anchor.ref.txHash}")
} catch (e: BlockchainException.ChainNotRegistered) {
    println("Chain not registered: ${e.chainId}; available: ${e.availableChains}")
}
```

#### read (`blockchains.read`)

Reads anchored data from a blockchain using the anchor reference.

```kotlin
suspend fun <T : Any> read(
    ref: AnchorRef,
    serializer: KSerializer<T>
): T
```

**Parameters:**
- `ref`: `AnchorRef` containing chain ID and transaction hash (required, must be valid)
- `serializer`: Kotlinx Serialization serializer for the data type (required, must match original type)

**Returns:** Deserialized `T`, or throws `BlockchainException` if the chain is not registered or read fails.

**Performance Characteristics:**
- **Time Complexity:** O(N) for deserialization where N is data size, O(1) for blockchain read
- **Space Complexity:** O(N) for deserialized data
- **Network Calls:** 1-2 (blockchain transaction read + data retrieval)
- **Blockchain Latency:** Varies by chain (typically faster than writes)
- **Thread Safety:** Thread-safe, can be called concurrently

**Edge Cases:**
- If `ref` points to non-existent transaction, returns `Unknown` error
- If data type doesn't match serializer, deserialization fails with `Unknown` error
- If on-chain digest doesn't match data digest, returns `ValidationFailed` error (tamper detection)
- If chain is not registered, returns `ChainNotRegistered` error

**Note:** This method reads the data that was stored during `anchor()`. The data must be stored separately (not on-chain, only digest is on-chain). If using a storage-backed anchor client, it retrieves from storage. Otherwise, you must provide the data separately and this verifies the digest matches.

**Example:**
```kotlin
// Simple usage
val anchorRef = AnchorRef(
    chainId = "algorand:testnet",
    txHash = "abc123..."
)
try {
    val data = trustweave.blockchains.read<MyData>(
        ref = anchorRef,
        serializer = MyData.serializer()
    )
    println("Read data: $data")
    println("Verified against on-chain digest")
} catch (error: TrustWeaveException) {
    when (error) {
        is BlockchainException.ChainNotRegistered -> {
            println("Chain not registered: ${error.chainId}")
            println("Available chains: ${error.availableChains}")
        }
        is TrustWeaveException.ValidationFailed -> {
            println("Data verification failed - possible tampering!")
            println("Reason: ${error.reason}")
        }
        else -> {
            println("Read error: ${error.message}")
        }
    }
}
```

**Errors:**
- `BlockchainException.ChainNotRegistered` - Chain ID not registered in registry
- `TrustWeaveException.ValidationFailed` - Data digest doesn't match on-chain digest (tamper detected) or deserialization failed
- `TrustWeaveException.Unknown` - Transaction not found or data retrieval failed

#### availableChains

Gets a list of available blockchain chain IDs.

```kotlin
fun availableChains(): List<String>
```

**Access via:** `trustWeave.blockchains.availableChains()`

**Returns:** `List<String>` - List of registered blockchain chain IDs in CAIP-2 format

**Example:**
```kotlin
val chains = trustWeave.blockchains.availableChains()
println("Available chains: $chains") // ["algorand:testnet", "polygon:mainnet"]
```

### Smart Contract Operations

The `contracts` service provides operations for creating, binding, and executing smart contracts.

#### draft

Creates a contract draft.

```kotlin
suspend fun draft(request: ContractDraftRequest): Result<SmartContract>
```

**Access via:** `trustWeave.contracts.draft(request)`

**Parameters:**
- **`request`** (ContractDraftRequest, required): Contract draft request containing contract type, execution model, parties, terms, etc.

**Returns:** `Result<SmartContract>` - The created contract draft

**Example:**
```kotlin
val contract = trustweave.contracts.draft(
    request = ContractDraftRequest(
        contractType = ContractType.Insurance,
        executionModel = ExecutionModel.Parametric(...),
        parties = ContractParties(...),
        terms = ContractTerms(...),
        effectiveDate = Instant.now().toString(),
        contractData = buildJsonObject { ... }
    )
).getOrThrow()
```

#### bindContract

Binds a contract by issuing a credential and anchoring it to a blockchain.

```kotlin
suspend fun bindContract(
    contractId: String,
    issuerDid: String,
    issuerKeyId: String,
    chainId: String
): Result<BoundContract>
```

**Access via:** `trustWeave.contracts.bindContract(...)`

**Returns:** `Result<BoundContract>` - The bound contract with credential and anchor reference

#### executeContract

Executes a contract based on its execution model.

```kotlin
suspend fun executeContract(
    contract: SmartContract,
    executionContext: ExecutionContext
): Result<ExecutionResult>
```

**Access via:** `trustWeave.contracts.executeContract(contract, executionContext)`

**Returns:** `Result<ExecutionResult>` - The execution result

#### Other Contract Methods

- **`issueContractCredential(...)`** (on **`SmartContractService`**): Issues a verifiable credential bound to a contract workflow
- **`anchorContract(contract, credential, chainId)`**: Anchors a contract to a blockchain
- **`activateContract(contractId)`**: Activates a contract (moves from PENDING to ACTIVE)
- **`evaluateConditions(contract, inputData)`**: Evaluates contract conditions
- **`updateStatus(contractId, newStatus, reason, metadata)`**: Updates contract status
- **`getContract(contractId)`**: Gets a contract by ID
- **`verifyContract(credentialId)`**: Verifies a contract credential

See [Smart Contract API](smart-contract-api.md) for detailed documentation.

### Resource cleanup (`close`)

The **`TrustWeave`** facade implements **`Closeable`**. Call **`close()`** when an instance is discarded so KMS connections, anchor clients, and other **`Closeable`** collaborators can release resources.

```kotlin
val trustWeave = runBlocking { TrustWeave.quickStart() }
try {
    // use trustWeave
} finally {
    trustWeave.close()
}
```

If you implement **`PluginLifecycle`** on your own adapters, **you** invoke those hooks from your composition root; the facade does not expose **`initialize()`** / **`start()`** / **`stop()`** methods. See [Plugin lifecycle](../advanced/plugin-lifecycle.md).

## Error Types

Facade methods return **sealed results** (`IssuanceResult`, `VerificationResult`, `DidCreationResult`, …) or **throw** domain exceptions extending **`TrustWeaveException`** (`DidException`, `BlockchainException`, `PluginException`, …). See [Error handling](../advanced/error-handling.md).

### Common Error Types

- `DidException.DidMethodNotRegistered` - DID method not registered
- `DidException.InvalidDidFormat` - Invalid DID format
- `TrustWeaveException.ValidationFailed` - Credential validation failed
- `BlockchainException.ChainNotRegistered` - Chain ID not registered
- `WalletException.WalletCreationFailed` - Wallet creation failed
- `PluginException.InitializationFailed` - Plugin initialization failed

## Error Handling

### Sealed results (credential pipeline and many facade APIs)

**`issue`**, **`verify`**, **`presentationResult`**, and **batch** flows return sealed types (`IssuanceResult`, `VerificationResult`, `PresentationResult`). **`createDid`** and several DID lifecycle APIs return **`DidCreationResult`** / related sealed types—use `when` (or test-only `getOrThrowDid()`), not a blanket try-catch, for those failures.

See [API patterns — results vs exceptions](../getting-started/api-patterns.md#api-contract-results-vs-exceptions) and [Result types guide](result-types-guide.md).

**Example (credential + DID results):**
```kotlin
import kotlinx.coroutines.runBlocking
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.dsl.credential.DidMethods.KEY
import org.trustweave.trust.dsl.credential.KeyAlgorithms.ED25519
import org.trustweave.credential.results.IssuanceResult
import org.trustweave.credential.results.VerificationResult
import org.trustweave.trust.types.DidCreationResult
import org.trustweave.testkit.services.*

fun main() = runBlocking {
    val trustWeave = TrustWeave.quickStart()

    val did = when (val dr = trustWeave.createDid { method(KEY); algorithm(ED25519) }) {
        is DidCreationResult.Success -> dr.did
        is DidCreationResult.Failure -> return@runBlocking
    }

    val issued = trustWeave.issue {
        credential { /* ... */ }
        signedBy(did)
    }
    val credential = when (issued) {
        is IssuanceResult.Success -> issued.credential
        is IssuanceResult.Failure -> return@runBlocking
    }

    val verification = trustWeave.verify(credential)
    when (verification) {
        is VerificationResult.Valid -> { /* ... */ }
        is VerificationResult.Invalid -> { /* exhaustive branches */ }
    }
}
```

### Exceptions and unwrapping helpers

- **`getOrThrow()`** / **`getOrThrowDid()`** throw **`IllegalStateException`** on failure—wrap call sites if you use them in production.
- **`wallet { }`** and some integration paths may throw **`TrustWeaveException`** subclasses (see [Error handling](../advanced/error-handling.md)).
### `Result<T>` and lower-level APIs

Some services return **`Result<T>`** for composition (plugins, custom implementations). Handle with `fold` or `getOrElse`.

**Best Practice:** Handle sealed results with exhaustive `when`; never treat **`AdapterNotReady`** results as success, and never log placeholder credentials from misconfigured **`verify { }`** as real holder data (see [Production checklist](../getting-started/production-integration-checklist.md)).

## Configuration

### Registering DID Methods

DID methods are registered during `TrustWeave` creation using the DSL:

```kotlin
import org.trustweave.trust.TrustWeave
import org.trustweave.testkit.services.*

val trustWeave = TrustWeave.build {
    keys {
        provider(IN_MEMORY)
        algorithm(ED25519)
    }
    did {
        method(KEY) {
            algorithm(ED25519)
        }
        method(WEB) {
            domain("example.com")
        }
        method(ION) {
            // ION-specific configuration
        }
    }
}
```

### Registering Blockchain Anchors

Blockchain anchor clients are registered during `TrustWeave` creation:

```kotlin
val trustWeave = TrustWeave.build {
    keys { ... }
    did { ... }
    anchor {
        chain("algorand:testnet") {
            provider(ALGORAND)
            // Chain-specific configuration
        }
        chain("polygon:mainnet") {
            provider(POLYGON)
        }
    }
}
```

### Registering Trust Registry

Trust registry is configured during `TrustWeave` creation:

```kotlin
import org.trustweave.testkit.services.*
val trustWeave = TrustWeave.build {
    keys { ... }
    did { ... }
    trust {
        provider(IN_MEMORY)
        // Or use a custom trust registry implementation
    }
}
```

**Note:** For advanced configuration with custom services, proof generators, or credential services, you may need to configure the underlying `TrustWeaveConfig` directly. See [Advanced Configuration](../advanced/README.md) for details.

## Related Documentation

- Error Handling](../advanced/error-handling.md) - Detailed error handling patterns
- Plugin Lifecycle](../advanced/plugin-lifecycle.md) - Plugin lifecycle management
- Wallet API](wallet-api.md) - Wallet operations reference
- Credential Service API](credential-service-api.md) - Credential service SPI
- DIDs Core Concept](../core-concepts/dids.md) - DID concepts and usage
- Verifiable Credentials Core Concept](../core-concepts/verifiable-credentials.md) - Credential concepts
- Troubleshooting](../getting-started/troubleshooting.md) - Common issues and solutions

