# Core API Reference

Complete API reference for TrustWeave's core facade API.

> **Version:** 1.0.0-SNAPSHOT  
> **Kotlin:** 2.2.0+ | **Java:** 21+  
> See [CHANGELOG.md](../../CHANGELOG.md) for version history and migration guides.

```kotlin
dependencies {
    implementation("com.trustweave:trustweave-all:1.0.0-SNAPSHOT")
}
```

## Overview

The TrustWeave facade provides a unified, elegant API for decentralized identity and trust operations. Operations throw `TrustWeaveError` exceptions on failure, which you can catch and handle. Some operations (like contract operations) return `Result<T>` directly.

**Key Concepts:**
- **Facade**: The `TrustWeave` class is the main entry point (facade pattern)
- **Service**: Focused services like `DidService`, `CredentialService`, `WalletService`, `BlockchainService`, `ContractService`
- **Provider**: Factory pattern implementations like `WalletFactory`
- **Registry**: Collections like `DidMethodRegistry`, `BlockchainAnchorRegistry` (method/chain collections)

**Service Organization:**
- **`dids`**: DID operations (create, resolve, update, deactivate)
- **`credentials`**: Credential operations (issue, verify)
- **`wallets`**: Wallet creation
- **`blockchains`**: Blockchain anchoring (anchor, read)
- **`contracts`**: Smart contract operations (draft, bind, execute, etc.)

## Quick Reference

| Operation | Method | Returns |
|-----------|--------|---------|
| Create DID | `dids.create()` | `DidDocument` |
| Resolve DID | `dids.resolve(did)` | `DidResolutionResult` |
| Update DID | `dids.update(did, updater)` | `DidDocument` |
| Deactivate DID | `dids.deactivate(did)` | `Boolean` |
| Get Available DID Methods | `dids.availableMethods()` | `List<String>` |
| Issue Credential | `credentials.issue(...)` | `VerifiableCredential` |
| Verify Credential | `credentials.verify(credential, config)` | `CredentialVerificationResult` |
| Create Wallet | `wallets.create(holderDid, ...)` | `Wallet` |
| Anchor Data | `blockchains.anchor(data, serializer, chainId)` | `AnchorResult` |
| Read Anchor | `blockchains.read(ref, serializer)` | `T` |
| Get Available Chains | `blockchains.availableChains()` | `List<String>` |
| Create Contract Draft | `contracts.draft(request)` | `Result<SmartContract>` |
| Bind Contract | `contracts.bindContract(...)` | `Result<BoundContract>` |
| Execute Contract | `contracts.executeContract(...)` | `Result<ExecutionResult>` |
| Register DID Methods | `didMethods { + DidKeyMethod() }` | `Unit` (during creation) |
| Register Blockchain Clients | `blockchains { "chainId" to client }` | `Unit` (during creation) |

## TrustWeave Class

### Creating TrustWeave Instances

```kotlin
// Create with defaults
val TrustWeave = TrustWeave.create()

// Create with custom configuration
val TrustWeave = TrustWeave.create {
    kms = InMemoryKeyManagementService()
    walletFactory = MyWalletFactory()
    
    didMethods {
        + DidKeyMethod()
        + DidWebMethod()
    }
    
    blockchains {
        "ethereum:mainnet" to ethereumClient
        "algorand:testnet" to algorandClient
    }
}

// Create from config
val config = TrustWeaveConfig(...)
val TrustWeave = TrustWeave.create(config)
```

### Service Properties

The TrustWeave facade exposes services through properties:

- **`dids`**: `DidService` - DID operations (create, resolve, update, deactivate)
- **`credentials`**: `CredentialService` - Credential operations (issue, verify)
- **`wallets`**: `WalletService` - Wallet creation
- **`blockchains`**: `BlockchainService` - Blockchain anchoring operations
- **`contracts`**: `ContractService` - Smart contract operations

**Example:**
```kotlin
val trustweave = TrustWeave.create()

// Access services
val did = trustweave.dids.create()
val methods = trustweave.dids.availableMethods()
val chains = trustweave.blockchains.availableChains()
```

### DID Operations

#### create

Creates a new DID using the default or specified method.

