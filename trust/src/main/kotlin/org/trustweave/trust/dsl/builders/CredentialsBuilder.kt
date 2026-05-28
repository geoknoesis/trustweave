package org.trustweave.trust.dsl.builders

import org.trustweave.credential.model.ProofType
import org.trustweave.trust.dsl.TrustWeaveDsl

/**
 * Credentials configuration builder.
 */
@TrustWeaveDsl
class CredentialsBuilder {
    var defaultProofType: ProofType? = null
    var autoAnchor: Boolean? = null
    var defaultChain: String? = null

    fun defaultProofType(type: ProofType) {
        defaultProofType = type
    }

    fun autoAnchor(enabled: Boolean) {
        autoAnchor = enabled
    }

    fun defaultChain(chainId: String) {
        defaultChain = chainId
    }
}
