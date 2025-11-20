# Core API Reference

Complete API reference for VeriCore's core facade API.

> **Version:** 1.0.0-SNAPSHOT  
> **Kotlin:** 2.2.0+ | **Java:** 21+  
> See [CHANGELOG.md](../../CHANGELOG.md) for version history and migration guides.

```kotlin
dependencies {
    implementation("com.geoknoesis.vericore:vericore-all:1.0.0-SNAPSHOT")
}
```

## Overview

The VeriCore facade provides a unified, elegant API for decentralized identity and trust operations. All operations return `Result<T>` for consistent error handling.

**Key Concepts:**
- **Facade**: The `VeriCore` class is the main entry point (facade pattern)
- **Service**: Implementations like `CredentialService`, `WalletService` (concrete implementations)
- **Provider**: Factory pattern implementations like `CredentialServiceProvider`, `WalletFactory`
- **Registry**: Collections like `DidMethodRegistry`, `BlockchainAnchorRegistry` (method/chain collections)

## Quick Reference

| Operation | Method | Returns |
|-----------|--------|---------|
| Create DID | `createDid()` | `Result<DidDocument>` |
| Resolve DID | `resolveDid(did)` | `Result<DidResolutionResult>` |
| Issue Credential | `issueCredential(...)` | `Result<VerifiableCredential>` |
| Verify Credential | `verifyCredential(credential)` | `Result<CredentialVerificationResult>` |
| Create Wallet | `createWallet(holderDid)` | `Result<Wallet>` |
| Anchor Data | `anchor(data, serializer, chainId)` | `Result<AnchorResult>` |
| Read Anchor | `readAnchor(ref, serializer)` | `Result<T>` |
| Get Available DID Methods | `getAvailableDidMethods()` | `List<String>` |
| Get Available Chains | `getAvailableChains()` | `List<String>` |
| Register DID Method | `registerDidMethod(method)` | `Unit` |
| Register Blockchain Client | `registerBlockchainClient(chainId, client)` | `Unit` |

## VeriCore Class

### Creating VeriCore Instances

```kotlin
// Create with defaults
val vericore = VeriCore.create()

// Create with custom configuration
val vericore = VeriCore.create {
    kms = InMemoryKeyManagementService()
    walletFactory = MyWalletFactory()
    
    didMethods {
        + DidKeyMethod()
        + DidWebMethod()
    }
    
    blockchain {
        "ethereum:mainnet" to ethereumClient
        "algorand:testnet" to algorandClient
    }
}

// Create from config
val config = VeriCoreConfig(...)
val vericore = VeriCore.create(config)
```

### Utility Methods

#### getAvailableDidMethods

Returns a list of registered DID method names available in the current VeriCore instance.

```kotlin
fun getAvailableDidMethods(): List<String>
```

**Returns:** `List<String>` - List of DID method names (e.g., `["key", "web", "ion"]`)

**Performance Characteristics:**
- **Time Complexity:** O(N) where N is the number of registered methods
- **Space Complexity:** O(N) for the returned list
- **Network Calls:** 0 (local operation)
- **Thread Safety:** ✅ Thread-safe (read-only operation)

**Edge Cases:**
- If no methods registered, returns empty list `[]`
- Method names are returned in registration order (implementation-dependent)
- Returns only method names, not method instances

**Use Cases:**
- Check available methods before creating DIDs
- Display available methods to users
- Validate method names before operations
- Debugging method registration issues

**Example:**
```kotlin
// Check available methods
val methods = vericore.getAvailableDidMethods()
println("Available DID methods: $methods")

// Use in error handling
val result = vericore.createDid("web")
result.fold(
    onSuccess = { did -> println("Created: ${did.id}") },
    onFailure = { error ->
        when (error) {
            is VeriCoreError.DidMethodNotRegistered -> {
                println("Method 'web' not available")
                println("Available methods: ${vericore.getAvailableDidMethods()}")
                // Suggest alternative or register method
            }
            else -> println("Error: ${error.message}")
        }
    }
)

// Validate before use
val desiredMethod = "web"
if (desiredMethod in vericore.getAvailableDidMethods()) {
    val did = vericore.createDid(desiredMethod).getOrThrow()
} else {
    println("Method '$desiredMethod' not available")
    println("Available: ${vericore.getAvailableDidMethods()}")
}
```

