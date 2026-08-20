package org.trustweave.credential.proof.internal.engines

import org.trustweave.credential.format.ProofSuiteId
import org.trustweave.credential.spi.proof.ProofEngine
import org.trustweave.credential.spi.proof.ProofEngineConfig
import org.trustweave.credential.spi.proof.ProofEngineProvider
import kotlin.collections.buildMap

/**
 * Provider for SD-JWT-VC proof engine.
 *
 * Discovered via [java.util.ServiceLoader]; see META-INF/services.
 *
 * Public because [ProofEngineProvider] is a public SPI: an implementation nobody outside the
 * module can reach or discover is not an SPI implementation. Applications that compose their own
 * signing stack — publishing status-list credentials, for instance — need a supported way to
 * obtain a SD-JWT VC engine without reaching into internals.
 */
class SdJwtProofEngineProvider : ProofEngineProvider {
    override val name = "sdjwt"

    override val supportedFormatIds = listOf(ProofSuiteId.SD_JWT_VC)

    override fun create(options: Map<String, Any?>): ProofEngine? =
        try {
            // Convert Map<String, Any?> to Map<String, Any> by filtering out null values
            val nonNullOptions =
                buildMap<String, Any> {
                    options.forEach { (key, value) ->
                        if (value != null) {
                            put(key, value)
                        }
                    }
                }
            val config = ProofEngineConfig(properties = nonNullOptions)
            SdJwtProofEngine(config)
        } catch (e: Exception) {
            null
        }
}
