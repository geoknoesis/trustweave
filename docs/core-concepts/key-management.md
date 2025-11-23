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
    if (kms.supportsAlgorithm("secp256k1")) {
        println("secp256k1 is supported")
    }
}
```

### Example: generating and using keys

```kotlin
import com.trustweave.kms.*

suspend fun issueSignerKey(kms: KeyManagementService): String {
    // Type-safe algorithm usage (recommended)
    val handle = kms.generateKey(
        algorithm = Algorithm.Ed25519,
        options = mapOf("label" to "issuer-root", "exportable" to false)
    )
    println("Generated key ${handle.id} (${handle.algorithm})")
    return handle.id
}

// Or use string-based API (convenience)
suspend fun issueSignerKeyString(kms: KeyManagementService): String {
    val handle = kms.generateKey(
        algorithmName = "Ed25519",
        options = mapOf("label" to "issuer-root", "exportable" to false)
    )
    return handle.id
}

suspend fun signDigest(kms: KeyManagementService, keyId: String, digest: ByteArray): ByteArray =
    kms.sign(keyId, digest)

**Outcome:** Demonstrates algorithm discovery and creating keys via the KMS abstraction—no direct coupling to backing implementations.
```

### Example: wallet-level key generation

```kotlin
val keyHandle = wallet.withKeyManagement { keys ->
    keys.generateKey("Ed25519") {
        label = "holder-authentication"
        exportable = true
    }
}
println("Holder key created: ${keyHandle.id}")

**Outcome:** Uses the wallet DSL to mint a holder key with custom metadata, returning the generated handle for later signing operations.
```

## Practical usage tips

- **Production** – back keys with Hardware Security Modules (HSMs) or cloud Key Management Service (KMS) ([AWS KMS](../integrations/aws-kms.md), [Azure Key Vault](../integrations/azure-kms.md), [Google Cloud KMS](../integrations/google-kms.md), [HashiCorp Vault](../integrations/hashicorp-vault-kms.md), etc.) via custom providers.  
- **Rotation** – maintain previous keys so verifiers can validate historic credentials; rotate key IDs in VC proofs.  
- **Access control** – enforce authorisation at the Key Management Service (KMS) boundary; TrustWeave assumes the provider handles policy.  
- **Testing** – rely on `InMemoryKeyManagementService` from `TrustWeave-testkit` for determinism.

## See also

- [Wallet API Reference – KeyManagement](../api-reference/wallet-api.md#keymanagement)  
- [KMS Integration Guides](../integrations/README.md#other-did--kms-integrations) – Implementation guides for AWS KMS, Azure Key Vault, Google Cloud KMS, HashiCorp Vault, and walt.id
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

The interface includes algorithm advertisement and type-safe algorithm support:

```kotlin
interface KeyManagementService {
    // Algorithm advertisement (required)
    suspend fun getSupportedAlgorithms(): Set<Algorithm>
    suspend fun supportsAlgorithm(algorithm: Algorithm): Boolean
    suspend fun supportsAlgorithm(algorithmName: String): Boolean
    
    // Key operations
    suspend fun generateKey(algorithm: Algorithm, options: Map<String, Any?> = emptyMap()): KeyHandle
    suspend fun generateKey(algorithmName: String, options: Map<String, Any?> = emptyMap()): KeyHandle
    suspend fun getPublicKey(keyId: String): KeyHandle
    suspend fun sign(keyId: String, data: ByteArray, algorithm: Algorithm? = null): ByteArray
    suspend fun sign(keyId: String, data: ByteArray, algorithmName: String?): ByteArray
    suspend fun deleteKey(keyId: String): Boolean
}
```

**All KMS implementations MUST advertise their supported algorithms** via `getSupportedAlgorithms()`.

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

Typed option builders make integration ergonomic:

```kotlin
val key = wallet.withKeyManagement { kms ->
    kms.generateKey("Ed25519") {
        label = "issuer-signing"
        exportable = false
    }
}
```

**Outcome:** Shows the typed builder API in action—making it easy to add provider-specific metadata while generating keys.

Most APIs accept either strongly typed options or `Map<String, Any?>` fallbacks for legacy integrations.

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
    ): KeyHandle {
        // Implementation using Vault API
        // ...
    }
    
    // ... implement other methods
}
```

Add `META-INF/services/com.trustweave.kms.spi.KeyManagementServiceProvider` containing the provider's fully qualified class name. After that, `TrustWeaveDefaults` can pick it up automatically.

**Important:** All providers MUST implement `supportedAlgorithms` and all KMS instances MUST implement `getSupportedAlgorithms()`.

## Related Reading

- [Wallet API Reference](../api-reference/wallet-api.md#keymanagement) for DSL hooks and typed options.
- [Testkit KMS](../modules/trustweave-common.md) documentation for testing helpers.
- [SPI Guide](../advanced/spi.md) to build custom providers.

