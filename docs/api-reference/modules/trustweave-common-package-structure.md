---
title: common Package Structure
redirect_from:
  - /modules/trustweave-common-package-structure/
parent: Module Reference
grand_parent: API Reference
---

# common Package Structure

The `common` module is organized into logical packages for better code organization and discoverability.

## Package Organization

```
org.trustweave.core/
├── exception/              # Exception types and error handling
│   ├── TrustWeaveException.kt
│   ├── PluginException.kt
│   ├── ProviderException.kt
│   ├── ConfigException.kt
│   └── SerializationException.kt
│
├── plugin/                 # Plugin infrastructure
│   ├── PluginRegistry.kt
│   ├── PluginMetadata.kt
│   ├── PluginConfiguration.kt
│   ├── PluginConfigurationLoader.kt
│   ├── PluginLifecycle.kt
│   ├── PluginType.kt
│   └── ProviderChain.kt
│
├── identifiers/            # Identifier value/open classes
│   └── Identifiers.kt      # Iri, KeyId, IriSerializer, KeyIdSerializer
│
├── serialization/          # Serialization helpers
│   ├── InstantSerializer.kt
│   └── SerializationModule.kt
│
└── util/                   # General utilities
    ├── DigestUtils.kt      # JSON canonicalization and digest computation
    ├── ResultExtensions.kt # Result<T> extension functions
    └── Validation.kt       # Generic validation infrastructure (ValidationResult)
```

## Package Details

### `org.trustweave.core.exception`

Exception types and error handling:

- **`TrustWeaveException`** – `open class` base type. Nested types: **`ValidationFailed`**, **`InvalidOperation`**, **`InvalidState`**, **`Unknown`**, **`DigestFailed`**, **`EncodeFailed`**, **`NotFound`**, **`UnsupportedAlgorithm`**
- **`PluginException`** – sealed sibling class extending `TrustWeaveException`. Subtypes: `NotFound`, `InitializationFailed`, `AlreadyRegistered`, `BlankId` (object)
- **`ProviderException`** – sealed sibling class. Subtypes: `NoneFound`, `PartiallyFound`, `AllFailed`
- **`ConfigException`** – sealed sibling class. Subtypes: `NotFound`, `ReadFailed`, `InvalidFormat`
- **`SerializationException`** – sealed sibling class. Subtypes: `InvalidJson`, `EncodeFailed`

**Example:**
```kotlin
import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.core.exception.PluginException

try {
    // operation
} catch (e: PluginException.NotFound) {
    // handle missing plugin
} catch (e: TrustWeaveException) {
    // handle other structured errors
}
```

### `org.trustweave.core.plugin`

Plugin infrastructure for extensibility:

- **`PluginMetadata`** – Metadata about plugins (capabilities, dependencies, configuration)
- **`PluginCapabilities`** – Domain-agnostic capabilities (features, extensions)
- **`PluginConfiguration`** – Configuration loaded from YAML/JSON files
- **`PluginConfigurationLoader`** – Loader for YAML/JSON plugin configurations
- **`PluginType`** – Framework-level plugin type enumeration (BLOCKCHAIN, CREDENTIAL_SERVICE, DID_METHOD, KMS, etc.)
- **`PluginLifecycle`** – Lifecycle interface for plugin initialization, startup, shutdown, and cleanup
- **`PluginRegistry`** / **`ProviderChain`** – Internal infrastructure (`internal` visibility). Use domain registries (`DidMethodRegistry`, `BlockchainAnchorRegistry`, etc.) instead.

> **TODO:** Add a public example for the domain-specific registries; `PluginRegistry` is no longer a public entry point.

### `org.trustweave.core.util`

General utilities used across TrustWeave:

- **`DigestUtils`** – JSON canonicalization and SHA-256 digest computation with multibase encoding (base58btc)
- **`ResultExtensions`** – Extension functions for `Result<T>` (mapError, combine, mapSequential, onSuccess, onFailure, etc.)
- **`Validation`** – Generic validation infrastructure (`ValidationResult` sealed class)

**Example:**
```kotlin
import org.trustweave.core.util.DigestUtils
import org.trustweave.core.util.ValidationResult

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
- `DidValidator` → `org.trustweave.did.validation.DidValidator` (in `did:did-core`)
- `ChainIdValidator` → `org.trustweave.anchor.validation.ChainIdValidator` (in `anchors:anchor-core`)

## Related Packages

### Domain-Specific Components

Domain-specific functionality is located in their respective modules:

- **Proof Types** → `org.trustweave.credential.proof.ProofType` (in `credentials:credential-api`)
- **Schema Format** → `org.trustweave.credential.SchemaFormat` (in `credentials:credential-api`)
- **DID Validation** → `org.trustweave.did.validation.DidValidator` (in `did:did-core`)
- **Chain ID Validation** → `org.trustweave.anchor.validation.ChainIdValidator` (in `anchors:anchor-core`)
- **DID errors** → `org.trustweave.did.exception.DidException` (in `did:did-core`)
- **Blockchain errors** → `org.trustweave.anchor.exceptions.BlockchainException` (in `anchors:anchor-core`)

## Migration Notes

If you're migrating from an older version:

- **`org.trustweave.json.DigestUtils`** → **`org.trustweave.core.util.DigestUtils`**
- **`org.trustweave.core.TrustWeaveException`** → **`org.trustweave.core.exception.TrustWeaveException`**
- **Legacy docs referring to `TrustWeaveError`** → use **`TrustWeaveException`** and domain types (`DidException`, `BlockchainException`, `PluginException`, …)
- **`org.trustweave.core.types.ProofType`** → **`org.trustweave.credential.model.ProofType`** (in `credentials:credential-api`)
- **`org.trustweave.core.DidValidator`** → **`org.trustweave.did.validation.DidValidator`** (in `did:did-core`)
- **`org.trustweave.core.ChainIdValidator`** → **`org.trustweave.anchor.validation.ChainIdValidator`** (in `anchors:anchor-core`)
- **`org.trustweave.core.ValidationResult`** → **`org.trustweave.core.util.ValidationResult`** (still in common, but validators moved)

## Benefits of This Organization

1. **Clear Separation of Concerns** – Related functionality is grouped together
2. **Easier Discovery** – Developers can find related classes more easily
3. **Better Scalability** – New utilities have a clear place to go
4. **Consistent Naming** – Follows common Kotlin/Java package conventions
5. **Reduced Coupling** – Clear boundaries between different concerns

