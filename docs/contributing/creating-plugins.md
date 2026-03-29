---
title: Creating TrustWeave Plugins
nav_exclude: true
---

# Creating TrustWeave Plugins

This guide explains how to create custom plugins for TrustWeave by implementing the various plugin interfaces.

## Overview

TrustWeave is designed with a plugin architecture that allows you to extend functionality by implementing specific interfaces. Plugins can be registered manually or discovered automatically via the Service Provider Interface (SPI).

## Plugin Types

TrustWeave supports the following plugin interfaces:

1. **DidMethod** - Implement custom DID methods (e.g., did:web, did:key, did:ion)
2. **BlockchainAnchorClient** - Add support for new blockchain networks
3. **ProofGenerator** - Implement custom proof types (e.g., Ed25519, JWT, BBS+)
4. **KeyManagementService** - Integrate with different key management backends
5. **CredentialService** - Add credential issuance/verification providers
6. **WalletFactory** - Create custom wallet storage backends

## Prerequisites

Add the necessary dependencies to your project:

```kotlin
dependencies {
    // Core interfaces
    implementation("org.trustweave:did-did-core:0.6.0")
    implementation("org.trustweave:anchors-anchor-core:0.6.0")
    implementation("org.trustweave:kms-kms-core:0.6.0")
    implementation("org.trustweave:common:0.6.0")

    // SPI support (optional, for auto-discovery)
    // Note: Module paths have been renamed to avoid circular dependency issues:
    // - did:core → did:did-core
    // - credentials:core → credentials:credential-core
    // - kms:core → kms:kms-core
    // - anchors:core → anchors:anchor-core
    // - wallet:core → wallet:wallet-core
    implementation("org.trustweave:common:0.6.0")
}
```

## 1. Implementing a DID Method

The `DidMethod` interface allows you to implement custom DID methods.

### Interface Definition

Implement **`org.trustweave.did.DidMethod`** in **`did-core`** (it extends **`DidMethodResolver`**). Resolution uses the type-safe **`Did`** identifier and returns sealed **`org.trustweave.did.resolver.DidResolutionResult`** — not a nullable document on a single data class.

```kotlin
// Abbreviated — see did-core for full Javadoc
interface DidMethod : DidMethodResolver {
    val method: String
    suspend fun createDid(options: DidCreationOptions = DidCreationOptions()): DidDocument
    override suspend fun resolveDid(did: Did): DidResolutionResult
    suspend fun updateDid(did: Did, updater: (DidDocument) -> DidDocument): DidDocument
    suspend fun deactivateDid(did: Did): Boolean
}
```

### Example Implementation

**Reference:** Copy from **`org.trustweave.testkit.did.DidKeyMockMethod`** — it shows **`GenerateKeyResult`**, **`VerificationMethodId`**, **`Did`**, **`kotlinx.datetime.Clock`**, and correct **`DidDocument`** construction.

**Resolution** must return **`DidResolutionResult.Success`** or a **`Failure`** subtype (e.g. **`NotFound`**, **`InvalidFormat`**, **`MethodNotRegistered`**, **`ResolutionError`**):

```kotlin
import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument
import org.trustweave.did.model.DidDocumentMetadata
import org.trustweave.did.resolver.DidResolutionMetadata
import org.trustweave.did.resolver.DidResolutionResult
import kotlinx.datetime.Clock

// private val documents: MutableMap<String, DidDocument> = ...

override suspend fun resolveDid(did: Did): DidResolutionResult {
    val document = documents[did.value]
    val now = Clock.System.now()
    return if (document != null) {
        DidResolutionResult.Success(
            document = document,
            documentMetadata = DidDocumentMetadata(created = now, updated = now),
            resolutionMetadata = DidResolutionMetadata(pattern = method)
        )
    } else {
        DidResolutionResult.Failure.NotFound(did = did, reason = "DID not found")
    }
}
```

Wrong method prefix or malformed DID string should use **`DidResolutionResult.Failure.InvalidFormat`** (or validate earlier with **`DidValidator`**). Unregistered method names are **`Failure.MethodNotRegistered`** at the registry layer.

### Registration

