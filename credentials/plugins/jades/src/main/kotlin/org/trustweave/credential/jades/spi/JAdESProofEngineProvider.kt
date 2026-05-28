package org.trustweave.credential.jades.spi

import org.trustweave.credential.format.ProofSuiteId
import org.trustweave.credential.jades.JAdESProofEngine
import org.trustweave.credential.spi.proof.ProofEngine
import org.trustweave.credential.spi.proof.ProofEngineProvider
import org.trustweave.kms.KeyManagementService
import org.trustweave.signatures.trustlists.TrustAnchorResolver

/**
 * SPI provider for [JAdESProofEngine].
 *
 * Discovered via Java `ServiceLoader`. Options:
 * - `"kms"` (required) — [KeyManagementService] for signing.
 * - `"trustAnchorResolver"` (optional) — [TrustAnchorResolver] used by the verifier; can also be
 *   supplied at runtime via `VerificationOptions.additionalOptions` or
 *   `ProofEngineConfig.properties`.
 */
class JAdESProofEngineProvider : ProofEngineProvider {

    override val name: String = "jades"

    override val supportedFormatIds: List<ProofSuiteId> = listOf(ProofSuiteId.JADES)

    override fun create(options: Map<String, Any?>): ProofEngine? {
        val kms = options["kms"] as? KeyManagementService ?: return null
        val resolver = options["trustAnchorResolver"] as? TrustAnchorResolver
        return JAdESProofEngine(kms = kms, trustAnchorResolver = resolver)
    }
}
