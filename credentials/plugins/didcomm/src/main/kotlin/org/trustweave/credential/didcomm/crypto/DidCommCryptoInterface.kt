package org.trustweave.credential.didcomm.crypto

import org.trustweave.credential.didcomm.models.DidCommEnvelope
import org.trustweave.did.model.DidDocument
import kotlinx.serialization.json.JsonObject

/**
 * Interface for DIDComm cryptographic operations.
 *
 * Implemented by [DidCommCryptoDidcomm] (didcomm-java, real crypto) and [DidCommCrypto]
 * (fail-closed stand-in that throws when no ECDH-capable provider is configured).
 */
interface DidCommCryptoInterface {
    /**
     * Encrypts a message using AuthCrypt (ECDH-1PU + AES-256-GCM).
     *
     * @param message The plaintext message JSON
     * @param fromDid Sender DID
     * @param fromKeyId Sender key ID for key agreement
     * @param toDid Recipient DID
     * @param toKeyId Recipient key id for multiplex / routing; with didcomm-java multiplex encryption the library
     *   may select agreement keys from the resolved DID document, so this may not affect the wire format.
     * @return Encrypted envelope
     */
    suspend fun encrypt(
        message: JsonObject,
        fromDid: String,
        fromKeyId: String,
        toDid: String,
        toKeyId: String
    ): DidCommEnvelope

    /**
     * Decrypts an encrypted envelope.
     *
     * @param envelope The encrypted envelope
     * @param recipientDid Recipient DID (caller context; didcomm-java unpack uses secrets and the packed payload)
     * @param recipientKeyId Recipient key ID (caller context for APIs that route by key id)
     * @param senderDid When non-blank, must match the unpacked message `from` when present, or [IllegalArgumentException] is thrown
     * @return Decrypted message JSON
     */
    suspend fun decrypt(
        envelope: DidCommEnvelope,
        recipientDid: String,
        recipientKeyId: String,
        senderDid: String
    ): JsonObject
}