```kotlin
suspend fun create(
    method: String = "key",
    options: DidCreationOptions = DidCreationOptions()
): DidDocument

suspend fun create(
    method: String = "key",
    configure: DidCreationOptionsBuilder.() -> Unit
): DidDocument
```

**Access via:** `TrustWeave.dids.create()`

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

**Returns:** `DidDocument` - W3C-compliant DID document containing:
- `id`: The DID string (e.g., `"did:key:z6Mk..."`)
- `verificationMethod`: Array of verification methods with public keys
- `authentication`: Authentication key references
- `assertionMethod`: Assertion key references (for signing)
- `service`: Optional service endpoints

**Note:** This method throws `TrustWeaveError` exceptions on failure. For error handling, wrap in try-catch or use extension functions.

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
val did = TrustWeave.dids.create()

// With custom method
val webDid = TrustWeave.dids.create(method = "web")

// With builder
val did = TrustWeave.dids.create("key") {
    algorithm = DidCreationOptions.KeyAlgorithm.ED25519
    purpose(DidCreationOptions.KeyPurpose.AUTHENTICATION)
}

// With error handling
val result = TrustWeave.dids.create()
result.fold(
    onSuccess = { did -> println("Created: ${did.id}") },
    onFailure = { error -> 
        when (error) {
            is TrustWeaveError.DidMethodNotRegistered -> {
                println("Method not registered: ${error.method}")
                println("Available methods: ${error.availableMethods}")
            }
            is TrustWeaveError.ValidationFailed -> {
                println("Validation failed: ${error.reason}")
                println("Field: ${error.field}")
            }
            else -> println("Error: ${error.message}")
        }
    }
)
```

**Errors:**
- `TrustWeaveError.DidMethodNotRegistered` - Method is not registered (includes available methods)
- `TrustWeaveError.InvalidDidFormat` - Invalid DID format (if validation fails)
- `TrustWeaveError.ValidationFailed` - Configuration validation failed
- `TrustWeaveError.InvalidOperation` - KMS operation failed

#### resolve

Resolves a DID to its document.

```kotlin
suspend fun resolve(did: String): DidResolutionResult
```

**Access via:** `TrustWeave.dids.resolve(did)`

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
// Simple usage
val result = TrustWeave.dids.resolve("did:key:z6Mk...")
if (result.document != null) {
    println("DID resolved: ${result.document.id}")
    println("Method: ${result.metadata.method}")
    println("Resolved at: ${result.metadata.resolvedAt}")
} else {
    println("DID not found: ${result.metadata.error}")
}

// With error handling
val result = TrustWeave.dids.resolve("did:key:z6Mk...")
result.fold(
    onSuccess = { resolution ->
        if (resolution.document != null) {
            println("DID resolved: ${resolution.document.id}")
            println("Verification methods: ${resolution.document.verificationMethod.size}")
        } else {
            println("DID not found: ${resolution.metadata.error}")
        }
    },
    onFailure = { error ->
        when (error) {
            is TrustWeaveError.InvalidDidFormat -> {
                println("Invalid DID format: ${error.reason}")
                println("Expected format: did:<method>:<identifier>")
            }
            is TrustWeaveError.DidMethodNotRegistered -> {
                println("Method not registered: ${error.method}")
                println("Available methods: ${error.availableMethods}")
            }
            is TrustWeaveError.DidNotFound -> {
                println("DID not found: ${error.did}")
                println("Available methods: ${error.availableMethods}")
            }
            else -> {
                println("Error: ${error.message}")
                error.context.forEach { (key, value) ->
                    println("  $key: $value")
                }
            }
        }
    }
)
```

**Errors:**
- `TrustWeaveError.InvalidDidFormat` - Invalid DID format (includes reason)
- `TrustWeaveError.DidMethodNotRegistered` - Method is not registered (includes available methods)
- `TrustWeaveError.DidNotFound` - DID not found (includes DID and available methods)

#### update

Updates a DID document by applying a transformation function.

```kotlin
suspend fun update(
    did: String,
    updater: (DidDocument) -> DidDocument
): DidDocument
```

**Access via:** `TrustWeave.dids.update(did, updater)`

