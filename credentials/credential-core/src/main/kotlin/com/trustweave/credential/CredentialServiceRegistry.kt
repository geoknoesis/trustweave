package com.trustweave.credential

import com.trustweave.credential.models.VerifiableCredential
import com.trustweave.credential.models.VerifiablePresentation
import java.util.concurrent.ConcurrentHashMap

/**
 * Instance-based registry for credential services.
 *
 * This replaces the previous `CredentialRegistry` singleton by providing an explicit
 * registry that can be owned by a specific application or test scope. The API is a
 * one-to-one match with the previous singleton to simplify migration.
 */
class CredentialServiceRegistry internal constructor(
    initialServices: Map<String, CredentialService> = emptyMap()
) {
    private val services = ConcurrentHashMap<String, CredentialService>(initialServices)

    fun register(service: CredentialService) {
        services[service.providerName] = service
    }

    fun unregister(providerName: String) {
        services.remove(providerName)
    }

    fun get(providerName: String? = null): CredentialService? {
        return if (providerName != null) {
            services[providerName]
        } else {
            services.values.firstOrNull()
        }
    }

    fun getAll(): Map<String, CredentialService> = services.toMap()

    fun isRegistered(providerName: String): Boolean = services.containsKey(providerName)

    suspend fun issue(
        credential: VerifiableCredential,
        options: CredentialIssuanceOptions = CredentialIssuanceOptions()
    ): VerifiableCredential {
        val service = resolveService(options.providerName)
        return service.issueCredential(credential, options)
    }

    suspend fun verify(
        credential: VerifiableCredential,
        options: CredentialVerificationOptions = CredentialVerificationOptions()
    ): CredentialVerificationResult {
        val service = resolveService(options.providerName)
        return service.verifyCredential(credential, options)
    }

    suspend fun createPresentation(
        credentials: List<VerifiableCredential>,
        options: PresentationOptions
    ): VerifiablePresentation {
        val service = resolveService(options.providerName)
        return service.createPresentation(credentials, options)
    }

    suspend fun verifyPresentation(
        presentation: VerifiablePresentation,
        options: PresentationVerificationOptions = PresentationVerificationOptions()
    ): PresentationVerificationResult {
        val service = resolveService(options.providerName)
        return service.verifyPresentation(presentation, options)
    }

    fun clear() {
        services.clear()
    }

    fun snapshot(): CredentialServiceRegistry = CredentialServiceRegistry(services.toMap())

    private fun resolveService(providerName: String?): CredentialService {
        val service = get(providerName)
        return service ?: throw IllegalArgumentException(
            if (providerName != null) {
                "No credential service registered for provider: $providerName"
            } else {
                "No credential service registered"
            }
        )
    }

    companion object {
        fun create(): CredentialServiceRegistry = CredentialServiceRegistry()
    }
}


