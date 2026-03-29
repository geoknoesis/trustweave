package org.trustweave.core.exception

/**
 * Sealed hierarchy for provider chain exceptions.
 *
 * Extracted from [TrustWeaveException] to give provider-related errors a focused home.
 */
sealed class ProviderException(
    code: String,
    message: String,
    context: Map<String, Any?> = emptyMap(),
    cause: Throwable? = null
) : TrustWeaveException(code, message, context, cause) {

    data class NoneFound(
        val pluginIds: List<String>,
        val availablePlugins: List<String> = emptyList()
    ) : ProviderException(
        code = "NO_PROVIDERS_FOUND",
        message = buildString {
            append("No providers found for plugin IDs: ${pluginIds.joinToString(", ")}")
            if (availablePlugins.isNotEmpty()) {
                append(". Available plugins: ${availablePlugins.joinToString(", ")}")
            } else {
                append(". No plugins are registered. Register plugins in TrustWeave.build { ... }")
            }
        },
        context = mapOf(
            "pluginIds" to pluginIds,
            "availablePlugins" to availablePlugins
        )
    )

    data class PartiallyFound(
        val requestedIds: List<String>,
        val foundIds: List<String>,
        val missingIds: List<String>
    ) : ProviderException(
        code = "PARTIAL_PROVIDERS_FOUND",
        message = "Only ${foundIds.size} of ${requestedIds.size} providers found. Missing: ${missingIds.joinToString(", ")}",
        context = mapOf(
            "requestedIds" to requestedIds,
            "foundIds" to foundIds,
            "missingIds" to missingIds
        )
    )

    data class AllFailed(
        val attemptedProviders: List<String>,
        val providerErrors: Map<String, String> = emptyMap(),
        val lastException: Throwable? = null
    ) : ProviderException(
        code = "ALL_PROVIDERS_FAILED",
        message = buildString {
            append("All providers in chain failed. Attempted: ${attemptedProviders.joinToString(", ")}")
            if (providerErrors.isNotEmpty()) {
                append(". Errors: ${providerErrors.entries.joinToString("; ") { "${it.key}: ${it.value}" }}")
            }
            append(" Check provider configurations and ensure all required dependencies are available.")
        },
        context = mapOf(
            "attemptedProviders" to attemptedProviders,
            "providerErrors" to providerErrors
        ),
        cause = lastException
    )
}
