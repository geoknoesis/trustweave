---
title: common
redirect_from:
  - /modules/trustweave-common/
parent: Module Reference
grand_parent: API Reference
---

# common

The `common` module provides **domain-agnostic core infrastructure** for TrustWeave:

- **Plugin Infrastructure** – Plugin registry, metadata, configuration, and provider chains
- **Error Handling** – Structured exceptions with rich context (`TrustWeaveException` and focused subclasses: `PluginException`, `ProviderException`, `ConfigException`, `SerializationException`, …)
- **JSON Utilities** – JSON canonicalization and SHA-256 digest computation
- **Result Utilities** – Extension functions for `Result<T>` error handling
- **Validation Infrastructure** – Generic validation framework (domain-specific validators are in their respective modules)

**Important:** This module is intentionally domain-agnostic. Domain-specific functionality (DID validation, credential errors, blockchain errors, etc.) is located in their respective domain modules (`did:did-core`, `credentials:credential-api`, `anchors:anchor-core`, etc.).

## Key Components

### Plugin Infrastructure (`org.trustweave.core.plugin`)
- **`PluginMetadata`** / **`PluginCapabilities`** – Plugin metadata with domain-agnostic capabilities
- **`PluginConfiguration`** / **`PluginConfigurationLoader`** – Configuration loaded from YAML/JSON files
- **`PluginType`** – Framework-level plugin type enumeration (BLOCKCHAIN, CREDENTIAL_SERVICE, DID_METHOD, etc.)
- **`PluginLifecycle`** – Lifecycle interface for plugin initialization, startup, shutdown, and cleanup
- **`PluginRegistry`** / **`ProviderChain`** – Internal infrastructure (`internal` visibility). Domain-specific registries (`DidMethodRegistry`, `BlockchainAnchorRegistry`, `TrustRegistry`) are the public entry points.

### Error Handling (`org.trustweave.core.exception`)
- **`TrustWeaveException`** – `open class` base. Nested types: **`ValidationFailed`**, **`InvalidOperation`**, **`InvalidState`**, **`Unknown`**, **`DigestFailed`**, **`EncodeFailed`**, **`NotFound`**, **`UnsupportedAlgorithm`**
- **`PluginException`** (sealed sibling) – `NotFound`, `InitializationFailed`, `AlreadyRegistered`, `BlankId`
- **`ProviderException`** (sealed sibling) – `NoneFound`, `PartiallyFound`, `AllFailed`
- **`ConfigException`** (sealed sibling) – `NotFound`, `ReadFailed`, `InvalidFormat`
- **`SerializationException`** (sealed sibling) – `InvalidJson`, `EncodeFailed`

### Utilities (`org.trustweave.core.util`)
- **`DigestUtils`** – JSON canonicalization and SHA-256 digest computation with multibase encoding (base58btc)
  - Optimized JSON canonicalization with lexicographical key sorting
  - Efficient SHA-256 digest computation
  - Multibase encoding (base58btc) support
- **`ResultExtensions`** – Extension functions for `Result<T>` (mapError, combine, mapSequential, trustweaveCatching, etc.)
  - Functional-style error handling
  - Automatic conversion of `Throwable` to `TrustWeaveException`
  - Error code extraction and categorization
- **`Validation`** – Generic validation infrastructure (`ValidationResult` sealed class)
  - Type-safe validation results
  - Reusable validation patterns

### Identifiers (`org.trustweave.core.identifiers`)
- **`Iri`** – RFC 3987 compliant Internationalized Resource Identifier
- **`KeyId`** – Type-safe key identifier with fragment support
- Extension functions for safe parsing (`toIriOrNull`, `toKeyIdOrNull`, etc.)

DID-scoped identifier **value classes** (**`Did`**, **`VerificationMethodId`**, **`DidUrl`**, …) live in **`did:did-core`** (`org.trustweave.did.identifiers` and related packages), not in `common`.

### Serialization (`org.trustweave.core.serialization`)
- **Custom Serializers (common)**: e.g. `IriSerializer`, `KeyIdSerializer`, `InstantSerializer` (see module sources for the full set)
- **SerializationModule**: Centralized serialization configuration
- DID-related serializers (**`DidSerializer`**, **`VerificationMethodIdSerializer`**, **`DidUrlSerializer`**, …) ship with **`did:did-core`**, not this module

**Note:** Domain-specific validators (DID, Chain ID) are in their respective domain modules:
- DID validation → `org.trustweave.did.validation.DidValidator`
- Chain ID validation → `org.trustweave.anchor.validation.ChainIdValidator`