**Typical approach:** ship a **`DidMethodProvider`** (and `META-INF/services/org.trustweave.did.spi.DidMethodProvider` (see existing plugins under `did/plugins/`)) so **`TrustWeave.build { }`** can resolve your method when the JAR is on the classpath.

**Smoke test in a coroutine:**
```kotlin
import kotlinx.coroutines.runBlocking
import org.trustweave.trust.TrustWeave

fun main() = runBlocking {
    val trustWeave = TrustWeave.quickStart()
    // Exercise createDid { method("yourMethod") } once SPI + classpath are wired.
}
```

There is **no** `TrustWeave.registerDidMethod(...)` on the facade; configuration is applied when the instance is built.

## 2. Implementing a Blockchain Anchor Client

The `BlockchainAnchorClient` interface allows you to add support for new blockchain networks.

### Interface Definition

```kotlin
interface BlockchainAnchorClient {
    suspend fun writePayload(
        payload: JsonElement,
        mediaType: String = "application/json"
    ): AnchorResult

    suspend fun readPayload(ref: AnchorRef): AnchorResult
}
```

### Example Implementation

```kotlin
package com.example.TrustWeave.plugins

import org.trustweave.anchor.*
import org.trustweave.core.exception.NotFoundException
import kotlinx.serialization.json.JsonElement
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Example blockchain anchor client implementation.
 * This stores anchors in memory (use actual blockchain SDK in production).
 */
class ExampleBlockchainAnchorClient(
    private val chainId: String,
    private val contract: String? = null
) : BlockchainAnchorClient {

    private val storage = ConcurrentHashMap<String, AnchorResult>()
    private val txCounter = AtomicLong(0)

    override suspend fun writePayload(
        payload: JsonElement,
        mediaType: String
    ): AnchorResult {
        // Generate transaction hash
        val txHash = "tx_${txCounter.incrementAndGet()}_${System.currentTimeMillis()}"

        // Create anchor reference
        val ref = AnchorRef(
            chainId = chainId,
            txHash = txHash,
            contract = contract
        )

        // Create anchor result
        val result = AnchorResult(
            ref = ref,
            payload = payload,
            mediaType = mediaType,
            timestamp = System.currentTimeMillis() / 1000
        )

        // Store (in production, submit to blockchain)
        storage[txHash] = result
        return result
    }

    override suspend fun readPayload(ref: AnchorRef): AnchorResult {
        if (ref.chainId != chainId) {
            throw IllegalArgumentException("Chain ID mismatch")
        }

        return storage[ref.txHash]
            ?: throw NotFoundException("Anchor not found: ${ref.txHash}")
    }
}
```

### Using AbstractBlockchainAnchorClient

For production implementations, extend `AbstractBlockchainAnchorClient` which provides fallback storage and common patterns:

```kotlin
import org.trustweave.anchor.AbstractBlockchainAnchorClient

class MyBlockchainClient(
    chainId: String,
    options: Map<String, Any?>
) : AbstractBlockchainAnchorClient(chainId, options) {

    override protected fun canSubmitTransaction(): Boolean {
        // Check if credentials are configured
        return options["privateKey"] != null
    }

    override protected suspend fun submitTransactionToBlockchain(
        payloadBytes: ByteArray
    ): String {
        // Submit to actual blockchain
        // Return transaction hash
        return "0x..."
    }

    override protected suspend fun readTransactionFromBlockchain(
        txHash: String
    ): AnchorResult {
        // Read from actual blockchain
        // Return AnchorResult
    }

    override protected fun buildExtraMetadata(mediaType: String): Map<String, String> {
        return mapOf("network" to "mainnet", "mediaType" to mediaType)
    }

    override protected fun generateTestTxHash(): String {
        return "test_${System.currentTimeMillis()}"
    }
}
```

### Registration

Expose a **`BlockchainAnchorClientProvider`** (SPI) named for your adapter, then reference it from **`TrustWeave.build`**:

```kotlin
val trustWeave = TrustWeave.build {
    anchor {
        chain("example:mainnet") {
            provider("example")
            options { /* passed to provider.create(chainId, options) */ }
        }
    }
}
```

## 3. Implementing a Proof Generator

The `ProofGenerator` interface allows you to implement custom proof types.

### Interface Definition

