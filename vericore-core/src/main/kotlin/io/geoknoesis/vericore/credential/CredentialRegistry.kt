package io.geoknoesis.vericore.credential

import io.geoknoesis.vericore.credential.models.VerifiableCredential
import io.geoknoesis.vericore.credential.models.VerifiablePresentation
import java.util.concurrent.ConcurrentHashMap

/**
 * Unified registry for credential services.
 * 
 * Provides high-level API that abstracts provider details.
 * Similar to DidRegistry and BlockchainRegistry, this registry
 * enables provider-agnostic credential operations.
 * 
 * **Thread Safety**: This registry is thread-safe for concurrent access.
 * 
 * **Example Usage**:
 * ```kotlin
 * // Register a credential service
 * val waltIdService = WaltIdCredentialService(...)
 * CredentialRegistry.register(waltIdService)
 * 
 * // Use unified API - automatically selects provider
 * val credential = CredentialRegistry.issue(credential, options)
 * val result = CredentialRegistry.verify(credential, options)
 * 
 * // Or specify provider explicitly
 * val options = CredentialIssuanceOptions(providerName = "waltid")
 * val credential = CredentialRegistry.issue(credential, options)
 * ```
 */
@Deprecated(
    message = "Inject CredentialService implementations via your own context instead of relying on the global CredentialRegistry singleton.",
    replaceWith = ReplaceWith("mutableMapOf<String, CredentialService>()"),
    level = DeprecationLevel.WARNING
)
object CredentialRegistry {
    private val services = ConcurrentHashMap<String, CredentialService>()
    
    /**
     * Register a credential service.
     * 
     * @param service Credential service to register
     */
    fun register(service: CredentialService) {
        services[service.providerName] = service
    }
    
    /**
     * Unregister a credential service.
     * 
     * @param providerName Provider name to unregister
     */
    fun unregister(providerName: String) {
        services.remove(providerName)
    }
    
    /**
     * Get a credential service by provider name.
     * 
     * @param providerName Provider name (e.g., "waltid", "godiddy"), or null for default
     * @return Credential service, or null if not found
     */
    fun get(providerName: String? = null): CredentialService? {
        return if (providerName != null) {
            services[providerName]
        } else {
            services.values.firstOrNull() // Default to first registered
        }
    }
    
    /**
     * Get all registered services.
     * 
     * @return Map of provider name to service
     */
    fun getAll(): Map<String, CredentialService> {
        return services.toMap()
    }
    
    /**
     * Check if a provider is registered.
     * 
     * @param providerName Provider name
     * @return true if provider is registered
     */
    fun isRegistered(providerName: String): Boolean {
        return services.containsKey(providerName)
    }
    
    /**
     * High-level issuance - automatically selects provider.
     * 
     * @param credential Credential to issue (without proof)
     * @param options Issuance options (may specify providerName)
     * @return Issued credential with proof
     * @throws IllegalArgumentException if no credential service is registered
     */
    suspend fun issue(
        credential: VerifiableCredential,
        options: CredentialIssuanceOptions = CredentialIssuanceOptions()
    ): VerifiableCredential {
        val service = get(options.providerName)
            ?: throw IllegalArgumentException(
                "No credential service registered${if (options.providerName != null) " for provider: ${options.providerName}" else ""}"
            )
        return service.issueCredential(credential, options)
    }
    
    /**
     * High-level verification - automatically selects provider.
     * 
     * @param credential Credential to verify
     * @param options Verification options (may specify providerName)
     * @return Verification result
     * @throws IllegalArgumentException if no credential service is registered
     */
    suspend fun verify(
        credential: VerifiableCredential,
        options: CredentialVerificationOptions = CredentialVerificationOptions()
    ): CredentialVerificationResult {
        val service = get(options.providerName)
            ?: throw IllegalArgumentException(
                "No credential service registered${if (options.providerName != null) " for provider: ${options.providerName}" else ""}"
            )
        return service.verifyCredential(credential, options)
    }
    
    /**
     * High-level presentation creation - automatically selects provider.
     * 
     * @param credentials List of credentials to include
     * @param options Presentation options
     * @return Verifiable presentation
     * @throws IllegalArgumentException if no credential service is registered
     */
    suspend fun createPresentation(
        credentials: List<VerifiableCredential>,
        options: PresentationOptions
    ): VerifiablePresentation {
        val service = get()
            ?: throw IllegalArgumentException("No credential service registered")
        return service.createPresentation(credentials, options)
    }
    
    /**
     * High-level presentation verification - automatically selects provider.
     * 
     * @param presentation Presentation to verify
     * @param options Verification options
     * @return Verification result
     * @throws IllegalArgumentException if no credential service is registered
     */
    suspend fun verifyPresentation(
        presentation: VerifiablePresentation,
        options: PresentationVerificationOptions = PresentationVerificationOptions()
    ): PresentationVerificationResult {
        val service = get(options.providerName)
            ?: throw IllegalArgumentException(
                "No credential service registered${if (options.providerName != null) " for provider: ${options.providerName}" else ""}"
            )
        return service.verifyPresentation(presentation, options)
    }
    
    /**
     * Clear all registered services.
     * Useful for testing.
     */
    fun clear() {
        services.clear()
    }
}