Add the module alongside any DID/KMS components you require:

```kotlin
dependencies {
    implementation("org.trustweave:common:0.6.0")
}
```

**Result:** Gradle exposes the core infrastructure APIs for plugin management, error handling, and JSON utilities:

```kotlin
import org.trustweave.core.plugin.PluginMetadata
import org.trustweave.core.plugin.PluginCapabilities
import org.trustweave.core.exception.SerializationException
import org.trustweave.core.exception.ConfigException
import org.trustweave.core.util.DigestUtils

// Describe a plugin (registration goes through domain registries such as
// DidMethodRegistry or BlockchainAnchorRegistry, not a global PluginRegistry).
val metadata = PluginMetadata(
    id = "my-plugin",
    name = "My Plugin",
    version = "1.0.0",
    provider = "custom",
    capabilities = PluginCapabilities(
        features = setOf("credential-storage")
    )
)

// Use JSON utilities
val digest = DigestUtils.sha256DigestMultibase(jsonElement)

// Handle errors with Result utilities
val result: Result<String> = someOperation()
result.fold(
    onSuccess = { value -> println("Success: $value") },
    onFailure = { error ->
        when (error) {
            is SerializationException.InvalidJson -> {
                println("Invalid JSON: ${error.parseError}")
            }
            is ConfigException.NotFound -> {
                println("Config not found: ${error.path}")
            }
            else -> println("Error: ${error.message}")
        }
    }
)
```

**Why it matters:** `common` provides the foundational infrastructure that all TrustWeave modules depend on. It's domain-agnostic, meaning it contains no business logic specific to DIDs, credentials, or blockchains—those are handled by their respective domain modules.

## Dependencies

- **Minimal dependencies**: Only Kotlin standard library, kotlinx.serialization, and kotlinx.coroutines
- **No domain dependencies**: This module does not depend on DID, credential, blockchain, or wallet modules
- **Upstream modules** (`did:did-core`, `credentials:credential-api`, `anchors:anchor-core`, etc.) depend on `common` and add domain-specific functionality

## Next Steps

- SPI interfaces are included in this module. See [SPI Documentation](../advanced/spi.md) to understand adapter/service expectations.
- Explore [`trust`](trustweave-trust.md) for the main `TrustWeave` facade and trust registry runtime components.
- See [JSON Utilities](trustweave-json.md) (now part of `common`) and [`kms:kms-core`](trustweave-kms.md) for supporting utilities.
- See [Package Structure](trustweave-common-package-structure.md) for detailed package organization.

## Package Structure

The `common` module is organized into logical packages:

- **`org.trustweave.core.exception`** – Exception types and error handling
  - `TrustWeaveException` (base; nested validation/unknown/digest types)
  - `PluginException`, `ProviderException`, `ConfigException`, `SerializationException`

- **`org.trustweave.core.plugin`** – Plugin infrastructure
  - `PluginMetadata`, `PluginCapabilities` (domain-agnostic)
  - `PluginConfiguration`, `PluginConfigurationLoader`, `PluginType` (framework-level plugin types)
  - `PluginLifecycle` (lifecycle management)
  - `PluginRegistry`, `ProviderChain` (`internal` infrastructure; not part of the public API)

- **`org.trustweave.core.util`** – General utilities
  - `DigestUtils` (JSON canonicalization and SHA-256 digest computation)
  - `ResultExtensions` (Result<T> extension functions)
  - `Validation` (generic validation infrastructure - `ValidationResult`)

- **`org.trustweave.core.identifiers`** – Identifier types
  - `Iri` (RFC 3987 compliant Internationalized Resource Identifier)
  - `KeyId` (type-safe key identifier)
  - Extension functions for safe parsing

- **`org.trustweave.core.serialization`** – Serialization support
  - Custom serializers for TrustWeave types (Iri, KeyId, Instant, Did, etc.)
  - `SerializationModule` for centralized configuration

**Note:** Domain-specific components are in their respective modules:
- DID validation → `org.trustweave.did.validation.DidValidator` (in `did:did-core`)
- Chain ID validation → `org.trustweave.anchor.validation.ChainIdValidator` (in `anchors:anchor-core`)
- Credential proof, format, and exception types live under `org.trustweave.credential.*` (in `credentials:credential-api`)

> **TODO:** Cross-link the specific credential exception/proof types once the credentials reference page is added.
