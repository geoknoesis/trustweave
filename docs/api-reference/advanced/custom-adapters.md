---
title: Creating Custom Adapters
nav_exclude: true
redirect_from:
  - /advanced/custom-adapters/

---

# Creating Custom Adapters

This guide explains how to create custom adapters for TrustWeave by implementing the service interfaces.

## Overview

TrustWeave's adapter architecture allows you to implement custom:

- **DID Methods** – custom DID method implementations
- **Blockchain Adapters** – support for new blockchain networks
- **KMS Providers** – integration with custom key management systems
- **Credential Services** – custom credential issuance/verification providers
- **Wallet Factories** – custom wallet storage backends

## Creating a Custom DID Method

### Implementing DidMethod

Implement **`org.trustweave.did.DidMethod`** with type-safe **`Did`**, **`VerificationMethod`**, **`VerificationMethodId`**, and sealed **`DidResolutionResult`**. For **`createDid`**, follow **`org.trustweave.testkit.did.DidKeyMockMethod`** (KMS **`GenerateKeyResult`**, document layout).

```kotlin
import org.trustweave.did.DidMethod
import org.trustweave.did.DidCreationOptions
import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument
import org.trustweave.did.resolver.DidResolutionMetadata
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.kms.KeyManagementService

class MyCustomDidMethod(
    private val kms: KeyManagementService,
    private val config: MyDidConfig
) : DidMethod {
    override val method: String = "mydid"

    override suspend fun createDid(options: DidCreationOptions): DidDocument {
        TODO("See DidKeyMockMethod in testkit for a working createDid implementation")
    }

    override suspend fun resolveDid(did: Did): DidResolutionResult {
        val document = resolveFromBackend(did)
        return if (document != null) {
            DidResolutionResult.Success(
                document = document,
                resolutionMetadata = DidResolutionMetadata(contentType = "application/did+json")
            )
        } else {
            DidResolutionResult.Failure.NotFound(did = did)
        }
    }

    override suspend fun updateDid(did: Did, updater: (DidDocument) -> DidDocument): DidDocument {
        val current = when (val r = resolveDid(did)) {
            is DidResolutionResult.Success -> r.document
            else -> error("DID not found: ${did.value}")
        }
        val updated = updater(current)
        persistToBackend(did, updated)
        return updated
    }

    override suspend fun deactivateDid(did: Did): Boolean = deactivateInBackend(did)

    private fun resolveFromBackend(did: Did): DidDocument? = null // your store
    private fun persistToBackend(did: Did, document: DidDocument) { /* … */ }
    private fun deactivateInBackend(did: Did): Boolean = false
}
```

### Creating DID Method Provider

```kotlin
import org.trustweave.did.spi.DidMethodProvider
import org.trustweave.did.*
import org.trustweave.kms.KeyManagementService

class MyDidMethodProvider : DidMethodProvider {
    override val name: String = "my-did-method"
    override val supportedMethods: List<String> = listOf("mydid")

    override fun create(
        methodName: String,
        options: DidCreationOptions
    ): DidMethod? {
        if (methodName != "mydid") return null

        // Create KMS
        val kms = createKms(options)

        // Create config from options
        val config = MyDidConfig.fromOptions(options)

        return MyCustomDidMethod(kms, config)
    }

    private fun createKms(options: DidCreationOptions): KeyManagementService {
        // Create or get KMS instance
        TODO("provide KMS implementation")
    }
}
```

### Registering Provider

Create service file at `src/main/resources/META-INF/services/org.trustweave.did.spi.DidMethodProvider`:

```
com.example.MyDidMethodProvider
```

## Creating a Custom Blockchain Adapter

### Implementing BlockchainAnchorClient