#### getAvailableChains

Returns a list of registered blockchain chain IDs available for anchoring operations.

```kotlin
fun getAvailableChains(): List<String>
```

**Returns:** `List<String>` - List of chain IDs in CAIP-2 format (e.g., `["algorand:testnet", "polygon:mainnet"]`)

**Performance Characteristics:**
- **Time Complexity:** O(N) where N is the number of registered chains
- **Space Complexity:** O(N) for the returned list
- **Network Calls:** 0 (local operation)
- **Thread Safety:** ✅ Thread-safe (read-only operation)

**Edge Cases:**
- If no chains registered, returns empty list `[]`
- Chain IDs are returned in registration order (implementation-dependent)
- Returns only chain IDs, not client instances

**Use Cases:**
- Check available chains before anchoring
- Display available chains to users
- Validate chain IDs before operations
- Debugging chain registration issues

**Example:**
```kotlin
// Check available chains
val chains = vericore.getAvailableChains()
println("Available chains: $chains")

// Use in error handling
val result = vericore.anchor(data, serializer, "algorand:testnet")
result.fold(
    onSuccess = { anchor -> println("Anchored: ${anchor.ref.txHash}") },
    onFailure = { error ->
        when (error) {
            is VeriCoreError.ChainNotRegistered -> {
                println("Chain 'algorand:testnet' not available")
                println("Available chains: ${vericore.getAvailableChains()}")
                // Suggest alternative or register chain
            }
            else -> println("Error: ${error.message}")
        }
    }
)

// Validate before use
val desiredChain = "algorand:testnet"
if (desiredChain in vericore.getAvailableChains()) {
    val anchor = vericore.anchor(data, serializer, desiredChain).getOrThrow()
} else {
    println("Chain '$desiredChain' not available")
    println("Available: ${vericore.getAvailableChains()}")
}
```

#### registerDidMethod

Registers a DID method implementation with the VeriCore instance. The method becomes available for creating and resolving DIDs.

```kotlin
fun registerDidMethod(method: DidMethod)
```

**Parameters:**
- `method`: The DID method implementation to register (required, must implement `DidMethod` interface)

**Returns:** `Unit` (no return value)

**Performance Characteristics:**
- **Time Complexity:** O(1) for registration (hash map insertion)
- **Space Complexity:** O(1) (single method registration)
- **Network Calls:** 0 (local operation)
- **Thread Safety:** ⚠️ Not thread-safe (modifies registry state)
  - Register methods during initialization or in a synchronized context
  - Avoid registering methods concurrently

**Edge Cases:**
- If method with same name already registered, replaces existing method
- If method is `null`, throws `IllegalArgumentException`
- Registration is immediate - method available immediately after call
- Method must be properly initialized before registration

**Use Cases:**
- Add custom DID method implementations
- Register methods discovered via SPI
- Dynamically add methods at runtime
- Replace default method implementations

**Example:**
```kotlin
import com.geoknoesis.vericore.did.*

// Register a DID method
val webMethod = DidWebMethod(
    kms = vericore.kms,
    domain = "example.com"
)
vericore.registerDidMethod(webMethod)

// Verify registration
val methods = vericore.getAvailableDidMethods()
println("Registered methods: $methods") // Should include "web"

// Now can use the method
val did = vericore.createDid("web").getOrThrow()
println("Created web DID: ${did.id}")

// Register multiple methods
vericore.registerDidMethod(DidKeyMethod(vericore.kms))
vericore.registerDidMethod(DidIonMethod(vericore.kms))
vericore.registerDidMethod(DidWebMethod(vericore.kms) { domain = "example.com" })

// Check all registered methods
println("All methods: ${vericore.getAvailableDidMethods()}")
```

