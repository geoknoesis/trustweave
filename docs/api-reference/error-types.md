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

All TrustWeave errors extend `TrustWeaveException`, a sealed class hierarchy. The exception hierarchy is organized by module:

```kotlin
// Base exception class
sealed class TrustWeaveException(
    val code: String,
    override val message: String,
    val context: Map<String, Any?> = emptyMap(),
    override val cause: Throwable? = null
) : Exception(message, cause) {
    // Core exceptions (in common module)
    // Plugin errors
    data class BlankPluginId(...) : TrustWeaveException(...)
    data class PluginAlreadyRegistered(...) : TrustWeaveException(...)
    data class PluginNotFound(...) : TrustWeaveException(...)
    data class PluginInitializationFailed(...) : TrustWeaveException(...)
    
    // Provider errors
    data class NoProvidersFound(...) : TrustWeaveException(...)
    data class PartialProvidersFound(...) : TrustWeaveException(...)
    data class AllProvidersFailed(...) : TrustWeaveException(...)
    
    // Configuration errors
    data class ConfigNotFound(...) : TrustWeaveException(...)
    data class ConfigReadFailed(...) : TrustWeaveException(...)
    data class InvalidConfigFormat(...) : TrustWeaveException(...)
    
    // JSON/Digest errors
    data class InvalidJson(...) : TrustWeaveException(...)
    data class JsonEncodeFailed(...) : TrustWeaveException(...)
    data class DigestFailed(...) : TrustWeaveException(...)
    data class EncodeFailed(...) : TrustWeaveException(...)
    
    // Generic errors
    data class ValidationFailed(...) : TrustWeaveException(...)
    data class InvalidOperation(...) : TrustWeaveException(...)
    data class InvalidState(...) : TrustWeaveException(...)
    data class NotFound(...) : TrustWeaveException(...)
    data class Unknown(...) : TrustWeaveException(...)
    data class UnsupportedAlgorithm(...) : TrustWeaveException(...)
}

// Module-specific exception hierarchies
sealed class DidException(...) : TrustWeaveException(...) {
    data class DidNotFound(...) : DidException(...)
    data class DidMethodNotRegistered(...) : DidException(...)
    data class InvalidDidFormat(...) : DidException(...)
}

sealed class CredentialException(...) : TrustWeaveException(...) {
    data class CredentialInvalid(...) : CredentialException(...)
    data class CredentialIssuanceFailed(...) : CredentialException(...)
}

sealed class BlockchainException(...) : TrustWeaveException(...) {
    data class TransactionFailed(...) : BlockchainException(...)
    data class ConnectionFailed(...) : BlockchainException(...)
    data class ConfigurationFailed(...) : BlockchainException(...)
    data class UnsupportedOperation(...) : BlockchainException(...)
    data class ChainNotRegistered(...) : BlockchainException(...)
}

sealed class WalletException(...) : TrustWeaveException(...) {
    data class WalletCreationFailed(...) : WalletException(...)
}
```

## Error Types by Category

### Plugin Errors

| Error Type | Code | Properties | When It Occurs |
|------------|------|------------|----------------|
| `BlankPluginId` | `BLANK_PLUGIN_ID` | - | Plugin ID is blank |
| `PluginAlreadyRegistered` | `PLUGIN_ALREADY_REGISTERED` | `pluginId`, `existingPlugin` | Duplicate plugin registration |
| `PluginNotFound` | `PLUGIN_NOT_FOUND` | `pluginId`, `pluginType` | Plugin lookup fails |
| `PluginInitializationFailed` | `PLUGIN_INITIALIZATION_FAILED` | `pluginId`, `reason` | Plugin initialization fails |

### Provider Errors

| Error Type | Code | Properties | When It Occurs |
|------------|------|------------|----------------|
| `NoProvidersFound` | `NO_PROVIDERS_FOUND` | `pluginIds`, `availablePlugins` | No providers found for plugin IDs |
| `PartialProvidersFound` | `PARTIAL_PROVIDERS_FOUND` | `requestedIds`, `foundIds`, `missingIds` | Some providers found, some missing |
| `AllProvidersFailed` | `ALL_PROVIDERS_FAILED` | `attemptedProviders`, `providerErrors`, `lastException` | All providers in chain failed |

### Configuration Errors

| Error Type | Code | Properties | When It Occurs |
|------------|------|------------|----------------|
| `ConfigNotFound` | `CONFIG_NOT_FOUND` | `path` | Configuration file/resource not found |
| `ConfigReadFailed` | `CONFIG_READ_FAILED` | `path`, `reason` | Failed to read configuration file |
| `InvalidConfigFormat` | `INVALID_CONFIG_FORMAT` | `jsonString`, `parseError`, `field` | Invalid JSON format in configuration |

### JSON/Digest Errors

| Error Type | Code | Properties | When It Occurs |
|------------|------|------------|----------------|
| `InvalidJson` | `INVALID_JSON` | `jsonString`, `parseError`, `position` | Invalid JSON parsing error |
| `JsonEncodeFailed` | `JSON_ENCODE_FAILED` | `element`, `reason` | JSON encoding/serialization failed |
| `DigestFailed` | `DIGEST_FAILED` | `algorithm`, `reason` | Digest computation failed |
| `EncodeFailed` | `ENCODE_FAILED` | `operation`, `reason` | Encoding operation failed |

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

#### Credential Errors

All credential errors are part of the `CredentialException` sealed class hierarchy (extends `TrustWeaveException`):

