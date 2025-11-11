# Key Management

> This guide is curated by [Geoknoesis LLC](https://www.geoknoesis.com). It outlines how VeriCore treats key custody across decentralized identity workflows.

## What is key management?

Key management covers the generation, storage, rotation, and usage of cryptographic keys. In VeriCore, every DID method, credential issuance, and presentation flow relies on a `KeyManagementService` (KMS) abstraction.

```kotlin
dependencies {
    implementation("com.geoknoesis.vericore:vericore-core:1.0.0-SNAPSHOT")
    implementation("com.geoknoesis.vericore:vericore-kms:1.0.0-SNAPSHOT")
}
```

**Result:** Gives you the KMS interfaces and helpers referenced in the examples below.

## Why key management matters

- **Proof integrity** – signing credentials and presentations requires private keys to stay secure.  
- **Interoperability** – different environments (cloud HSM, Vault, in-memory test KMS) provide the same interface.  
- **Lifecycle** – keys can be rotated or deactivated while preserving credential history via key identifiers (`keyId`).

## How VeriCore models key management

| Component | Purpose |
|-----------|---------|
| `KeyManagementService` | Core interface (`generateKey`, `getPublicKey`, `sign`, `deleteKey`). |
| `KeyHandle` | Describes a key (id, algorithm, public material). |
| `KeyManagementServiceProvider` | SPI entry point for auto-discoverable providers. |
| `wallet.withKeyManagement { … }` | Wallet DSL hook for higher-level workflows. |

Built-in providers include the in-memory test KMS and the walt.id-backed implementation. You can add your own via SPI.

### Example: generating and using keys

```kotlin
import com.geoknoesis.vericore.kms.KeyManagementService

suspend fun issueSignerKey(kms: KeyManagementService): String {
    val handle = kms.generateKey(
        algorithm = "Ed25519",
        options = mapOf("label" to "issuer-root", "exportable" to false)
    )
    println("Generated key ${handle.id} (${handle.algorithm})")
    return handle.id
}

suspend fun signDigest(kms: KeyManagementService, keyId: String, digest: ByteArray): ByteArray =
    kms.sign(keyId, digest)

**Outcome:** Demonstrates creating a labelled key and using it to sign digests via the KMS abstraction—no direct coupling to backing implementations.
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

- **Production** – back keys with HSMs or cloud KMS (AWS KMS, HashiCorp Vault, etc.) via custom providers.  
- **Rotation** – maintain previous keys so verifiers can validate historic credentials; rotate key IDs in VC proofs.  
- **Access control** – enforce authorisation at the KMS boundary; VeriCore assumes the provider handles policy.  
- **Testing** – rely on `InMemoryKeyManagementService` from `vericore-testkit` for determinism.

## See also

- [Wallet API Reference – KeyManagement](../api-reference/wallet-api.md#keymanagement)  
- [DIDs](dids.md) for how keys feed DID documents.  
- [Credential Service API](../api-reference/credential-service-api.md) to see where keys sign credentials.  
- [Advanced – Key Rotation](../advanced/key-rotation.md) *(to be added in a later step of this plan).*  
- [Architecture Overview](../introduction/architecture-overview.md)
# Key Management

Key management underpins every trust workflow in VeriCore. Keys sign credentials and presentations, decrypt payloads, and authenticate wallets. The platform treats KMS as a first-class SPI so you can swap implementations without rewriting your business logic.

## Responsibilities

A `KeyManagementService` is responsible for:

- **Key generation** – create asymmetric key pairs based on requested algorithms (`Ed25519`, `secp256k1`, etc.).
- **Key lookup** – fetch metadata for stored keys (`KeyHandle`) including algorithm and optional public key material (JWK or multibase).
- **Signing** – produce digital signatures for arbitrary byte arrays.
- **Deletion / rotation** – remove or rotate keys if the provider supports it.

The default interface is deliberately small:

```kotlin
interface KeyManagementService {
    suspend fun generateKey(algorithm: String, options: Map<String, Any?> = emptyMap()): KeyHandle
    suspend fun getPublicKey(keyId: String): KeyHandle
    suspend fun sign(keyId: String, data: ByteArray, algorithm: String? = null): ByteArray
    suspend fun deleteKey(keyId: String): Boolean
}
```

## Built-in Providers

| Module | Provider | Notes |
|--------|----------|-------|
| `vericore-testkit` | `InMemoryKeyManagementService` | Ideal for unit tests; stores keys in-memory. |
| `vericore-waltid` | `WaltIdKeyManagementService` | Uses walt.id crypto to generate and sign keys. |
| Community | SPI implementations | Register via `META-INF/services/com.geoknoesis.vericore.kms.spi.KeyManagementServiceProvider`. |

To use a custom provider, include it on the classpath and VeriCore will discover it automatically when building the facade (`VeriCore.create { keys { provider("custom") } }`).

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

- **Production** – back your `KeyManagementService` with an HSM, cloud KMS, or equivalent secure enclave. Never store production keys in process memory.
- **Rotation** – implement periodic key rotation and maintain historic keys (with `keyId` suffixes) so verifiers can still check older proofs.
- **Access control** – centralize authorization for key usage; VeriCore assumes the KMS enforces policy.

## Extending the SPI

Create a module that implements `KeyManagementServiceProvider`:

```kotlin
class VaultKmsProvider : KeyManagementServiceProvider {
    override val name = "vault"
    override fun create(options: Map<String, Any?>): KeyManagementService = VaultKms(options)
}
```

Add `META-INF/services/com.geoknoesis.vericore.kms.spi.KeyManagementServiceProvider` containing the provider’s fully qualified class name. After that, `VeriCoreDefaults` can pick it up automatically.

## Related Reading

- [Wallet API Reference](../api-reference/wallet-api.md#keymanagement) for DSL hooks and typed options.
- [Testkit KMS](../modules/vericore-core.md) documentation for testing helpers.
- [SPI Guide](../modules/vericore-spi.md) to build custom providers.

