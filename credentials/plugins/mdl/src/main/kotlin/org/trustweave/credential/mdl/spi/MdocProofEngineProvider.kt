package org.trustweave.credential.mdl.spi

import org.trustweave.credential.format.ProofSuiteId
import org.trustweave.credential.mdl.engine.MdocProofEngine
import org.trustweave.credential.spi.proof.ProofEngine
import org.trustweave.credential.spi.proof.ProofEngineProvider
import org.trustweave.kms.KeyManagementService

class MdocProofEngineProvider : ProofEngineProvider {

    override val name = "mso_mdoc"

    override val supportedFormatIds: List<ProofSuiteId> = listOf(ProofSuiteId.MDOC)

    override fun create(options: Map<String, Any?>): ProofEngine? {
        val kms = options["kms"] as? KeyManagementService ?: return null
        return MdocProofEngine(kms)
    }
}