**Parameters:**
- **`did`** (String, required): The DID to update
- **`updater`** (Function, required): Function that transforms the current document to the new document

**Returns:** `DidDocument` - The updated DID document

**Edge Cases:**
- If DID format invalid → `TrustWeaveError.InvalidDidFormat`
- If method not registered → `TrustWeaveError.DidMethodNotRegistered`
- If update operation fails → `TrustWeaveError.InvalidOperation`

**Example:**
```kotlin
val updated = trustweave.dids.update("did:key:example") { document ->
    document.copy(
        service = document.service + Service(
            id = "${document.id}#service-1",
            type = "LinkedDomains",
            serviceEndpoint = "https://example.com/service"
        )
    )
}
```

#### deactivate

Deactivates a DID, marking it as no longer active.

```kotlin
suspend fun deactivate(did: String): Boolean
```

**Access via:** `TrustWeave.dids.deactivate(did)`

**Parameters:**
- **`did`** (String, required): The DID to deactivate

**Returns:** `Boolean` - `true` if deactivated successfully, `false` otherwise

**Edge Cases:**
- If DID format invalid → `TrustWeaveError.InvalidDidFormat`
- If method not registered → `TrustWeaveError.DidMethodNotRegistered`
- If DID already deactivated → Returns `false`

**Example:**
```kotlin
val deactivated = trustweave.dids.deactivate("did:key:example")
if (deactivated) {
    println("DID deactivated successfully")
}
```

#### availableMethods

Gets a list of available DID method names.

```kotlin
fun availableMethods(): List<String>
```

**Access via:** `TrustWeave.dids.availableMethods()`

**Returns:** `List<String>` - List of registered DID method names

**Example:**
```kotlin
val methods = trustweave.dids.availableMethods()
println("Available methods: $methods") // ["key", "web", "ion"]
```

### Credential Operations

#### issueCredential

Issues a verifiable credential with cryptographic proof.

```kotlin
suspend fun issueCredential(
    issuerDid: String,
    issuerKeyId: String,
    credentialSubject: JsonElement,
    types: List<String> = listOf("VerifiableCredential"),
    expirationDate: String? = null
): Result<VerifiableCredential>
```

**Parameters:**

- **`issuerDid`** (String, required): The DID of the credential issuer
  - **Format**: Must match `did:<method>:<identifier>` pattern
  - **Requirements**: 
    - Must be resolvable (DID method must be registered)
    - Issuer's DID document must contain verification methods
  - **Example**: `"did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK"`
  - **Validation**: Automatically validated before issuance

- **`issuerKeyId`** (String, required): The key ID from issuer's DID document
  - **Format**: 
    - Absolute: `"${issuerDid}#<key-id>"` (e.g., `"did:key:z6Mk...#key-1"`)
    - Relative: `"#<key-id>"` (e.g., `"#key-1"`)
  - **Requirements**:
    - Must exist in issuer's `verificationMethod` array
    - Key must support signing operations
  - **Example**: `"did:key:z6Mk...#key-1"` or `"#key-1"`
  - **How to find**: Use `didDocument.verificationMethod.firstOrNull()?.id`

- **`credentialSubject`** (JsonElement, required): Credential claims as JSON object
  - **Type**: Must be a `JsonObject` (not array or primitive)
  - **Required fields**:
    - `"id"`: Subject identifier (DID or other identifier)
  - **Optional fields**: Any additional claims
  - **Example**:
    ```kotlin
    buildJsonObject {
        put("id", "did:key:subject")
        put("name", "Alice")
        put("email", "alice@example.com")
        put("degree", "Bachelor of Science")
    }
    ```

- **`types`** (List<String>, optional): Credential type identifiers
  - **Default**: `["VerifiableCredential"]`
  - **Format**: Array of type strings
  - **Convention**: First type should be `"VerifiableCredential"`, followed by specific types
  - **Example**: `listOf("VerifiableCredential", "PersonCredential", "DegreeCredential")`

- **`expirationDate`** (String?, optional): Expiration date in ISO 8601 format
  - **Format**: ISO 8601 datetime string (e.g., `"2025-12-31T23:59:59Z"`)
  - **Default**: `null` (no expiration)
  - **Validation**: Must be a valid ISO 8601 datetime
  - **Example**: `"2025-12-31T23:59:59Z"`

