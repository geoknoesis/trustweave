package io.geoknoesis.vericore.godiddy

import io.geoknoesis.vericore.did.DidCreationOptions
import io.geoknoesis.vericore.did.DidMethodRegistry
import io.geoknoesis.vericore.did.spi.DidMethodProvider
import io.geoknoesis.vericore.godiddy.issuer.GodiddyIssuer
import io.geoknoesis.vericore.godiddy.registrar.GodiddyRegistrar
import io.geoknoesis.vericore.godiddy.resolver.GodiddyResolver
import io.geoknoesis.vericore.godiddy.spi.GodiddyDidMethodProvider
import io.geoknoesis.vericore.godiddy.verifier.GodiddyVerifier
import java.util.ServiceLoader

/**
 * Integration helper for godiddy services.
 * Provides SPI-based discovery and registration of godiddy implementations.
 */
object GodiddyIntegration {

    /**
     * Discovers and registers all godiddy DID methods via SPI.
     *
     * @param options Configuration options (baseUrl, timeout, apiKey, etc.)
     * @return A GodiddyIntegrationResult containing registered services
     */
    fun discoverAndRegister(
        registry: DidMethodRegistry,
        options: DidCreationOptions = DidCreationOptions()
    ): GodiddyIntegrationResult {
        // Create configuration from options
        val config = GodiddyConfig.fromOptions(options)
        val client = GodiddyClient(config)
        
        // Create service clients
        val resolver = GodiddyResolver(client)
        val registrar = try {
            GodiddyRegistrar(client)
        } catch (e: Exception) {
            null // Registrar may not be available
        }
        val issuer = try {
            GodiddyIssuer(client)
        } catch (e: Exception) {
            null // Issuer may not be available
        }
        val verifier = try {
            GodiddyVerifier(client)
        } catch (e: Exception) {
            null // Verifier may not be available
        }
        
        // Discover DID method provider
        val didProviders = ServiceLoader.load(DidMethodProvider::class.java)
        val godiddyDidProvider = didProviders.find { it.name == "godiddy" }
            ?: throw IllegalStateException("godiddy DID method provider not found. Ensure vericore-godiddy is on classpath.")
        
        // Register supported DID methods
        val registeredMethods = mutableListOf<String>()
        for (methodName in godiddyDidProvider.supportedMethods) {
            val method = godiddyDidProvider.create(methodName, options)
            if (method != null) {
                registry.register(method)
                registeredMethods.add(methodName)
            }
        }

        return GodiddyIntegrationResult(
            registry = registry,
            registeredDidMethods = registeredMethods,
            resolver = resolver,
            registrar = registrar,
            issuer = issuer,
            verifier = verifier
        )
    }

    /**
     * Manually setup godiddy integration with configuration.
     *
     * @param baseUrl Base URL for godiddy services (defaults to public service)
     * @param didMethods List of DID method names to register (defaults to all supported)
     * @param options Additional configuration options
     * @return A GodiddyIntegrationResult
     */
    fun setup(
        baseUrl: String? = null,
        registry: DidMethodRegistry,
        didMethods: List<String>? = null,
        options: DidCreationOptions = DidCreationOptions()
    ): GodiddyIntegrationResult {
        // Merge baseUrl into options if provided
        val mergedOptions = if (baseUrl != null) {
            options.copy(additionalProperties = options.additionalProperties + ("baseUrl" to baseUrl))
        } else {
            options
        }
        
        // Create configuration
        val config = GodiddyConfig.fromOptions(mergedOptions)
        val client = GodiddyClient(config)
        
        // Create service clients
        val resolver = GodiddyResolver(client)
        val registrar = try {
            GodiddyRegistrar(client)
        } catch (e: Exception) {
            null
        }
        val issuer = try {
            GodiddyIssuer(client)
        } catch (e: Exception) {
            null
        }
        val verifier = try {
            GodiddyVerifier(client)
        } catch (e: Exception) {
            null
        }
        
        // Discover DID method provider
        val didProviders = ServiceLoader.load(DidMethodProvider::class.java)
        val godiddyDidProvider = didProviders.find { it.name == "godiddy" }
            ?: throw IllegalStateException("godiddy DID method provider not found. Ensure vericore-godiddy is on classpath.")
        
        // Register requested DID methods or all supported
        val methodsToRegister = didMethods ?: godiddyDidProvider.supportedMethods
        val registeredMethods = mutableListOf<String>()
        
        for (methodName in methodsToRegister) {
            if (methodName in godiddyDidProvider.supportedMethods) {
                val method = godiddyDidProvider.create(methodName, mergedOptions)
                if (method != null) {
                    registry.register(method)
                    registeredMethods.add(methodName)
                }
            }
        }

        return GodiddyIntegrationResult(
            registry = registry,
            registeredDidMethods = registeredMethods,
            resolver = resolver,
            registrar = registrar,
            issuer = issuer,
            verifier = verifier
        )
    }
}

