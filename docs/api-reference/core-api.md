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

Gets a list of all registered DID method names.

```kotlin
fun getAvailableDidMethods(): List<String>
```

**Example:**
```kotlin
val methods = vericore.getAvailableDidMethods()
println("Available DID methods: $methods")
// Output: [key, web, ion]
```

**Use Cases:**
- Debugging: Check which methods are registered
- Validation: Verify a method exists before using it
- UI: Display available methods to users

#### getAvailableChains

Gets a list of all registered blockchain chain IDs.

```kotlin
fun getAvailableChains(): List<String>
```

**Example:**
```kotlin
val chains = vericore.getAvailableChains()
println("Available chains: $chains")
// Output: [algorand:testnet, polygon:mainnet]
```

**Use Cases:**
- Debugging: Check which chains are registered
- Validation: Verify a chain exists before anchoring
- UI: Display available chains to users

#### registerDidMethod

Registers a DID method after VeriCore creation. Useful for dynamic registration.

```kotlin
fun registerDidMethod(method: DidMethod)
```

**Example:**
```kotlin
val vericore = VeriCore.create()
vericore.registerDidMethod(DidWebMethod())
// Now you can use did:web
val webDid = vericore.createDid("web").getOrThrow()
```

**Note:** Prefer registering methods during `VeriCore.create { }` configuration when possible.

#### registerBlockchainClient

Registers a blockchain client after VeriCore creation. Useful for dynamic registration.

```kotlin
fun registerBlockchainClient(chainId: String, client: BlockchainAnchorClient)
```

**Example:**
```kotlin
val vericore = VeriCore.create()
val algorandClient = AlgorandBlockchainAnchorClient(...)
vericore.registerBlockchainClient("algorand:testnet", algorandClient)
// Now you can anchor to algorand:testnet
```

**Note:** Prefer registering clients during `VeriCore.create { }` configuration when possible.

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

**Example:**
```kotlin
// Simple usage
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
            }
            else -> println("Error: ${error.message}")
        }
    }
)
```

**Errors:**
- `VeriCoreError.DidMethodNotRegistered` - Method is not registered
- `VeriCoreError.InvalidDidFormat` - Invalid DID format (if validation fails)

#### resolveDid

Resolves a DID to its document.

```kotlin
suspend fun resolveDid(did: String): Result<DidResolutionResult>
```

**Example:**
```kotlin
// Simple usage
val result = vericore.resolveDid("did:key:z6Mk...").getOrThrow()
if (result.document != null) {
    println("DID resolved: ${result.document.id}")
}

// With error handling
val result = vericore.resolveDid("did:key:z6Mk...")
result.fold(
    onSuccess = { resolution ->
        if (resolution.document != null) {
            println("DID resolved: ${resolution.document.id}")
        } else {
            println("DID not found")
        }
    },
    onFailure = { error ->
        when (error) {
            is VeriCoreError.InvalidDidFormat -> {
                println("Invalid DID format: ${error.reason}")
            }
            is VeriCoreError.DidMethodNotRegistered -> {
                println("Method not registered: ${error.method}")
            }
            else -> println("Error: ${error.message}")
        }
    }
)
```

**Parameters:**
- `did`: The DID string to resolve (must match format `did:<method>:<identifier>`)

**Returns:** `Result<DidResolutionResult>` containing:
- `document`: The DID document if found, `null` if not found
- `metadata`: Resolution metadata (method, timestamp, etc.)

**Errors:**
- `VeriCoreError.InvalidDidFormat` - Invalid DID format
- `VeriCoreError.DidMethodNotRegistered` - Method is not registered
- `VeriCoreError.DidNotFound` - DID not found (if resolution fails)

### Credential Operations

#### issueCredential

Issues a verifiable credential.

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
- `issuerDid`: The DID of the credential issuer (must be resolvable)
- `issuerKeyId`: The key ID from the issuer's DID document (e.g., `"${issuerDid}#key-1"`)
- `credentialSubject`: JSON object containing the credential claims (must include `"id"` field)
- `types`: List of credential types (default: `["VerifiableCredential"]`)
- `expirationDate`: Optional expiration date in ISO 8601 format (e.g., `"2025-12-31T23:59:59Z"`)

