---
title: trustweave-common
---

# trustweave-common

The `trustweave-common` module provides **domain-agnostic core infrastructure** for TrustWeave:

- **Plugin Infrastructure** – Plugin registry, metadata, configuration, and provider chains
- **Error Handling** – Structured error types with rich context (`TrustWeaveError` hierarchy)
- **JSON Utilities** – JSON canonicalization and SHA-256 digest computation
- **Result Utilities** – Extension functions for `Result<T>` error handling
- **Validation Infrastructure** – Generic validation framework (domain-specific validators are in their respective modules)

**Important:** This module is intentionally domain-agnostic. Domain-specific functionality (DID validation, credential errors, blockchain errors, etc.) is located in their respective domain modules (`trustweave-did`, `trustweave-credentials`, `trustweave-anchor`, etc.).

## Key Components

### Plugin Infrastructure (`com.trustweave.core.plugin`)
- **`PluginRegistry`** – Thread-safe, capability-based plugin discovery and registration
- **`PluginMetadata`** – Plugin metadata with domain-agnostic capabilities
- **`PluginConfiguration`** – Configuration loaded from YAML/JSON files
- **`ProviderChain`** – Provider chain with automatic fallback support
- **`PluginType`** – Framework-level plugin type enumeration (BLOCKCHAIN, CREDENTIAL_SERVICE, DID_METHOD, etc.)
- **`PluginLifecycle`** – Lifecycle interface for plugin initialization, startup, shutdown, and cleanup

### Error Handling (`com.trustweave.core.exception`)
- **`TrustWeaveException`** – Base exception for TrustWeave operations
- **`TrustWeaveError`** – Sealed hierarchy of structured errors with rich context:
  - **Plugin Errors**: `BlankPluginId`, `PluginAlreadyRegistered`, `PluginNotFound`, `PluginInitializationFailed`
  - **Provider Errors**: `NoProvidersFound`, `PartialProvidersFound`, `AllProvidersFailed`
  - **Configuration Errors**: `ConfigNotFound`, `ConfigReadFailed`, `InvalidConfigFormat`
  - **JSON/Digest Errors**: `InvalidJson`, `JsonEncodeFailed`, `DigestFailed`, `EncodeFailed`
  - **Generic Errors**: `ValidationFailed`, `InvalidOperation`, `InvalidState`, `Unknown`

### Utilities (`com.trustweave.core.util`)
- **`DigestUtils`** – JSON canonicalization and SHA-256 digest computation with multibase encoding (base58btc)
- **`ResultExtensions`** – Extension functions for `Result<T>` (mapError, combine, mapSequential, etc.)
- **`Validation`** – Generic validation infrastructure (`ValidationResult` sealed class)
- **`TrustWeaveConstants`** – Common constants

**Note:** Domain-specific validators (DID, Chain ID) are in their respective domain modules:
- DID validation → `com.trustweave.did.validation.DidValidator`
- Chain ID validation → `com.trustweave.anchor.validation.ChainIdValidator`

Add the module alongside any DID/KMS components you require:

```kotlin
dependencies {
    implementation("com.trustweave:trustweave-common:1.0.0-SNAPSHOT")
}
```

**Result:** Gradle exposes the core infrastructure APIs for plugin management, error handling, and JSON utilities:

```kotlin
import com.trustweave.core.plugin.*
import com.trustweave.core.exception.TrustWeaveError
import com.trustweave.core.util.*

// Register a plugin
val metadata = PluginMetadata(
    id = "my-plugin",
    name = "My Plugin",
    version = "1.0.0",
    provider = "custom",
    capabilities = PluginCapabilities(
        features = setOf("credential-storage")
    )
)
try {
    PluginRegistry.register(metadata, pluginInstance)
} catch (e: TrustWeaveError.BlankPluginId) {
    println("Plugin ID cannot be blank")
} catch (e: TrustWeaveError.PluginAlreadyRegistered) {
    println("Plugin already registered: ${e.pluginId}")
}

// Use JSON utilities
val digest = DigestUtils.sha256DigestMultibase(jsonElement)

// Handle errors with Result utilities
val result: Result<String> = someOperation()
result.fold(
    onSuccess = { value -> println("Success: $value") },
    onFailure = { error ->
        when (error) {
            is TrustWeaveError.InvalidJson -> {
                println("Invalid JSON: ${error.parseError}")
            }
            is TrustWeaveError.ConfigNotFound -> {
                println("Config not found: ${error.path}")
            }
            else -> println("Error: ${error.message}")
        }
    }
)
```

**Why it matters:** `trustweave-common` provides the foundational infrastructure that all TrustWeave modules depend on. It's domain-agnostic, meaning it contains no business logic specific to DIDs, credentials, or blockchains—those are handled by their respective domain modules.

## Dependencies

- **Minimal dependencies**: Only Kotlin standard library, kotlinx.serialization, and kotlinx.coroutines
- **No domain dependencies**: This module does not depend on DID, credential, blockchain, or wallet modules
- **Upstream modules** (`trustweave-did`, `trustweave-credentials`, `trustweave-anchor`, etc.) depend on `trustweave-common` and add domain-specific functionality

## Next Steps

- SPI interfaces are included in this module. See [SPI Documentation](../advanced/spi.md) to understand adapter/service expectations.
- Explore [`trustweave-trust`](core-modules.md) for trust registry runtime components.
- See [JSON Utilities](trustweave-json.md) (now part of common module) and [`trustweave-kms`](trustweave-kms.md) for supporting utilities.
- See [Package Structure](trustweave-common-package-structure.md) for detailed package organization.

## Package Structure

The `trustweave-common` module is organized into logical packages:

- **`com.trustweave.core.exception`** – Exception types and error handling
  - `TrustWeaveException`, `NotFoundException`, `InvalidOperationException`
  - `TrustWeaveError` (sealed class hierarchy with 13+ specific error types)
  
- **`com.trustweave.core.plugin`** – Plugin infrastructure
  - `PluginRegistry` (thread-safe, capability-based discovery)
  - `PluginMetadata`, `PluginCapabilities` (domain-agnostic)
  - `PluginConfiguration`, `PluginType` (framework-level plugin types)
  - `ProviderChain` (fallback support)
  - `PluginLifecycle` (lifecycle management)
  
- **`com.trustweave.core.util`** – General utilities
  - `DigestUtils` (JSON canonicalization and SHA-256 digest computation)
  - `ResultExtensions` (Result<T> extension functions)
  - `TrustWeaveConstants`
  - `Validation` (generic validation infrastructure - `ValidationResult`)

**Note:** Domain-specific components are in their respective modules:
- DID validation → `com.trustweave.did.validation.DidValidator` (in `trustweave-did`)
- Chain ID validation → `com.trustweave.anchor.validation.ChainIdValidator` (in `trustweave-anchor`)
- Credential errors → `com.trustweave.credential.exception.CredentialError` (in `trustweave-credentials`)
- Proof types → `com.trustweave.credential.proof.ProofType` (in `trustweave-credentials`)
