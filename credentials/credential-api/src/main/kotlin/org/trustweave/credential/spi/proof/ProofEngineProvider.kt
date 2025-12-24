package org.trustweave.credential.spi.proof

import org.trustweave.credential.format.ProofSuiteId

/**
 * SPI Provider for proof engines.
 * 
 * Allows automatic discovery of ProofEngine implementations via Java ServiceLoader.
 * 
 * **ServiceLoader Registration:**
 * Create a file `META-INF/services/org.trustweave.credential.spi.proof.ProofEngineProvider`
 * with the content:
 * ```
 * com.example.MyProofEngineProvider
 * ```
 * 
 * **Example Usage:**
 * ```kotlin
 * class VcLdEngineProvider : ProofEngineProvider {
 *     override val name = "vc-ld"
 *     
 *     override fun create(options: Map<String, Any?>): ProofEngine? {
 *         return VcLdProofEngine(options)
 *     }
 *     
 *     override val supportedFormatIds: List<ProofSuiteId>
 *         get() = listOf(ProofSuiteId.VC_LD)
 * }
 * ```
 */
interface ProofEngineProvider {
    /**
     * Provider name (e.g., "vc-ld", "sd-jwt-vc").
     */
    val name: String
    
    /**
     * Supported proof suite IDs.
     * 
     * Returns the list of proof suite IDs this provider can create proof engines for.
     * Proof suites: VC_LD, VC_JWT, SD_JWT_VC
     */
    val supportedFormatIds: List<ProofSuiteId>
    
    /**
     * Creates a ProofEngine instance.
     * 
     * @param options Configuration options
     * @return Proof engine instance, or null if creation failed
     */
    fun create(options: Map<String, Any?> = emptyMap()): ProofEngine?
}

