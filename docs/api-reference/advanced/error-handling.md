---
title: Error Handling
nav_order: 30
parent: Advanced Topics
keywords:
  - error handling
  - exceptions
  - trustweaveexception
  - debugging
  - troubleshooting
redirect_from:
  - /advanced/error-handling/

---

# Error Handling

TrustWeave provides structured error handling with rich context for better debugging and error recovery.

## Overview

**Important:** The **`TrustWeave` facade is hybrid.** Credential operations **`issue`**, **`verify`**, **`presentationResult`**, and **batch** flows return **sealed result types**, not exceptions, for the primary failure modes (including **`AdapterNotReady`** when misconfigured). Many **DID** APIs return **`DidCreationResult`** and related sealed types.

**Sealed-result error handling (preferred for credentials):**
- Use exhaustive **`when`** on **`IssuanceResult`**, **`VerificationResult`**, **`PresentationResult`**
- See [API patterns — results vs exceptions](../../getting-started/api-patterns.md#api-contract-results-vs-exceptions)

**Exception-based error handling:**
- **`getOrThrow()`** / **`getOrThrowDid()`** throw **`IllegalStateException`**
- **Wallet**, some anchors/trust integration, and **`PresentationResult.getOrThrow()`** may throw **`TrustWeaveException`** and related domain exceptions

**`Result<T>` and services:**
- Lower-level or plugin APIs may return Kotlin **`Result<T>`**

## TrustWeave Facade Error Handling

Use **`when`** on sealed results for credential and DID results; use **try-catch** around **`getOrThrow()`** or APIs documented as throwing:

```kotlin
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.types.DidCreationResult
import org.trustweave.trust.types.WalletCreationResult
import org.trustweave.credential.results.IssuanceResult
import kotlinx.coroutines.runBlocking
import org.trustweave.trust.dsl.credential.DidMethods.KEY
import org.trustweave.trust.dsl.credential.KeyAlgorithms.ED25519
import org.trustweave.trust.dsl.credential.KmsProviders.IN_MEMORY

fun main() = runBlocking {
    val trustWeave = TrustWeave.build {
        keys { provider(IN_MEMORY); algorithm(ED25519) }
        did { method(KEY) { algorithm(ED25519) } }
    }

    val did = when (val dr = trustWeave.createDid { method(KEY); algorithm(ED25519) }) {
        is DidCreationResult.Success -> dr.did
        is DidCreationResult.Failure.MethodNotRegistered -> {
            println("DID method '${dr.method}' not registered; available: ${dr.availableMethods}")
            return@runBlocking
        }
        is DidCreationResult.Failure -> {
            println("DID creation failed: $dr")
            return@runBlocking
        }
    }
    println("Created DID: $did")

    when (val issued = trustWeave.issue {
        credential {
            type("VerifiableCredential", "ExampleCredential")
            issuer(did)
            subject {
                id("did:key:holder")
                "name" to "Alice"
            }
        }
        signedBy(did) // key id auto-extracted from the issuer DID document
    }) {
        is IssuanceResult.Success -> println("Issued credential: ${issued.credential.id}")
        is IssuanceResult.Failure.AdapterNotReady -> println("Configure CredentialService: ${issued.allErrors.joinToString()}")
        is IssuanceResult.Failure -> println("Issuance failed: ${issued.allErrors.joinToString()}")
    }

    when (val w = trustWeave.wallet { holder(did) }) {
        is WalletCreationResult.Success ->
            println("Wallet: ${w.wallet.holderDid}")
        is WalletCreationResult.Failure ->
            println("Wallet creation failed: $w")
    }
}
```

**Domain-Specific Exception Types (wallet and other throwing paths):**
- `DidException` (in `did:did-core`): `DidMethodNotRegistered`, `DidNotFound`, `InvalidDidFormat`, `DidResolutionFailed`, `DidCreationFailed`, `DidUpdateFailed`, `DidDeactivationFailed`, `RequiresAction`
- `WalletException` (in `wallet:wallet-core`): `WalletCreationFailed`, `WalletFactoryNotConfigured`, `InvalidHolderDid`, `StorageError`
- `BlockchainException` (in `anchors:anchor-core`): `ChainNotRegistered`, `TransactionFailed`, `ConnectionFailed`, `ConfigurationFailed`, `UnsupportedOperation`
- `TrustWeaveException`: Base exception with error codes and context

## Error Types

TrustWeave uses a sealed hierarchy of error types that extend `TrustWeaveException`. All errors include:
- **code**: String error code for programmatic handling
- **message**: Human-readable error message
- **context**: Map of additional context information
- **cause**: Optional underlying exception

### Complete Error Type Reference

| Error Type | Code | Properties | When It Occurs | Module |
|------------|------|------------|----------------|--------|
| **Plugin Errors** ||||
| `BlankPluginId` | `BLANK_PLUGIN_ID` | - | Plugin ID is blank | `common` |
| `PluginAlreadyRegistered` | `PLUGIN_ALREADY_REGISTERED` | `pluginId`, `existingPlugin` | Duplicate plugin registration | `common` |
| `PluginNotFound` | `PLUGIN_NOT_FOUND` | `pluginId`, `pluginType` | Plugin lookup fails | `common` |
| `PluginInitializationFailed` | `PLUGIN_INITIALIZATION_FAILED` | `pluginId`, `reason` | Plugin initialization fails | `common` |
| **Provider Errors** ||||
| `NoProvidersFound` | `NO_PROVIDERS_FOUND` | `pluginIds`, `availablePlugins` | No providers found for plugin IDs | `common` |
| `PartialProvidersFound` | `PARTIAL_PROVIDERS_FOUND` | `requestedIds`, `foundIds`, `missingIds` | Some providers found, some missing | `common` |
| `AllProvidersFailed` | `ALL_PROVIDERS_FAILED` | `attemptedProviders`, `providerErrors`, `lastException` | All providers in chain failed | `common` |
| **Configuration Errors** ||||
| `ConfigNotFound` | `CONFIG_NOT_FOUND` | `path` | Configuration file/resource not found | `common` |
| `ConfigReadFailed` | `CONFIG_READ_FAILED` | `path`, `reason` | Failed to read configuration file | `common` |
| `InvalidConfigFormat` | `INVALID_CONFIG_FORMAT` | `jsonString`, `parseError`, `field` | Invalid JSON format in configuration | `common` |
| **JSON/Digest Errors** ||||
| `InvalidJson` | `INVALID_JSON` | `jsonString`, `parseError`, `position` | Invalid JSON parsing error | `common` |
| `JsonEncodeFailed` | `JSON_ENCODE_FAILED` | `element`, `reason` | JSON encoding/serialization failed | `common` |
| `DigestFailed` | `DIGEST_FAILED` | `algorithm`, `reason` | Digest computation failed | `common` |
| `EncodeFailed` | `ENCODE_FAILED` | `operation`, `reason` | Encoding operation failed | `common` |
| **Generic Errors** ||||
| `ValidationFailed` | `VALIDATION_FAILED` | `field`, `reason`, `value` | Input validation fails | `common` |
| `InvalidOperation` | `INVALID_OPERATION` | `message`, `context`, `cause` | Invalid operation attempted | `common` |
| `InvalidState` | `INVALID_STATE` | `message`, `context`, `cause` | Invalid state detected | `common` |
| `Unknown` | `UNKNOWN_ERROR` | `message`, `context`, `cause` | Unhandled exception | `common` |
| `UnsupportedAlgorithm` | `UNSUPPORTED_ALGORITHM` | `algorithm`, `supportedAlgorithms` | Algorithm not supported | `common` |
| **Domain-Specific Errors** (in respective modules) ||||
| `DidNotFound` | `DID_NOT_FOUND` | `did`, `availableMethods` | DID resolution fails | `did` |
| `DidMethodNotRegistered` | `DID_METHOD_NOT_REGISTERED` | `method`, `availableMethods` | Using unregistered DID method | `did` |
| `InvalidDidFormat` | `INVALID_DID_FORMAT` | `did`, `reason` | DID format validation fails | `did` |
| `DidResolutionFailed` | `DID_RESOLUTION_FAILED` | `did`, `reason` | DID resolution throws (rare; prefer sealed `DidResolutionResult`) | `did` |
| `DidCreationFailed` | `DID_CREATION_FAILED` | `did`, `reason` | DID creation failure (when not surfaced via `DidCreationResult`) | `did` |
| `ChainNotRegistered` | `CHAIN_NOT_REGISTERED` | `chainId`, `availableChains` | Using unregistered blockchain | `anchor` |
| `WalletCreationFailed` | `WALLET_CREATION_FAILED` | `reason`, `provider`, `walletId` | Wallet creation fails (low-level) | `wallet` |
| `InvalidHolderDid` | `INVALID_HOLDER_DID` | `holderDid`, `reason` | Invalid holder DID | `wallet` |
| `StorageError` | `WALLET_STORAGE_ERROR` | `operation`, `reason` | Wallet storage error | `wallet` |

### DID-Related Errors (in `trustweave-did` module)

```kotlin
import org.trustweave.did.exception.DidException
import org.trustweave.did.exception.DidException.DidMethodNotRegistered
import org.trustweave.did.exception.DidException.DidNotFound
import org.trustweave.did.exception.DidException.InvalidDidFormat
import org.trustweave.trust.dsl.credential.DidMethods.KEY
import org.trustweave.trust.types.getOrThrowDid

// `getOrThrowDid()` throws IllegalStateException (not DidException) — wrap
// DID-method-level exceptions where you call into resolution/update APIs directly.
try {
    val did = trustWeave.createDid { method(KEY) }.getOrThrowDid()
} catch (error: DidException) {
    when (error) {
        is DidMethodNotRegistered -> {
            println("Method not registered: ${error.method}")
            println("Available: ${error.availableMethods}")
        }
        is DidNotFound -> {
            println("DID not found: ${error.did.value}")
        }
        is InvalidDidFormat -> {
            println("Invalid format: ${error.reason}")
        }
        else -> println("DID error: ${error.message}")
    }
}
```

### Credential-related errors (sealed result, not exception)

> **Note:** TrustWeave does **not** ship a `CredentialException` hierarchy. Credential issuance and verification surface failures via sealed results — handle them exhaustively rather than with try/catch.

```kotlin
import org.trustweave.credential.results.IssuanceResult

when (val issued = trustWeave.issue { /* ... */ }) {
    is IssuanceResult.Success -> { /* use issued.credential */ }
    is IssuanceResult.Failure.UnsupportedFormat -> println("Unsupported: ${issued.format.value}")
    is IssuanceResult.Failure.AdapterNotReady -> println("Configure CredentialService")
    is IssuanceResult.Failure.InvalidRequest -> println("Invalid field '${issued.field}': ${issued.reason}")
    is IssuanceResult.Failure.AdapterError -> println("Adapter error: ${issued.reason}")
    is IssuanceResult.Failure.MultipleFailures -> println(issued.allErrors.joinToString())
}
```

### Blockchain-Related Errors (in `trustweave-anchor` module)

```kotlin
import org.trustweave.anchor.exceptions.BlockchainException

// Chain not registered
BlockchainException.ChainNotRegistered(
    chainId = "ethereum:mainnet",
    availableChains = listOf("algorand:testnet", "polygon:testnet")
)

// Transaction failed
BlockchainException.TransactionFailed(
    chainId = "algorand:testnet",
    txHash = "ABC123...",
    operation = "anchor",
    reason = "Insufficient funds"
)

// Connection failed
BlockchainException.ConnectionFailed(
    chainId = "ethereum:mainnet",
    endpoint = "https://rpc.example.com",
    reason = "Connection timeout"
)
```

### Wallet creation (`WalletCreationResult`)

`trustWeave.wallet { }` returns **`WalletCreationResult`**. Prefer **`when`**; **`getOrThrow()`** throws **`IllegalStateException`** (not **`WalletException`**) with a detailed message.

```kotlin
import org.trustweave.trust.types.WalletCreationResult

when (val w = trustWeave.wallet { holder("did:key:holder") }) {
    is WalletCreationResult.Success -> println("Wallet: ${w.wallet.holderDid}")
    is WalletCreationResult.Failure.InvalidHolderDid -> println("Invalid holder: ${w.reason}")
    is WalletCreationResult.Failure.FactoryNotConfigured -> println("No factory: ${w.reason}")
    is WalletCreationResult.Failure.StorageFailed -> println("Storage: ${w.reason}")
    is WalletCreationResult.Failure.Other -> println("Other: ${w.reason}")
}
```

Lower-level wallet APIs may still throw **`WalletException`** subtypes where documented.

### Plugin-related errors

```kotlin
import org.trustweave.core.exception.PluginException

// Blank plugin id (singleton type)
PluginException.BlankId

PluginException.AlreadyRegistered(
    pluginId = "waltid-credential",
    existingPlugin = "walt.id Credential Service"
)

PluginException.NotFound(
    pluginId = "waltid-credential",
    pluginType = "credential-service"
)

PluginException.InitializationFailed(
    pluginId = "waltid-credential",
    reason = "Configuration missing"
)
```

### Provider chain errors

```kotlin
import org.trustweave.core.exception.ProviderException

ProviderException.NoneFound(
    pluginIds = listOf("provider1", "provider2"),
    availablePlugins = listOf("provider3", "provider4")
)

ProviderException.PartiallyFound(
    requestedIds = listOf("provider1", "provider2", "provider3"),
    foundIds = listOf("provider1", "provider2"),
    missingIds = listOf("provider3")
)

ProviderException.AllFailed(
    attemptedProviders = listOf("provider1", "provider2"),
    providerErrors = mapOf(
        "provider1" to "Connection timeout",
        "provider2" to "Authentication failed"
    ),
    lastException = timeoutException
)
```

### Configuration errors

```kotlin
import org.trustweave.core.exception.ConfigException

ConfigException.NotFound(path = "/path/to/config.json")

ConfigException.ReadFailed(
    path = "/path/to/config.json",
    reason = "Permission denied"
)

ConfigException.InvalidFormat(
    jsonString = "{ invalid json }",
    parseError = "Expected ',' or '}'",
    field = "plugins"
)
```

### JSON / digest / encoding errors

```kotlin
import org.trustweave.core.exception.SerializationException
import org.trustweave.core.exception.TrustWeaveException

SerializationException.InvalidJson(
    jsonString = "{ invalid }",
    parseError = "Expected ',' or '}'",
    position = "line 1, column 10"
)

SerializationException.EncodeFailed(
    element = "{ large object }",
    reason = "Circular reference detected"
)

TrustWeaveException.DigestFailed(
    algorithm = "SHA-256",
    reason = "Algorithm not available"
)

TrustWeaveException.EncodeFailed(
    operation = "base58-encoding",
    reason = "Invalid byte array"
)
```

### Validation Errors

```kotlin
import org.trustweave.core.exception.TrustWeaveException

// Validation failed
TrustWeaveException.ValidationFailed(
    field = "issuer",
    reason = "Invalid DID format",
    value = "invalid-did"
)
```

### Generic Errors

```kotlin
import org.trustweave.core.exception.TrustWeaveException

// Invalid operation
TrustWeaveException.InvalidOperation(
    code = "INVALID_OPERATION",
    message = "Operation not allowed in current state",
    context = mapOf("operation" to "createDid", "state" to "stopped"),
    cause = null
)

// Invalid state
TrustWeaveException.InvalidState(
    code = "INVALID_STATE",
    message = "TrustWeave not initialized",
    context = emptyMap(),
    cause = null
)

// Resource not found
TrustWeaveException.NotFound(
    resource = "did:key:z6Mk...",
    message = "Resource not found: did:key:z6Mk...",
    context = emptyMap(),
    cause = null
)

// Unknown error (catch-all)
TrustWeaveException.Unknown(
    code = "UNKNOWN_ERROR",
    message = "Unexpected error occurred",
    context = emptyMap(),
    cause = originalException
)
```

## Error Code Quick Reference

Quick lookup table for common error codes and their solutions:

| Code | Error Type | Common Causes | Solutions |
|------|------------|---------------|-----------|
| `DID_NOT_FOUND` | `DidNotFound` | DID not resolvable, method not registered, network issue | Check DID format, ensure method registered, verify network connectivity |
| `DID_METHOD_NOT_REGISTERED` | `DidMethodNotRegistered` | Method not in registry | Register the method on `DidMethodRegistry` / `TrustWeave.build`, or list names via `trustWeave.configuration.didRegistry.getAllMethodNames()` |
| `INVALID_DID_FORMAT` | `InvalidDidFormat` | DID doesn't match `did:<method>:<identifier>` format | Validate DID format before use, check for typos |
| `CHAIN_NOT_REGISTERED` | `ChainNotRegistered` | Chain not registered in registry | Register a client on `BlockchainAnchorRegistry` / `TrustWeave.build { anchor { ... } }`, or list IDs via `trustWeave.configuration.blockchainRegistry.getAllChainIds()` |
| `WALLET_CREATION_FAILED` | `WalletCreationFailed` | Provider not found, configuration invalid, storage unavailable | Check provider name, verify configuration, ensure storage accessible |
| `BLANK_PLUGIN_ID` | `BlankPluginId` | Plugin ID is blank | Provide a non-blank plugin ID |
| `PLUGIN_ALREADY_REGISTERED` | `PluginAlreadyRegistered` | Plugin already registered | Unregister existing plugin or use different ID |
| `PLUGIN_NOT_FOUND` | `PluginNotFound` | Plugin not on classpath, not registered | Add plugin dependency, register plugin manually |
| `PLUGIN_INITIALIZATION_FAILED` | `PluginInitializationFailed` | Configuration missing, connection failed, dependency issue | Check plugin configuration, verify dependencies, test connectivity |
| `NO_PROVIDERS_FOUND` | `NoProvidersFound` | No providers found for plugin IDs | Check plugin IDs, verify plugins are registered |
| `PARTIAL_PROVIDERS_FOUND` | `PartialProvidersFound` | Some providers found, some missing | Check missing plugin IDs, register missing plugins |
| `ALL_PROVIDERS_FAILED` | `AllProvidersFailed` | All providers in chain failed | Check provider errors, verify provider configuration |
| `CONFIG_NOT_FOUND` | `ConfigNotFound` | Configuration file/resource not found | Check file path, verify resource exists |
| `CONFIG_READ_FAILED` | `ConfigReadFailed` | Failed to read configuration file | Check file permissions, verify file is readable |
| `INVALID_CONFIG_FORMAT` | `InvalidConfigFormat` | Invalid JSON format in configuration | Validate JSON syntax, check field types |
| `INVALID_JSON` | `InvalidJson` | Invalid JSON parsing error | Validate JSON syntax, check for malformed JSON |
| `JSON_ENCODE_FAILED` | `JsonEncodeFailed` | JSON encoding/serialization failed | Check for circular references, verify object structure |
| `DIGEST_FAILED` | `DigestFailed` | Digest computation failed | Verify algorithm is available, check input data |
| `ENCODE_FAILED` | `EncodeFailed` | Encoding operation failed | Verify encoding operation, check input data |
| `VALIDATION_FAILED` | `ValidationFailed` | Input doesn't meet requirements | Validate inputs before operations, check format requirements |

## Common Pitfalls

### Pitfall 1: Using getOrThrow() in Production

**Bad:**
```kotlin
// Throws on failure — fine for scripts; risky in servers without a top-level handler
val did = trustWeave.createDid { }.getOrThrowDid()
```

**Good:**
```kotlin
when (val dr = trustWeave.createDid { }) {
    is DidCreationResult.Success -> processDid(dr.did)
    is DidCreationResult.Failure.MethodNotRegistered -> {
        logger.warn("Method not registered: ${dr.method}; available: ${dr.availableMethods}")
    }
    is DidCreationResult.Failure -> {
        logger.error("DID creation failed: $dr")
    }
}
```

**Why:** `createDid` returns a **sealed `DidCreationResult`**. Prefer exhaustive **`when`** in production instead of **`getOrThrowDid()`**.

### Pitfall 2: Not Checking Error Context

**Bad:**
```kotlin
result.fold(
    onFailure = { error ->
        println("Error: ${error.message}")  // Loses valuable context
    }
)
```

**Good:**
```kotlin
result.fold(
    onFailure = { error ->
        logger.error("Error: ${error.message}")
        logger.debug("Error code: ${error.code}")
        logger.debug("Context: ${error.context}")

        // Use context for better error handling
        when (error) {
            is DidException.DidMethodNotRegistered -> {
                logger.info("Available methods: ${error.availableMethods}")
                // Suggest alternatives to user
            }
            is BlockchainException.ChainNotRegistered -> {
                logger.info("Available chains: ${error.availableChains}")
                // Suggest fallback chains
            }
            // ... handle other specific errors
        }
    }
)
```

**Why:** Error context contains valuable debugging information and alternative options.

### Pitfall 3: Ignoring Warnings in Verification Results

**Bad:**
```kotlin
when (val v = trustWeave.verify(credential)) {
    is VerificationResult.Valid -> processCredential(credential) // ignores warnings on `v`
    is VerificationResult.Invalid -> { }
}
```

**Good:**
```kotlin
when (val v = trustWeave.verify(credential)) {
    is VerificationResult.Valid -> {
        if (v.warnings.isNotEmpty()) {
            logger.warn("Credential verified with warnings: ${v.warnings}")
        }
        processCredential(v.credential)
    }
    is VerificationResult.Invalid -> {
        logger.error("Invalid: ${v.allErrors.joinToString()}")
    }
}
```

**Why:** **`VerificationResult.Valid`** carries **warnings** you should log or policy-check before treating the credential as fully trusted.

### Pitfall 4: Not Validating Inputs Before Operations

**Bad:**
```kotlin
// No validation, may fail with cryptic error
val resolution = trustWeave.resolveDid(userInputDid)
```

**Good:**
```kotlin
// Validate before operation
val validation = DidValidator.validateFormat(userInputDid)
if (!validation.isValid()) {
    val error = validation as ValidationResult.Invalid
    return Result.failure(
        DidException.InvalidDidFormat(
            did = userInputDid,
            reason = error.message
        )
    )
}

// Now safe to proceed — still handle Failure branches
when (val resolution = trustWeave.resolveDid(userInputDid)) {
    is DidResolutionResult.Success -> { /* use resolution.document */ }
    is DidResolutionResult.Failure -> { /* NotFound, MethodNotRegistered, … */ }
}
```

**Why:** Early validation improves messages; resolution returns a **sealed `DidResolutionResult`** you should handle explicitly.

### Pitfall 5: Not Handling Specific Error Types

**Bad:**
```kotlin
result.fold(
    onFailure = { error ->
        // Generic handling loses specific error information
        println("Something went wrong: ${error.message}")
    }
)
```

**Good:**
```kotlin
result.fold(
    onFailure = { error ->
        when (error) {
            is DidException.DidMethodNotRegistered -> {
                // Specific handling for method not registered
                logger.warn("Method not registered: ${error.method}")
                logger.info("Available methods: ${error.availableMethods}")
                // Register method or suggest alternatives
            }
            is DidException.InvalidDidFormat -> {
                // Specific handling for invalid format
                logger.error("Invalid DID format: ${error.reason}")
                // Show format requirements to user
            }
            is CredentialException.CredentialInvalid -> {
                // Specific handling for invalid credential
                logger.error("Credential invalid: ${error.reason}")
                logger.debug("Field: ${error.field}")
                // Fix credential or reject
            }
            else -> {
                // Generic handling for unknown errors
                logger.error("Unexpected error: ${error.message}", error)
            }
        }
    }
)
```

**Why:** Specific error types provide actionable information for recovery.

## Error Handling Patterns

### Basic Error Handling

```kotlin
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.types.DidCreationResult

val trustWeave = TrustWeave.quickStart()

when (val dr = trustWeave.createDid { }) {
    is DidCreationResult.Success -> println("Created DID: ${dr.did.value}")
    is DidCreationResult.Failure.MethodNotRegistered -> {
        println("Method not registered: ${dr.method}")
        println("Available methods: ${dr.availableMethods}")
    }
    is DidCreationResult.Failure -> println("DID creation failed: $dr")
}
```

### Using getOrThrow for Simple Cases

```kotlin
// Scripts / tests: collapse failures to an exception
val did = trustWeave.createDid { }.getOrThrowDid()
```

### Error Context

Blockchain anchoring via **`trustWeave.blockchains`** throws **`BlockchainException`** (e.g. **`ChainNotRegistered`**) on configuration errors; successful calls return **`AnchorResult`**.

```kotlin
import org.trustweave.anchor.exceptions.BlockchainException

try {
    val anchor = trustWeave.blockchains.anchor(
        data = payload,
        serializer = MyPayload.serializer(),
        chainId = "ethereum:mainnet"
    )
    println("Anchored: ${anchor.ref.txHash}")
} catch (e: BlockchainException.ChainNotRegistered) {
    println("Chain ID: ${e.chainId}")
    println("Available chains: ${e.availableChains}")
}
```

### Converting Exceptions to Errors

TrustWeave automatically converts exceptions to `TrustWeaveException`:

```kotlin
import org.trustweave.core.exception.toTrustWeaveException

try {
    // Some operation that might throw
    val result = someOperation()
} catch (e: Exception) {
    val error = e.toTrustWeaveException()
    println("Error code: ${error.code}")
    println("Context: ${error.context}")
}
```

## Result utilities and batching

Lower-level APIs may return Kotlin **`Result<T>`**. The **`org.trustweave.core.util`** module provides helpers such as **`Result.mapError`** (see source / tests). For the **`TrustWeave`** facade, prefer **sealed results** (`DidCreationResult`, `IssuanceResult`, `DidResolutionResult`, …) and **`when`**.

### Batching DID creation (coroutines)

```kotlin
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.types.DidCreationResult

suspend fun createMany(trustWeave: TrustWeave) = coroutineScope {
    val outcomes = listOf(
        async { trustWeave.createDid { } },
        async { trustWeave.createDid { } },
        async { trustWeave.createDid { } },
    ).awaitAll()
    outcomes.forEach { dr ->
        when (dr) {
            is DidCreationResult.Success -> println(dr.did.value)
            is DidCreationResult.Failure -> println("Failed: $dr")
        }
    }
}
```

### Batching DID resolution

```kotlin
suspend fun resolveMany(trustWeave: TrustWeave, dids: List<String>) = coroutineScope {
    dids.map { did ->
        async { trustWeave.resolveDid(did) }
    }.awaitAll().forEach { res ->
        when (res) {
            is DidResolutionResult.Success -> println("Resolved: ${res.document.id}")
            is DidResolutionResult.Failure -> println("Resolve failed: $res")
        }
    }
}
```

## Input Validation

TrustWeave validates inputs before operations to catch errors early:

### DID Validation

```kotlin
import org.trustweave.did.validation.DidValidator
import org.trustweave.core.util.ValidationResult

// Validate DID format
val validation = DidValidator.validateFormat("did:key:z6Mk...")
if (!validation.isValid()) {
    val error = validation as ValidationResult.Invalid
    println("Validation failed: ${error.message}")
    println("Field: ${error.field}")
    println("Value: ${error.value}")
}

// Validate DID method
val availableMethods = listOf("key", "web")
val methodValidation = DidValidator.validateMethod("did:key:z6Mk...", availableMethods)
if (!methodValidation.isValid()) {
    println("Method not supported")
}
```

### Credential Validation

```kotlin
import org.trustweave.credential.validation.CredentialValidator

// Validate credential structure
val validation = CredentialValidator.validateStructure(credential)
if (!validation.isValid()) {
    val error = validation as ValidationResult.Invalid
    println("Credential validation failed: ${error.message}")
    println("Field: ${error.field}")
}

// Validate proof
val proofValidation = CredentialValidator.validateProof(credential)
if (!proofValidation.isValid()) {
    println("Credential missing proof")
}
```

### Chain ID Validation

```kotlin
import org.trustweave.anchor.validation.ChainIdValidator

// Validate chain ID format
val validation = ChainIdValidator.validateFormat("algorand:testnet")
if (!validation.isValid()) {
    println("Invalid chain ID format")
}

// Validate chain is registered
val availableChains = listOf("algorand:testnet", "polygon:testnet")
val registeredValidation = ChainIdValidator.validateRegistered("ethereum:mainnet", availableChains)
if (!registeredValidation.isValid()) {
    println("Chain not registered")
}
```

## Best Practices

### 1. Always Handle Errors

```kotlin
// Bad: ignoring the sealed result
val dr = trustWeave.createDid { }

// Good: exhaustive handling (or getOrThrowDid() only where appropriate)
when (val dr = trustWeave.createDid { }) {
    is DidCreationResult.Success -> { /* use dr.did */ }
    is DidCreationResult.Failure -> { /* log / map to client error */ }
}
```

### 2. Use Error Context

```kotlin
import org.trustweave.anchor.exceptions.BlockchainException

try {
    trustWeave.blockchains.anchor(data, serializer, chainId)
} catch (e: BlockchainException.ChainNotRegistered) {
    logger.error("Anchoring failed: ${e.message}")
    logger.debug("Available chains: ${e.availableChains}")
}
```

### 3. Check Error Types

```kotlin
when (val dr = trustWeave.createDid { method(WEB) }) {
    is DidCreationResult.Success -> { /* … */ }
    is DidCreationResult.Failure.MethodNotRegistered -> {
        println("Method 'web' not available. Try: ${dr.availableMethods}")
    }
    is DidCreationResult.Failure -> println("DID creation failed: $dr")
}
```

### 4. Validate Inputs Early

```kotlin
val did = "did:key:z6Mk..."
val validation = DidValidator.validateFormat(did)
if (!validation.isValid()) {
    return Result.failure(
        DidException.InvalidDidFormat(did, validation.errorMessage() ?: "invalid")
    )
}
when (val r = trustWeave.resolveDid(did)) {
    is DidResolutionResult.Success -> { /* … */ }
    is DidResolutionResult.Failure -> { /* … */ }
}
```

### 5. Batch Explicitly

For multiple resolutions, use coroutines (see [Result utilities and batching](#result-utilities-and-batching)) and handle each **`DidResolutionResult`**.

## Plugin registry errors

The **`TrustWeave`** facade does not expose **`initialize()` / `start()` / `stop()`** for plugins. Registration and SPI discovery use **`PluginRegistry`** and related APIs; failures surface as **`PluginException`** (for example **`InitializationFailed`**).

```kotlin
import org.trustweave.core.exception.PluginException

try {
    // register or load a plugin-capable provider
} catch (e: PluginException.InitializationFailed) {
    println("Plugin ${e.pluginId} failed: ${e.reason}")
}
```

See [Plugin lifecycle](plugin-lifecycle.md) for registration patterns.

## Migration Guide

If you're migrating from exception-based error handling to Result-based:

### Before (Exception-only style)

```kotlin
try {
    val did = trustWeave.createDid { }.getOrThrowDid()
    val credential = trustWeave.issue { /* ... */ }.getOrThrow()
} catch (e: IllegalArgumentException) {
    println("Invalid argument: ${e.message}")
} catch (e: Exception) {
    println("Error: ${e.message}")
}
```

### After (Sealed results + domain exceptions)

```kotlin
when (val dr = trustWeave.createDid { }) {
    is DidCreationResult.Success -> {
        when (val issued = trustWeave.issue { /* ... */ }) {
            is IssuanceResult.Success -> { /* use issued.credential */ }
            is IssuanceResult.Failure -> println("Issuance failed: ${issued.allErrors.joinToString()}")
        }
    }
    is DidCreationResult.Failure.MethodNotRegistered -> {
        println("Method not registered: ${dr.method}")
    }
    is DidCreationResult.Failure -> {
        println("DID creation failed: $dr")
    }
}
```

## Error Recovery Patterns

### Retry with backoff (sealed `DidResolutionResult`)

`resolveDid` returns a **sealed result**, not `Result<T>`. Retry only when failure might be transient (your policy may differ):

```kotlin
import kotlin.math.min
import kotlinx.coroutines.delay
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.trust.TrustWeave

suspend fun resolveWithRetry(trustWeave: TrustWeave, did: String): DidResolutionResult {
    var waitMs = 500L
    repeat(3) { attempt ->
        when (val r = trustWeave.resolveDid(did)) {
            is DidResolutionResult.Success -> return r
            is DidResolutionResult.Failure.NotFound -> return r
            is DidResolutionResult.Failure.MethodNotRegistered -> return r
            else -> {
                if (attempt == 2) return r
                delay(waitMs)
                waitMs = min(waitMs * 2, 5_000L)
            }
        }
    }
    error("unreachable")
}
```

### Fallback strategies

There is no generic “swap DID method in the string and re-resolve” API—fallbacks are **policy**: use another resolver, another endpoint, or a cached document. Prefer **`DidResolutionResult.Failure`** branches to drive that logic.

### Configure methods before `createDid`

Register DID methods in **`TrustWeave.build { did { ... } }`** (or on the underlying **`DidMethodRegistry`**) **before** calling **`createDid`**. Runtime “auto-registration” snippets are not part of the public facade.

### Circuit breaker with `Result`

If you wrap facade calls into **`Result`** for resilience libraries, keep the boundary explicit:

```kotlin
fun didResolutionToResult(r: DidResolutionResult): Result<DidResolutionResult> =
    when (r) {
        is DidResolutionResult.Success -> Result.success(r)
        is DidResolutionResult.Failure -> Result.failure(IllegalStateException(r.toString()))
    }

// circuitBreaker.execute { didResolutionToResult(trustWeave.resolveDid(did)) }
```

### Verification degradation

Use **`VerificationResult`** from **`trustWeave.verify`**. A minimal degraded path is “structure-only” checks when the credential service is unavailable:

```kotlin
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.results.VerificationResult
import org.trustweave.credential.validation.CredentialValidator
import org.trustweave.trust.TrustWeave

suspend fun verifyWithDegrade(trustWeave: TrustWeave, credential: VerifiableCredential, strictMode: Boolean) {
    when (val v = trustWeave.verify(credential)) {
        is VerificationResult.Valid -> { /* use v.credential, inspect v.warnings */ }
        is VerificationResult.Invalid.AdapterNotReady -> {
            if (!strictMode && CredentialValidator.validateStructure(credential).isValid()) {
                // Policy: accept structurally valid credentials when verification service is down
            } else { /* reject */ }
        }
        is VerificationResult.Invalid -> { /* handle v.allErrors */ }
    }
}
```

### Batch resolution

```kotlin
suspend fun batchResolve(
    trustWeave: TrustWeave,
    dids: List<String>
): Map<String, DidResolutionResult> = dids.associateWith { trustWeave.resolveDid(it) }
```

### Timeouts

```kotlin
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.trustweave.core.exception.TrustWeaveException

suspend fun <T> withTimeoutOrResult(timeoutMillis: Long, block: suspend () -> T): Result<T> =
    try {
        Result.success(withTimeout(timeoutMillis) { block() })
    } catch (e: TimeoutCancellationException) {
        Result.failure(
            TrustWeaveException.Unknown(
                message = "Operation timed out after ${timeoutMillis}ms",
                cause = e
            )
        )
    }

// Usage: wrap a suspend call (here: resolution returns DidResolutionResult, not Result)
val resolutionResult = withTimeoutOrResult(5_000) {
    trustWeave.resolveDid("did:web:example.com")
}.getOrThrow()
```

## Exception vs Sealed Result Patterns

For a detailed explanation of when to use exceptions vs sealed results, see [Error Handling Patterns](error-handling-patterns.md):
- When to use exceptions (programming errors)
- When to use sealed results (expected failures)
- Decision matrix for choosing the right pattern
- Best practices for each pattern

## Related Documentation

- Error Handling Patterns](error-handling-patterns.md) - Exceptions vs sealed results guide
- API Reference](../api-reference/)
- Verification Policies](verification-policies.md)
- Plugin Lifecycle](plugin-lifecycle.md)
- Troubleshooting](../getting-started/troubleshooting.md)

