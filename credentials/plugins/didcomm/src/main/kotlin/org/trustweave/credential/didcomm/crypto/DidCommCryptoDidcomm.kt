package org.trustweave.credential.didcomm.crypto

import org.didcommx.didcomm.DIDComm
import org.didcommx.didcomm.model.PackEncryptedParams
import org.didcommx.didcomm.model.UnpackParams
import org.didcommx.didcomm.secret.SecretResolver
import org.trustweave.credential.didcomm.crypto.interop.BlockingDidDocResolver
import org.trustweave.credential.didcomm.crypto.interop.DidCommEnvelopeJson
import org.trustweave.credential.didcomm.crypto.interop.DidCommMessageBuilder
import org.trustweave.credential.didcomm.crypto.interop.toJsonObject
import org.trustweave.credential.didcomm.models.DidCommEnvelope
import org.trustweave.did.model.DidDocument
import org.trustweave.kms.KeyManagementService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

/**
 * DIDComm V2 crypto using [org.didcommx.didcomm.DIDComm] (AuthCrypt / ECDH-1PU + AES per library defaults).
 *
 * Requires a [SecretResolver] that can supply private keys for the sender and recipient key IDs used in
 * [encrypt]/[decrypt]. Resolve DID documents with [resolveDid]; they are converted via [TrustWeaveDidDocMapper].
 *
 * **Mediators:** [PackEncryptedParams] uses `forward(false)` so messages are not wrapped for mediators by default.
 * Enable forwarding in a custom integration if you use mediators.
 *
 * @param kms Reserved for callers that construct crypto alongside a [KeyManagementService] (signing, future KMS-backed secrets).
 * @param resolveTimeoutMs Per-DID resolution budget passed to [BlockingDidDocResolver].
 */
class DidCommCryptoDidcomm(
    @Suppress("UNUSED_PARAMETER") private val kms: KeyManagementService,
    private val resolveDid: suspend (String) -> DidDocument?,
    private val secretResolver: SecretResolver,
    resolveTimeoutMs: Long = 30_000L,
) : DidCommCryptoInterface {

    private val didDocResolver = BlockingDidDocResolver(
        dispatcher = Dispatchers.IO,
        resolveTimeoutMs = resolveTimeoutMs,
        suspendResolve = resolveDid,
    )
    private val didComm = DIDComm(didDocResolver, secretResolver)

    override suspend fun encrypt(
        message: JsonObject,
        fromDid: String,
        fromKeyId: String,
        toDid: String,
        toKeyId: String,
    ): DidCommEnvelope = withContext(Dispatchers.IO) {
        val didMessage = DidCommMessageBuilder.fromJsonObject(message, fromDid, toDid)
        val params = PackEncryptedParams.builder(didMessage, toDid)
            .from(fromKeyId)
            .forward(false)
            .didDocResolver(didDocResolver)
            .secretResolver(secretResolver)
            .build()
        val packed = didComm.packEncrypted(params).packedMessage
        DidCommEnvelopeJson.packedJsonToEnvelope(packed)
    }

    override suspend fun decrypt(
        envelope: DidCommEnvelope,
        recipientDid: String,
        recipientKeyId: String,
        senderDid: String,
    ): JsonObject = withContext(Dispatchers.IO) {
        val packed = DidCommEnvelopeJson.envelopeToPackedJson(envelope)
        val params = UnpackParams.Builder(packed)
            .unwrapReWrappingForward(false)
            .didDocResolver(didDocResolver)
            .secretResolver(secretResolver)
            .build()
        val result = didComm.unpack(params)
        val msg = result.message
        if (senderDid.isNotBlank()) {
            val unpackedFrom = msg.from
            if (unpackedFrom != null && unpackedFrom != senderDid) {
                throw IllegalArgumentException(
                    "DIDComm decrypted message from '$unpackedFrom' does not match expected senderDid '$senderDid'",
                )
            }
        }
        msg.toJsonObject()
    }
}
