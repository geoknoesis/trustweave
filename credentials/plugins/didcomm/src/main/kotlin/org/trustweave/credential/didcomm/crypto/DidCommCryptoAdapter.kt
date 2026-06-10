package org.trustweave.credential.didcomm.crypto

import org.didcommx.didcomm.secret.SecretResolver
import org.trustweave.credential.didcomm.crypto.interop.DidCommEnvelopeJson
import org.trustweave.credential.didcomm.models.DidCommEnvelope
import org.trustweave.did.model.DidDocument
import org.trustweave.kms.KeyManagementService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

/**
 * Adapter over [DidCommCryptoDidcomm] (didcomm-java / DIDComm v2).
 *
 * When [useDidcommJava] is true, [secretResolver] must be non-null.
 *
 * When [useDidcommJava] is false the adapter is **fail-closed**: every encrypt/decrypt call
 * throws [UnsupportedOperationException]. The former placeholder crypto ([DidCommCrypto]) used a
 * constant shared secret and has been removed; there is no configuration of this adapter that
 * produces ciphertext without real key material.
 */
class DidCommCryptoAdapter(
    private val kms: KeyManagementService,
    private val resolveDid: suspend (String) -> DidDocument?,
    val useDidcommJava: Boolean = true,
    private val secretResolver: SecretResolver? = null,
) : DidCommCryptoInterface {

    private val failClosedCrypto = DidCommCrypto(kms, resolveDid)
    private val didcommCrypto: DidCommCryptoDidcomm? =
        if (useDidcommJava && secretResolver != null) DidCommCryptoDidcomm(kms, resolveDid, secretResolver) else null

    override suspend fun encrypt(
        message: JsonObject,
        fromDid: String,
        fromKeyId: String,
        toDid: String,
        toKeyId: String,
    ): DidCommEnvelope = withContext(Dispatchers.IO) {
        crypto().encrypt(message, fromDid, fromKeyId, toDid, toKeyId)
    }

    override suspend fun decrypt(
        envelope: DidCommEnvelope,
        recipientDid: String,
        recipientKeyId: String,
        senderDid: String,
    ): JsonObject = withContext(Dispatchers.IO) {
        crypto().decrypt(envelope, recipientDid, recipientKeyId, senderDid)
    }

    /**
     * Decrypts a didcomm-java packed JSON string (same wire format as [DidCommPacker] encrypted output).
     */
    suspend fun decryptFromPacked(
        packedMessage: String,
        recipientDid: String,
        recipientKeyId: String,
        senderDid: String,
    ): JsonObject = withContext(Dispatchers.IO) {
        val envelope = DidCommEnvelopeJson.packedJsonToEnvelope(packedMessage)
        crypto().decrypt(envelope, recipientDid, recipientKeyId, senderDid)
    }

    private fun crypto(): DidCommCryptoInterface {
        if (useDidcommJava) {
            return didcommCrypto
                ?: throw IllegalStateException(
                    "DIDComm Java crypto requires a non-null SecretResolver. " +
                        "Supply one, e.g. DidCommFactory.createInMemoryService(kms, resolveDid, secretResolver).",
                )
        }
        // Fail closed: placeholder crypto has been removed; this instance throws on every operation.
        return failClosedCrypto
    }
}
