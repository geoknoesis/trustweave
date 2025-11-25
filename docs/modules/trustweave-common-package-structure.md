---
title: trustweave-common Package Structure
---

# trustweave-common Package Structure

The `trustweave-common` module is organized into logical packages for better code organization and discoverability.

## Package Organization

```
com.trustweave.core/
├── exception/              # Exception types and error handling
│   ├── TrustWeaveException.kt
│   └── TrustWeaveErrors.kt
│
├── plugin/                 # Plugin infrastructure
│   ├── PluginRegistry.kt
│   ├── PluginMetadata.kt
│   ├── PluginConfiguration.kt
│   └── ProviderChain.kt
│
└── util/                   # General utilities
    ├── DigestUtils.kt      # JSON canonicalization and digest computation
    ├── ResultExtensions.kt # Result<T> extension functions
    ├── TrustWeaveConstants.kt
    └── Validation.kt       # Generic validation infrastructure (ValidationResult)
```

## Package Details

### `com.trustweave.core.exception`

Exception types and error handling:

- **`TrustWeaveException`** – Base exception for TrustWeave operations
- **`NotFoundException`** – Exception thrown when a requested resource is not found
- **`InvalidOperationException`** – Exception thrown when an operation is invalid
- **`TrustWeaveError`** – Sealed class hierarchy for structured API errors with context

**Error Types in `TrustWeaveError`:**
- **Plugin Errors**: `BlankPluginId`, `PluginAlreadyRegistered`, `PluginNotFound`, `PluginInitializationFailed`
- **Provider Errors**: `NoProvidersFound`, `PartialProvidersFound`, `AllProvidersFailed`
- **Configuration Errors**: `ConfigNotFound`, `ConfigReadFailed`, `InvalidConfigFormat`
- **JSON/Digest Errors**: `InvalidJson`, `JsonEncodeFailed`, `DigestFailed`, `EncodeFailed`
- **Generic Errors**: `ValidationFailed`, `InvalidOperation`, `InvalidState`, `Unknown`, `UnsupportedAlgorithm`

**Example:**
```kotlin
import com.trustweave.core.exception.TrustWeaveError
import com.trustweave.core.exception.NotFoundException

try {
    // operation
} catch (e: NotFoundException) {
    // handle not found
} catch (e: TrustWeaveError) {
    // handle structured error
}
```

### `com.trustweave.core.plugin`

Plugin infrastructure for extensibility:

- **`PluginRegistry`** – Thread-safe, unified plugin registry for capability-based discovery
- **`PluginMetadata`** – Metadata about plugins (capabilities, dependencies, configuration)
- **`PluginCapabilities`** – Domain-agnostic capabilities (features, extensions)
- **`PluginConfiguration`** – Configuration loaded from YAML/JSON files
- **`PluginType`** – Framework-level plugin type enumeration (BLOCKCHAIN, CREDENTIAL_SERVICE, DID_METHOD, KMS, etc.)
- **`ProviderChain`** – Provider chain with automatic fallback support
- **`PluginLifecycle`** – Lifecycle interface for plugin initialization, startup, shutdown, and cleanup

**Example:**
```kotlin
import com.trustweave.core.plugin.PluginRegistry
import com.trustweave.core.plugin.PluginMetadata

PluginRegistry.register(metadata, instance)
val plugins = PluginRegistry.findByCapability("credential-storage")
```

### `com.trustweave.core.util`

General utilities used across TrustWeave:

- **`DigestUtils`** – JSON canonicalization and SHA-256 digest computation with multibase encoding (base58btc)
- **`ResultExtensions`** – Extension functions for `Result<T>` (mapError, combine, mapSequential, onSuccess, onFailure, etc.)
- **`TrustWeaveConstants`** – Common constants
- **`Validation`** – Generic validation infrastructure (`ValidationResult` sealed class)

**Example:**
```kotlin
import com.trustweave.core.util.DigestUtils
import com.trustweave.core.util.ValidationResult

val digest = DigestUtils.sha256DigestMultibase(jsonElement)
val canonical = DigestUtils.canonicalizeJson(jsonString)

// Generic validation result
val validation: ValidationResult = someValidation()
if (!validation.isValid()) {
    val error = validation as ValidationResult.Invalid
    println("Validation failed: ${error.message}")
}
```

**Note:** Domain-specific validators are in their respective modules:
- `DidValidator` → `com.trustweave.did.validation.DidValidator` (in `trustweave-did`)
- `ChainIdValidator` → `com.trustweave.anchor.validation.ChainIdValidator` (in `trustweave-anchor`)

## Related Packages

### Domain-Specific Components

Domain-specific functionality is located in their respective modules:

- **Proof Types** → `com.trustweave.credential.proof.ProofType` (in `trustweave-credentials`)
- **Schema Format** → `com.trustweave.credential.SchemaFormat` (in `trustweave-credentials`)
- **DID Validation** → `com.trustweave.did.validation.DidValidator` (in `trustweave-did`)
- **Chain ID Validation** → `com.trustweave.anchor.validation.ChainIdValidator` (in `trustweave-anchor`)
- **Credential Errors** → `com.trustweave.credential.exception.CredentialError` (in `trustweave-credentials`)
- **DID Errors** → `com.trustweave.did.exception.DidError` (in `trustweave-did`)
- **Blockchain Errors** → `com.trustweave.anchor.exceptions.BlockchainError` (in `trustweave-anchor`)

## Migration Notes

If you're migrating from an older version:

- **`com.trustweave.json.DigestUtils`** → **`com.trustweave.core.util.DigestUtils`**
- **`com.trustweave.core.TrustWeaveException`** → **`com.trustweave.core.exception.TrustWeaveException`**
- **`com.trustweave.core.TrustWeaveError`** → **`com.trustweave.core.exception.TrustWeaveError`**
- **`com.trustweave.core.types.ProofType`** → **`com.trustweave.credential.proof.ProofType`** (in `trustweave-credentials`)
- **`com.trustweave.core.DidValidator`** → **`com.trustweave.did.validation.DidValidator`** (in `trustweave-did`)
- **`com.trustweave.core.ChainIdValidator`** → **`com.trustweave.anchor.validation.ChainIdValidator`** (in `trustweave-anchor`)
- **`com.trustweave.core.ValidationResult`** → **`com.trustweave.core.util.ValidationResult`** (still in common, but validators moved)

## Benefits of This Organization

1. **Clear Separation of Concerns** – Related functionality is grouped together
2. **Easier Discovery** – Developers can find related classes more easily
3. **Better Scalability** – New utilities have a clear place to go
4. **Consistent Naming** – Follows common Kotlin/Java package conventions
5. **Reduced Coupling** – Clear boundaries between different concerns

