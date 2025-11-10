package io.geoknoesis.vericore.waltid

import io.geoknoesis.vericore.did.DidMethodRegistry
import io.geoknoesis.vericore.did.spi.DidMethodProvider
import io.geoknoesis.vericore.kms.KeyManagementService
import io.geoknoesis.vericore.kms.spi.KeyManagementServiceProvider
import java.util.ServiceLoader

/**
 * Integration helper for walt.id adapters.
 * Provides SPI-based discovery and registration of walt.id implementations.
 */
object WaltIdIntegration {

    /**
     * Discovers and registers all walt.id adapters via SPI.
     *
     * @param options Configuration options
     * @return A WaltIdIntegrationResult containing registered services
     */
    fun discoverAndRegister(
        registry: DidMethodRegistry,
        options: Map<String, Any?> = emptyMap()
    ): WaltIdIntegrationResult {
        // Discover KMS providers
        val kmsProviders = ServiceLoader.load(KeyManagementServiceProvider::class.java)
        val waltIdKmsProvider = kmsProviders.find { it.name == "waltid" }
            ?: throw IllegalStateException("walt.id KMS provider not found. Ensure vericore-waltid is on classpath.")

        val kms = waltIdKmsProvider.create(options)

        // Discover DID method providers
        val didProviders = ServiceLoader.load(DidMethodProvider::class.java)
        val waltIdDidProvider = didProviders.find { it.name == "waltid" }
            ?: throw IllegalStateException("walt.id DID method provider not found. Ensure vericore-waltid is on classpath.")

        // Register supported DID methods
        val registeredMethods = mutableListOf<String>()
        for (methodName in waltIdDidProvider.supportedMethods) {
            val method = waltIdDidProvider.create(methodName, options + mapOf("kms" to kms))
            if (method != null) {
                registry.register(method)
                registeredMethods.add(methodName)
            }
        }

        return WaltIdIntegrationResult(
            kms = kms,
            registry = registry,
            registeredDidMethods = registeredMethods
        )
    }

    /**
     * Manually setup walt.id integration with a provided KMS.
     *
     * @param kms The KeyManagementService to use (can be walt.id or any compatible implementation)
     * @param didMethods List of DID method names to register (defaults to ["key", "web"])
     * @param options Configuration options
     * @return A WaltIdIntegrationResult
     */
    fun setup(
        kms: KeyManagementService,
        registry: DidMethodRegistry,
        didMethods: List<String> = listOf("key", "web"),
        options: Map<String, Any?> = emptyMap()
    ): WaltIdIntegrationResult {
        // Discover DID method provider
        val didProviders = ServiceLoader.load(DidMethodProvider::class.java)
        val waltIdDidProvider = didProviders.find { it.name == "waltid" }
            ?: throw IllegalStateException("walt.id DID method provider not found. Ensure vericore-waltid is on classpath.")

        // Register requested DID methods
        val registeredMethods = mutableListOf<String>()
        for (methodName in didMethods) {
            if (methodName in waltIdDidProvider.supportedMethods) {
                val method = waltIdDidProvider.create(methodName, options + mapOf("kms" to kms))
                if (method != null) {
                    registry.register(method)
                    registeredMethods.add(methodName)
                }
            }
        }

        return WaltIdIntegrationResult(
            kms = kms,
            registry = registry,
            registeredDidMethods = registeredMethods
        )
    }
}

/**
 * Result of walt.id integration setup.
 */
data class WaltIdIntegrationResult(
    val kms: KeyManagementService,
    val registry: DidMethodRegistry,
    val registeredDidMethods: List<String>
)

