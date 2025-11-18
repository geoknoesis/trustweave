# Creating VeriCore Plugins

This guide explains how to create custom plugins for VeriCore by implementing the various plugin interfaces.

## Overview

VeriCore is designed with a plugin architecture that allows you to extend functionality by implementing specific interfaces. Plugins can be registered manually or discovered automatically via the Service Provider Interface (SPI).

## Plugin Types

VeriCore supports the following plugin interfaces:

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
    implementation("com.geoknoesis.vericore:vericore-did:1.0.0-SNAPSHOT")
    implementation("com.geoknoesis.vericore:vericore-anchor:1.0.0-SNAPSHOT")
    implementation("com.geoknoesis.vericore:vericore-kms:1.0.0-SNAPSHOT")
    implementation("com.geoknoesis.vericore:vericore-core:1.0.0-SNAPSHOT")
    
    // SPI support (optional, for auto-discovery)
    implementation("com.geoknoesis.vericore:vericore-spi:1.0.0-SNAPSHOT")
}
```

## 1. Implementing a DID Method

The `DidMethod` interface allows you to implement custom DID methods.

### Interface Definition

```kotlin
interface DidMethod {
    val method: String  // e.g., "web", "key", "ion"
    
    suspend fun createDid(options: DidCreationOptions): DidDocument
    suspend fun resolveDid(did: String): DidResolutionResult
    suspend fun updateDid(did: String, updater: (DidDocument) -> DidDocument): DidDocument
    suspend fun deactivateDid(did: String): Boolean
}
```

### Example Implementation

```kotlin
package com.example.vericore.plugins

import com.geoknoesis.vericore.did.*
import com.geoknoesis.vericore.kms.KeyManagementService
import java.time.Instant
import java.util.UUID

/**
 * Example implementation of a custom DID method.
 * This creates simple did:example DIDs stored in memory.
 */
class ExampleDidMethod(
    private val kms: KeyManagementService
) : DidMethod {
    
    override val method = "example"
    
    // In-memory storage (use a database in production)
    private val documents = mutableMapOf<String, DidDocument>()
    
    override suspend fun createDid(options: DidCreationOptions): DidDocument {
        // Generate a key using the KMS
        val algorithm = options.algorithm.algorithmName
        val keyHandle = kms.generateKey(algorithm, options.additionalProperties)
        
        // Create DID identifier
        val didId = UUID.randomUUID().toString().replace("-", "")
        val did = "did:$method:$didId"
        
        // Create verification method
        val verificationMethodId = "$did#${keyHandle.id}"
        val verificationMethod = VerificationMethodRef(
            id = verificationMethodId,
            type = when (algorithm.uppercase()) {
                "ED25519" -> "Ed25519VerificationKey2020"
                "SECP256K1" -> "EcdsaSecp256k1VerificationKey2019"
                else -> "JsonWebKey2020"
            },
            controller = did,
            publicKeyJwk = keyHandle.publicKeyJwk,
            publicKeyMultibase = keyHandle.publicKeyMultibase
        )
        
        // Build DID Document
        val document = DidDocument(
            id = did,
            verificationMethod = listOf(verificationMethod),
            authentication = listOf(verificationMethodId),
            assertionMethod = if (options.purposes.contains(KeyPurpose.ASSERTION)) {
                listOf(verificationMethodId)
            } else emptyList()
        )
        
        // Store document
        documents[did] = document
        return document
    }
    
    override suspend fun resolveDid(did: String): DidResolutionResult {
        // Validate DID format
        if (!did.startsWith("did:$method:")) {
            return DidResolutionResult(
                document = null,
                resolutionMetadata = mapOf("error" to "invalidDid")
            )
        }
        
        val document = documents[did]
        val now = Instant.now()
        
        return if (document != null) {
            DidResolutionResult(
                document = document,
                documentMetadata = DidDocumentMetadata(
                    created = now,
                    updated = now
                ),
                resolutionMetadata = emptyMap()
            )
        } else {
            DidResolutionResult(
                document = null,
                resolutionMetadata = mapOf("error" to "notFound")
            )
        }
    }
    
    override suspend fun updateDid(
        did: String,
        updater: (DidDocument) -> DidDocument
    ): DidDocument {
        val current = documents[did]
            ?: throw IllegalArgumentException("DID not found: $did")
        
        val updated = updater(current)
        documents[did] = updated
        return updated
    }
    
    override suspend fun deactivateDid(did: String): Boolean {
        return documents.remove(did) != null
    }
}
```

### Registration

**Manual Registration:**
```kotlin
val vericore = VeriCore.create {
    kms = InMemoryKeyManagementService()
    
    didMethods {
        + ExampleDidMethod(kms!!)
    }
}
```

**Or via VeriCore instance:**
```kotlin
val vericore = VeriCore.create()
vericore.registerDidMethod(ExampleDidMethod(kms))
```

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
package com.example.vericore.plugins

import com.geoknoesis.vericore.anchor.*
import com.geoknoesis.vericore.core.NotFoundException
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
import com.geoknoesis.vericore.anchor.AbstractBlockchainAnchorClient

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

```kotlin
val vericore = VeriCore.create {
    blockchain {
        "example:mainnet" to ExampleBlockchainAnchorClient("example:mainnet")
    }
}