**Best Practices:**
- Register methods during VeriCore initialization
- Use SPI discovery for automatic registration when possible
- Register methods before creating DIDs that use them
- Check `getAvailableDidMethods()` after registration to verify

**Note:** Prefer registering methods during `VeriCore.create { }` configuration when possible for better organization and thread safety.

#### registerBlockchainClient

Registers a blockchain anchor client for a specific chain ID. The client becomes available for anchoring operations.

```kotlin
fun registerBlockchainClient(chainId: String, client: BlockchainAnchorClient)
```

**Parameters:**
- `chainId`: Chain ID in CAIP-2 format (e.g., `"algorand:testnet"`, `"polygon:mainnet"`) (required)
- `client`: The blockchain anchor client implementation (required, must implement `BlockchainAnchorClient`)

**Returns:** `Unit` (no return value)

**Performance Characteristics:**
- **Time Complexity:** O(1) for registration (hash map insertion)
- **Space Complexity:** O(1) (single client registration)
- **Network Calls:** 0 (local operation, but client may validate connection)
- **Thread Safety:** ⚠️ Not thread-safe (modifies registry state)
  - Register clients during initialization or in a synchronized context
  - Avoid registering clients concurrently

**Edge Cases:**
- If chain ID with same value already registered, replaces existing client
- If `chainId` is invalid format, may throw `IllegalArgumentException`
- If `client` is `null`, throws `IllegalArgumentException`
- Registration is immediate - client available immediately after call
- Client should be properly initialized before registration

**Use Cases:**
- Add blockchain clients for anchoring
- Register clients for different networks (testnet/mainnet)
- Dynamically add clients at runtime
- Replace client implementations

**Example:**
```kotlin
import com.geoknoesis.vericore.anchor.*
import com.geoknoesis.vericore.anchor.options.*

// Register Algorand testnet client
val algorandClient = AlgorandBlockchainAnchorClient(
    chainId = "algorand:testnet",
    options = AlgorandOptions(
        algodUrl = "https://testnet-api.algonode.cloud",
        privateKey = "your-private-key"
    )
)
vericore.registerBlockchainClient("algorand:testnet", algorandClient)

// Verify registration
val chains = vericore.getAvailableChains()
println("Registered chains: $chains") // Should include "algorand:testnet"

// Now can use for anchoring
val anchor = vericore.anchor(
    data = myData,
    serializer = MyData.serializer(),
    chainId = "algorand:testnet"
).getOrThrow()

// Register multiple chains
vericore.registerBlockchainClient("algorand:testnet", algorandTestnetClient)
vericore.registerBlockchainClient("algorand:mainnet", algorandMainnetClient)
vericore.registerBlockchainClient("polygon:testnet", polygonTestnetClient)

// Check all registered chains
println("All chains: ${vericore.getAvailableChains()}")
```

**Best Practices:**
- Register clients during VeriCore initialization
- Use proper chain ID format (CAIP-2: `chain:network`)
- Initialize clients with proper credentials before registration
- Register clients before anchoring operations
- Check `getAvailableChains()` after registration to verify

**Note:** Prefer registering clients during `VeriCore.create { }` configuration when possible for better organization and thread safety.

### DID Operations

#### createDid

Creates a new DID using the default or specified method.

```kotlin
suspend fun createDid(
    method: String = "key",
    options: DidCreationOptions = DidCreationOptions()
): Result<DidDocument>

suspend fun createDid(
    method: String = "key",
    configure: DidCreationOptionsBuilder.() -> Unit
): Result<DidDocument>
```

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