```kotlin
interface ProofGenerator {
    val proofType: String  // e.g., "Ed25519Signature2020"

    suspend fun generateProof(
        credential: VerifiableCredential,
        keyId: String,
        options: ProofOptions
    ): Proof
}
```

### Example Implementation

```kotlin
package com.example.TrustWeave.plugins

import org.trustweave.credential.model.vc.*
import org.trustweave.credential.proof.*
import org.trustweave.core.util.normalizeKeyId

/**
 * Example proof generator implementation.
 */
class ExampleProofGenerator(
    private val signer: suspend (ByteArray, String) -> ByteArray
) : ProofGenerator {

    override val proofType = "ExampleSignature2020"

    override suspend fun generateProof(
        credential: VerifiableCredential,
        keyId: String,
        options: ProofOptions
    ): Proof {
        // Normalize key ID
        val normalizedKeyId = normalizeKeyId(keyId)

        // Create proof document (credential without proof)
        val proofDocument = credential.copy(proof = null)

        // Canonicalize and create digest
        val canonicalJson = proofDocument.toCanonicalJson() // Implement this
        val digest = canonicalJson.hash() // Implement this

        // Sign the digest
        val signature = signer(digest, normalizedKeyId)

        // Create proof
        return Proof(
            type = proofType,
            created = java.time.Instant.now().toString(),
            proofPurpose = options.proofPurpose,
            verificationMethod = options.verificationMethod
                ?: "$credential.issuer#$normalizedKeyId",
            proofValue = signature.encodeBase64(), // Implement encoding
            challenge = options.challenge,
            domain = options.domain
        )
    }
}
```

### Registration

Proof generators are not registered on a separate `proofGenerators { }` builder. They are typically bundled inside a custom **`CredentialService`** (see below) or the default service constructed from KMS + DID resolution.

## 4. Implementing a Key Management Service

The `KeyManagementService` interface allows you to integrate with different key management backends.

### Interface Definition

```kotlin
interface KeyManagementService {
    suspend fun generateKey(
        algorithm: String,
        options: Map<String, Any?> = emptyMap()
    ): KeyHandle

    suspend fun getPublicKey(keyId: KeyId): KeyHandle

    suspend fun sign(
        keyId: KeyId,
        data: ByteArray,
        algorithm: String? = null
    ): ByteArray

    suspend fun deleteKey(keyId: KeyId): Boolean
}
```

### Example Implementation

```kotlin
package com.example.TrustWeave.plugins

import org.trustweave.kms.*
import java.security.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Example KMS implementation using Java's KeyPairGenerator.
 */
class ExampleKeyManagementService : KeyManagementService {

    private val keys = ConcurrentHashMap<String, KeyPair>()

    override suspend fun generateKey(
        algorithm: String,
        options: Map<String, Any?>
    ): KeyHandle {
        val keyPairGenerator = when (algorithm.uppercase()) {
            "ED25519" -> KeyPairGenerator.getInstance("EdDSA")
            "SECP256K1" -> KeyPairGenerator.getInstance("EC")
            else -> throw IllegalArgumentException("Unsupported algorithm: $algorithm")
        }

        keyPairGenerator.initialize(256)
        val keyPair = keyPairGenerator.generateKeyPair()

        val keyIdString = "key_${System.currentTimeMillis()}"
        keys[keyIdString] = keyPair

        // Convert to JWK format (simplified)
        val publicKeyJwk = mapOf(
            "kty" to "EC",
            "crv" to algorithm,
            "x" to keyPair.public.encoded.toString(Charsets.UTF_8)
        )

        return KeyHandle(
            id = KeyId(keyIdString),
            algorithm = algorithm,
            publicKeyJwk = publicKeyJwk
        )
    }

    override suspend fun getPublicKey(keyId: KeyId): KeyHandle {
        val keyPair = keys[keyId.value]
            ?: throw KeyNotFoundException("Key not found: ${keyId.value}")

        return KeyHandle(
            id = keyId,
            algorithm = "Ed25519", // Determine from keyPair
            publicKeyJwk = mapOf("kty" to "EC") // Convert properly
        )
    }

    override suspend fun sign(
        keyId: KeyId,
        data: ByteArray,
        algorithm: String?
    ): ByteArray {
        val keyPair = keys[keyId.value]
            ?: throw KeyNotFoundException("Key not found: ${keyId.value}")

        val signer = Signature.getInstance("Ed25519")
        signer.initSign(keyPair.private)
        signer.update(data)
        return signer.sign()
    }

    override suspend fun deleteKey(keyId: KeyId): Boolean {
        return keys.remove(keyId.value) != null
    }
}
```

