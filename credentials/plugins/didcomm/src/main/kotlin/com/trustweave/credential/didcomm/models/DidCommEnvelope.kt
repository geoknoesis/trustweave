package com.trustweave.credential.didcomm.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * DIDComm V2 encrypted envelope (JWM format).
 *
 * The envelope contains:
 * - protected: Base64URL-encoded protected headers
 * - recipients: Array of recipient information (encrypted keys)
 * - iv: Initialization vector for encryption
 * - ciphertext: Encrypted message content
 * - tag: Authentication tag
 *
 * This follows the JWM (JSON Web Message) specification used by DIDComm V2.
 */
@Serializable
data class DidCommEnvelope(
    val protected: String, // Base64URL-encoded protected headers
    val recipients: List<DidCommRecipient>,
    val iv: String, // Base64URL-encoded IV
    val ciphertext: String, // Base64URL-encoded encrypted content
    val tag: String // Base64URL-encoded authentication tag
)

/**
 * Recipient information in an encrypted envelope.
 */
@Serializable
data class DidCommRecipient(
    val header: DidCommRecipientHeader,
    val encrypted_key: String // Base64URL-encoded encrypted key
)

/**
 * Recipient header containing key information.
 */
@Serializable
data class DidCommRecipientHeader(
    val kid: String, // Key ID (verification method reference)
    val alg: String = "ECDH-1PU+A256KW", // Key agreement algorithm
    val epk: JsonObject? = null // Ephemeral public key (JWK)
)

/**
 * Protected headers for JWM envelope.
 */
data class DidCommProtectedHeaders(
    val typ: String = "application/didcomm-encrypted+json",
    val alg: String = "ECDH-1PU+A256KW",
    val enc: String = "A256GCM",
    val skid: String? = null // Sender key ID
)