**Returns:** `Result<DidDocument>`
- **Success**: W3C-compliant DID document containing:
  - `id`: The DID string (e.g., `"did:key:z6Mk..."`)
  - `verificationMethod`: Array of verification methods with public keys
  - `authentication`: Authentication key references
  - `assertionMethod`: Assertion key references (for signing)
  - `service`: Optional service endpoints
- **Failure**: `VeriCoreError` with specific error type

**Default Behavior:**
- Uses `did:key` method if not specified
- Generates ED25519 key pair
- Creates verification method with `#key-1` fragment
- Adds key to `authentication` and `assertionMethod` arrays

**Edge Cases:**
- If method not registered → `VeriCoreError.DidMethodNotRegistered` with available methods list
- If algorithm not supported by method → `VeriCoreError.ValidationFailed` with reason
- If KMS fails to generate key → `VeriCoreError.InvalidOperation` with context
- If method-specific validation fails → `VeriCoreError.ValidationFailed` with field details

**Performance:**
- Time complexity: O(1) for key generation (if cached)
- Network calls: 0 (local key generation for did:key)
- Thread-safe: ✅ Yes (suspend function, thread-safe KMS operations)

**Example:**
```kotlin
// Simple usage (uses defaults: did:key, ED25519)
val did = vericore.createDid().getOrThrow()

// With custom method
val webDid = vericore.createDid(method = "web").getOrThrow()

// With builder
val did = vericore.createDid("key") {
    algorithm = DidCreationOptions.KeyAlgorithm.ED25519
    purpose(DidCreationOptions.KeyPurpose.AUTHENTICATION)
}.getOrThrow()

// With error handling
val result = vericore.createDid()
result.fold(
    onSuccess = { did -> println("Created: ${did.id}") },
    onFailure = { error -> 
        when (error) {
            is VeriCoreError.DidMethodNotRegistered -> {
                println("Method not registered: ${error.method}")
                println("Available methods: ${error.availableMethods}")
            }
            is VeriCoreError.ValidationFailed -> {
                println("Validation failed: ${error.reason}")
                println("Field: ${error.field}")
            }
            else -> println("Error: ${error.message}")
        }
    }
)
```

**Errors:**
- `VeriCoreError.DidMethodNotRegistered` - Method is not registered (includes available methods)
- `VeriCoreError.InvalidDidFormat` - Invalid DID format (if validation fails)
- `VeriCoreError.ValidationFailed` - Configuration validation failed
- `VeriCoreError.InvalidOperation` - KMS operation failed

#### resolveDid

Resolves a DID to its document.

```kotlin
suspend fun resolveDid(did: String): Result<DidResolutionResult>
```

**Parameters:**

- **`did`** (String, required): The DID string to resolve
  - **Format**: Must match `did:<method>:<identifier>` pattern
  - **Example**: `"did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK"`
  - **Validation**: Automatically validated before resolution
  - **Requirements**: Method must be registered in VeriCore instance

**Returns:** `Result<DidResolutionResult>`

**Success Case - `DidResolutionResult`:**
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
- Returns `VeriCoreError` with specific error type

**Edge Cases:**
- If DID format invalid → `VeriCoreError.InvalidDidFormat` with reason
- If method not registered → `VeriCoreError.DidMethodNotRegistered` with available methods
- If DID not found → `VeriCoreError.DidNotFound` with DID and available methods
- If resolution service unavailable → `VeriCoreError.InvalidOperation` with context

**Performance:**
- Time complexity: O(1) for local methods (did:key), O(n) for network methods where n = network latency
- Network calls: 0 for did:key (local), 1+ for network-based methods (did:web, did:ion)
- Thread-safe: ✅ Yes (suspend function)

