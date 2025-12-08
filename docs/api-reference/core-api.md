---
title: Core API Reference
nav_order: 4
parent: API Reference
keywords:
  - api
  - core api
  - trustlayer
  - dids
  - credentials
  - wallets
  - blockchain
  - trust
---

# Core API Reference

Complete API reference for TrustWeave's TrustWeave API.

> **Version:** 1.0.0-SNAPSHOT
> **Kotlin:** 2.2.0+ | **Java:** 21+
> See [CHANGELOG.md](../../CHANGELOG.md) for version history and migration guides.
>
> **Note:** This API reference documents the `TrustWeave` API, which is the primary interface for trust and identity operations in TrustWeave. The TrustWeave provides a DSL-based API for creating DIDs, issuing credentials, managing wallets, and more.

```kotlin
dependencies {
    implementation("com.trustweave:trustweave-all:1.0.0-SNAPSHOT")
}
```

## Overview

The TrustWeave provides a unified, DSL-based API for decentralized identity and trust operations. The API uses suspend functions and DSL builders for type-safe, fluent operations.

**Key Concepts:**
- **TrustWeave**: The main entry point for trust and identity operations
- **DSL Builders**: Fluent builders for configuring and performing operations (e.g., `issue { }`, `createDid { }`)
- **Configuration**: Configure KMS, DID methods, anchors, and more using the `TrustWeave.build { }` DSL
- **Context**: The `TrustWeaveContext` provides access to underlying services when needed

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
| Create DID | `trustWeave.createDid { }` | `String` (DID) |
| Update DID | `trustWeave.updateDid { }` | `DidDocument` |
| Delegate DID | `trustWeave.delegate { }` | `DelegationChainResult` |
| Rotate Key | `trustWeave.rotateKey { }` | `Any` |
| Issue Credential | `trustWeave.issue { }` | `VerifiableCredential` |
| Verify Credential | `trustWeave.verify { }` | `CredentialVerificationResult` |
| Create Wallet | `trustWeave.wallet { }` | `Wallet` |
| Trust Operations (DSL) | `trustWeave.trust { }` | `Unit` |
| Add Trust Anchor | `trustWeave.addTrustAnchor(did) { }` | `Boolean` |
| Remove Trust Anchor | `trustWeave.removeTrustAnchor(did)` | `Boolean` |
| Is Trusted Issuer | `trustWeave.isTrustedIssuer(did, type?)` | `Boolean` |
| Get Trust Path | `trustWeave.getTrustPath(fromDid, toDid)` | `TrustPathResult?` |
| Get Trusted Issuers | `trustWeave.getTrustedIssuers(type?)` | `List<String>` |
| Get DSL Context | `trustWeave.getDslContext()` | `TrustWeaveContext` |
| Get Configuration | `trustWeave.configuration` | `TrustWeaveConfig` |
| Create from Config | `TrustWeave.from(config)` | `TrustWeave` |

## TrustWeave Class

### Creating TrustWeave Instances

```kotlin
import com.trustweave.trust.TrustWeave
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    // Create with defaults (in-memory KMS, did:key method)
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
    }

    // Create with custom configuration
    val trustWeave = TrustWeave.build {
        keys {
            provider("inMemory")
            algorithm("Ed25519")
        }
        did {
            method("key") {
                algorithm("Ed25519")
            }
            method("web") {
                domain("example.com")
            }
        }
        anchor {
            chain("algorand:testnet") {
                provider("algorand")
            }
        }
        trust {
            provider("inMemory")
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
- **`trust { }`**: Manage trust anchors (DSL style)
- **`addTrustAnchor()`**: Add trust anchor (direct method)
- **`removeTrustAnchor()`**: Remove trust anchor (direct method)
- **`isTrustedIssuer()`**: Check if issuer is trusted
- **`getTrustPath()`**: Find trust path between DIDs
- **`getTrustedIssuers()`**: Get all trusted issuers
- **`getDslContext()`**: Access underlying DSL context (advanced)
- **`configuration`**: Access configuration (advanced)

**Example:**
```kotlin
val trustLayer = TrustLayer.build { ... }

// Create DID
val did = trustWeave.createDid {
    method("key")
    algorithm("Ed25519")
}

// Issue credential
val credential = trustWeave.issue {
    credential { ... }
    signedBy(issuerDid = did, keyId = "$did#key-1")
}