// Or after creation
vericore.registerBlockchainClient(
    "example:mainnet",
    ExampleBlockchainAnchorClient("example:mainnet")
)
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
package com.example.vericore.plugins

import com.geoknoesis.vericore.credential.models.*
import com.geoknoesis.vericore.credential.proof.*
import com.geoknoesis.vericore.core.normalizeKeyId

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

```kotlin
val vericore = VeriCore.create {
    proofGenerators {
        + ExampleProofGenerator { data, keyId ->
            kms.sign(keyId, data)
        }
    }
}
```

## 4. Implementing a Key Management Service

The `KeyManagementService` interface allows you to integrate with different key management backends.

### Interface Definition

```kotlin
interface KeyManagementService {
    suspend fun generateKey(
        algorithm: String,
        options: Map<String, Any?> = emptyMap()
    ): KeyHandle
    
    suspend fun getPublicKey(keyId: String): KeyHandle
    
    suspend fun sign(
        keyId: String,
        data: ByteArray,
        algorithm: String? = null
    ): ByteArray
    
    suspend fun deleteKey(keyId: String): Boolean
}
```

### Example Implementation

```kotlin
package com.example.vericore.plugins

import com.geoknoesis.vericore.kms.*
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
        
        val keyId = "key_${System.currentTimeMillis()}"
        keys[keyId] = keyPair
        
        // Convert to JWK format (simplified)
        val publicKeyJwk = mapOf(
            "kty" to "EC",
            "crv" to algorithm,
            "x" to keyPair.public.encoded.toString(Charsets.UTF_8)
        )
        
        return KeyHandle(
            id = keyId,
            algorithm = algorithm,
            publicKeyJwk = publicKeyJwk
        )
    }
    
    override suspend fun getPublicKey(keyId: String): KeyHandle {
        val keyPair = keys[keyId]
            ?: throw KeyNotFoundException("Key not found: $keyId")
        
        return KeyHandle(
            id = keyId,
            algorithm = "Ed25519", // Determine from keyPair
            publicKeyJwk = mapOf("kty" to "EC") // Convert properly
        )
    }
    
    override suspend fun sign(
        keyId: String,
        data: ByteArray,
        algorithm: String?
    ): ByteArray {
        val keyPair = keys[keyId]
            ?: throw KeyNotFoundException("Key not found: $keyId")
        
        val signer = Signature.getInstance("Ed25519")
        signer.initSign(keyPair.private)
        signer.update(data)
        return signer.sign()
    }
    
    override suspend fun deleteKey(keyId: String): Boolean {
        return keys.remove(keyId) != null
    }
}
```

### Registration

```kotlin
val vericore = VeriCore.create {
    kms = ExampleKeyManagementService()
}
```

## 5. Implementing a Credential Service

The `CredentialService` interface allows you to add credential issuance/verification providers.

