package org.trustweave.did.sidetree

import java.util.Base64

/**
 * Sidetree commitment values: `base64url(SHA-256(JCS(publicKeyJwk)))`.
 *
 * Anchored on-ledger to prove future ownership of the corresponding update or
 * recovery key without revealing the key itself. See Sidetree v1.0.0 §6.
 */
object SidetreeCommitment {

    private val b64url: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()

    fun compute(publicKeyJwk: Map<String, Any?>): String {
        val canonical = SidetreeJcs.canonicalize(publicKeyJwk)
        return b64url.encodeToString(SidetreeJcs.sha256(canonical))
    }
}