| Error Type | Code | Properties | When It Occurs | Module |
|------------|------|------------|----------------|--------|
| `CredentialException.CredentialInvalid` | `CREDENTIAL_INVALID` | `reason`, `credentialId`, `field` | Credential validation fails | `credentials` |
| `CredentialException.CredentialIssuanceFailed` | `CREDENTIAL_ISSUANCE_FAILED` | `reason`, `issuerDid` | Credential issuance fails | `credentials` |

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

## Error Handling Examples

### Handling DID Errors

```kotlin
import com.trustweave.did.exception.DidException
import com.trustweave.core.exception.TrustWeaveException

try {
    val did = trustLayer.createDid { method("key") }
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

```kotlin
import com.trustweave.credential.exception.CredentialException
import com.trustweave.core.exception.TrustWeaveException

try {
    val credential = trustLayer.issue { ... }
} catch (error: TrustWeaveException) {
    when (error) {
        is CredentialException.CredentialInvalid -> {
            println("Credential invalid: ${error.reason}")
            if (error.field != null) {
                println("Field: ${error.field}")
            }
        }
        is CredentialException.CredentialIssuanceFailed -> {
            println("Issuance failed: ${error.reason}")
        }
        else -> {
            println("Error: ${error.message}")
        }
    }
}
```

### Handling Blockchain Errors

```kotlin
import com.trustweave.anchor.exceptions.BlockchainException
import com.trustweave.core.exception.TrustWeaveException

try {
    val anchor = trustLayer.anchor { ... }
} catch (error: TrustWeaveException) {
    when (error) {
        is BlockchainException.ChainNotRegistered -> {
            println("Chain not registered: ${error.chainId}")
            println("Available: ${error.availableChains}")
        }
        is BlockchainException.TransactionFailed -> {
            println("Transaction failed: ${error.reason}")
            if (error.txHash != null) {
                println("Transaction hash: ${error.txHash}")
            }
        }
        is BlockchainException.ConnectionFailed -> {
            println("Connection failed: ${error.reason}")
        }
        else -> {
            println("Error: ${error.message}")
        }
    }
}
```

### Handling Wallet Errors

```kotlin
import com.trustweave.wallet.exception.WalletException
import com.trustweave.core.exception.TrustWeaveException

try {
    val wallet = trustLayer.wallet { ... }
} catch (error: TrustWeaveException) {
    when (error) {
        is WalletException.WalletCreationFailed -> {
            println("Wallet creation failed: ${error.reason}")
            println("Provider: ${error.provider}")
        }
        else -> {
            println("Error: ${error.message}")
        }
    }
}
```

## Error Codes

All exceptions have a `code` property that contains a string error code. Error codes are defined as string constants within each exception class. You can access the error code via the `code` property:

```kotlin
import com.trustweave.core.exception.TrustWeaveException

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
| `BLANK_PLUGIN_ID` | `TrustWeaveException.BlankPluginId` | `common` |
| `PLUGIN_ALREADY_REGISTERED` | `TrustWeaveException.PluginAlreadyRegistered` | `common` |
| `PLUGIN_NOT_FOUND` | `TrustWeaveException.PluginNotFound` | `common` |
| `PLUGIN_INITIALIZATION_FAILED` | `TrustWeaveException.PluginInitializationFailed` | `common` |
| `NO_PROVIDERS_FOUND` | `TrustWeaveException.NoProvidersFound` | `common` |
| `PARTIAL_PROVIDERS_FOUND` | `TrustWeaveException.PartialProvidersFound` | `common` |
| `ALL_PROVIDERS_FAILED` | `TrustWeaveException.AllProvidersFailed` | `common` |
| `CONFIG_NOT_FOUND` | `TrustWeaveException.ConfigNotFound` | `common` |
| `CONFIG_READ_FAILED` | `TrustWeaveException.ConfigReadFailed` | `common` |
| `INVALID_CONFIG_FORMAT` | `TrustWeaveException.InvalidConfigFormat` | `common` |
| `INVALID_JSON` | `TrustWeaveException.InvalidJson` | `common` |
| `JSON_ENCODE_FAILED` | `TrustWeaveException.JsonEncodeFailed` | `common` |
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
| `CREDENTIAL_INVALID` | `CredentialException.CredentialInvalid` | `credentials` |
| `CREDENTIAL_ISSUANCE_FAILED` | `CredentialException.CredentialIssuanceFailed` | `credentials` |
| `BLOCKCHAIN_TRANSACTION_FAILED` | `BlockchainException.TransactionFailed` | `anchor` |
| `BLOCKCHAIN_CONNECTION_FAILED` | `BlockchainException.ConnectionFailed` | `anchor` |
| `BLOCKCHAIN_CONFIGURATION_FAILED` | `BlockchainException.ConfigurationFailed` | `anchor` |
| `BLOCKCHAIN_UNSUPPORTED_OPERATION` | `BlockchainException.UnsupportedOperation` | `anchor` |
| `CHAIN_NOT_REGISTERED` | `BlockchainException.ChainNotRegistered` | `anchor` |
| `WALLET_CREATION_FAILED` | `WalletException.WalletCreationFailed` | `wallet` |

## Related Documentation

- **[Error Handling Guide](../advanced/error-handling.md)** - Complete error handling guide
- **[Core API](core-api.md)** - API methods that throw these errors
- **[Quick Reference](quick-reference.md)** - Quick API lookup

