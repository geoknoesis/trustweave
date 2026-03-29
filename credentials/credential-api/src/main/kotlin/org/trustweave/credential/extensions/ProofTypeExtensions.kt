package org.trustweave.credential.extensions

import org.trustweave.credential.format.ProofSuiteId
import org.trustweave.credential.model.ProofType

/**
 * Maps a [ProofType] to its default [ProofSuiteId].
 *
 * Encapsulates the one-to-one mapping between proof types and proof suites,
 * following the Single Responsibility Principle—the mapping lives with the domain type.
 *
 * **Example:**
 * ```kotlin
 * val suite = ProofType.Ed25519Signature2020.toProofSuiteId()
 * // ProofSuiteId.VC_LD
 * ```
 */
fun ProofType.toProofSuiteId(): ProofSuiteId = when (this) {
    is ProofType.Ed25519Signature2020 -> ProofSuiteId.VC_LD
    is ProofType.JsonWebSignature2020 -> ProofSuiteId.VC_JWT
    is ProofType.BbsBlsSignature2020 -> ProofSuiteId.SD_JWT_VC
    is ProofType.Custom -> ProofSuiteId.VC_LD
}
