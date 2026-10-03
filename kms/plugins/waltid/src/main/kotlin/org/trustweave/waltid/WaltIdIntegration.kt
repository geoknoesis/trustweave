package org.trustweave.waltid

import org.trustweave.did.DidCreationOptions
import org.trustweave.did.registry.DidMethodRegistry
import org.trustweave.did.spi.DidMethodProvider
import org.trustweave.kms.KeyManagementService
import java.util.ServiceLoader

/**
 * Integration helper for walt.id adapters.
 *
 * Creates the walt.id KMS and registers DID methods backed by it. The DID methods come from the
 * real method plugins discovered via SPI (e.g. `did:plugins:key`, `did:plugins:web`); a method
 * whose plugin is not on the classpath is simply not registered, and is absent from
 * [WaltIdIntegrationResult.registeredDidMethods].
 */
object WaltIdIntegration {
    private val DEFAULT_METHODS = listOf("key", "web")

    /**
     * Registers, for each requested method, the first SPI provider that supports it — excluding
     * the deprecated placeholder provider named "waltid" — wired to [kms].
     */
    private fun registerMethods(
        kms: KeyManagementService,
        registry: DidMethodRegistry,
        didMethods: List<String>,
        options: DidCreationOptions,
    ): List<String> {
        val providers = ServiceLoader.load(DidMethodProvider::class.java).filter { it.name != "waltid" }
        val methodOptions = options.copy(additionalProperties = options.additionalProperties + ("kms" to kms))
        return didMethods.filter { methodName ->
            val method =
                providers
                    .firstOrNull { methodName in it.supportedMethods }
                    ?.create(methodName, methodOptions)
            if (method != null) registry.register(method)
            method != null
        }
    }

    /**
     * Discovers and registers all walt.id adapters via SPI.
     *
     * @param options Configuration options
     * @return A WaltIdIntegrationResult containing registered services
     */
    fun discoverAndRegister(
        registry: DidMethodRegistry,
        options: DidCreationOptions = DidCreationOptions(),
    ): WaltIdIntegrationResult {
        // Create KMS using factory API
        val kms =
            try {
                org.trustweave.kms.KeyManagementServices
                    .create("waltid", options.additionalProperties)
            } catch (e: IllegalArgumentException) {
                throw IllegalStateException("walt.id KMS provider not found. Ensure TrustWeave-waltid is on classpath.", e)
            }

        val registeredMethods = registerMethods(kms, registry, DEFAULT_METHODS, options)

        return WaltIdIntegrationResult(
            kms = kms,
            registry = registry,
            registeredDidMethods = registeredMethods,
        )
    }

    /**
     * Manually setup walt.id integration with a provided KMS.
     *
     * @param kms The KeyManagementService to use (can be walt.id or any compatible implementation)
     * @param didMethods DID method names to register (defaults to ["key", "web"]); each needs its plugin on the classpath
     * @param options Configuration options
     * @return A WaltIdIntegrationResult
     */
    fun setup(
        kms: KeyManagementService,
        registry: DidMethodRegistry,
        didMethods: List<String> = listOf("key", "web"),
        options: DidCreationOptions = DidCreationOptions(),
    ): WaltIdIntegrationResult {
        val registeredMethods = registerMethods(kms, registry, didMethods, options)

        return WaltIdIntegrationResult(
            kms = kms,
            registry = registry,
            registeredDidMethods = registeredMethods,
        )
    }
}

/**
 * Result of walt.id integration setup.
 */
data class WaltIdIntegrationResult(
    val kms: KeyManagementService,
    val registry: DidMethodRegistry,
    val registeredDidMethods: List<String>,
)
