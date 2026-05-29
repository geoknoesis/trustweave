---
title: Error Types
nav_order: 2
parent: API Reference
keywords:
  - errors
  - error types
  - exceptions
  - error handling
---

# Error Types Reference

Complete reference for all TrustWeave error types.

## Error Hierarchy

All TrustWeave exceptions extend the **open** class `TrustWeaveException`. Plugin / provider / config / serialization errors live in dedicated **sealed** subhierarchies (in `common`), and each domain module ships its own sealed `*Exception` family.

```kotlin
// Base (open, NOT sealed — domain modules extend it)
open class TrustWeaveException(
    open val code: String,
    override val message: String,
    open val context: Map<String, Any?> = emptyMap(),
    override val cause: Throwable? = null
) : Exception(message, cause) {
    // Core generic exceptions (nested in this class)
    data class DigestFailed(...) : TrustWeaveException(...)
    data class EncodeFailed(...) : TrustWeaveException(...)
    data class ValidationFailed(...) : TrustWeaveException(...)
    data class InvalidOperation(...) : TrustWeaveException(...)
    data class InvalidState(...) : TrustWeaveException(...)
    data class NotFound(...) : TrustWeaveException(...)
    data class UnsupportedAlgorithm(...) : TrustWeaveException(...)
    data class Unknown(...) : TrustWeaveException(...)
}

// Plugin hierarchy (org.trustweave.core.exception.PluginException)
sealed class PluginException : TrustWeaveException(...) {
    data class NotFound(...) : PluginException(...)
    data class InitializationFailed(...) : PluginException(...)
    data class AlreadyRegistered(...) : PluginException(...)
    object BlankId : PluginException(...)   // singleton, not a data class
}

// Provider hierarchy (org.trustweave.core.exception.ProviderException)
sealed class ProviderException : TrustWeaveException(...) {
    data class NoneFound(...) : ProviderException(...)
    data class PartiallyFound(...) : ProviderException(...)
    data class AllFailed(...) : ProviderException(...)
}

// Configuration hierarchy (org.trustweave.core.exception.ConfigException)
sealed class ConfigException : TrustWeaveException(...) {
    data class NotFound(...) : ConfigException(...)
    data class ReadFailed(...) : ConfigException(...)
    data class InvalidFormat(...) : ConfigException(...)
}

// JSON / serialization hierarchy (org.trustweave.core.exception.SerializationException)
sealed class SerializationException : TrustWeaveException(...) {
    data class InvalidJson(...) : SerializationException(...)
    data class EncodeFailed(...) : SerializationException(...)
}

// Domain hierarchies
sealed class DidException : TrustWeaveException(...) {
    data class DidNotFound(...); data class DidMethodNotRegistered(...);
    data class InvalidDidFormat(...); data class DidResolutionFailed(...);
    data class DidCreationFailed(...); data class DidUpdateFailed(...);
    data class DidDeactivationFailed(...); data class RequiresAction(...);
}

sealed class BlockchainException : TrustWeaveException(...) {
    data class TransactionFailed(...); data class ConnectionFailed(...);
    data class ConfigurationFailed(...); data class UnsupportedOperation(...);
    data class ChainNotRegistered(...);
}

sealed class WalletException : TrustWeaveException(...) {
    data class WalletCreationFailed(...); data class WalletFactoryNotConfigured(...);
    data class InvalidHolderDid(...); data class StorageError(...);
}

sealed class KmsException : TrustWeaveException(...) {
    data class KeyNotFound(...); data class KeyGenerationFailed(...);
    data class SigningFailed(...); data class KeyDeletionFailed(...);
}

// There is NO CredentialException class. Credential issuance/verification
// uses sealed result types (IssuanceResult, VerificationResult) instead.
```

## Error Types by Category

### Plugin Errors (`PluginException`)

| Error Type | Code | Properties | When It Occurs |
|------------|------|------------|----------------|
| `PluginException.BlankId` (singleton object) | `BLANK_PLUGIN_ID` | – | Plugin ID is blank |
| `PluginException.AlreadyRegistered` | `PLUGIN_ALREADY_REGISTERED` | `pluginId`, `existingPlugin` | Duplicate plugin registration |
| `PluginException.NotFound` | `PLUGIN_NOT_FOUND` | `pluginId`, `pluginType` | Plugin lookup fails |
| `PluginException.InitializationFailed` | `PLUGIN_INITIALIZATION_FAILED` | `pluginId`, `reason` | Plugin initialization fails |

