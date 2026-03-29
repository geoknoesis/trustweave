---
title: Creating Custom Adapters
nav_exclude: true
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

    private suspend fun resolveFromBackend(did: Did): DidDocument? = null // your store
    private suspend fun persistToBackend(did: Did, document: DidDocument) { /* … */ }
    private suspend fun deactivateInBackend(did: Did): Boolean = false
}
```

### Creating DID Method Provider

```kotlin
import org.trustweave.did.spi.DidMethodProvider
import org.trustweave.did.*

class MyDidMethodProvider : DidMethodProvider {
    override val name: String = "my-did-method"
    override val supportedMethods: List<String> = listOf("mydid")

    override fun create(
        method: String,
        options: DidCreationOptions
    ): DidMethod? {
        if (method != "mydid") return null

        // Create KMS
        val kms = createKms(options)

        // Create config from options
        val config = MyDidConfig.fromOptions(options)

        return MyCustomDidMethod(kms, config)
    }

    private fun createKms(options: DidCreationOptions): KeyManagementService {
        // Create or get KMS instance
        return /* implementation */
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

class MyBlockchainAnchorClient(
    override val chainId: String,
    private val config: MyBlockchainConfig
) : BlockchainAnchorClient {

    override suspend fun writePayload(payload: ByteArray): AnchorResult {
        // Submit transaction to blockchain
        val txHash = submitTransaction(payload)

        // Wait for confirmation
        val blockHeight = waitForConfirmation(txHash)

        return AnchorResult(
            anchorRef = AnchorRef(
                chainId = chainId,
                transactionHash = txHash,
                metadata = mapOf(
                    "blockHeight" to blockHeight,
                    "timestamp" to System.currentTimeMillis()
                )
            ),
            payload = payload
        )
    }

    override suspend fun readPayload(anchorRef: AnchorRef): ByteArray? {
        // Read transaction from blockchain
        return readTransaction(anchorRef.transactionHash)
    }

    private suspend fun submitTransaction(payload: ByteArray): String {
        // Submit to blockchain
        return /* implementation */
    }

    private suspend fun waitForConfirmation(txHash: String): Long {
        // Wait for confirmation
        return /* implementation */
    }

    private suspend fun readTransaction(txHash: String): ByteArray? {
        // Read from blockchain
        return /* implementation */
    }
}
```

### Creating Blockchain Adapter Provider

```kotlin
import org.trustweave.anchor.spi.BlockchainAnchorClientProvider
import org.trustweave.anchor.*

class MyBlockchainAdapterProvider : BlockchainAnchorClientProvider {
    override val name: String = "my-blockchain"

    override fun supportsChain(chainId: String): Boolean {
        return chainId.startsWith("myblockchain:")
    }

    override fun create(
        chainId: String,
        options: Map<String, Any?>
    ): BlockchainAnchorClient {
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
import org.trustweave.kms.*

class MyKeyManagementService(
    private val config: MyKmsConfig
) : KeyManagementService {

    override suspend fun generateKey(algorithm: Algorithm): Key {
        // Generate key in your KMS
        val keyId = generateKeyInKms(algorithm)
        val publicKey = getPublicKey(keyId)

        return Key(
            id = keyId,
            algorithm = algorithm,
            publicKey = publicKey
        )
    }

    override suspend fun sign(keyId: String, data: ByteArray): ByteArray {
        // Sign data using your KMS
        return signWithKms(keyId, data)
    }

    override suspend fun getPublicKey(keyId: String): PublicKey {
        // Get public key from your KMS
        return getPublicKeyFromKms(keyId)
    }

    override suspend fun deleteKey(keyId: String): Boolean {
        // Delete key from your KMS
        return deleteKeyFromKms(keyId)
    }

    private suspend fun generateKeyInKms(algorithm: Algorithm): String {
        // Generate in your KMS
        return /* implementation */
    }

    private suspend fun signWithKms(keyId: String, data: ByteArray): ByteArray {
        // Sign with your KMS
        return /* implementation */
    }

    private suspend fun getPublicKeyFromKms(keyId: String): PublicKey {
        // Get from your KMS
        return /* implementation */
    }

    private suspend fun deleteKeyFromKms(keyId: String): Boolean {
        // Delete from your KMS
        return /* implementation */
    }
}
```

### Creating KMS Provider

```kotlin
import org.trustweave.kms.spi.KeyManagementServiceProvider
import org.trustweave.kms.*

class MyKmsProvider : KeyManagementServiceProvider {
    override val name: String = "my-kms"
    override val supportedAlgorithms: List<Algorithm> = listOf(
        Algorithm.Ed25519,
        Algorithm.Secp256k1,
        Algorithm.P256
    )

    override fun create(options: Map<String, Any?>): KeyManagementService? {
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
import org.trustweave.testkit.*
import kotlin.test.Test
import kotlin.test.assertNotNull

class MyCustomDidMethodTest {
    @Test
    fun testCreateDid() = runBlocking {
        val kms = InMemoryKeyManagementService()
        val config = MyDidConfig.default()
        val method = MyCustomDidMethod(kms, config)

        val did = method.createDid(didCreationOptions {
            algorithm = KeyAlgorithm.Ed25519
        })

        assertNotNull(did)
        assert(did.id.startsWith("did:mydid:"))
    }
}
```

### Integration Testing

```kotlin
import org.trustweave.testkit.*
import kotlin.test.Test

class MyBlockchainAdapterIntegrationTest {
    @Test
    fun testAnchorAndRead() = runBlocking {
        val config = MyBlockchainConfig.testnet()
        val client = MyBlockchainAnchorClient("myblockchain:testnet", config)

        val payload = "Hello, TrustWeave!".toByteArray()
        val result = client.writePayload(payload)

        val readData = client.readPayload(result.anchorRef)
        assert(readData.contentEquals(payload))
    }
}
```

## Best Practices

### Error Handling

Use TrustWeave's error types:

```kotlin
import org.trustweave.core.exception.TrustWeaveException
import kotlin.Result

suspend fun createDid(options: DidCreationOptions): Result<DidDocument> {
    return try {
        val document: DidDocument = /* implementation */
        Result.success(document)
    } catch (e: Exception) {
        Result.failure(
            DidException.DidCreationFailed(
                reason = e.message ?: "Unknown error"
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

class MyBlockchainAnchorClient(
    override val chainId: String,
    private val config: MyBlockchainConfig
) : BlockchainAnchorClient, PluginLifecycle {

    override suspend fun initialize(options: Map<String, Any?>): Boolean {
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

    override suspend fun cleanup(): Boolean {
        // Clean up resources
        return true
    }
}
```

## Next Steps

- Review [Creating Plugins](../contributing/creating-plugins.md) for detailed plugin implementation guide
- See [SPI](spi.md) for service provider interface details
- Check [Plugin Lifecycle](plugin-lifecycle.md) for lifecycle management
- Explore existing adapters in `chains/plugins/algorand`, `did/plugins/key`, etc. for examples

## References

- Creating Plugins](../contributing/creating-plugins.md)
- Service Provider Interface](spi.md)
- Plugin Lifecycle](plugin-lifecycle.md)
- Service Provider Interface](spi.md)

