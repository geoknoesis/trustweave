package org.trustweave.trust.dsl.builders

import org.trustweave.did.KeyAlgorithm
import org.trustweave.kms.KeyManagementService

/**
 * Keys (KMS) configuration builder.
 */
class KeysBuilder {
    var provider: String? = null
    private var algorithmString: String? = null
    private var algorithmEnum: KeyAlgorithm? = null
    var kms: KeyManagementService? = null
    var signer: (suspend (ByteArray, String) -> ByteArray)? = null

    /**
     * Resolved algorithm string; prefers enum value if set.
     */
    val algorithm: String?
        get() = algorithmEnum?.algorithmName ?: algorithmString

    fun provider(name: String) {
        provider = name
    }

    fun algorithm(name: String) {
        algorithmString = name
        algorithmEnum = null
    }

    fun algorithm(value: KeyAlgorithm) {
        algorithmEnum = value
        algorithmString = null
    }

    fun custom(kms: KeyManagementService) {
        this.kms = kms
    }

    fun signer(signerFn: suspend (ByteArray, String) -> ByteArray) {
        this.signer = signerFn
    }
}