### Registration

```kotlin
val trustWeave = TrustWeave.build {
    customKms(ExampleKeyManagementService())
    did { method("key") { algorithm("Ed25519") } }
}
```

## 5. Extending credential issuance / verification

The public **`CredentialService`** API is **`issue(IssuanceRequest)`**, **`verify(...)`**, **`createPresentation`**, **`verifyPresentation`**, and format helpers (`supports`, `supportedFormats`). The default implementation composes built-in **proof engines** (VC-LD, SD-JWT-VC, etc.).

**Typical extensions (instead of reimplementing the whole service):**

- Implement or configure **`ProofEngine`** / SPI types under `credentials/credential-api` (see `spi.proof` and internal engines).
- Supply a custom **`credentialService(...)`** instance only if you truly replace orchestration; register it on the facade:

```kotlin
import org.trustweave.testkit.services.*
val trustWeave = TrustWeave.build {
    keys { provider(IN_MEMORY); algorithm(ED25519) }
    did { method(KEY) { algorithm(ED25519) } }
    credentialService(myCustomCredentialService)
}
```

See **[Credential Service API Reference](../api-reference/credential-service-api.md)** for the current method list and **`CredentialServices` / `credentialService` factories**.

## 6. Implementing a Wallet Factory

The `WalletFactory` interface allows you to create custom wallet storage backends.

### Interface Definition

```kotlin
interface WalletFactory {
    suspend fun create(
        providerName: String,
        walletId: String? = null,
        walletDid: String? = null,
        holderDid: String? = null,
        options: WalletCreationOptions = WalletCreationOptions()
    ): Any
}
```

### Example Implementation

```kotlin
package com.example.TrustWeave.plugins

import org.trustweave.testkit.credential.InMemoryWallet
import org.trustweave.wallet.Wallet
import org.trustweave.wallet.services.WalletCreationOptions
import org.trustweave.wallet.services.WalletFactory

/**
 * Example wallet factory implementation.
 */
class ExampleWalletFactory : WalletFactory {

    override suspend fun create(
        providerName: String,
        walletId: String?,
        walletDid: String?,
        holderDid: String?,
        options: WalletCreationOptions
    ): Wallet {
        return when (providerName) {
            "inMemory" -> {
                // Create in-memory wallet
                InMemoryWallet(
                    walletId = walletId ?: generateWalletId(),
                    holderDid = holderDid ?: throw IllegalArgumentException("holderDid required")
                )
            }
            "database" -> {
                throw UnsupportedOperationException(
                    "Replace with your own Wallet implementation (e.g. JDBC-backed) wired to options.storagePath"
                )
            }
            else -> throw IllegalArgumentException("Unknown provider: $providerName")
        }
    }

    private fun generateWalletId(): String {
        return "wallet_${System.currentTimeMillis()}"
    }
}
```

### Registration

```kotlin
import kotlinx.coroutines.runBlocking
import org.trustweave.trust.TrustWeave

val trustWeave = runBlocking {
    TrustWeave.build {
        factories(walletFactory = ExampleWalletFactory())
        did { method("key") { algorithm("Ed25519") } }
    }
}
```

## Plugin Registration Methods

### Manual Registration

Configure plugins when **building** the facade (SPI-discovered providers are merged automatically):

```kotlin
import kotlinx.coroutines.runBlocking
import org.trustweave.trust.TrustWeave

val trustWeave = runBlocking {
    TrustWeave.build {
        customKms(MyKms())
        factories(walletFactory = MyWalletFactory())

        did {
            method("mymethod") { /* options consumed by your DidMethodProvider, if any */ }
        }

        anchor {
            chain("myChain:mainnet") {
                provider("myProvider") // BlockchainAnchorClientProvider name
                options { /* passed to provider.create(chainId, options) */ }
            }
        }

        credentialService(MyCredentialService())
    }
}
```

### Runtime reconfiguration