### Provider Errors (`ProviderException`)

| Error Type | Code | Properties | When It Occurs |
|------------|------|------------|----------------|
| `ProviderException.NoneFound` | `NO_PROVIDERS_FOUND` | `pluginIds`, `availablePlugins` | No providers found for plugin IDs |
| `ProviderException.PartiallyFound` | `PARTIAL_PROVIDERS_FOUND` | `requestedIds`, `foundIds`, `missingIds` | Some providers found, some missing |
| `ProviderException.AllFailed` | `ALL_PROVIDERS_FAILED` | `attemptedProviders`, `providerErrors`, `lastException` | All providers in chain failed |

### Configuration Errors (`ConfigException`)

| Error Type | Code | Properties | When It Occurs |
|------------|------|------------|----------------|
| `ConfigException.NotFound` | `CONFIG_NOT_FOUND` | `path` | Configuration file/resource not found |
| `ConfigException.ReadFailed` | `CONFIG_READ_FAILED` | `path`, `reason` | Failed to read configuration file |
| `ConfigException.InvalidFormat` | `INVALID_CONFIG_FORMAT` | `jsonString`, `parseError`, `field` | Invalid JSON format in configuration |

### JSON/Digest Errors

| Error Type | Code | Properties | When It Occurs |
|------------|------|------------|----------------|
| `SerializationException.InvalidJson` | `INVALID_JSON` | `jsonString`, `parseError`, `position` | Invalid JSON parsing error |
| `SerializationException.EncodeFailed` | `JSON_ENCODE_FAILED` | `element`, `reason` | JSON encoding/serialization failed |
| `TrustWeaveException.DigestFailed` | `DIGEST_FAILED` | `algorithm`, `reason` | Digest computation failed |
| `TrustWeaveException.EncodeFailed` | `ENCODE_FAILED` | `operation`, `reason` | Encoding operation failed |

### Generic Errors

| Error Type | Code | Properties | When It Occurs |
|------------|------|------------|----------------|
| `ValidationFailed` | `VALIDATION_FAILED` | `field`, `reason`, `value` | Input validation fails |
| `InvalidOperation` | `INVALID_OPERATION` | `message`, `context`, `cause` | Invalid operation attempted |
| `InvalidState` | `INVALID_STATE` | `message`, `context`, `cause` | Invalid state detected |
| `NotFound` | `NOT_FOUND` | `resource`, `message`, `context`, `cause` | Resource not found |
| `Unknown` | `UNKNOWN_ERROR` | `message`, `context`, `cause` | Unhandled exception |
| `UnsupportedAlgorithm` | `UNSUPPORTED_ALGORITHM` | `algorithm`, `supportedAlgorithms` | Algorithm not supported |

### Domain-Specific Errors

#### DID Errors

All DID errors are part of the `DidException` sealed class hierarchy (extends `TrustWeaveException`):

| Error Type | Code | Properties | When It Occurs | Module |
|------------|------|------------|----------------|--------|
| `DidException.DidNotFound` | `DID_NOT_FOUND` | `did`, `availableMethods` | DID resolution fails | `did` |
| `DidException.DidMethodNotRegistered` | `DID_METHOD_NOT_REGISTERED` | `method`, `availableMethods` | Using unregistered DID method | `did` |
| `DidException.InvalidDidFormat` | `INVALID_DID_FORMAT` | `did`, `reason` | DID format validation fails | `did` |
| `DidException.DidResolutionFailed` | `DID_RESOLUTION_FAILED` | `did`, `reason`, `cause` | DID resolution failed at runtime | `did` |
| `DidException.DidCreationFailed` | `DID_CREATION_FAILED` | `did?`, `reason`, `cause` | DID method failed to create | `did` |
| `DidException.DidUpdateFailed` | `DID_UPDATE_FAILED` | `did`, `reason`, `cause` | DID update failed | `did` |
| `DidException.DidDeactivationFailed` | `DID_DEACTIVATION_FAILED` | `did`, `reason`, `cause` | DID deactivation failed | `did` |
| `DidException.RequiresAction` | `DID_REQUIRES_ACTION` | `did?`, `action`, `reason` | Registrar returned an ACTION state | `did` |