**Returns:** `Result<VerifiableCredential>`
- **Success**: Signed `VerifiableCredential` with:
  - `id`: Auto-generated credential ID (UUID)
  - `issuer`: Issuer DID
  - `issuanceDate`: Current timestamp
  - `credentialSubject`: Provided subject data
  - `type`: Credential types
  - `proof`: Cryptographic proof (signature)
- **Failure**: `TrustWeaveError` with specific error type

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
// Simple usage
val credential = trustweave.credentials.issue(
    issuer = "did:key:issuer",
    subject = buildJsonObject {
        put("id", "did:key:subject")
        put("name", "Alice")
    },
    config = IssuanceConfig(
        proofType = ProofType.Ed25519Signature2020,
        keyId = "key-1",
        issuerDid = "did:key:issuer"
    ),
    types = listOf("PersonCredential")
)

// With error handling (wrap in try-catch)
try {
    val credential = trustweave.credentials.issue(...)
    println("Issued: ${credential.id}")
} catch (error: TrustWeaveError) {
    when (error) {
        is TrustWeaveError.InvalidDidFormat -> {
            println("Invalid issuer DID: ${error.reason}")
        }
        is TrustWeaveError.CredentialInvalid -> {
            println("Credential invalid: ${error.reason}")
            println("Field: ${error.field}")
        }
        else -> println("Error: ${error.message}")
    }
}
```

**Errors:**
- `TrustWeaveError.InvalidDidFormat` - Invalid issuer DID format
- `TrustWeaveError.DidMethodNotRegistered` - Issuer DID method not registered
- `TrustWeaveError.CredentialInvalid` - Credential validation failed

#### verify

Verifies a verifiable credential by checking proof, issuer DID resolution, expiration, and revocation status.

```kotlin
suspend fun verify(
    credential: VerifiableCredential,
    config: VerificationConfig = VerificationConfig()
): CredentialVerificationResult
```

**Access via:** `TrustWeave.credentials.verify(credential, config)`

**Parameters:**
- `credential`: The verifiable credential to verify (required, must be valid JSON-LD structure)
- `options`: Verification options (optional, default: all checks enabled)
  - `checkExpiration`: Check if credential has expired (default: `true`)
  - `validateSchema`: Validate credential schema (default: `true`)
  - `enforceStatus`: Check revocation status (default: `true`)
  - `expectedAudience`: Expected audience DIDs (default: `null`)
  - `requireAnchoring`: Require blockchain anchoring (default: `false`)

**Returns:** `Result<CredentialVerificationResult>` containing:
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
val result = trustweave.credentials.verify(credential)
if (result.valid) {
    println("Credential is valid")
} else {
    println("Errors: ${result.errors}")
}

// With custom verification configuration
val config = VerificationConfig(
    checkRevocation = true,
    checkExpiration = true,
    verifyBlockchainAnchor = true,
    chainId = "algorand:testnet"
)
val result = trustweave.credentials.verify(credential, config)

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

### Wallet Operations

#### createWallet

Creates a wallet for storing credentials. Wallets provide secure storage and management of verifiable credentials for a specific holder.

```kotlin
suspend fun createWallet(
    holderDid: String,
    walletId: String = UUID.randomUUID().toString(),
    provider: WalletProvider = WalletProvider.InMemory,
    options: WalletCreationOptions = WalletCreationOptions()
): Result<Wallet>

