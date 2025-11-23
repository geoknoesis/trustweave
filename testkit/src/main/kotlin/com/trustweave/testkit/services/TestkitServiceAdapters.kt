package com.trustweave.testkit.services

import com.trustweave.kms.services.KmsFactory
import com.trustweave.did.services.DidMethodFactory
import com.trustweave.anchor.services.BlockchainAnchorClientFactory
import com.trustweave.trust.services.TrustRegistryFactory
import com.trustweave.revocation.services.StatusListManagerFactory
import com.trustweave.kms.KeyManagementService
import com.trustweave.kms.spi.KeyManagementServiceProvider
import com.trustweave.did.DidMethod
import com.trustweave.did.DidCreationOptions
import com.trustweave.did.spi.DidMethodProvider
import com.trustweave.testkit.kms.InMemoryKeyManagementService
import com.trustweave.testkit.did.DidKeyMockMethod
import com.trustweave.testkit.trust.InMemoryTrustRegistry
import com.trustweave.testkit.anchor.InMemoryBlockchainAnchorClient
import com.trustweave.credential.revocation.InMemoryStatusListManager
import com.trustweave.anchor.BlockchainAnchorClient
import com.trustweave.anchor.spi.BlockchainAnchorClientProvider
import java.util.ServiceLoader

/**
 * Factory implementations for testkit services.
 * 
 * Provides factory methods for creating test implementations without reflection.
 * Also handles SPI discovery for providers that may be available.
 */

/**
 * KMS Factory implementation for testkit.
 * Handles both testkit implementations and SPI-based providers.
 */
class TestkitKmsFactory : KmsFactory {
    override suspend fun createInMemory(): Pair<Any, (suspend (ByteArray, String) -> ByteArray)?> {
        val kms = InMemoryKeyManagementService()
        val kmsRef = kms
        val signerFn: suspend (ByteArray, String) -> ByteArray = { data, keyId ->
            kmsRef.sign(keyId, data)
        }
        return Pair(kms as Any, signerFn)
    }
    
    override suspend fun createFromProvider(
        providerName: String,
        algorithm: String
    ): Pair<Any, (suspend (ByteArray, String) -> ByteArray)?> {
        if (providerName == "inMemory") {
            return createInMemory()
        }
        
        // Try SPI discovery
        try {
            val providers = ServiceLoader.load(KeyManagementServiceProvider::class.java)
            val provider = providers.find { it.name == providerName }
            if (provider != null) {
                val kms = provider.create(mapOf("algorithm" to algorithm))
                // For SPI providers, we can't easily create a signer without knowing the implementation
                // Return null signer - caller must provide one
                return Pair(kms as Any, null)
            }
        } catch (e: Exception) {
            // SPI classes not available or provider not found
        }
        
        throw IllegalStateException(
            "KMS provider '$providerName' not found. " +
            "Ensure TrustWeave-$providerName is on classpath or use 'inMemory' for testing."
        )
    }
}

/**
 * DID Method Factory implementation for testkit.
 * Handles both testkit implementations and SPI-based providers.
 */
class TestkitDidMethodFactory : DidMethodFactory {
    override suspend fun create(
        methodName: String,
        config: Any,
        kms: Any
    ): Any? {
        // Try SPI discovery first
        try {
            val providers = ServiceLoader.load(DidMethodProvider::class.java)
            for (provider in providers) {
                if (methodName in provider.supportedMethods) {
                    val keyManagementService = kms as? KeyManagementService
                        ?: throw IllegalArgumentException("KMS must be KeyManagementService instance")
                    
                    // Convert config to options map
                    var creationOptions = when (config) {
                        is DidCreationOptions -> config
                        is Map<*, *> -> DidCreationOptions.fromMap(config.toStringKeyMap())
                        else -> DidCreationOptions()
                    }
                    if (!creationOptions.additionalProperties.containsKey("kms")) {
                        creationOptions = creationOptions.copy(
                            additionalProperties = creationOptions.additionalProperties + ("kms" to keyManagementService)
                        )
                    }
                    
                    val method = provider.create(methodName, creationOptions)
                    if (method != null) {
                        return method as Any
                    }
                }
            }
        } catch (e: Exception) {
            // SPI classes not available, fall through to testkit
        }
        
        // Fallback to testkit for "key" method
        if (methodName == "key") {
            val keyManagementService = kms as? KeyManagementService
                ?: throw IllegalArgumentException("KMS must be KeyManagementService instance")
            return DidKeyMockMethod(keyManagementService) as Any
        }
        
        return null // Method not found
    }
}

/**
 * StatusListManager Factory implementation.
 */
class TestkitStatusListManagerFactory : StatusListManagerFactory {
    override suspend fun create(providerName: String): Any {
        if (providerName == "inMemory") {
            return InMemoryStatusListManager() as Any
        }
        throw IllegalStateException(
            "StatusListManager provider '$providerName' not found. " +
            "Use 'inMemory' for testing."
        )
    }
}

/**
 * TrustRegistry Factory implementation.
 */
class TestkitTrustRegistryFactory : TrustRegistryFactory {
    override suspend fun create(providerName: String): Any {
        if (providerName == "inMemory") {
            return InMemoryTrustRegistry() as Any
        }
        throw IllegalStateException(
            "TrustRegistry provider '$providerName' not found. " +
            "Use 'inMemory' for testing."
        )
    }
}

/**
 * BlockchainAnchorClient Factory implementation.
 * Handles both testkit implementations and SPI-based providers.
 */
class TestkitBlockchainAnchorClientFactory : BlockchainAnchorClientFactory {
    override suspend fun create(
        chainId: String,
        providerName: String,
        config: Any
    ): Any {
        if (providerName == "inMemory") {
            val configMap = config as? Map<*, *>
            val contract = configMap?.get("contract") as? String
            return InMemoryBlockchainAnchorClient(chainId, contract) as Any
        }
        
        // Try SPI discovery
        try {
            val providers = ServiceLoader.load(BlockchainAnchorClientProvider::class.java)
            val provider = providers.find { it.name == providerName }
                if (provider != null) {
                    val configMap = (config as? Map<*, *>)?.toStringKeyMap() ?: emptyMap()
                    val client = provider.create(chainId, configMap)
                    return client as Any
            }
        } catch (e: Exception) {
            // SPI classes not available or provider not found
        }
        
        throw IllegalStateException(
            "BlockchainAnchorClient provider '$providerName' not found for chain '$chainId'. " +
            "Ensure TrustWeave-$providerName is on classpath or use 'inMemory' for testing."
        )
    }
}

private fun Map<*, *>.toStringKeyMap(): Map<String, Any?> =
    entries.mapNotNull { (key, value) ->
        (key as? String)?.let { it to value }
    }.toMap()