### Interface Definition

```kotlin
interface CredentialService {
    val providerName: String
    val supportedProofTypes: List<String>
    val supportedSchemaFormats: List<SchemaFormat>
    
    suspend fun issueCredential(
        credential: VerifiableCredential,
        options: CredentialIssuanceOptions
    ): VerifiableCredential
    
    suspend fun verifyCredential(
        credential: VerifiableCredential,
        options: CredentialVerificationOptions
    ): CredentialVerificationResult
    
    suspend fun createPresentation(
        credentials: List<VerifiableCredential>,
        options: PresentationOptions
    ): VerifiablePresentation
    
    suspend fun verifyPresentation(
        presentation: VerifiablePresentation,
        options: PresentationVerificationOptions
    ): PresentationVerificationResult
}
```

### Example Implementation

```kotlin
package com.example.vericore.plugins

import com.geoknoesis.vericore.credential.*
import com.geoknoesis.vericore.credential.models.*
import com.geoknoesis.vericore.credential.proof.*
import com.geoknoesis.vericore.spi.SchemaFormat

/**
 * Example credential service implementation.
 */
class ExampleCredentialService(
    private val proofGenerator: ProofGenerator
) : CredentialService {
    
    override val providerName = "example"
    override val supportedProofTypes = listOf("Ed25519Signature2020")
    override val supportedSchemaFormats = listOf(SchemaFormat.JSON_SCHEMA)
    
    override suspend fun issueCredential(
        credential: VerifiableCredential,
        options: CredentialIssuanceOptions
    ): VerifiableCredential {
        // Generate proof
        val proof = proofGenerator.generateProof(
            credential = credential,
            keyId = options.keyId ?: throw IllegalArgumentException("keyId required"),
            options = ProofOptions(
                proofPurpose = "assertionMethod",
                challenge = options.challenge,
                domain = options.domain
            )
        )
        
        // Attach proof to credential
        return credential.copy(proof = proof)
    }
    
    override suspend fun verifyCredential(
        credential: VerifiableCredential,
        options: CredentialVerificationOptions
    ): CredentialVerificationResult {
        val errors = mutableListOf<String>()
        var proofValid = false
        var notExpired = true
        var notRevoked = true
        
        // Verify proof
        if (credential.proof == null) {
            errors.add("Credential missing proof")
        } else {
            // Implement proof verification
            proofValid = true // Simplified
        }
        
        // Check expiration
        if (options.checkExpiration && credential.expirationDate != null) {
            val expiration = java.time.Instant.parse(credential.expirationDate)
            notExpired = expiration.isAfter(java.time.Instant.now())
            if (!notExpired) {
                errors.add("Credential expired")
            }
        }
        
        // Check revocation (simplified)
        if (options.checkRevocation) {
            // Implement revocation check
            notRevoked = true
        }
        
        return CredentialVerificationResult(
            valid = errors.isEmpty() && proofValid && notExpired && notRevoked,
            errors = errors,
            proofValid = proofValid,
            notExpired = notExpired,
            notRevoked = notRevoked
        )
    }
    
    override suspend fun createPresentation(
        credentials: List<VerifiableCredential>,
        options: PresentationOptions
    ): VerifiablePresentation {
        // Create presentation
        val presentation = VerifiablePresentation(
            id = "urn:uuid:${java.util.UUID.randomUUID()}",
            type = listOf("VerifiablePresentation"),
            holder = options.holderDid,
            verifiableCredential = credentials
        )
        
        // Generate proof
        val proof = proofGenerator.generateProof(
            credential = VerifiableCredential(
                id = presentation.id,
                type = listOf("VerifiablePresentation"),
                issuer = options.holderDid,
                credentialSubject = kotlinx.serialization.json.buildJsonObject {
                    // Presentation-specific subject
                },
                issuanceDate = java.time.Instant.now().toString()
            ),
            keyId = options.keyId ?: throw IllegalArgumentException("keyId required"),
            options = ProofOptions(
                proofPurpose = "authentication",
                challenge = options.challenge,
                domain = options.domain
            )
        )
        
        return presentation.copy(proof = proof)
    }
    
    override suspend fun verifyPresentation(
        presentation: VerifiablePresentation,
        options: PresentationVerificationOptions
    ): PresentationVerificationResult {
        // Implement presentation verification
        return PresentationVerificationResult(
            valid = true,
            presentationProofValid = true,
            challengeValid = true
        )
    }
}
```