suspend fun createWallet(
    holderDid: String,
    walletId: String = UUID.randomUUID().toString(),
    provider: WalletProvider = WalletProvider.InMemory,
    configure: WalletOptionsBuilder.() -> Unit
): Result<Wallet>
```

**Parameters:**
- `holderDid`: The DID of the credential holder (required, must be valid DID format)
- `walletId`: Unique identifier for the wallet (default: random UUID, must be unique per provider)
- `provider`: Wallet provider implementation (default: `InMemory`, options: `InMemory`, `Database`, `FileSystem`, `S3`, etc.)
- `options`: Wallet creation options (enable organization, presentation, etc.)
- `configure`: Builder function for wallet options (alternative to `options` parameter)

**Returns:** `Result<Wallet>` containing the created wallet instance with:
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
val wallet = trustweave.wallets.create(holderDid = "did:key:holder")
println("Created wallet: ${wallet.walletId}")
println("Holder: ${wallet.holderDid}")

// With custom type and options
val wallet = trustweave.wallets.create(
    holderDid = "did:key:holder",
    type = WalletType.Database,
    options = WalletCreationOptions(
        label = "Holder Wallet",
        enableOrganization = true,
        enablePresentation = true
    )
)

// Use wallet directly
wallet.store(credential)
val retrieved = wallet.get(credentialId)
val allCredentials = wallet.list()
```

**Errors:**
- `TrustWeaveError.WalletCreationFailed` - Wallet creation failed (provider not found, configuration invalid, storage unavailable, duplicate wallet ID)
- `TrustWeaveError.InvalidDidFormat` - Invalid holder DID format
- `TrustWeaveError.ValidationFailed` - Invalid wallet options or configuration

**Example - With Organization and Presentation:**
```kotlin
val wallet = TrustWeave.createWallet("did:key:holder") {
    enableOrganization = true
    enablePresentation = true
    storagePath = "/wallets/holder-123"
}.getOrThrow()

// Now wallet supports collections, tags, and presentations
wallet.withOrganization { org ->
    org.createCollection("Education")
}
```

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

**Access via:** `TrustWeave.blockchains.availableChains()`

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

**Access via:** `TrustWeave.contracts.draft(request)`

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

**Access via:** `TrustWeave.contracts.bindContract(...)`

**Returns:** `Result<BoundContract>` - The bound contract with credential and anchor reference

#### executeContract

Executes a contract based on its execution model.

```kotlin
suspend fun executeContract(
    contract: SmartContract,
    executionContext: ExecutionContext
): Result<ExecutionResult>
```

**Access via:** `TrustWeave.contracts.executeContract(contract, executionContext)`

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

All TrustWeave operations throw `TrustWeaveError` exceptions on failure. Operations do not return `Result<T>` directly - they throw exceptions that you can catch and handle.

**Example:**
```kotlin
try {
    val did = trustweave.dids.create()
    val credential = trustweave.credentials.issue(...)
    val wallet = trustweave.wallets.create(holderDid = did.id)
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

**Note:** Some operations (like contract operations) return `Result<T>` directly. Check the method signature for each operation.

## Configuration

### Registering DID Methods

DID methods are registered during TrustWeave creation using the DSL:

```kotlin
val trustweave = TrustWeave.create {
    didMethods {
        + DidKeyMethod(kms)
        + DidWebMethod(kms) { domain = "example.com" }
        + DidIonMethod(kms)
    }
}
```

### Registering Blockchain Clients

Blockchain clients are registered during TrustWeave creation using the DSL:

```kotlin
val trustweave = TrustWeave.create {
    blockchains {
        "algorand:testnet" to algorandClient
        "polygon:mainnet" to polygonClient
        "ethereum:mainnet" to ethereumClient
    }
}
```

### Registering Credential Services

Credential services can be registered during creation:

```kotlin
val trustweave = TrustWeave.create {
    credentialServices {
        + MyCredentialService()
    }
}
```

### Registering Proof Generators

Proof generators can be registered during creation:

```kotlin
val trustweave = TrustWeave.create {
    proofGenerators {
        + Ed25519ProofGenerator(signer)
        + BbsProofGenerator(signer)
    }
}
```

## Related Documentation

- [Error Handling](../advanced/error-handling.md) - Detailed error handling patterns
- [Plugin Lifecycle](../advanced/plugin-lifecycle.md) - Plugin lifecycle management
- [Wallet API](wallet-api.md) - Wallet operations reference
- [Credential Service API](credential-service-api.md) - Credential service SPI
- [DIDs Core Concept](../core-concepts/dids.md) - DID concepts and usage
- [Verifiable Credentials Core Concept](../core-concepts/verifiable-credentials.md) - Credential concepts
- [Troubleshooting](../getting-started/troubleshooting.md) - Common issues and solutions