```kotlin
import org.trustweave.anchor.*
import kotlinx.serialization.json.JsonElement

class MyBlockchainAnchorClient(
    val chainId: String,
    private val config: MyBlockchainConfig
) : BlockchainAnchorClient {

    override suspend fun writePayload(
        payload: JsonElement,
        mediaType: String
    ): AnchorResult {
        // Submit transaction to blockchain
        val txHash = submitTransaction(payload)

        // Wait for confirmation
        val blockHeight = waitForConfirmation(txHash)

        return AnchorResult(
            ref = AnchorRef(
                chainId = chainId,
                txHash = txHash,
                extra = mapOf(
                    "blockHeight" to blockHeight.toString(),
                    "timestamp" to System.currentTimeMillis().toString()
                )
            ),
            payload = payload,
            mediaType = mediaType
        )
    }

    override suspend fun readPayload(ref: AnchorRef): AnchorResult {
        // Read transaction from blockchain
        val payload = readTransaction(ref.txHash)
            ?: throw IllegalStateException("Anchor not found: ${ref.txHash}")
        return AnchorResult(ref = ref, payload = payload)
    }

    private fun submitTransaction(payload: JsonElement): String {
        TODO("submit transaction to your chain")
    }

    private fun waitForConfirmation(txHash: String): Long {
        TODO("await N confirmations")
    }

    private fun readTransaction(txHash: String): JsonElement? {
        TODO("read payload from your chain")
    }
}
```

### Creating Blockchain Adapter Provider

```kotlin
import org.trustweave.anchor.spi.BlockchainAnchorClientProvider
import org.trustweave.anchor.*

class MyBlockchainAdapterProvider : BlockchainAnchorClientProvider {
    override val name: String = "my-blockchain"

    override val supportedChains: List<String> = listOf(
        "myblockchain:mainnet",
        "myblockchain:testnet"
    )

    override fun create(
        chainId: String,
        options: Map<String, Any?>
    ): BlockchainAnchorClient? {
        if (chainId !in supportedChains) return null
        val config = MyBlockchainConfig.fromOptions(options)
        return MyBlockchainAnchorClient(chainId, config)
    }
}
```

### Registering Provider

Create service file at `src/main/resources/META-INF/services/org.trustweave.anchor.spi.BlockchainAnchorClientProvider`:

```
com.example.MyBlockchainAdapterProvider
```

## Creating a Custom KMS Provider

### Implementing KeyManagementService

```kotlin
import org.trustweave.core.identifiers.KeyId
import org.trustweave.kms.*
import org.trustweave.kms.results.*

class MyKeyManagementService(
    private val config: MyKmsConfig
) : KeyManagementService {

    override suspend fun getSupportedAlgorithms(): Set<Algorithm> =
        setOf(Algorithm.Ed25519, Algorithm.Secp256k1, Algorithm.P256)

    override suspend fun generateKey(
        algorithm: Algorithm,
        options: Map<String, Any?>
    ): GenerateKeyResult {
        if (!supportsAlgorithm(algorithm)) {
            return GenerateKeyResult.Failure.UnsupportedAlgorithm(
                algorithm = algorithm,
                supportedAlgorithms = getSupportedAlgorithms()
            )
        }
        return try {
            val handle = generateKeyInKms(algorithm)
            GenerateKeyResult.Success(handle)
        } catch (e: Exception) {
            GenerateKeyResult.Failure.Error(algorithm, e.message ?: "unknown")
        }
    }

    override suspend fun sign(
        keyId: KeyId,
        data: ByteArray,
        algorithm: Algorithm?
    ): SignResult {
        return try {
            SignResult.Success(signature = signWithKms(keyId, data))
        } catch (e: Exception) {
            SignResult.Failure.Error(keyId, e.message ?: "unknown")
        }
    }

    override suspend fun getPublicKey(keyId: KeyId): GetPublicKeyResult {
        val handle = getPublicKeyFromKms(keyId)
            ?: return GetPublicKeyResult.Failure.KeyNotFound(keyId)
        return GetPublicKeyResult.Success(handle)
    }

    override suspend fun deleteKey(keyId: KeyId): DeleteKeyResult {
        return if (deleteKeyFromKms(keyId)) DeleteKeyResult.Deleted else DeleteKeyResult.NotFound
    }

    private fun generateKeyInKms(algorithm: Algorithm): KeyHandle { TODO() }
    private fun signWithKms(keyId: KeyId, data: ByteArray): ByteArray { TODO() }
    private fun getPublicKeyFromKms(keyId: KeyId): KeyHandle? { TODO() }
    private fun deleteKeyFromKms(keyId: KeyId): Boolean { TODO() }
}
```

### Creating KMS Provider