**Returns:** `Result<VerifiableCredential>` containing the signed credential with proof

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
- `credential`: The verifiable credential to verify
- `options`: Verification options (check expiration, revocation, etc.)

**Returns:** `Result<CredentialVerificationResult>` containing:
- `valid`: Overall validity (all checks passed)
- `proofValid`: Proof signature is valid
- `issuerValid`: Issuer DID resolved successfully
- `notRevoked`: Credential is not revoked (if revocation checked)
- `errors`: List of error messages if verification failed
- `warnings`: List of warnings (e.g., expiring soon)

**Example:**
```kotlin
// Simple usage
val result = vericore.verifyCredential(credential).getOrThrow()
if (result.valid) {
    println("Credential is valid")
} else {
    println("Errors: ${result.errors}")
}

// With error handling
val result = vericore.verifyCredential(credential)
result.fold(
    onSuccess = { verification ->
        if (verification.valid) {
            println("Credential is valid")
        } else {
            println("Credential invalid: ${verification.errors.joinToString()}")
        }
    },
    onFailure = { error -> println("Verification error: ${error.message}") }
)
```

**Errors:**
- `VeriCoreError.CredentialInvalid` - Credential validation failed

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
- `issuerDid`: The DID of the issuer who owns this status list
- `purpose`: `StatusPurpose.REVOCATION` or `StatusPurpose.SUSPENSION`
- `size`: Initial size of the status list in entries (default: 131072 = 16KB)
- `customId`: Optional custom ID for the status list (auto-generated if null)

**Returns:** `Result<StatusListCredential>` containing the created status list

**Example:**
```kotlin
val statusList = vericore.createStatusList(
    issuerDid = "did:key:issuer",
    purpose = StatusPurpose.REVOCATION
).getOrThrow()

println("Status List ID: ${statusList.id}")
```

#### revokeCredential

Revokes a credential by adding it to a status list.

```kotlin
suspend fun revokeCredential(
    credentialId: String,
    statusListId: String
): Result<Boolean>
```

**Parameters:**
- `credentialId`: The ID of the credential to revoke
- `statusListId`: The ID of the status list to add the credential to

**Returns:** `Result<Boolean>` - true if revocation succeeded

**Example:**
```kotlin
val revoked = vericore.revokeCredential(
    credentialId = "cred-123",
    statusListId = statusList.id
).getOrThrow()

if (revoked) {
    println("Credential revoked successfully")
}
```

#### suspendCredential

Suspends a credential (temporarily disables it).

```kotlin
suspend fun suspendCredential(
    credentialId: String,
    statusListId: String
): Result<Boolean>
```

**Parameters:**
- `credentialId`: The ID of the credential to suspend
- `statusListId`: The ID of the suspension status list

**Returns:** `Result<Boolean>` - true if suspension succeeded

**Example:**
```kotlin
val suspended = vericore.suspendCredential(
    credentialId = "cred-123",
    statusListId = suspensionList.id
).getOrThrow()
```

#### checkRevocationStatus

Checks if a credential is revoked or suspended.

```kotlin
suspend fun checkRevocationStatus(
    credential: VerifiableCredential
): Result<RevocationStatus>
```

**Parameters:**
- `credential`: The credential to check

**Returns:** `Result<RevocationStatus>` containing:
- `revoked`: Whether the credential is revoked
- `suspended`: Whether the credential is suspended
- `statusListId`: The status list ID if applicable
- `reason`: Optional revocation reason

**Example:**
```kotlin
val status = vericore.checkRevocationStatus(credential).getOrThrow()

if (status.revoked) {
    println("Credential is revoked")
    println("Status List: ${status.statusListId}")
} else if (status.suspended) {
    println("Credential is suspended")
} else {
    println("Credential is valid")
}
```

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

Creates a wallet for storing credentials.

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
- `holderDid`: The DID of the credential holder (required)
- `walletId`: Unique identifier for the wallet (default: random UUID)
- `provider`: Wallet provider implementation (default: `InMemory`)
- `options`: Wallet creation options (enable organization, presentation, etc.)
- `configure`: Builder function for wallet options (alternative to `options` parameter)