### Registration

```kotlin
val vericore = VeriCore.create {
    credentialServices {
        + ExampleCredentialService(proofGenerator)
    }
}
```

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
package com.example.vericore.plugins

import com.geoknoesis.vericore.spi.services.*
import com.geoknoesis.vericore.credential.wallet.Wallet

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
    ): Any {
        return when (providerName) {
            "inMemory" -> {
                // Create in-memory wallet
                InMemoryWallet(
                    walletId = walletId ?: generateWalletId(),
                    holderDid = holderDid ?: throw IllegalArgumentException("holderDid required")
                )
            }
            "database" -> {
                // Create database-backed wallet
                DatabaseWallet(
                    walletId = walletId ?: generateWalletId(),
                    holderDid = holderDid ?: throw IllegalArgumentException("holderDid required"),
                    connectionString = options.storagePath
                        ?: throw IllegalArgumentException("storagePath required for database wallet")
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
val vericore = VeriCore.create {
    walletFactory = ExampleWalletFactory()
}
```

## Plugin Registration Methods

### Manual Registration

Register plugins directly when creating VeriCore:

```kotlin
val vericore = VeriCore.create {
    kms = MyKms()
    walletFactory = MyWalletFactory()
    
    didMethods {
        + MyDidMethod(kms!!)
    }
    
    blockchain {
        "myChain:mainnet" to MyBlockchainClient("myChain:mainnet")
    }
    
    proofGenerators {
        + MyProofGenerator(signer)
    }
    
    credentialServices {
        + MyCredentialService()
    }
}
```

### Runtime Registration

Register plugins after VeriCore creation:

```kotlin
val vericore = VeriCore.create()

vericore.registerDidMethod(MyDidMethod(kms))
vericore.registerBlockchainClient("myChain:mainnet", MyBlockchainClient("myChain:mainnet"))
```

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

2. Create service file: `META-INF/services/com.geoknoesis.vericore.did.DidMethodProvider`
```
com.example.MyDidMethodProvider
```

## Plugin Lifecycle

Plugins can optionally implement `PluginLifecycle` for initialization and cleanup:

```kotlin
import com.geoknoesis.vericore.spi.PluginLifecycle

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

VeriCore automatically manages lifecycle for all registered plugins:

```kotlin
val vericore = VeriCore.create { ... }

// Initialize all plugins
vericore.initialize().getOrThrow()

// Start all plugins
vericore.start().getOrThrow()

// ... use vericore ...

// Stop all plugins
vericore.stop().getOrThrow()

// Cleanup
vericore.cleanup().getOrThrow()
```

See [Plugin Lifecycle](../advanced/plugin-lifecycle.md) for more details.

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
fun `test plugin with VeriCore`() = runTest {
    val vericore = VeriCore.create {
        kms = InMemoryKeyManagementService()
        didMethods {
            + ExampleDidMethod(kms!!)
        }
    }
    
    val did = vericore.createDid("example").getOrThrow()
    assertTrue(did.id.startsWith("did:example:"))
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

- Review existing implementations in `vericore-testkit` for reference
- See [Integration Modules](../integrations/README.md) for production examples
- Check [Plugin Lifecycle](../advanced/plugin-lifecycle.md) for lifecycle management
- Review [Architecture Overview](../introduction/architecture-overview.md) for design patterns

## Related Documentation

- [Plugin Lifecycle Management](../advanced/plugin-lifecycle.md)
- [Integration Modules](../integrations/README.md)
- [Architecture Overview](../introduction/architecture-overview.md)
- [Core API Reference](../api-reference/core-api.md)
- [Credential Service API](../api-reference/credential-service-api.md)

