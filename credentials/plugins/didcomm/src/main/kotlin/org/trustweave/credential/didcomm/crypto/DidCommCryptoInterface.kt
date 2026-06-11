package org.trustweave.credential.didcomm.crypto

import org.trustweave.credential.didcomm.models.DidCommEnvelope
import kotlinx.serialization.json.JsonObject

/**
 * Result of [DidCommCryptoInterface.decrypt].
 *
 * @property message The decrypted plaintext message JSON
 * @property authenticatedSenderDid DID of the **cryptographically** authenticated sender, or `null`.
 *   Non-null only when the envelope was sender-authenticated encryption (AuthCrypt / ECDH-1PU):
 *   the value is derived from the sender key id the key agreement actually used
 *   (didcomm-java `Metadata.encryptedFrom`, with any `#fragment` stripped) — never from the
 *   attacker-controllable plaintext `from` header. Anonymous encryption (AnonCrypt / ECDH-ES)
 *   yields `null` because it does not authenticate the sender: anyone holding the recipient's
 *   public key can produce such an envelope with an arbitrary plaintext `from`.
 */
data class DidCommDecryptResult(
    val message: JsonObject,
    val authenticatedSenderDid: String? = null,
)

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
     * @param senderDid When non-blank, the envelope MUST be sender-authenticated (AuthCrypt) and the
     *   cryptographic sender — the DID of the key actually used for key agreement, not the plaintext
     *   `from` header — must equal this value, or [IllegalArgumentException] is thrown. The plaintext
     *   `from` is additionally checked for consistency, but is never trusted on its own (under
     *   AnonCrypt it is attacker-controlled). Blank means "no sender expectation": anonymous
     *   (AnonCrypt) envelopes decrypt with [DidCommDecryptResult.authenticatedSenderDid] = `null`.
     * @return Decrypted message JSON plus the authenticated sender DID (AuthCrypt only)
     */
    suspend fun decrypt(
        envelope: DidCommEnvelope,
        recipientDid: String,
        recipientKeyId: String,
        senderDid: String
    ): DidCommDecryptResult
}
