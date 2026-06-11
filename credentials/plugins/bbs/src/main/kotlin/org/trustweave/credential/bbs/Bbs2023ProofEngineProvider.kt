package org.trustweave.credential.bbs

import org.trustweave.credential.format.ProofSuiteId
import org.trustweave.credential.spi.proof.ProofEngine
import org.trustweave.credential.spi.proof.ProofEngineConfig
import org.trustweave.credential.spi.proof.ProofEngineProvider

/**
 * ServiceLoader-registered provider for the BBS-2023 proof engine.
 *
 * Registered in `META-INF/services/org.trustweave.credential.spi.proof.ProofEngineProvider`.
 *
 * **Supported format:** [ProofSuiteId.BBS_2023]
 *
 * **Options (passed as `options` map):**
 * | Key          | Type               | Description                               |
 * |--------------|--------------------|-------------------------------------------|
 * | `keyPair`    | [Bls12381KeyPair]  | BLS12-381 signing key pair; required for  |
 * |              |                    | issuance (issue fails closed without it)  |
 * | `didResolver`| `DidResolver`      | Issuer DID resolver; required for         |
 * |              |                    | verification (verify fails closed without)|
 */
class Bbs2023ProofEngineProvider : ProofEngineProvider {

    override val name: String = "bbs-2023"

    override val supportedFormatIds: List<ProofSuiteId> = listOf(ProofSuiteId.BBS_2023)

    override fun create(options: Map<String, Any?>): ProofEngine? {
        if (ProofSuiteId.BBS_2023 !in supportedFormatIds) return null
        return try {
            val nonNullOptions = buildMap<String, Any> {
                options.forEach { (k, v) -> if (v != null) put(k, v) }
            }
            val config = ProofEngineConfig(properties = nonNullOptions)
            Bbs2023ProofEngine(config)
        } catch (_: Exception) {
            null
        }
    }
}