// Create wallet
val wallet = trustWeave.wallet {
    holder("did:key:holder")
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
- If method not registered → `TrustWeaveError.DidMethodNotRegistered` with available methods list
- If algorithm not supported by method → `TrustWeaveError.ValidationFailed` with reason
- If KMS fails to generate key → `TrustWeaveError.InvalidOperation` with context
- If method-specific validation fails → `TrustWeaveError.ValidationFailed` with field details

**Performance:**
- Time complexity: O(1) for key generation (if cached)
- Network calls: 0 (local key generation for did:key)
- Thread-safe: ✅ Yes (suspend function, thread-safe KMS operations)

**Example:**
```kotlin
// Simple usage (uses defaults: did:key, ED25519)
val didResult = trustWeave.createDid {
    method("key")
    algorithm("Ed25519")
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
        didResult.cause?.printStackTrace()
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

// For tests and examples, use getOrFail() helper
import com.trustweave.testkit.getOrFail

val did = trustWeave.createDid {
    method("key")
    algorithm("Ed25519")
}.getOrFail() // Throws AssertionError on failure

// With custom method
val webDidResult = trustLayer.createDid {
    method("web")
    domain("example.com")
}
when (webDidResult) {
    is DidCreationResult.Success -> println("Created: ${webDidResult.did.value}")
    else -> println("Error: ${webDidResult.reason}")
}
```

**Error Types:**
- `DidCreationResult.Failure.MethodNotRegistered` - Method is not registered (includes available methods)
- `DidCreationResult.Failure.KeyGenerationFailed` - Key generation failed (includes reason and optional cause)
- `DidCreationResult.Failure.DocumentCreationFailed` - Document creation failed (includes reason and optional cause)
- `DidCreationResult.Failure.InvalidConfiguration` - Configuration validation failed (includes reason and details)
- `DidCreationResult.Failure.Other` - Other error (includes reason and optional cause)

#### resolve

Resolves a DID to its document.

```kotlin
suspend fun resolve(did: String): DidResolutionResult
```

**Access via:** `trustLayer.getDslContext().resolveDid(did)` or use DID resolver directly

**Parameters:**

- **`did`** (String, required): The DID string to resolve
  - **Format**: Must match `did:<method>:<identifier>` pattern
  - **Example**: `"did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK"`
  - **Validation**: Automatically validated before resolution
  - **Requirements**: Method must be registered in TrustWeave instance

**Returns:** `DidResolutionResult` directly (not wrapped in Result)

**DidResolutionResult Structure:**
```kotlin
data class DidResolutionResult(
    val document: DidDocument?,  // null if DID not found
    val metadata: DidResolutionMetadata
)

data class DidResolutionMetadata(
    val method: String,           // DID method used for resolution
    val resolvedAt: Instant,      // Resolution timestamp
    val resolverVersion: String?, // Resolver version (if available)
    val error: String?            // Error message if resolution failed
)
```

**Properties:**
- `document`: The DID document if found, `null` if not found
- `metadata.method`: The DID method that was resolved
- `metadata.resolvedAt`: When the resolution occurred
- `metadata.resolverVersion`: Version of resolver used (if available)
- `metadata.error`: Error message if resolution failed (document will be null)

**Failure Case:**
- Throws `TrustWeaveError` exception with specific error type
- For error handling, wrap in try-catch or use extension functions

**Edge Cases:**
- If DID format invalid → `TrustWeaveError.InvalidDidFormat` with reason
- If method not registered → `TrustWeaveError.DidMethodNotRegistered` with available methods
- If DID not found → `TrustWeaveError.DidNotFound` with DID and available methods
- If resolution service unavailable → `TrustWeaveError.InvalidOperation` with context

**Performance:**
- Time complexity: O(1) for local methods (did:key), O(n) for network methods where n = network latency
- Network calls: 0 for did:key (local), 1+ for network-based methods (did:web, did:ion)
- Thread-safe: ✅ Yes (suspend function)

**Example:**
```kotlin
import com.trustweave.did.identifiers.Did

// Simple usage - access resolver via DSL context
val context = trustLayer.getDslContext()
val resolver = context.getDidResolver()
val did = Did("did:key:z6Mk...")
val result = resolver?.resolve(did)
if (result?.document != null) {
    println("DID resolved: ${result.document.id}")
} else {
    println("DID not found")
}
```

**Errors:**
- `TrustWeaveError.InvalidDidFormat` - Invalid DID format (includes reason)
- `TrustWeaveError.DidMethodNotRegistered` - Method is not registered (includes available methods)
- `TrustWeaveError.DidNotFound` - DID not found (includes DID and available methods)

#### update

Updates a DID document by applying a transformation function.

```kotlin
suspend fun updateDid(block: DidUpdateBuilder.() -> Unit): DidUpdateResult
```

**Access via:** `trustLayer.updateDid { }`

**Parameters:**
- **`did`** (String, required): The DID to update (via DSL builder)
- **`addKey { }`**, **`addService { }`**, etc.: DSL methods for updating the document

**Returns:** `DidUpdateResult` - Sealed result type containing:
- **Success**: `DidUpdateResult.Success` with `document`: The updated DID document
- **Failure**: Various failure types (DidNotFound, AuthorizationFailed, UpdateFailed, Other)

**Edge Cases:**
- If DID format invalid → `TrustWeaveError.InvalidDidFormat`
- If method not registered → `TrustWeaveError.DidMethodNotRegistered`
- If update operation fails → `TrustWeaveError.InvalidOperation`

**Example:**
```kotlin
import com.trustweave.did.identifiers.Did

val did = Did("did:key:example")
val updateResult = trustLayer.updateDid {
    did(did.value)  // DSL builder accepts string for convenience
    addService {
        id("${did.value}#service-1")
        type("LinkedDomains")
        endpoint("https://example.com/service")
    }
}

when (updateResult) {
    is DidUpdateResult.Success -> {
        println("Updated DID document: ${updateResult.document.id}")
    }
    is DidUpdateResult.Failure.DidNotFound -> {
        println("DID not found: ${updateResult.did.value}")
    }
    is DidUpdateResult.Failure.AuthorizationFailed -> {
        println("Authorization failed: ${updateResult.reason}")
    }
    is DidUpdateResult.Failure.UpdateFailed -> {
        println("Update failed: ${updateResult.reason}")
    }
    is DidUpdateResult.Failure.Other -> {
        println("Error: ${updateResult.reason}")
    }
}
```

#### deactivate

Deactivates a DID, marking it as no longer active.

```kotlin
suspend fun deactivateDid(did: Did): Boolean
```

**Access via:** `trustLayer.getDslContext().deactivateDid(did)` or use DID method directly

**Parameters:**
- **`did`** (Did, required): Type-safe DID identifier to deactivate

**Returns:** `Boolean` - `true` if deactivated successfully, `false` otherwise

**Edge Cases:**
- If DID format invalid → `TrustWeaveError.InvalidDidFormat`
- If method not registered → `TrustWeaveError.DidMethodNotRegistered`
- If DID already deactivated → Returns `false`

**Example:**
```kotlin
import com.trustweave.did.identifiers.Did

// Access via DSL context
val context = trustLayer.getDslContext()
val did = Did("did:key:example")
val deactivated = context.deactivateDid(did)
if (deactivated) {
    println("DID deactivated successfully")
}
```

#### availableMethods

Gets a list of available DID method names.

```kotlin
fun availableMethods(): List<String>
```

**Access via:** `trustLayer.configuration.didMethods.keys` or access registry directly

**Returns:** `List<String>` - List of registered DID method names

**Example:**
```kotlin
val methods = trustLayer.configuration.didMethods.keys
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

- **`signedBy(issuerDid: String, keyId: String)`**: Specify issuer and key for signing
  - **`issuerDid`**: The DID of the credential issuer
  - **`keyId`**: The key ID from issuer's DID document (e.g., `"$issuerDid#key-1"`)

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

**Failure Cases:**
- `IssuanceResult.Failure.IssuerResolutionFailed` - Issuer DID resolution failed
- `IssuanceResult.Failure.KeyNotFound` - Key not found in issuer DID document
- `IssuanceResult.Failure.SigningFailed` - Signing operation failed
- `IssuanceResult.Failure.ProofGenerationFailed` - Proof generation failed
- `IssuanceResult.Failure.InvalidCredential` - Credential validation failed
- `IssuanceResult.Failure.SchemaValidationFailed` - Schema validation failed
- `IssuanceResult.Failure.Other` - Other error with reason and optional cause

**Edge Cases:**

- **If `issuerKeyId` doesn't exist in DID document**:
  - Returns: `TrustWeaveError.InvalidDidFormat` with reason indicating key not found
  - Solution: Verify key exists using `didDocument.verificationMethod.find { it.id == issuerKeyId }`

- **If `credentialSubject` missing `"id"` field**:
  - Returns: `TrustWeaveError.CredentialInvalid` with `field = "credentialSubject.id"`
  - Solution: Always include `"id"` field in credential subject

- **If issuer DID method not registered**:
  - Returns: `TrustWeaveError.DidMethodNotRegistered` with available methods list
  - Solution: Register DID method or use available method

- **If issuer DID not resolvable**:
  - Returns: `TrustWeaveError.DidNotFound` with available methods
  - Solution: Ensure DID is valid and method is registered

- **If `expirationDate` is invalid format**:
  - Returns: `TrustWeaveError.ValidationFailed` with reason
  - Solution: Use ISO 8601 format (e.g., `"2025-12-31T23:59:59Z"`)

**Performance Characteristics:**

- **Time Complexity**:
  - O(1) for key lookup (if cached)
  - O(n) for proof generation where n = credential size
  - O(1) for DID resolution (if cached)
- **Network Calls**:
  - 1 call for DID resolution (unless cached)
  - 0 calls for signing (uses local KMS)
- **Thread Safety**:
  - ✅ Thread-safe (all operations are suspend functions)
  - ✅ Safe for concurrent use
- **Resource Usage**:
  - Memory: O(n) where n = credential size
  - CPU: Moderate (cryptographic operations)

**Example:**
```kotlin
// Simple usage with error handling
val issuanceResult = trustWeave.issue {
    credential {
        type("VerifiableCredential", "PersonCredential")
        issuer("did:key:issuer")
        subject {
            id("did:key:subject")
            "name" to "Alice"
        }
    }
    signedBy(issuerDid = "did:key:issuer", keyId = "did:key:issuer#key-1")
}

when (issuanceResult) {
    is IssuanceResult.Success -> {
        println("Issued credential: ${issuanceResult.credential.id}")
    }
    is IssuanceResult.Failure.IssuerResolutionFailed -> {
        println("Issuer DID resolution failed: ${issuanceResult.issuerDid}")
        println("Reason: ${issuanceResult.reason}")
    }
    is IssuanceResult.Failure.KeyNotFound -> {
        println("Key not found: ${issuanceResult.keyId}")
        println("Reason: ${issuanceResult.reason}")
    }
    is IssuanceResult.Failure.SigningFailed -> {
        println("Signing failed: ${issuanceResult.reason}")
        issuanceResult.cause?.printStackTrace()
    }
    is IssuanceResult.Failure.ProofGenerationFailed -> {
        println("Proof generation failed: ${issuanceResult.reason}")
    }
    is IssuanceResult.Failure.InvalidCredential -> {
        println("Invalid credential: ${issuanceResult.reason}")
        if (issuanceResult.errors.isNotEmpty()) {
            println("Errors: ${issuanceResult.errors.joinToString()}")
        }
    }
    is IssuanceResult.Failure.SchemaValidationFailed -> {
        println("Schema validation failed: ${issuanceResult.reason}")
        println("Errors: ${issuanceResult.errors.joinToString()}")
    }
    is IssuanceResult.Failure.Other -> {
        println("Error: ${issuanceResult.reason}")
        issuanceResult.cause?.printStackTrace()
    }
}

// For tests and examples, use getOrFail() helper
import com.trustweave.testkit.getOrFail

val credential = trustWeave.issue {
    credential {
        type("VerifiableCredential", "PersonCredential")
        issuer(issuerDid)
        subject {
            id("did:key:subject")
            "name" to "Alice"
        }
    }
    signedBy(issuerDid = issuerDid, keyId = "$issuerDid#key-1")
}.getOrFail() // Throws AssertionError on failure
```

**Error Types:**
- `IssuanceResult.Failure.IssuerResolutionFailed` - Issuer DID resolution failed
- `IssuanceResult.Failure.KeyNotFound` - Key not found in issuer DID document
- `IssuanceResult.Failure.SigningFailed` - Signing operation failed (includes reason and optional cause)
- `IssuanceResult.Failure.ProofGenerationFailed` - Proof generation failed (includes reason and optional cause)
- `IssuanceResult.Failure.InvalidCredential` - Credential validation failed (includes reason and errors list)
- `IssuanceResult.Failure.SchemaValidationFailed` - Schema validation failed (includes reason and errors list)
- `IssuanceResult.Failure.Other` - Other error (includes reason and optional cause)

#### verify

Verifies a verifiable credential by checking proof, issuer DID resolution, expiration, and revocation status.

```kotlin
suspend fun verify(block: VerificationBuilder.() -> Unit): CredentialVerificationResult
```

**Access via:** `trustLayer.verify { }`

**Parameters:**

The DSL builder provides a fluent API for configuring verification:

- **`credential(VerifiableCredential)`**: The credential to verify (required)
- **`checkExpiration(Boolean)`**: Check if credential has expired (default: `true`)
- **`checkRevocation(Boolean)`**: Check revocation status (default: `true`)
- **`checkTrust(Boolean)`**: Verify issuer is trusted (default: `false`)
- **`expectedAudience(String?)`**: Expected audience DID (default: `null`)

**Returns:** `CredentialVerificationResult` containing:
- `valid`: Overall validity (all checks passed)
- `proofValid`: Proof signature is valid
- `issuerValid`: Issuer DID resolved successfully
- `notExpired`: Credential has not expired (if expiration checked)
- `notRevoked`: Credential is not revoked (if revocation checked)
- `errors`: List of error messages if verification failed
- `warnings`: List of warnings (e.g., expiring soon, missing optional fields)

**Performance Characteristics:**
- **Time Complexity:**
  - O(1) for proof verification (cryptographic operation)
  - O(1) for DID resolution (if cached), O(N) for network-based methods
  - O(1) for expiration check
  - O(1) for revocation check (if status list cached)
- **Network Calls:**
  - 1 call for issuer DID resolution (unless cached)
  - 0-1 calls for revocation status check (if enabled and not cached)
- **Thread Safety:** ✅ Thread-safe, can be called concurrently
- **Resource Usage:**
  - Memory: O(N) where N = credential size
  - CPU: Moderate (cryptographic operations)

**Edge Cases:**
- If credential has no proof → `proofValid = false`, `valid = false`
- If issuer DID cannot be resolved → `issuerValid = false`, `valid = false`
- If credential expired and `checkExpiration = true` → `notExpired = false`, `valid = false`
- If credential revoked and `enforceStatus = true` → `notRevoked = false`, `valid = false`
- If proof signature invalid → `proofValid = false`, `valid = false`
- If issuer DID method not registered → Returns `TrustWeaveError.DidMethodNotRegistered`
- If credential structure invalid → Returns `TrustWeaveError.CredentialInvalid`

**Example:**
```kotlin
// Simple usage
val result = trustLayer.verify {
    credential(credential)
}
if (result.valid) {
    println("Credential is valid")
} else {
    println("Errors: ${result.errors}")
}

// With custom verification configuration
val result = trustLayer.verify {
    credential(credential)
    checkExpiration(true)
    checkRevocation(true)
    checkTrust(true) // Verify issuer is trusted
}

if (result.valid) {
    println("✅ Credential is valid")
    println("   Proof valid: ${result.proofValid}")
    println("   Issuer valid: ${result.issuerValid}")
    println("   Not expired: ${result.notExpired}")
    println("   Not revoked: ${result.notRevoked}")

    if (result.warnings.isNotEmpty()) {
        println("   Warnings: ${result.warnings.joinToString()}")
    }
} else {
    println("❌ Credential invalid")
    println("   Errors: ${result.errors.joinToString()}")
}
```

**Errors:**
- `TrustWeaveError.CredentialInvalid` - Credential validation failed (missing fields, invalid structure)
- `TrustWeaveError.DidMethodNotRegistered` - Issuer DID method not registered
- `TrustWeaveError.DidNotFound` - Issuer DID cannot be resolved

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
            is TrustWeaveError.DidNotFound -> {
                println("Issuer DID not found: ${error.did}")
            }
            else -> println("Error: ${error.message}")
        }
    }
)
```

**Errors:**
- `TrustWeaveError.DidNotFound` - Issuer DID cannot be resolved
- `TrustWeaveError.InvalidDidFormat` - Invalid issuer DID format
- `TrustWeaveError.ValidationFailed` - Invalid size or purpose value

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
            is TrustWeaveError.ValidationFailed -> {
                println("Invalid credential ID or status list ID")
            }
            else -> println("Revocation error: ${error.message}")
        }
    }
)
```

**Errors:**
- `TrustWeaveError.ValidationFailed` - Invalid credential ID or status list ID format
- `TrustWeaveError.InvalidState` - Status list not found or full

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
            is TrustWeaveError.ValidationFailed -> {
                println("❌ Invalid credential ID or status list ID")
            }
            is TrustWeaveError.InvalidState -> {
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
- `TrustWeaveError.ValidationFailed` - Invalid credential ID or status list ID format
- `TrustWeaveError.InvalidState` - Status list not found or full

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
- If status list not found → Returns `TrustWeaveError.InvalidState`
- If credential ID hash collision → Extremely rare (1 in 2^64), may return incorrect status
- If status list not accessible → Returns `TrustWeaveError.InvalidOperation`

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
            is TrustWeaveError.InvalidState -> {
                println("❌ Status list not found")
            }
            is TrustWeaveError.InvalidOperation -> {
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
- `TrustWeaveError.InvalidState` - Status list not found or not accessible
- `TrustWeaveError.InvalidOperation` - Cannot access status list (network error, etc.)
- `TrustWeaveError.CredentialInvalid` - Credential structure invalid (missing required fields)

#### Using BlockchainRevocationRegistry

For blockchain-anchored revocation, use `BlockchainRevocationRegistry` with an anchoring strategy:

```kotlin
import com.trustweave.credential.revocation.*
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