**Returns:** `Result<Wallet>` containing the created wallet instance

**Wallet Options:**
- `enableOrganization`: Enable collections, tags, and metadata features
- `enablePresentation`: Enable presentation and selective disclosure support
- `storagePath`: File system or bucket path for persistent storage
- `encryptionKey`: Secret material for at-rest encryption

**Example:**
```kotlin
// Simple usage
val wallet = vericore.createWallet("did:key:holder").getOrThrow()

// With builder
val wallet = vericore.createWallet("did:key:holder") {
    label = "Holder Wallet"
    storagePath = "/wallets/holder"
    enableOrganization = true
    enablePresentation = true
}.getOrThrow()

// With error handling
val result = vericore.createWallet("did:key:holder")
result.fold(
    onSuccess = { wallet -> println("Created wallet: ${wallet.walletId}") },
    onFailure = { error ->
        when (error) {
            is VeriCoreError.WalletCreationFailed -> {
                println("Wallet creation failed: ${error.reason}")
            }
            else -> println("Error: ${error.message}")
        }
    }
)
```

**Errors:**
- `VeriCoreError.WalletCreationFailed` - Wallet creation failed (provider not found, configuration invalid, etc.)
- `VeriCoreError.InvalidDidFormat` - Invalid holder DID format

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

Anchors data to a blockchain for tamper evidence and timestamping.

```kotlin
suspend fun <T : Any> anchor(
    data: T,
    serializer: KSerializer<T>,
    chainId: String
): Result<AnchorResult>
```

**Parameters:**
- `data`: The data to anchor (any serializable type)
- `serializer`: Kotlinx Serialization serializer for the data type
- `chainId`: Chain ID in CAIP-2 format (e.g., `"algorand:testnet"`)

**Returns:** `Result<AnchorResult>` containing:
- `ref`: `AnchorRef` with chain ID and transaction hash
- `timestamp`: When the data was anchored

**Note:** The data is serialized to JSON, canonicalized, and the digest is stored on the blockchain. The full data is not stored, only the digest.

**Example:**
```kotlin
// Simple usage
val result = vericore.anchor(
    data = myData,
    serializer = MyData.serializer(),
    chainId = "algorand:testnet"
).getOrThrow()
println("Anchored at: ${result.ref.txHash}")

// With error handling
val result = vericore.anchor(data, serializer, chainId)
result.fold(
    onSuccess = { anchor -> println("Anchored at: ${anchor.ref.txHash}") },
    onFailure = { error ->
        when (error) {
            is VeriCoreError.ChainNotRegistered -> {
                println("Chain not registered: ${error.chainId}")
                println("Available chains: ${error.availableChains}")
            }
            else -> println("Anchoring error: ${error.message}")
        }
    }
)
```

**Errors:**
- `VeriCoreError.ChainNotRegistered` - Chain ID not registered
- `VeriCoreError.ValidationFailed` - Invalid chain ID format

#### readAnchor

Reads anchored data from a blockchain using the anchor reference.

```kotlin
suspend fun <T : Any> readAnchor(
    ref: AnchorRef,
    serializer: KSerializer<T>
): Result<T>
```

**Parameters:**
- `ref`: `AnchorRef` containing chain ID and transaction hash
- `serializer`: Kotlinx Serialization serializer for the data type

**Returns:** `Result<T>` containing the deserialized data

**Note:** This reads the data that was stored during `anchor()`. The data must match the serializer type.

**Example:**
```kotlin
// Simple usage
val data = vericore.readAnchor<MyData>(
    ref = anchorRef,
    serializer = MyData.serializer()
).getOrThrow()

// With error handling
val result = vericore.readAnchor<MyData>(ref, serializer)
result.fold(
    onSuccess = { data -> println("Read: $data") },
    onFailure = { error ->
        when (error) {
            is VeriCoreError.ChainNotRegistered -> {
                println("Chain not registered: ${error.chainId}")
            }
            else -> println("Read error: ${error.message}")
        }
    }
)
```

**Errors:**
- `VeriCoreError.ChainNotRegistered` - Chain ID not registered

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

