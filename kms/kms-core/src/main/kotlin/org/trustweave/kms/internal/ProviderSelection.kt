package org.trustweave.kms.internal

import org.trustweave.kms.Algorithm
import org.trustweave.kms.KeyManagementService
import org.trustweave.kms.spi.KeyManagementServiceProvider

/** Selects providers independently of ServiceLoader's process-wide discovery cache. */
internal class ProviderSelection(
    private val providers: List<KeyManagementServiceProvider>,
) {
    fun findBestProviderFor(
        algorithm: Algorithm,
        preferredProvider: String? = null,
    ): String? = select(algorithm, preferredProvider)?.name

    private fun select(
        algorithm: Algorithm,
        preferredProvider: String?,
    ): KeyManagementServiceProvider? {
        preferredProvider?.let { providerName ->
            val provider = providers.firstOrNull { it.name == providerName }
            if (provider?.supportsAlgorithm(algorithm) == true) return provider
        }

        // Preserve discovery order and avoid consulting providers after the first match.
        return providers.firstOrNull { it.supportsAlgorithm(algorithm) }
    }

    fun createProviderFor(
        algorithm: Algorithm,
        preferredProvider: String? = null,
        options: Map<String, Any?> = emptyMap(),
    ): KeyManagementService? = select(algorithm, preferredProvider)?.create(options)
}