**Example:**
```kotlin
// Simple usage
val result = vericore.resolveDid("did:key:z6Mk...").getOrThrow()
if (result.document != null) {
    println("DID resolved: ${result.document.id}")
    println("Method: ${result.metadata.method}")
    println("Resolved at: ${result.metadata.resolvedAt}")
} else {
    println("DID not found: ${result.metadata.error}")
}

// With error handling
val result = vericore.resolveDid("did:key:z6Mk...")
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
            is VeriCoreError.InvalidDidFormat -> {
                println("Invalid DID format: ${error.reason}")
                println("Expected format: did:<method>:<identifier>")
            }
            is VeriCoreError.DidMethodNotRegistered -> {
                println("Method not registered: ${error.method}")
                println("Available methods: ${error.availableMethods}")
            }
            is VeriCoreError.DidNotFound -> {
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
- `VeriCoreError.InvalidDidFormat` - Invalid DID format (includes reason)
- `VeriCoreError.DidMethodNotRegistered` - Method is not registered (includes available methods)
- `VeriCoreError.DidNotFound` - DID not found (includes DID and available methods)

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
- **Failure**: `VeriCoreError` with specific error type

**Edge Cases:**

- **If `issuerKeyId` doesn't exist in DID document**:
  - Returns: `VeriCoreError.InvalidDidFormat` with reason indicating key not found
  - Solution: Verify key exists using `didDocument.verificationMethod.find { it.id == issuerKeyId }`

- **If `credentialSubject` missing `"id"` field**:
  - Returns: `VeriCoreError.CredentialInvalid` with `field = "credentialSubject.id"`
  - Solution: Always include `"id"` field in credential subject

- **If issuer DID method not registered**:
  - Returns: `VeriCoreError.DidMethodNotRegistered` with available methods list
  - Solution: Register DID method or use available method

- **If issuer DID not resolvable**:
  - Returns: `VeriCoreError.DidNotFound` with available methods
  - Solution: Ensure DID is valid and method is registered

- **If `expirationDate` is invalid format**:
  - Returns: `VeriCoreError.ValidationFailed` with reason
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
val credential = vericore.issueCredential(
    issuerDid = "did:key:issuer",
    issuerKeyId = "key-1",
    credentialSubject = buildJsonObject {
        put("id", "did:key:subject")
        put("name", "Alice")
    },
    types = listOf("PersonCredential")
).getOrThrow()

// With error handling
val result = vericore.issueCredential(...)
result.fold(
    onSuccess = { credential -> println("Issued: ${credential.id}") },
    onFailure = { error ->
        when (error) {
            is VeriCoreError.InvalidDidFormat -> {
                println("Invalid issuer DID: ${error.reason}")
            }
            is VeriCoreError.CredentialInvalid -> {
                println("Credential invalid: ${error.reason}")
                println("Field: ${error.field}")
            }
            else -> println("Error: ${error.message}")
        }
    }
)
```

**Errors:**
- `VeriCoreError.InvalidDidFormat` - Invalid issuer DID format
- `VeriCoreError.DidMethodNotRegistered` - Issuer DID method not registered
- `VeriCoreError.CredentialInvalid` - Credential validation failed

#### verifyCredential

Verifies a verifiable credential by checking proof, issuer DID resolution, expiration, and revocation status.

```kotlin
suspend fun verifyCredential(
    credential: VerifiableCredential,
    options: CredentialVerificationOptions = CredentialVerificationOptions()
): Result<CredentialVerificationResult>
```

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
- If issuer DID method not registered → Returns `VeriCoreError.DidMethodNotRegistered`
- If credential structure invalid → Returns `VeriCoreError.CredentialInvalid`

