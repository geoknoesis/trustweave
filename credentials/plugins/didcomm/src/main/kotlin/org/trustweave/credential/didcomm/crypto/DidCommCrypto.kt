package org.trustweave.credential.didcomm.crypto

import org.trustweave.credential.didcomm.models.DidCommEnvelope
import org.trustweave.did.model.DidDocument
import org.trustweave.kms.KeyManagementService
import kotlinx.serialization.json.JsonObject

/**
 * Fail-closed stand-in for DIDComm V2 crypto when no ECDH-capable provider is configured.
 *
 * A previous revision of this class shipped "placeholder" crypto whose ECDH-1PU key agreement
 * returned a constant all-zero shared secret, making every envelope decryptable by anyone.
 * That implementation has been removed and is intentionally not recoverable: real DIDComm v2
 * key agreement requires access to private key material (X25519 / NIST-curve ECDH), which is
 * not reachable through the handle-based [KeyManagementService] API available here.
 *
 * For real, interoperable DIDComm v2 crypto use [DidCommCryptoDidcomm] with a
 * [org.didcommx.didcomm.secret.SecretResolver] that supplies JWK private keys
 * (see `DidCommFactory.createInMemoryService`).
 *
 * Every operation on this class throws [UnsupportedOperationException]; it never produces
 * ciphertext and never returns plaintext.
 */
class DidCommCrypto(
    @Suppress("UNUSED_PARAMETER") private val kms: KeyManagementService,
    @Suppress("UNUSED_PARAMETER") private val resolveDid: suspend (String) -> DidDocument?
) : DidCommCryptoInterface {

    /**
     * Always throws [UnsupportedOperationException]; see class documentation.
     */
    override suspend fun encrypt(
        message: JsonObject,
        fromDid: String,
        fromKeyId: String,
        toDid: String,
        toKeyId: String
    ): DidCommEnvelope = failClosed()

    /**
     * Always throws [UnsupportedOperationException]; see class documentation.
     */
    override suspend fun decrypt(
        envelope: DidCommEnvelope,
        recipientDid: String,
        recipientKeyId: String,
        senderDid: String
    ): JsonObject = failClosed()

    private fun failClosed(): Nothing =
        throw UnsupportedOperationException(
            "DIDComm encryption requires an ECDH-capable crypto provider; not configured. " +
                "Supply a SecretResolver with JWK private key material and use didcomm-java crypto " +
                "(e.g. DidCommFactory.createInMemoryService(kms, resolveDid, secretResolver)). " +
                "The former placeholder implementation derived keys from a constant shared secret " +
                "and has been removed for security reasons."
        )
}
