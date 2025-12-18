---
title: Key Management
nav_order: 7
parent: Core Concepts
keywords:
  - key management
  - kms
  - keys
  - cryptography
  - aws kms
  - azure key vault
  - waltid
  - security
---

# Key Management

> This guide is curated by [Geoknoesis LLC](https://www.geoknoesis.com). It outlines how TrustWeave treats key custody across decentralized identity workflows.

## What is key management?

Key management covers the generation, storage, rotation, and usage of cryptographic keys. In TrustWeave, every Decentralized Identifier (DID) method, credential issuance, and presentation flow relies on a `KeyManagementService` (Key Management Service, KMS) abstraction.

```kotlin
dependencies {
    implementation("com.trustweave:trustweave-common:1.0.0-SNAPSHOT")
    implementation("com.trustweave:trustweave-kms:1.0.0-SNAPSHOT")
}
```

**Result:** Gives you the Key Management Service (KMS) interfaces and helpers referenced in the examples below.

## Why key management matters

- **Proof integrity** – signing credentials and presentations requires private keys to stay secure.
- **Interoperability** – different environments (cloud Hardware Security Module (HSM), Vault, in-memory test Key Management Service (KMS)) provide the same interface.
- **Lifecycle** – keys can be rotated or deactivated while preserving credential history via key identifiers (`keyId`).

## How TrustWeave models key management

| Component | Purpose |
|-----------|---------|
| `KeyManagementService` | Core interface (`generateKey`, `getPublicKey`, `sign`, `deleteKey`). |
| `KeyHandle` | Describes a key (id, algorithm, public material). |
| `KeyManagementServiceProvider` | SPI entry point for auto-discoverable providers. |
| `wallet.withKeyManagement { … }` | Wallet DSL hook for higher-level workflows. |

Built-in providers include the in-memory test Key Management Service (KMS) and the walt.id-backed implementation. You can add your own via Service Provider Interface (SPI).

### Example: checking algorithm support

```kotlin
import com.trustweave.kms.*

suspend fun checkKmsCapabilities(kms: KeyManagementService) {
    // Get all supported algorithms
    val supported = kms.getSupportedAlgorithms()
    println("Supported algorithms: ${supported.joinToString { it.name }}")

    // Check specific algorithm
    if (kms.supportsAlgorithm(Algorithm.Ed25519)) {
        println("Ed25519 is supported")
    }

    // Check by name (case-insensitive)
    if (kms.supportsalgorithm(SECP256K1)) {
        println("secp256k1 is supported")
    }
}
```

### Example: generating and using keys

```kotlin
import com.trustweave.kms.*

suspend fun issueSignerKey(kms: KeyManagementService): KeyId? {
    // Type-safe algorithm usage with Result-based API (recommended)
    val result = kms.generateKey(
        algorithm = Algorithm.Ed25519,
        options = mapOf(
            KmsOptionKeys.KEY_ID to "issuer-root",
            KmsOptionKeys.DESCRIPTION to "Issuer root key"
        )
    )
    
    return when (result) {
        is GenerateKeyResult.Success -> {
            val handle = result.keyHandle
            println("Generated key ${handle.id.value} (${handle.algorithm})")
            handle.id
        }
        is GenerateKeyResult.Failure -> {
            println("Failed to generate key: ${result.reason}")
            null
        }
    }
}

suspend fun signDigest(kms: KeyManagementService, keyId: KeyId, digest: ByteArray): ByteArray? {
    val result = kms.sign(keyId, digest)
    return when (result) {
        is SignResult.Success -> result.signature
        is SignResult.Failure -> {
            println("Failed to sign: ${result.reason}")
            null
        }
    }
}

**Outcome:** Demonstrates algorithm discovery and creating keys via the KMS abstraction using the Result-based API for type-safe error handling—no direct coupling to backing implementations.
```

### Example: wallet-level key generation

```kotlin
import com.trustweave.kms.*
import com.trustweave.kms.results.*

val keyResult = wallet.withKeyManagement { keys ->
    keys.generateKey(Algorithm.Ed25519, mapOf(
        KmsOptionKeys.KEY_ID to "holder-authentication",
        KmsOptionKeys.DESCRIPTION to "Holder authentication key"
    ))
}

when (keyResult) {
    is GenerateKeyResult.Success -> {
        val keyHandle = keyResult.keyHandle
        println("Holder key created: ${keyHandle.id.value}")
    }
    is GenerateKeyResult.Failure -> {
        println("Failed to create key: ${keyResult.reason}")
    }
}

**Outcome:** Uses the wallet DSL to mint a holder key with custom metadata using the Result-based API, returning the generated handle for later signing operations.
```

## Practical usage tips

- **Production** – back keys with Hardware Security Modules (HSMs) or cloud Key Management Service (KMS) ([AWS KMS](../integrations/aws-kms.md), [Azure Key Vault](../integrations/azure-kms.md), [Google Cloud KMS](../integrations/google-kms.md), [HashiCorp Vault](../integrations/hashicorp-vault-kms.md), [IBM Key Protect](../integrations/ibm-key-protect-kms.md), etc.) via custom providers.
- **Rotation** – maintain previous keys so verifiers can validate historic credentials; rotate key IDs in VC proofs.
- **Access control** – enforce authorisation at the Key Management Service (KMS) boundary; TrustWeave assumes the provider handles policy.
- **Testing** – rely on `InMemoryKeyManagementService` from the `inmemory` plugin for determinism.
- **Error Handling** – always use the Result-based API (`generateKey`, `sign`, etc.) for type-safe error handling.

## See also

- [Wallet API Reference – KeyManagement](../api-reference/wallet-api.md#keymanagement)
- [KMS Integration Guides](../integrations/README.md#key-management-systems-kms) – Implementation guides for AWS KMS, Azure Key Vault, Google Cloud KMS, HashiCorp Vault, IBM Key Protect, and InMemory KMS
- [KMS Quick Start Guide](../kms/KMS_QUICK_START.md) – Quick start examples for all KMS plugins
- [KMS Configuration Guide](../kms/KMS_PLUGINS_CONFIGURATION.md) – Complete configuration reference
- [DIDs](dids.md) for how keys feed DID documents.
- [Credential Service API](../api-reference/credential-service-api.md) to see where keys sign credentials.
- [Advanced – Key Rotation](../advanced/key-rotation.md) *(to be added in a later step of this plan).*
- [Architecture Overview](../introduction/architecture-overview.md)

---

# Key Management

Key management underpins every trust workflow in TrustWeave. Keys sign credentials and presentations, decrypt payloads, and authenticate wallets. The platform treats Key Management Service (KMS) as a first-class Service Provider Interface (SPI) so you can swap implementations without rewriting your business logic.

## Responsibilities

A `KeyManagementService` is responsible for:

- **Key generation** – create asymmetric key pairs based on requested algorithms (`Ed25519`, `secp256k1`, etc.).
- **Key lookup** – fetch metadata for stored keys (`KeyHandle`) including algorithm and optional public key material (JWK or multibase).
- **Signing** – produce digital signatures for arbitrary byte arrays.
- **Deletion / rotation** – remove or rotate keys if the provider supports it.

The interface includes algorithm advertisement, type-safe algorithm support, and algorithm validation:

```kotlin
interface KeyManagementService {
    // Algorithm advertisement (required)
    suspend fun getSupportedAlgorithms(): Set<Algorithm>
    suspend fun supportsAlgorithm(algorithm: Algorithm): Boolean
    suspend fun supportsAlgorithm(algorithmName: String): Boolean

    // Key operations (Result-based API)
    suspend fun generateKey(algorithm: Algorithm, options: Map<String, Any?> = emptyMap()): GenerateKeyResult
    suspend fun generateKey(algorithmName: String, options: Map<String, Any?> = emptyMap()): GenerateKeyResult
    suspend fun getPublicKey(keyId: KeyId): GetPublicKeyResult
    suspend fun sign(keyId: KeyId, data: ByteArray, algorithm: Algorithm? = null): SignResult
    suspend fun sign(keyId: KeyId, data: ByteArray, algorithmName: String?): SignResult
    suspend fun deleteKey(keyId: KeyId): DeleteKeyResult
}
```

**All operations use Result-based API for type-safe error handling.** Results are sealed classes that ensure all error cases are handled at compile time.

**All KMS implementations MUST advertise their supported algorithms** via `getSupportedAlgorithms()`.

### Algorithm Validation

TrustWeave provides built-in algorithm validation to ensure that the algorithm specified for signing operations is compatible with the key's actual algorithm. This prevents accidental misuse of keys with incompatible algorithms.

**Algorithm validation is built into the Result-based API:**

```kotlin
import com.trustweave.kms.*
import com.trustweave.kms.results.*

// Get key and validate algorithm
val publicKeyResult = kms.getPublicKey(keyId)
when (publicKeyResult) {
    is GetPublicKeyResult.Success -> {
        val keyHandle = publicKeyResult.keyHandle
        val keyAlgorithm = Algorithm.parse(keyHandle.algorithm)
        
        // Check algorithm compatibility
        if (keyAlgorithm != null && Algorithm.Ed25519.isCompatibleWith(keyAlgorithm)) {
            // Algorithm is compatible
            val sign = kms.sign(keyId, data, Algorithm.Ed25519)
            // Handle sign result...
        }
    }
    is GetPublicKeyResult.Failure -> {
        // Handle error...
    }
}

// Or let the KMS handle validation automatically
val sign = kms.sign(keyId, data, Algorithm.Ed25519)
when (sign) {
    is SignResult.Success -> {
        // Signing succeeded
    }
    is SignResult.Failure.UnsupportedAlgorithm -> {
        // Algorithm incompatible with key - handled automatically
        println("Algorithm ${sign.requestedAlgorithm} not compatible with key algorithm ${sign.keyAlgorithm}")
    }
    is SignResult.Failure.KeyNotFound -> {
        // Key doesn't exist
    }
    is SignResult.Failure.Error -> {
        // Other error
    }
}
```

**Best Practice:** The Result-based API automatically validates algorithm compatibility. Always handle all Result cases for type-safe error handling.

## Built-in Providers

| Module | Provider | Supported Algorithms | Notes |
|--------|----------|----------------------|-------|
| `TrustWeave-testkit` | `InMemoryKeyManagementService` | Ed25519, secp256k1 | Ideal for unit tests; stores keys in-memory. |
| `kms/plugins/waltid` | `WaltIdKeyManagementService` | Ed25519, secp256k1, P-256, P-384, P-521 | Uses walt.id crypto to generate and sign keys. |
| Community | SPI implementations | Varies by provider | Register via `META-INF/services/com.trustweave.kms.spi.KeyManagementServiceProvider`. |

To use a custom provider, include it on the classpath and TrustWeave will discover it automatically when building the facade (`TrustWeave.create { keys { provider("custom") } }`).

### Algorithm Discovery

You can discover which providers support specific algorithms:

```kotlin
import com.trustweave.kms.*

// Discover all providers and their algorithms
val providers = AlgorithmDiscovery.discoverProviders()
providers.forEach { (name, algorithms) ->
    println("$name supports: ${algorithms.joinToString { it.name }}")
}

// Find providers that support Ed25519
val ed25519Providers = AlgorithmDiscovery.findProvidersFor(Algorithm.Ed25519)
println("Providers supporting Ed25519: $ed25519Providers")

// Find and create best provider for an algorithm
val kms = AlgorithmDiscovery.createProviderFor(
    algorithm = Algorithm.Ed25519,
    preferredProvider = "waltid"
)
```

## Options and Builders

Use `KmsOptionKeys` constants for type-safe option keys:

```kotlin
import com.trustweave.kms.*
import com.trustweave.kms.KmsOptionKeys
import com.trustweave.kms.results.*

val result = wallet.withKeyManagement { kms ->
    kms.generateKey(Algorithm.Ed25519, mapOf(
        KmsOptionKeys.KEY_ID to "issuer-signing",
        KmsOptionKeys.DESCRIPTION to "Issuer signing key",
        KmsOptionKeys.EXPORTABLE to false
    ))
}

when (result) {
    is GenerateKeyResult.Success -> {
        val keyHandle = result.keyHandle
        // Use key handle
    }
    is GenerateKeyResult.Failure -> {
        // Handle error
    }
}
```

**Outcome:** Shows the Result-based API with type-safe option keys—making it easy to add provider-specific metadata while generating keys with compile-time error checking.

All APIs use `Map<String, Any?>` for options, with `KmsOptionKeys` constants providing type safety.

## Signing in Credential Workflows

During credential issuance the facade automatically:

1. Requests/generates an issuer key if needed.
2. Invokes `sign` with the canonicalized credential bytes.
3. Embeds the resulting proof (JWS or Ed25519Signature2020) into the `VerifiableCredential`.

Presentation workflows follow the same pattern when creating verifiable presentations or selective disclosures.

## Security Guidance

- **Production** – back your `KeyManagementService` with a Hardware Security Module (HSM), cloud Key Management Service (KMS), or equivalent secure enclave. Never store production keys in process memory.
- **Rotation** – implement periodic key rotation and maintain historic keys (with `keyId` suffixes) so verifiers can still check older proofs.
- **Access control** – centralize authorization for key usage; TrustWeave assumes the Key Management Service (KMS) enforces policy.

## Extending the SPI

Create a module that implements `KeyManagementServiceProvider`:

```kotlin
import com.trustweave.kms.*

class VaultKmsProvider : KeyManagementServiceProvider {
    override val name = "vault"

    // MUST advertise supported algorithms
    override val supportedAlgorithms: Set<Algorithm> = setOf(
        Algorithm.Ed25519,
        Algorithm.Secp256k1,
        Algorithm.P256,
        Algorithm.P384,
        Algorithm.P521
    )

    override fun create(options: Map<String, Any?>): KeyManagementService = VaultKms(options)
}

class VaultKms(private val options: Map<String, Any?>) : KeyManagementService {
    companion object {
        val SUPPORTED_ALGORITHMS = setOf(
            Algorithm.Ed25519,
            Algorithm.Secp256k1,
            Algorithm.P256,
            Algorithm.P384,
            Algorithm.P521
        )
    }

    // MUST implement algorithm advertisement
    override suspend fun getSupportedAlgorithms(): Set<Algorithm> = SUPPORTED_ALGORITHMS

    override suspend fun generateKey(
        algorithm: Algorithm,
        options: Map<String, Any?>
    ): GenerateKeyResult {
        // Implementation using Vault API
        // Return GenerateKeyResult.Success or GenerateKeyResult.Failure
        // ...
    }

    override suspend fun getPublicKey(keyId: KeyId): GetPublicKeyResult {
        // Return GetPublicKeyResult.Success or GetPublicKeyResult.Failure
        // ...
    }

    override suspend fun sign(
        keyId: KeyId,
        data: ByteArray,
        algorithm: Algorithm?
    ): SignResult {
        // Return SignResult.Success or SignResult.Failure
        // ...
    }

    override suspend fun deleteKey(keyId: KeyId): DeleteKeyResult {
        // Return DeleteKeyResult.Deleted, DeleteKeyResult.NotFound, or DeleteKeyResult.Failure
        // ...
    }
}
```

Add `META-INF/services/com.trustweave.kms.spi.KeyManagementServiceProvider` containing the provider's fully qualified class name. After that, `TrustWeaveDefaults` can pick it up automatically.

**Important:** All providers MUST implement `supportedAlgorithms` and all KMS instances MUST implement `getSupportedAlgorithms()`.

## Next Steps

**Ready to use Key Management?**
- [Wallet API Reference – KeyManagement](../api-reference/wallet-api.md#keymanagement) - DSL hooks and typed options
- [Algorithm Compatibility Table](algorithm-compatibility-table.md) - Algorithm support across DIDs, VCs, AWS KMS, and Azure Key Vault
- [KMS Integrations](../integrations/README.md#key-management-systems-kms) - AWS KMS, Azure Key Vault, WaltID, and more

**Want to learn more?**
- [DIDs](dids.md) - Understand how keys are used in DID documents
- [Verifiable Credentials](verifiable-credentials.md) - Learn how keys sign credentials
- [SPI Guide](../advanced/spi.md) - Build custom KMS providers

**Explore related concepts:**
- [Testkit KMS](../modules/trustweave-common.md) - Testing helpers
- [Security Best Practices](../security/README.md) - Security guidelines

