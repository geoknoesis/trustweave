package org.trustweave.credential.proof.internal.engines

import org.trustweave.credential.format.ProofSuiteId
import org.trustweave.credential.spi.proof.ProofEngine
import org.trustweave.credential.spi.proof.ProofEngineConfig
import org.trustweave.credential.spi.proof.ProofEngineProvider
import kotlin.collections.buildMap

/**
 * Provider for SD-JWT-VC proof engine.
 */
internal class SdJwtProofEngineProvider : ProofEngineProvider {
    
    override val name = "sdjwt"
    
    override val supportedFormatIds = listOf(ProofSuiteId.SD_JWT_VC)
    
    override fun create(options: Map<String, Any?>): ProofEngine? {
        return try {
            // Convert Map<String, Any?> to Map<String, Any> by filtering out null values
            val nonNullOptions = buildMap<String, Any> {
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
}