```kotlin
import org.trustweave.kms.spi.KeyManagementServiceProvider
import org.trustweave.kms.*

class MyKmsProvider : KeyManagementServiceProvider {
    override val name: String = "my-kms"
    override val supportedAlgorithms: Set<Algorithm> = setOf(
        Algorithm.Ed25519,
        Algorithm.Secp256k1,
        Algorithm.P256
    )

    override fun create(options: Map<String, Any?>): KeyManagementService {
        val config = MyKmsConfig.fromOptions(options)
        return MyKeyManagementService(config)
    }
}
```

### Registering Provider

Create service file at `src/main/resources/META-INF/services/org.trustweave.kms.spi.KeyManagementServiceProvider`:

```
com.example.MyKmsProvider
```

## Testing Custom Adapters

### Unit Testing

```kotlin
import kotlinx.coroutines.runBlocking
import org.trustweave.did.KeyAlgorithm
import org.trustweave.did.didCreationOptions
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import kotlin.test.Test
import kotlin.test.assertNotNull

class MyCustomDidMethodTest {
    @Test
    fun testCreateDid() = runBlocking {
        val kms = InMemoryKeyManagementService()
        val config = MyDidConfig.default()
        val method = MyCustomDidMethod(kms, config)

        val document = method.createDid(didCreationOptions {
            algorithm = KeyAlgorithm.ED25519
        })

        assertNotNull(document)
        assert(document.id.value.startsWith("did:mydid:"))
    }
}
```

### Integration Testing

```kotlin
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class MyBlockchainAdapterIntegrationTest {
    @Test
    fun testAnchorAndRead() = runBlocking {
        val config = MyBlockchainConfig.testnet()
        val client = MyBlockchainAnchorClient("myblockchain:testnet", config)

        val payload = JsonPrimitive("Hello, TrustWeave!")
        val result = client.writePayload(payload, "application/json")

        val readBack = client.readPayload(result.ref)
        assertEquals(payload, readBack.payload)
    }
}
```

## Best Practices

### Error Handling

Use TrustWeave's error types:

```kotlin
import org.trustweave.did.DidCreationOptions
import org.trustweave.did.exception.DidException
import org.trustweave.did.model.DidDocument

suspend fun createDid(options: DidCreationOptions): Result<DidDocument> {
    return try {
        val document: DidDocument = TODO("implementation")
        Result.success(document)
    } catch (e: Exception) {
        Result.failure(
            DidException.DidCreationFailed(
                did = null,
                reason = e.message ?: "Unknown error",
                cause = e
            )
        )
    }
}
```

### Configuration Validation

Validate configuration early:

```kotlin
class MyDidConfig private constructor(
    val endpoint: String,
    val apiKey: String?
) {
    companion object {
        fun fromOptions(options: DidCreationOptions): MyDidConfig {
            val endpoint = options.additionalProperties["endpoint"] as? String
                ?: throw IllegalArgumentException("endpoint required")

            val apiKey = options.additionalProperties["apiKey"] as? String

            return MyDidConfig(endpoint, apiKey)
        }
    }
}
```

### Lifecycle Management

Implement `PluginLifecycle` if needed:

```kotlin
import org.trustweave.core.plugin.PluginLifecycle

class MyLifecycleAwareAnchorClient(
    val chainId: String,
    private val config: MyBlockchainConfig
) : BlockchainAnchorClient, PluginLifecycle {

    override suspend fun initialize(config: Map<String, Any?>): Boolean {
        // Initialize connections
        return true
    }

    override suspend fun start(): Boolean {
        // Start background processes
        return true
    }

    override suspend fun stop(): Boolean {
        // Stop background processes
        return true
    }

    override suspend fun cleanup() {
        // Clean up resources (return type is Unit, do not throw)
    }

    // ... implement BlockchainAnchorClient methods ...
}
```

## Next Steps

- Review [Creating Plugins](../../contributing/creating-plugins.md) for detailed plugin implementation guide
- See [SPI](spi.md) for service provider interface details
- Check [Plugin Lifecycle](plugin-lifecycle.md) for lifecycle management
- Explore existing adapters in `chains/plugins/algorand`, `did/plugins/key`, etc. for examples

## References

- Creating Plugins](../contributing/creating-plugins.md)
- Service Provider Interface](spi.md)
- Plugin Lifecycle](plugin-lifecycle.md)
- Service Provider Interface](spi.md)