#### Credential Errors

There is **no** `CredentialException` class. Credential issuance and verification use sealed result types instead — see [`IssuanceResult`](result-types-guide.md#issuanceresult) and [`VerificationResult`](result-types-guide.md#verificationresult). Underlying KMS / DID failures may still throw `KmsException` / `DidException` during issuance.

#### Blockchain Errors

All blockchain errors are part of the `BlockchainException` sealed class hierarchy (extends `TrustWeaveException`):

| Error Type | Code | Properties | When It Occurs | Module |
|------------|------|------------|----------------|--------|
| `BlockchainException.TransactionFailed` | `BLOCKCHAIN_TRANSACTION_FAILED` | `chainId`, `txHash`, `operation`, `payloadSize`, `gasUsed`, `reason` | Blockchain transaction fails | `anchor` |
| `BlockchainException.ConnectionFailed` | `BLOCKCHAIN_CONNECTION_FAILED` | `chainId`, `endpoint`, `reason` | Connection to blockchain fails | `anchor` |
| `BlockchainException.ConfigurationFailed` | `BLOCKCHAIN_CONFIGURATION_FAILED` | `chainId`, `configKey`, `reason` | Blockchain configuration fails | `anchor` |
| `BlockchainException.UnsupportedOperation` | `BLOCKCHAIN_UNSUPPORTED_OPERATION` | `chainId`, `operation`, `reason` | Operation not supported on chain | `anchor` |
| `BlockchainException.ChainNotRegistered` | `CHAIN_NOT_REGISTERED` | `chainId`, `availableChains` | Using unregistered blockchain | `anchor` |

#### Wallet Errors

All wallet errors are part of the `WalletException` sealed class hierarchy (extends `TrustWeaveException`):

| Error Type | Code | Properties | When It Occurs | Module |
|------------|------|------------|----------------|--------|
| `WalletException.WalletCreationFailed` | `WALLET_CREATION_FAILED` | `reason`, `provider`, `walletId` | Wallet creation fails | `wallet` |
| `WalletException.WalletFactoryNotConfigured` | `WALLET_FACTORY_NOT_CONFIGURED` | `reason` | No `WalletFactory` wired in `TrustWeave.build { }` | `wallet` |
| `WalletException.InvalidHolderDid` | `INVALID_HOLDER_DID` | `holderDid`, `reason` | Holder DID invalid | `wallet` |
| `WalletException.StorageError` | `WALLET_STORAGE_ERROR` | `operation`, `reason`, `cause` | Backing store failed | `wallet` |

#### KMS Errors

All KMS errors are part of the `KmsException` sealed class hierarchy (extends `TrustWeaveException`):

| Error Type | Code | Properties | When It Occurs | Module |
|------------|------|------------|----------------|--------|
| `KmsException.KeyNotFound` | `KEY_NOT_FOUND` | `keyId`, `keyType?` | Key lookup fails | `kms` |
| `KmsException.KeyGenerationFailed` | `KEY_GENERATION_FAILED` | `algorithm`, `reason`, `cause?` | Key generation fails | `kms` |
| `KmsException.SigningFailed` | `SIGNING_FAILED` | `keyId`, `reason`, `cause?` | Signing fails | `kms` |
| `KmsException.KeyDeletionFailed` | `KEY_DELETION_FAILED` | `keyId`, `reason`, `cause?` | Key deletion fails | `kms` |

## Error Handling Examples

### Handling DID Errors

```kotlin
import org.trustweave.did.exception.DidException
import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.trust.dsl.credential.DidMethods.KEY

try {
    val did = trustWeave.createDid { method(KEY) }
} catch (error: TrustWeaveException) {
    when (error) {
        is DidException.DidMethodNotRegistered -> {
            println("Method not registered: ${error.method}")
            println("Available: ${error.availableMethods}")
        }
        is DidException.InvalidDidFormat -> {
            println("Invalid DID format: ${error.reason}")
        }
        is DidException.DidNotFound -> {
            println("DID not found: ${error.did}")
        }
        else -> {
            println("Error: ${error.message}")
        }
    }
}
```

### Handling Credential Errors

Issuance returns **`IssuanceResult`**. Prefer **`when`**; use **`getOrThrow()`** only when you intentionally collapse failures to exceptions.

```kotlin
import org.trustweave.credential.results.IssuanceResult

when (val issued = trustWeave.issue { ... }) {
    is IssuanceResult.Success -> {
        val credential = issued.credential
        // use credential
    }
    is IssuanceResult.Failure.InvalidRequest -> {
        println("Invalid request: ${issued.field} — ${issued.reason}")
    }
    is IssuanceResult.Failure.AdapterError -> {
        println("Issuance failed: ${issued.reason}")
    }
    is IssuanceResult.Failure -> {
        println("Issuance failed: ${issued.allErrors.joinToString()}")
    }
}

// Verification flows return `VerificationResult` (sealed). DID/KMS errors thrown
// from the underlying layers extend `TrustWeaveException` (e.g. `DidException`, `KmsException`).
```

### Handling Blockchain Errors

```kotlin
import org.trustweave.anchor.exceptions.BlockchainException
import kotlinx.serialization.Serializable

@Serializable
data class MyData(val id: String, val value: String)

// BlockchainService.anchor returns AnchorResult on success; chain/client problems throw BlockchainException
try {
    val anchor = trustWeave.blockchains.anchor(
        data = MyData("123", "test"),
        serializer = MyData.serializer(),
        chainId = "algorand:testnet"
    )
    println("Anchored: ${anchor.ref.txHash}")
} catch (error: BlockchainException.ChainNotRegistered) {
    println("Chain not registered: ${error.chainId}")
    println("Available: ${error.availableChains}")
} catch (error: BlockchainException.TransactionFailed) {
    println("Transaction failed: ${error.reason}")
    error.txHash?.let { println("Transaction hash: $it") }
} catch (error: BlockchainException.ConnectionFailed) {
    println("Connection failed: ${error.reason}")
} catch (error: Throwable) {
    println("Error: ${error.message}")
}
```

### Handling Wallet Errors

`trustWeave.wallet { ... }` returns a sealed `WalletCreationResult` — prefer `when` over `try/catch`. `WalletException` subtypes still appear when a lower-level wallet provider throws.

```kotlin
import org.trustweave.trust.types.WalletCreationResult

when (val r = trustWeave.wallet { holder("did:key:holder") }) {
    is WalletCreationResult.Success -> r.wallet
    is WalletCreationResult.Failure.InvalidHolderDid -> println("Bad DID: ${r.reason}")
    is WalletCreationResult.Failure.FactoryNotConfigured -> println("No factory: ${r.reason}")
    is WalletCreationResult.Failure.StorageFailed -> println("Storage: ${r.reason}")
    is WalletCreationResult.Failure.Other -> println("Other: ${r.reason}")
}
```

## Error Codes

All exceptions have a `code` property that contains a string error code. Error codes are defined as string constants within each exception class. You can access the error code via the `code` property:

```kotlin
import org.trustweave.core.exception.TrustWeaveException

try {
    // ... operation
} catch (error: TrustWeaveException) {
    println("Error code: ${error.code}")
    println("Error message: ${error.message}")
    println("Error context: ${error.context}")

    // Error codes are strings, e.g.:
    // "PLUGIN_NOT_FOUND"
    // "DID_METHOD_NOT_REGISTERED"
    // "CHAIN_NOT_REGISTERED"
    // "BLOCKCHAIN_TRANSACTION_FAILED"
    // etc.
}
```

### Common Error Codes

| Code | Exception Type | Module |
|------|----------------|--------|
| `BLANK_PLUGIN_ID` | `PluginException.BlankId` (object) | `common` |
| `PLUGIN_ALREADY_REGISTERED` | `PluginException.AlreadyRegistered` | `common` |
| `PLUGIN_NOT_FOUND` | `PluginException.NotFound` | `common` |
| `PLUGIN_INITIALIZATION_FAILED` | `PluginException.InitializationFailed` | `common` |
| `NO_PROVIDERS_FOUND` | `ProviderException.NoneFound` | `common` |
| `PARTIAL_PROVIDERS_FOUND` | `ProviderException.PartiallyFound` | `common` |
| `ALL_PROVIDERS_FAILED` | `ProviderException.AllFailed` | `common` |
| `CONFIG_NOT_FOUND` | `ConfigException.NotFound` | `common` |
| `CONFIG_READ_FAILED` | `ConfigException.ReadFailed` | `common` |
| `INVALID_CONFIG_FORMAT` | `ConfigException.InvalidFormat` | `common` |
| `INVALID_JSON` | `SerializationException.InvalidJson` | `common` |
| `JSON_ENCODE_FAILED` | `SerializationException.EncodeFailed` | `common` |
| `DIGEST_FAILED` | `TrustWeaveException.DigestFailed` | `common` |
| `ENCODE_FAILED` | `TrustWeaveException.EncodeFailed` | `common` |
| `VALIDATION_FAILED` | `TrustWeaveException.ValidationFailed` | `common` |
| `INVALID_OPERATION` | `TrustWeaveException.InvalidOperation` | `common` |
| `INVALID_STATE` | `TrustWeaveException.InvalidState` | `common` |
| `NOT_FOUND` | `TrustWeaveException.NotFound` | `common` |
| `UNKNOWN_ERROR` | `TrustWeaveException.Unknown` | `common` |
| `UNSUPPORTED_ALGORITHM` | `TrustWeaveException.UnsupportedAlgorithm` | `common` |
| `DID_NOT_FOUND` | `DidException.DidNotFound` | `did` |
| `DID_METHOD_NOT_REGISTERED` | `DidException.DidMethodNotRegistered` | `did` |
| `INVALID_DID_FORMAT` | `DidException.InvalidDidFormat` | `did` |
| `DID_RESOLUTION_FAILED` | `DidException.DidResolutionFailed` | `did` |
| `DID_CREATION_FAILED` | `DidException.DidCreationFailed` | `did` |
| `DID_UPDATE_FAILED` | `DidException.DidUpdateFailed` | `did` |
| `DID_DEACTIVATION_FAILED` | `DidException.DidDeactivationFailed` | `did` |
| `DID_REQUIRES_ACTION` | `DidException.RequiresAction` | `did` |
| `BLOCKCHAIN_TRANSACTION_FAILED` | `BlockchainException.TransactionFailed` | `anchors` |
| `BLOCKCHAIN_CONNECTION_FAILED` | `BlockchainException.ConnectionFailed` | `anchors` |
| `BLOCKCHAIN_CONFIGURATION_FAILED` | `BlockchainException.ConfigurationFailed` | `anchors` |
| `BLOCKCHAIN_UNSUPPORTED_OPERATION` | `BlockchainException.UnsupportedOperation` | `anchors` |
| `CHAIN_NOT_REGISTERED` | `BlockchainException.ChainNotRegistered` | `anchors` |
| `WALLET_CREATION_FAILED` | `WalletException.WalletCreationFailed` | `wallet` |
| `WALLET_FACTORY_NOT_CONFIGURED` | `WalletException.WalletFactoryNotConfigured` | `wallet` |
| `INVALID_HOLDER_DID` | `WalletException.InvalidHolderDid` | `wallet` |
| `WALLET_STORAGE_ERROR` | `WalletException.StorageError` | `wallet` |
| `KEY_NOT_FOUND` | `KmsException.KeyNotFound` | `kms` |
| `KEY_GENERATION_FAILED` | `KmsException.KeyGenerationFailed` | `kms` |
| `SIGNING_FAILED` | `KmsException.SigningFailed` | `kms` |
| `KEY_DELETION_FAILED` | `KmsException.KeyDeletionFailed` | `kms` |

## Related Documentation

- **[Error Handling Guide](advanced/error-handling.md)** - Complete error handling guide
- **[Core API](core-api.md)** - API methods that throw these errors
- **[Quick Reference](quick-reference.md)** - Quick API lookup