**Example:**
```kotlin
// Simple usage (testing/prototyping)
val result = vericore.verifyCredential(credential).getOrThrow()
if (result.valid) {
    println("Credential is valid")
} else {
    println("Errors: ${result.errors}")
}

// Production pattern with error handling
val result = vericore.verifyCredential(credential)
result.fold(
    onSuccess = { verification ->
        if (verification.valid) {
            println("✅ Credential is valid")
            println("   Proof valid: ${verification.proofValid}")
            println("   Issuer valid: ${verification.issuerValid}")
            println("   Not expired: ${verification.notExpired}")
            println("   Not revoked: ${verification.notRevoked}")
            
            if (verification.warnings.isNotEmpty()) {
                println("   Warnings: ${verification.warnings.joinToString()}")
            }
        } else {
            println("❌ Credential invalid")
            println("   Errors: ${verification.errors.joinToString()}")
            println("   Proof valid: ${verification.proofValid}")
            println("   Issuer valid: ${verification.issuerValid}")
        }
    },
    onFailure = { error ->
        when (error) {
            is VeriCoreError.CredentialInvalid -> {
                println("❌ Credential validation failed: ${error.reason}")
                println("   Field: ${error.field}")
            }
            is VeriCoreError.DidMethodNotRegistered -> {
                println("❌ Issuer DID method not registered: ${error.method}")
                println("   Available methods: ${error.availableMethods}")
            }
            else -> {
                println("❌ Verification error: ${error.message}")
            }
        }
    }
)

// With custom verification options
val options = CredentialVerificationOptions(
    checkExpiration = true,
    enforceStatus = true,
    expectedAudience = setOf("did:key:verifier-123")
)

val result = vericore.verifyCredential(credential, options)
```

**Errors:**
- `VeriCoreError.CredentialInvalid` - Credential validation failed (missing fields, invalid structure)
- `VeriCoreError.DidMethodNotRegistered` - Issuer DID method not registered
- `VeriCoreError.DidNotFound` - Issuer DID cannot be resolved

### Revocation and Status List Management

VeriCore provides comprehensive revocation management with blockchain anchoring support. See [Blockchain-Anchored Revocation](../core-concepts/blockchain-anchored-revocation.md) for detailed documentation.

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
val statusList = vericore.createStatusList(
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
            is VeriCoreError.DidNotFound -> {
                println("Issuer DID not found: ${error.did}")
            }
            else -> println("Error: ${error.message}")
        }
    }
)
```

**Errors:**
- `VeriCoreError.DidNotFound` - Issuer DID cannot be resolved
- `VeriCoreError.InvalidDidFormat` - Invalid issuer DID format
- `VeriCoreError.ValidationFailed` - Invalid size or purpose value

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
val revoked = vericore.revokeCredential(
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
            is VeriCoreError.ValidationFailed -> {
                println("Invalid credential ID or status list ID")
            }
            else -> println("Revocation error: ${error.message}")
        }
    }
)
```

