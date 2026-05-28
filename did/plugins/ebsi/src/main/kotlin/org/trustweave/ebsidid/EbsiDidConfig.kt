package org.trustweave.ebsidid

/**
 * Configuration for the did:ebsi method implementation.
 *
 * **Example Usage:**
 * ```kotlin
 * // Resolve-only (no bearer token)
 * val config = EbsiDidConfig.pilot()
 *
 * // Full read/write access
 * val config = EbsiDidConfig.pilot(bearerToken = "eyJ...")
 * ```
 *
 * @property apiBaseUrl Base URL for the EBSI DID Registry REST API.
 * @property network    EBSI network environment (PILOT, CONFORMANCE, PRODUCTION).
 * @property bearerToken Bearer token for create/update operations; `null` = resolve-only mode.
 * @property timeoutSeconds HTTP client timeout in seconds (default: 30).
 */
data class EbsiDidConfig(
    val apiBaseUrl: String,
    val network: EbsiNetwork,
    val bearerToken: String? = null,
    val timeoutSeconds: Long = 30,
) {

    companion object {

        /** EBSI pilot API base URL. */
        const val PILOT_URL = "https://api-pilot.ebsi.eu"

        /** EBSI conformance API base URL. */
        const val CONFORMANCE_URL = "https://api-conformance.ebsi.eu"

        /** EBSI production API base URL. */
        const val PRODUCTION_URL = "https://api.ebsi.eu"

        /**
         * Creates a configuration targeting the EBSI pilot environment.
         *
         * @param bearerToken Optional bearer token for write operations.
         */
        fun pilot(bearerToken: String? = null): EbsiDidConfig =
            EbsiDidConfig(PILOT_URL, EbsiNetwork.PILOT, bearerToken)

        /**
         * Creates a configuration targeting the EBSI conformance environment.
         *
         * @param bearerToken Optional bearer token for write operations.
         */
        fun conformance(bearerToken: String? = null): EbsiDidConfig =
            EbsiDidConfig(CONFORMANCE_URL, EbsiNetwork.CONFORMANCE, bearerToken)

        /**
         * Creates a configuration targeting the EBSI production environment.
         *
         * @param bearerToken Optional bearer token for write operations.
         */
        fun production(bearerToken: String? = null): EbsiDidConfig =
            EbsiDidConfig(PRODUCTION_URL, EbsiNetwork.PRODUCTION, bearerToken)

        /**
         * Creates a configuration from a property map (for SPI provider use).
         *
         * Recognised keys: `apiBaseUrl`, `network`, `bearerToken`, `timeoutSeconds`.
         */
        fun fromMap(map: Map<String, Any?>): EbsiDidConfig {
            val network = (map["network"] as? String)
                ?.let { runCatching { EbsiNetwork.valueOf(it.uppercase()) }.getOrNull() }
                ?: EbsiNetwork.PILOT
            val defaultUrl = when (network) {
                EbsiNetwork.PILOT -> PILOT_URL
                EbsiNetwork.CONFORMANCE -> CONFORMANCE_URL
                EbsiNetwork.PRODUCTION -> PRODUCTION_URL
            }
            return EbsiDidConfig(
                apiBaseUrl = map["apiBaseUrl"] as? String ?: defaultUrl,
                network = network,
                bearerToken = map["bearerToken"] as? String,
                timeoutSeconds = (map["timeoutSeconds"] as? Number)?.toLong() ?: 30L,
            )
        }
    }
}
