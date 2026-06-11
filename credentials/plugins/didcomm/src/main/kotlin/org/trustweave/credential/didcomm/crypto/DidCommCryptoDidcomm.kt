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
 * **Sender authentication:** [decrypt] binds the expected `senderDid` to the *cryptographic* sender
 * reported by didcomm-java's unpack metadata (`encryptedFrom`, set only for AuthCrypt) — never to the
 * plaintext `from` header alone, which is attacker-controlled under anonymous (AnonCrypt) encryption.
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
    ): DidCommDecryptResult = withContext(Dispatchers.IO) {
        val packed = DidCommEnvelopeJson.envelopeToPackedJson(envelope)
        val params = UnpackParams.Builder(packed)
            .unwrapReWrappingForward(false)
            .didDocResolver(didDocResolver)
            .secretResolver(secretResolver)
            .build()
        val result = didComm.unpack(params)
        val msg = result.message
        val metadata = result.metadata

        // didcomm-java 0.3.2 sets `Metadata.encryptedFrom` (the sender key id the ECDH-1PU key
        // agreement actually used) ONLY on the AuthCrypt unpack path, which also sets
        // `authenticated`. AnonCrypt (ECDH-ES) sets `anonymousSender` and never `encryptedFrom`:
        // its plaintext `from` is attacker-controlled and must not be trusted for authentication.
        val authenticatedSenderDid: String? =
            if (metadata.authenticated) metadata.encryptedFrom?.substringBefore("#") else null

        // Plaintext-consistency guard: under AuthCrypt, DIDComm v2 requires the message `from`
        // to correspond to the `skid` used for key agreement.
        val unpackedFrom = msg.from
        if (authenticatedSenderDid != null && unpackedFrom != null && unpackedFrom != authenticatedSenderDid) {
            throw IllegalArgumentException(
                "DIDComm plaintext 'from' ('$unpackedFrom') does not match the cryptographically " +
                    "authenticated sender '$authenticatedSenderDid'"
            )
        }

        if (senderDid.isNotBlank()) {
            if (authenticatedSenderDid == null) {
                throw IllegalArgumentException(
                    "Expected authenticated message from '$senderDid' but the envelope is anonymous " +
                        "(anoncrypt does not authenticate the sender)"
                )
            }
            if (authenticatedSenderDid != senderDid) {
                throw IllegalArgumentException(
                    "DIDComm envelope cryptographic sender '$authenticatedSenderDid' does not match " +
                        "expected senderDid '$senderDid'"
                )
            }
            // Additional guard (kept from the plaintext-only check this replaces): an AuthCrypt
            // message without a `from` violates the DIDComm v2 spec — fail closed.
            if (unpackedFrom == null) {
                throw IllegalArgumentException(
                    "Expected authenticated message from '$senderDid' but the decrypted message carries no 'from'"
                )
            }
        }
        DidCommDecryptResult(msg.toJsonObject(), authenticatedSenderDid)
    }
}
