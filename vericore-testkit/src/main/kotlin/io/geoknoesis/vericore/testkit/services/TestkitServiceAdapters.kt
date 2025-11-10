package io.geoknoesis.vericore.testkit.services

import io.geoknoesis.vericore.spi.services.*
import io.geoknoesis.vericore.kms.KeyManagementService
import io.geoknoesis.vericore.kms.spi.KeyManagementServiceProvider
import io.geoknoesis.vericore.did.DidMethod
import io.geoknoesis.vericore.did.spi.DidMethodProvider
import io.geoknoesis.vericore.testkit.kms.InMemoryKeyManagementService
import io.geoknoesis.vericore.testkit.did.DidKeyMockMethod
import io.geoknoesis.vericore.testkit.trust.InMemoryTrustRegistry
import io.geoknoesis.vericore.testkit.anchor.InMemoryBlockchainAnchorClient
import io.geoknoesis.vericore.credential.revocation.InMemoryStatusListManager
import io.geoknoesis.vericore.anchor.BlockchainAnchorClient
import io.geoknoesis.vericore.anchor.spi.BlockchainAnchorClientProvider
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
            "Ensure vericore-$providerName is on classpath or use 'inMemory' for testing."
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
                    val configMap = when (config) {
                        is Map<*, *> -> config as Map<String, Any?>
                        else -> emptyMap<String, Any?>()
                    }
                    
                    val method = provider.create(methodName, configMap)
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
                val configMap = config as? Map<*, *> ?: emptyMap<String, Any?>()
                val client = provider.create(chainId, configMap as Map<String, Any?>)
                return client as Any
            }
        } catch (e: Exception) {
            // SPI classes not available or provider not found
        }
        
        throw IllegalStateException(
            "BlockchainAnchorClient provider '$providerName' not found for chain '$chainId'. " +
            "Ensure vericore-$providerName is on classpath or use 'inMemory' for testing."
        )
    }
}