**Errors:**
- `VeriCoreError.ValidationFailed` - Invalid credential ID or status list ID format
- `VeriCoreError.InvalidState` - Status list not found or full

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
val suspended = vericore.suspendCredential(
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
            is VeriCoreError.ValidationFailed -> {
                println("❌ Invalid credential ID or status list ID")
            }
            is VeriCoreError.InvalidState -> {
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
- `VeriCoreError.ValidationFailed` - Invalid credential ID or status list ID format
- `VeriCoreError.InvalidState` - Status list not found or full

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
- If status list not found → Returns `VeriCoreError.InvalidState`
- If credential ID hash collision → Extremely rare (1 in 2^64), may return incorrect status
- If status list not accessible → Returns `VeriCoreError.InvalidOperation`

**Example:**
```kotlin
val status = vericore.checkRevocationStatus(credential).fold(
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
            is VeriCoreError.InvalidState -> {
                println("❌ Status list not found")
            }
            is VeriCoreError.InvalidOperation -> {
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
- `VeriCoreError.InvalidState` - Status list not found or not accessible
- `VeriCoreError.InvalidOperation` - Cannot access status list (network error, etc.)
- `VeriCoreError.CredentialInvalid` - Credential structure invalid (missing required fields)

#### Using BlockchainRevocationRegistry

For blockchain-anchored revocation, use `BlockchainRevocationRegistry` with an anchoring strategy:

```kotlin
import com.geoknoesis.vericore.credential.revocation.*
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
val wallet = vericore.createWallet("did:key:holder").fold(
    onSuccess = { w -> 
        println("Created wallet: ${w.walletId}")
        println("Holder: ${w.holderDid}")
    },
    onFailure = { error ->
        when (error) {
            is VeriCoreError.WalletCreationFailed -> {
                println("Wallet creation failed: ${error.reason}")
                println("Provider: ${error.provider}")
            }
            is VeriCoreError.InvalidDidFormat -> {
                println("Invalid holder DID: ${error.reason}")
            }
            else -> println("Error: ${error.message}")
        }
    }
)

// With builder (production-ready with persistence)
val wallet = vericore.createWallet("did:key:holder") {
    label = "Holder Wallet"
    storagePath = "/wallets/holder-123"
    enableOrganization = true
    enablePresentation = true
    encryptionKey = SecretKeySpec(keyBytes, "AES")
}.getOrThrow()

// With database provider
val wallet = vericore.createWallet(
    holderDid = "did:key:holder",
    provider = WalletProvider.Database,
    options = WalletCreationOptions(
        storagePath = "jdbc:postgresql://localhost/vericore",
        enableOrganization = true
    )
).getOrThrow()
```

**Errors:**
- `VeriCoreError.WalletCreationFailed` - Wallet creation failed (provider not found, configuration invalid, storage unavailable, duplicate wallet ID)
- `VeriCoreError.InvalidDidFormat` - Invalid holder DID format
- `VeriCoreError.ValidationFailed` - Invalid wallet options or configuration

**Example - With Organization and Presentation:**
```kotlin
val wallet = vericore.createWallet("did:key:holder") {
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
val result = vericore.anchor(
    data = myData,
    serializer = MyData.serializer(),
    chainId = "algorand:testnet"
).fold(
    onSuccess = { anchor ->
        println("Anchored at: ${anchor.ref.txHash}")
        println("Block: ${anchor.ref.blockNumber}")
        println("Timestamp: ${anchor.timestamp}")
        // Store anchorRef for later retrieval
        saveAnchorRef(anchor.ref)
    },
    onFailure = { error ->
        when (error) {
            is VeriCoreError.ChainNotRegistered -> {
                println("Chain not registered: ${error.chainId}")
                println("Available chains: ${error.availableChains}")
                // Register chain or use alternative
            }
            is VeriCoreError.ValidationFailed -> {
                println("Invalid data or chain ID: ${error.reason}")
            }
            else -> {
                println("Anchoring error: ${error.message}")
                error.cause?.printStackTrace()
            }
        }
    }
)

// With timeout handling
import kotlinx.coroutines.withTimeout

val result = withTimeout(30000) { // 30 second timeout
    vericore.anchor(data, serializer, chainId)
}
```

**Errors:**
- `VeriCoreError.ChainNotRegistered` - Chain ID not registered in registry
- `VeriCoreError.ValidationFailed` - Invalid chain ID format or data serialization failure
- `VeriCoreError.Unknown` - Blockchain transaction failed (network error, insufficient funds, etc.)

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
val data = vericore.readAnchor<MyData>(
    ref = anchorRef,
    serializer = MyData.serializer()
).fold(
    onSuccess = { retrieved ->
        println("Read data: $retrieved")
        println("Verified against on-chain digest")
    },
    onFailure = { error ->
        when (error) {
            is VeriCoreError.ChainNotRegistered -> {
                println("Chain not registered: ${error.chainId}")
                println("Available chains: ${error.availableChains}")
            }
            is VeriCoreError.ValidationFailed -> {
                println("Data verification failed - possible tampering!")
                println("Reason: ${error.reason}")
            }
            else -> {
                println("Read error: ${error.message}")
                error.cause?.printStackTrace()
            }
        }
    }
)

// With type safety
val result: Result<MyData> = vericore.readAnchor(
    ref = anchorRef,
    serializer = MyData.serializer()
)
```

**Errors:**
- `VeriCoreError.ChainNotRegistered` - Chain ID not registered in registry
- `VeriCoreError.ValidationFailed` - Data digest doesn't match on-chain digest (tamper detected) or deserialization failed
- `VeriCoreError.Unknown` - Transaction not found or data retrieval failed

### Plugin Lifecycle Management

#### initialize

Initializes all plugins that implement `PluginLifecycle`.

```kotlin
suspend fun initialize(config: Map<String, Any?> = emptyMap()): Result<Unit>
```

**Example:**
```kotlin
val vericore = VeriCore.create()

val config = mapOf(
    "database" to mapOf("url" to "jdbc:postgresql://localhost/vericore")
)

vericore.initialize(config).fold(
    onSuccess = { println("Plugins initialized") },
    onFailure = { error -> println("Initialization error: ${error.message}") }
)
```

#### start

Starts all plugins that implement `PluginLifecycle`.

```kotlin
suspend fun start(): Result<Unit>
```

**Example:**
```kotlin
vericore.start().fold(
    onSuccess = { println("Plugins started") },
    onFailure = { error -> println("Start error: ${error.message}") }
)
```

#### stop

Stops all plugins that implement `PluginLifecycle`.

```kotlin
suspend fun stop(): Result<Unit>
```

**Example:**
```kotlin
vericore.stop().fold(
    onSuccess = { println("Plugins stopped") },
    onFailure = { error -> println("Stop error: ${error.message}") }
)
```

#### cleanup

Cleans up all plugins that implement `PluginLifecycle`.

```kotlin
suspend fun cleanup(): Result<Unit>
```

**Example:**
```kotlin
vericore.cleanup().fold(
    onSuccess = { println("Plugins cleaned up") },
    onFailure = { error -> println("Cleanup error: ${error.message}") }
)
```

## Error Types

All operations can return errors of type `VeriCoreError`. See [Error Handling](../advanced/error-handling.md) for complete error type documentation.

### Common Error Types

- `VeriCoreError.DidMethodNotRegistered` - DID method not registered
- `VeriCoreError.InvalidDidFormat` - Invalid DID format
- `VeriCoreError.CredentialInvalid` - Credential validation failed
- `VeriCoreError.ChainNotRegistered` - Chain ID not registered
- `VeriCoreError.WalletCreationFailed` - Wallet creation failed
- `VeriCoreError.PluginInitializationFailed` - Plugin initialization failed

## Result Utilities

VeriCore provides extension functions for working with `Result<T>`:

### mapError

Transform errors in a Result:

```kotlin
val result = vericore.createDid()
    .mapError { it.toVeriCoreError() }
```

### combine

Combine multiple Results:

```kotlin
val results = listOf(
    vericore.createDid(),
    vericore.createDid(),
    vericore.createDid()
)

val combined = results.combine { dids ->
    dids.map { it.id }
}
```

### mapAsync

Batch operations with async mapping:

```kotlin
val dids = listOf("did:key:1", "did:key:2", "did:key:3")
val results = dids.mapAsync { did ->
    vericore.resolveDid(did)
}
```

## Input Validation

VeriCore validates inputs before operations:

### DID Validation

```kotlin
import com.geoknoesis.vericore.core.DidValidator

val validation = DidValidator.validateFormat("did:key:z6Mk...")
if (!validation.isValid()) {
    val error = validation as ValidationResult.Invalid
    println("Validation failed: ${error.message}")
}
```

### Credential Validation

```kotlin
import com.geoknoesis.vericore.core.CredentialValidator

val validation = CredentialValidator.validateStructure(credential)
if (!validation.isValid()) {
    val error = validation as ValidationResult.Invalid
    println("Credential validation failed: ${error.message}")
}
```

### Chain ID Validation

```kotlin
import com.geoknoesis.vericore.core.ChainIdValidator

val validation = ChainIdValidator.validateFormat("algorand:testnet")
if (!validation.isValid()) {
    println("Invalid chain ID format")
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