The **`TrustWeave`** facade does not support adding DID methods or anchor clients **after** construction. Build a new instance with an updated **`TrustWeave.build { }`** block (or manage long-lived clients yourself and call **`TrustWeave.from(config)`** only from code that can obtain a **`TrustWeaveConfig`** legally).

### SPI Auto-Discovery (Advanced)

For automatic discovery via Java ServiceLoader, implement provider interfaces:

1. Create a provider class:
```kotlin
class MyDidMethodProvider : DidMethodProvider {
    override val name = "mymethod"

    override fun create(options: Map<String, Any?>): DidMethod? {
        val kms = options["kms"] as? KeyManagementService
            ?: return null
        return MyDidMethod(kms)
    }
}
```

2. Create service file: `META-INF/services/org.trustweave.did.DidMethodProvider`
```
com.example.MyDidMethodProvider
```

## Plugin Lifecycle

Plugins can optionally implement `PluginLifecycle` for initialization and cleanup:

```kotlin
import org.trustweave.core.plugin.PluginLifecycle

class MyBlockchainClient : BlockchainAnchorClient, PluginLifecycle {

    override suspend fun initialize(config: Map<String, Any?>): Boolean {
        // Initialize connections, load configuration
        return true
    }

    override suspend fun start(): Boolean {
        // Start background processes
        return true
    }

    override suspend fun stop(): Boolean {
        // Stop accepting new operations
        return true
    }

    override suspend fun cleanup() {
        // Clean up resources
    }
}
```

Call **`trustWeave.close()`** when you discard the facade so **`Closeable`** collaborators (KMS, anchor clients, etc.) can shut down. If **your** plugin implements **`PluginLifecycle`**, invoke those hooks from your own wiring; the facade does not expose **`initialize()`** / **`start()`** / **`stop()`**.

See [Plugin lifecycle](../advanced/plugin-lifecycle.md) for patterns.

## Testing Your Plugin

### Unit Testing

```kotlin
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class ExampleDidMethodTest {

    @Test
    fun `test create DID`() = runTest {
        val kms = InMemoryKeyManagementService()
        val method = ExampleDidMethod(kms)

        val document = method.createDid()

        assertNotNull(document)
        assertTrue(document.id.startsWith("did:example:"))
        assertFalse(document.verificationMethod.isEmpty())
    }

    @Test
    fun `test resolve DID`() = runTest {
        val kms = InMemoryKeyManagementService()
        val method = ExampleDidMethod(kms)

        val document = method.createDid()
        val result = method.resolveDid(document.id)

        assertNotNull(result.document)
        assertEquals(document.id, result.document!!.id)
    }
}
```

### Integration Testing

```kotlin
@Test
fun `test plugin with TrustWeave`() = runTest {
    val trustWeave = TrustWeave.build {
        customKms(InMemoryKeyManagementService())
        did { method("example") { algorithm("Ed25519") } }
    }

    val did = trustWeave.createDid { method("example") }.getOrThrowDid()
    assertTrue(did.value.startsWith("did:example:"))
}
```

## Best Practices

1. **Error Handling**: Always throw appropriate exceptions (`KeyNotFoundException`, `NotFoundException`, etc.)
2. **Thread Safety**: Use concurrent collections for in-memory storage
3. **Resource Management**: Implement `PluginLifecycle` for plugins that need initialization/cleanup
4. **Documentation**: Document any method-specific options or requirements
5. **Testing**: Provide both unit tests and integration tests
6. **Type Safety**: Use type-safe options classes when available
7. **Idempotency**: Make operations idempotent where possible
8. **Validation**: Validate inputs early and provide clear error messages

## Next Steps

- Review existing implementations in `trustweave-testkit` for reference
- See [Integration Modules](../integrations/README.md) for production examples
- Check [Plugin Lifecycle](../advanced/plugin-lifecycle.md) for lifecycle management
- Review [Architecture Overview](../introduction/architecture-overview.md) for design patterns

## Related Documentation

- Plugin Lifecycle Management](../advanced/plugin-lifecycle.md)
- Integration Modules](../integrations/README.md)
- Architecture Overview](../introduction/architecture-overview.md)
- Core API Reference](../api-reference/core-api.md)
- Credential Service API](../api-reference/credential-service-api.md)


