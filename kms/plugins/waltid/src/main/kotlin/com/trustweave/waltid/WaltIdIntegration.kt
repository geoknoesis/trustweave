package com.trustweave.waltid

import com.trustweave.did.DidCreationOptions
import com.trustweave.did.registry.DidMethodRegistry
import com.trustweave.did.spi.DidMethodProvider
import com.trustweave.kms.KeyManagementService
import com.trustweave.kms.spi.KeyManagementServiceProvider
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
        options: DidCreationOptions = DidCreationOptions()
    ): WaltIdIntegrationResult {
        // Create KMS using factory API
        val kms = try {
            com.trustweave.kms.KeyManagementServices.create("waltid", options.additionalProperties)
        } catch (e: IllegalArgumentException) {
            throw IllegalStateException("walt.id KMS provider not found. Ensure TrustWeave-waltid is on classpath.", e)
        }

        // Discover DID method providers
        val didProviders = ServiceLoader.load(DidMethodProvider::class.java)
        val waltIdDidProvider = didProviders.find { it.name == "waltid" }
            ?: throw IllegalStateException("walt.id DID method provider not found. Ensure TrustWeave-waltid is on classpath.")

        // Register supported DID methods
        val registeredMethods = mutableListOf<String>()
        for (methodName in waltIdDidProvider.supportedMethods) {
            val methodOptions = options.copy(
                additionalProperties = options.additionalProperties + ("kms" to kms)
            )
            val method = waltIdDidProvider.create(methodName, methodOptions)
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
        options: DidCreationOptions = DidCreationOptions()
    ): WaltIdIntegrationResult {
        // Discover DID method provider
        val didProviders = ServiceLoader.load(DidMethodProvider::class.java)
        val waltIdDidProvider = didProviders.find { it.name == "waltid" }
            ?: throw IllegalStateException("walt.id DID method provider not found. Ensure TrustWeave-waltid is on classpath.")

        // Register requested DID methods
        val registeredMethods = mutableListOf<String>()
        for (methodName in didMethods) {
            if (methodName in waltIdDidProvider.supportedMethods) {
                val methodOptions = options.copy(
                    additionalProperties = options.additionalProperties + ("kms" to kms)
                )
                val method = waltIdDidProvider.create(methodName, methodOptions)
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

