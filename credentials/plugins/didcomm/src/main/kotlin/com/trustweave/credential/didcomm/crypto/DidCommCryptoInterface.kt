package com.trustweave.credential.didcomm.crypto

import com.trustweave.credential.didcomm.models.DidCommEnvelope
import com.trustweave.did.model.DidDocument
import kotlinx.serialization.json.JsonObject

/**
 * Interface for DIDComm cryptographic operations.
 *
 * Allows switching between placeholder and production implementations.
 */
interface DidCommCryptoInterface {
    /**
     * Encrypts a message using AuthCrypt (ECDH-1PU + AES-256-GCM).
     *
     * @param message The plaintext message JSON
     * @param fromDid Sender DID
     * @param fromKeyId Sender key ID for key agreement
     * @param toDid Recipient DID
     * @param toKeyId Recipient key ID for key agreement
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
     * @param recipientDid Recipient DID
     * @param recipientKeyId Recipient key ID
     * @param senderDid Sender DID (for verification)
     * @return Decrypted message JSON
     */
    suspend fun decrypt(
        envelope: DidCommEnvelope,
        recipientDid: String,
        recipientKeyId: String,
        senderDid: String
    ): JsonObject
}