Trust operations allow you to manage trust anchors and verify issuer trust relationships. The trust registry must be configured during `TrustLayer.build { }`.

#### trust (DSL Style)

Performs trust operations using the trust DSL. Provides a fluent API for managing trust anchors and discovering trust paths.

```kotlin
suspend fun trust(block: suspend TrustBuilder.() -> Unit)
```

**Access via:** `trustLayer.trust { }`

**Parameters:**

The DSL builder provides a fluent API for trust operations:
- **`addAnchor(String) { }`**: Add a trust anchor with metadata
- **`removeAnchor(String)`**: Remove a trust anchor
- **`isTrusted(String, String?)`**: Check if an issuer is trusted
- **`getTrustPath(String, String)`**: Find trust path between DIDs
- **`getTrustedIssuers(String?)`**: Get all trusted issuers

**Returns:** `Unit`

**Edge Cases:**
- If trust registry is not configured → `IllegalStateException` with configuration instructions
- If anchor already exists → Returns without error (idempotent)
- If anchor doesn't exist when removing → Returns without error (idempotent)

**Example:**
```kotlin
val trustLayer = TrustLayer.build {
    trust { provider("inMemory") }
}

// Using DSL
trustLayer.trust {
    addAnchor("did:key:university") {
        credentialTypes("EducationCredential")
        description("Trusted university")
    }

    val isTrusted = isTrusted("did:key:university", "EducationCredential")
    val path = getTrustPath("did:key:verifier", "did:key:issuer")
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

**Access via:** `trustLayer.addTrustAnchor(anchorDid) { }`

**Parameters:**
- **`anchorDid`** (String, required): The DID of the trust anchor
- **`block`** (TrustAnchorMetadataBuilder.() -> Unit, optional): Configuration block for trust anchor metadata
  - `credentialTypes(String...)`: Credential types this anchor can issue
  - `description(String)`: Human-readable description
  - `metadata(Map<String, Any>)`: Additional metadata

**Returns:** `Boolean` - `true` if the anchor was added successfully, `false` if it already exists

**Example:**
```kotlin
val added = trustLayer.addTrustAnchor("did:key:university") {
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

**Access via:** `trustLayer.removeTrustAnchor(anchorDid)`

**Parameters:**
- **`anchorDid`** (String, required): The DID of the trust anchor to remove

**Returns:** `Boolean` - `true` if the anchor was removed, `false` if it didn't exist

**Example:**
```kotlin
val removed = trustLayer.removeTrustAnchor("did:key:university")
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

**Access via:** `trustLayer.isTrustedIssuer(issuerDid, credentialType)`

**Parameters:**
- **`issuerDid`** (String, required): The DID of the issuer to check
- **`credentialType`** (String?, optional): The credential type (null means check for any type)

**Returns:** `Boolean` - `true` if the issuer is trusted, `false` otherwise

**Example:**
```kotlin
val isTrusted = trustLayer.isTrustedIssuer(
    issuerDid = "did:key:university",
    credentialType = "EducationCredential"
)

if (isTrusted) {
    println("Issuer is trusted for EducationCredential")
}
```

**Errors:**
- `IllegalStateException` - Trust registry is not configured

#### getTrustPath

Finds a trust path between two DIDs.

```kotlin
suspend fun getTrustPath(fromDid: String, toDid: String): TrustPathResult?
```

**Access via:** `trustLayer.getTrustPath(fromDid, toDid)`

**Parameters:**
- **`fromDid`** (String, required): The starting DID (typically the verifier)
- **`toDid`** (String, required): The target DID (typically the issuer)

**Returns:** `TrustPathResult?` - Trust path if one exists, `null` otherwise

**Example:**
```kotlin
val path = trustLayer.getTrustPath(
    fromDid = "did:key:verifier",
    toDid = "did:key:issuer"
)

if (path != null) {
    println("Trust path found: ${path.path}")
} else {
    println("No trust path found")
}
```

**Errors:**
- `IllegalStateException` - Trust registry is not configured

#### getTrustedIssuers

Gets all trusted issuers for a specific credential type.

```kotlin
suspend fun getTrustedIssuers(credentialType: String? = null): List<String>
```

**Access via:** `trustLayer.getTrustedIssuers(credentialType)`

**Parameters:**
- **`credentialType`** (String?, optional): The credential type (null means all types)

**Returns:** `List<String>` - List of trusted issuer DIDs

**Example:**
```kotlin
val trustedIssuers = trustLayer.getTrustedIssuers("EducationCredential")
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

**Access via:** `trustLayer.wallet { }`

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
- `TrustWeaveError.WalletCreationFailed` - Wallet creation failed (provider not found, configuration invalid, storage unavailable, duplicate wallet ID)
- `TrustWeaveError.InvalidDidFormat` - Invalid holder DID format
- `TrustWeaveError.ValidationFailed` - Invalid wallet options or configuration

### Advanced TrustLayer Methods

#### getDslContext

Gets the DSL context for advanced operations. Use this only when you need to access lower-level services or perform operations not exposed by the TrustLayer facade.

```kotlin
fun getDslContext(): TrustLayerContext
```

**Access via:** `trustLayer.getDslContext()`

**Returns:** `TrustLayerContext` - The DSL context providing access to underlying services

**When to use:**
- Accessing lower-level DID resolver directly
- Performing advanced operations not in the facade
- Custom service integration

**Example:**
```kotlin
val context = trustLayer.getDslContext()
val resolver = context.getDidResolver()
val result = resolver?.resolve("did:key:example")
```

**Note:** Most operations should be done through `TrustLayer` methods. Only use this for advanced use cases.

#### configuration

Gets the underlying configuration object. Provides access to lower-level configuration details if needed.

```kotlin
val configuration: TrustLayerConfig
```

**Access via:** `trustLayer.configuration`

**Returns:** `TrustLayerConfig` - The configuration object

**When to use:**
- Inspecting registered DID methods
- Checking configured providers
- Advanced configuration access

**Example:**
```kotlin
val config = trustLayer.configuration
val didMethods = config.didMethods.keys
println("Registered DID methods: $didMethods")
```

**Note:** Most operations should be done through `TrustLayer` methods. Only use this for inspection or advanced use cases.

#### from (Companion Method)

Creates a TrustLayer from an existing TrustLayerConfig. Useful when you already have a configuration object and want to create the facade wrapper.

```kotlin
fun from(config: TrustLayerConfig): TrustLayer
```

**Access via:** `TrustLayer.from(config)`

**Parameters:**
- **`config`** (TrustLayerConfig, required): The existing configuration object

**Returns:** `TrustLayer` - A TrustLayer instance wrapping the provided config

**When to use:**
- Reusing a configuration object
- Creating multiple TrustLayer instances from the same config
- Advanced configuration scenarios

**Example:**
```kotlin
// Create config once
val config = trustLayer("my-instance") {
    keys { provider("inMemory"); algorithm("Ed25519") }
    did { method("key") { algorithm("Ed25519") } }
}

// Create multiple TrustLayer instances from the same config
val trustLayer1 = TrustLayer.from(config)
val trustLayer2 = TrustLayer.from(config)
```

**Note:** The configuration is shared between instances. Changes to one may affect others.

### Blockchain Anchoring

#### anchor

Anchors data to a blockchain for tamper evidence and timestamping. The data is serialized to JSON, canonicalized, and only the digest is stored on-chain (not the full data).

```kotlin
suspend fun <T : Any> anchor(
    data: T,
    serializer: KSerializer<T>,
    chainId: String
): Result<AnchorResult>
```

**Parameters:**
- `data`: The data to anchor (any serializable type, must be JSON-serializable)
- `serializer`: Kotlinx Serialization serializer for the data type (required)
- `chainId`: Chain ID in CAIP-2 format (e.g., `"algorand:testnet"`, `"polygon:mainnet"`)

**Returns:** `Result<AnchorResult>` containing:
- `ref`: `AnchorRef` with:
  - `chainId`: The blockchain chain identifier
  - `txHash`: Transaction hash of the anchor
  - `blockNumber`: Block number where anchored (if available)
- `timestamp`: When the data was anchored (ISO 8601 format)

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

**Note:** The full data is NOT stored on-chain. Only a cryptographic digest (hash) is stored. To retrieve the original data, you must store it separately and use `readAnchor()` with the stored data.

**Example:**
```kotlin
// Simple usage
val myData = MyData(id = "123", value = "test")
try {
    val anchor = trustweave.blockchains.anchor(
        data = myData,
        serializer = MyData.serializer(),
        chainId = "algorand:testnet"
    )
    println("Anchored at: ${anchor.ref.txHash}")
    println("Block: ${anchor.ref.blockNumber}")
    println("Timestamp: ${anchor.timestamp}")
    // Store anchorRef for later retrieval
    saveAnchorRef(anchor.ref)
} catch (error: TrustWeaveError) {
    when (error) {
        is TrustWeaveError.ChainNotRegistered -> {
            println("Chain not registered: ${error.chainId}")
            println("Available chains: ${error.availableChains}")
        }
        else -> {
            println("Anchoring error: ${error.message}")
        }
    }
}

// With timeout handling
import kotlinx.coroutines.withTimeout

val anchor = withTimeout(30000) { // 30 second timeout
    trustweave.blockchains.anchor(
        data = data,
        serializer = serializer,
        chainId = chainId
    )
}
```

**Errors:**
- `TrustWeaveError.ChainNotRegistered` - Chain ID not registered in registry
- `TrustWeaveError.ValidationFailed` - Invalid chain ID format or data serialization failure
- `TrustWeaveError.Unknown` - Blockchain transaction failed (network error, insufficient funds, etc.)

#### readAnchor

Reads anchored data from a blockchain using the anchor reference. This retrieves the data that was stored during `anchor()` and verifies it matches the on-chain digest.

```kotlin
suspend fun <T : Any> readAnchor(
    ref: AnchorRef,
    serializer: KSerializer<T>
): Result<T>
```

**Parameters:**
- `ref`: `AnchorRef` containing chain ID and transaction hash (required, must be valid)
- `serializer`: Kotlinx Serialization serializer for the data type (required, must match original type)

**Returns:** `Result<T>` containing the deserialized data matching the serializer type

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
} catch (error: TrustWeaveError) {
    when (error) {
        is TrustWeaveError.ChainNotRegistered -> {
            println("Chain not registered: ${error.chainId}")
            println("Available chains: ${error.availableChains}")
        }
        is TrustWeaveError.ValidationFailed -> {
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
- `TrustWeaveError.ChainNotRegistered` - Chain ID not registered in registry
- `TrustWeaveError.ValidationFailed` - Data digest doesn't match on-chain digest (tamper detected) or deserialization failed
- `TrustWeaveError.Unknown` - Transaction not found or data retrieval failed

#### availableChains

Gets a list of available blockchain chain IDs.

```kotlin
fun availableChains(): List<String>
```

**Access via:** `trustWeave.blockchains.availableChains()`

**Returns:** `List<String>` - List of registered blockchain chain IDs in CAIP-2 format

**Example:**
```kotlin
val chains = trustweave.blockchains.availableChains()
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

- **`issueCredential(contract, issuerDid, issuerKeyId)`**: Issues a verifiable credential for a contract
- **`anchorContract(contract, credential, chainId)`**: Anchors a contract to a blockchain
- **`activateContract(contractId)`**: Activates a contract (moves from PENDING to ACTIVE)
- **`evaluateConditions(contract, inputData)`**: Evaluates contract conditions
- **`updateStatus(contractId, newStatus, reason, metadata)`**: Updates contract status
- **`getContract(contractId)`**: Gets a contract by ID
- **`verifyContract(credentialId)`**: Verifies a contract credential

See [Smart Contract API](smart-contract-api.md) for detailed documentation.

### Plugin Lifecycle Management

#### initialize

Initializes all plugins that implement `PluginLifecycle`.

```kotlin
suspend fun initialize(config: Map<String, Any?> = emptyMap()): Result<Unit>
```

**Example:**
```kotlin
val trustweave = TrustWeave.create()

val config = mapOf(
    "database" to mapOf("url" to "jdbc:postgresql://localhost/TrustWeave")
)

try {
    trustweave.initialize(config)
    println("Plugins initialized")
} catch (error: TrustWeaveError) {
    println("Initialization error: ${error.message}")
}
```

#### start

Starts all plugins that implement `PluginLifecycle`.

```kotlin
suspend fun start(): Result<Unit>
```

**Example:**
```kotlin
try {
    trustweave.start()
    println("Plugins started")
} catch (error: TrustWeaveError) {
    println("Start error: ${error.message}")
}
```

#### stop

Stops all plugins that implement `PluginLifecycle`.

```kotlin
suspend fun stop(): Result<Unit>
```

**Example:**
```kotlin
try {
    trustweave.stop()
    println("Plugins stopped")
} catch (error: TrustWeaveError) {
    println("Stop error: ${error.message}")
}
```

#### cleanup

Cleans up all plugins that implement `PluginLifecycle`.

```kotlin
suspend fun cleanup(): Result<Unit>
```

**Example:**
```kotlin
try {
    trustweave.cleanup()
    println("Plugins cleaned up")
} catch (error: TrustWeaveError) {
    println("Cleanup error: ${error.message}")
}
```

## Error Types

All operations can return errors of type `TrustWeaveError`. See [Error Handling](../advanced/error-handling.md) for complete error type documentation.

### Common Error Types

- `TrustWeaveError.DidMethodNotRegistered` - DID method not registered
- `TrustWeaveError.InvalidDidFormat` - Invalid DID format
- `TrustWeaveError.CredentialInvalid` - Credential validation failed
- `TrustWeaveError.ChainNotRegistered` - Chain ID not registered
- `TrustWeaveError.WalletCreationFailed` - Wallet creation failed
- `TrustWeaveError.PluginInitializationFailed` - Plugin initialization failed

## Error Handling

### Exception-Based Error Handling (TrustLayer Methods)

**All `TrustLayer` methods throw exceptions on failure.** This includes:
- `createDid()`, `updateDid()`, `delegate()`, `rotateKey()`
- `issue()`, `verify()`
- `wallet()`
- Trust operations (`addTrustAnchor()`, `removeTrustAnchor()`, etc.)

**Example:**
```kotlin
import com.trustweave.trust.TrustWeave
import com.trustweave.core.TrustWeaveError

try {
    val trustWeave = TrustWeave.build { ... }
    val did = trustWeave.createDid {
        method("key")
        algorithm("Ed25519")
    }
    val credential = trustWeave.issue {
        credential { ... }
        signedBy(issuerDid = did, keyId = "$did#key-1")
    }
    val wallet = trustWeave.wallet {
        holder(did)
    }
} catch (error: TrustWeaveError) {
    when (error) {
        is TrustWeaveError.DidMethodNotRegistered -> {
            println("Method not registered: ${error.method}")
            println("Available methods: ${error.availableMethods}")
        }
        is TrustWeaveError.ChainNotRegistered -> {
            println("Chain not registered: ${error.chainId}")
            println("Available chains: ${error.availableChains}")
        }
        else -> println("Error: ${error.message}")
    }
}
```

### Result-Based Error Handling (Lower-Level APIs)

**Some lower-level APIs return `Result<T>` directly.** This includes:
- Contract operations (when accessed via lower-level APIs)
- Some plugin lifecycle methods
- Custom service implementations

**Example:**
```kotlin
val result = someService.operation()
result.fold(
    onSuccess = { value ->
        // Handle success
        println("Success: $value")
    },
    onFailure = { error ->
        // Handle error
        when (error) {
            is TrustWeaveError.ValidationFailed -> {
                println("Validation failed: ${error.reason}")
            }
            else -> println("Error: ${error.message}")
        }
    }
)
```

**When to use which pattern:**
- **Exception-based (TrustLayer)**: Use for all `TrustLayer` facade methods. Wrap in try-catch for production code.
- **Result-based**: Use when working with lower-level service APIs that explicitly return `Result<T>`.

**Best Practice:** Always handle errors explicitly. Never ignore exceptions or Result failures in production code.

## Configuration

### Registering DID Methods

DID methods are registered during `TrustLayer` creation using the DSL:

```kotlin
import com.trustweave.trust.TrustWeave

val trustLayer = TrustLayer.build {
    keys {
        provider("inMemory")
        algorithm("Ed25519")
    }
    did {
        method("key") {
            algorithm("Ed25519")
        }
        method("web") {
            domain("example.com")
        }
        method("ion") {
            // ION-specific configuration
        }
    }
}
```

### Registering Blockchain Anchors

Blockchain anchor clients are registered during `TrustLayer` creation:

```kotlin
val trustLayer = TrustLayer.build {
    keys { ... }
    did { ... }
    anchor {
        chain("algorand:testnet") {
            provider("algorand")
            // Chain-specific configuration
        }
        chain("polygon:mainnet") {
            provider("polygon")
        }
    }
}
```

### Registering Trust Registry

Trust registry is configured during `TrustLayer` creation:

```kotlin
val trustLayer = TrustLayer.build {
    keys { ... }
    did { ... }
    trust {
        provider("inMemory")
        // Or use a custom trust registry implementation
    }
}
```

**Note:** For advanced configuration with custom services, proof generators, or credential services, you may need to configure the underlying `TrustLayerConfig` directly. See [Advanced Configuration](../advanced/README.md) for details.

## Related Documentation

- [Error Handling](../advanced/error-handling.md) - Detailed error handling patterns
- [Plugin Lifecycle](../advanced/plugin-lifecycle.md) - Plugin lifecycle management
- [Wallet API](wallet-api.md) - Wallet operations reference
- [Credential Service API](credential-service-api.md) - Credential service SPI
- [DIDs Core Concept](../core-concepts/dids.md) - DID concepts and usage
- [Verifiable Credentials Core Concept](../core-concepts/verifiable-credentials.md) - Credential concepts
- [Troubleshooting](../getting-started/troubleshooting.md) - Common issues and solutions

