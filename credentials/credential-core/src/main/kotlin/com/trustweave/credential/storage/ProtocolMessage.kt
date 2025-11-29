package com.trustweave.credential.storage

/**
 * Generic protocol message interface.
 *
 * All protocol messages (DIDComm, OIDC4VCI, CHAPI, etc.) should implement
 * this interface to enable generic storage, search, and analytics.
 *
 * **Example Usage:**
 * ```kotlin
 * data class DidCommMessage(
 *     val id: String,
 *     val type: String,
 *     // ... other fields
 * ) : ProtocolMessage {
 *     override val messageId: String get() = id
 *     override val messageType: String get() = type
 *     // ... implement other properties
 * }
 * ```
 */
interface ProtocolMessage {
    /**
     * Unique message identifier.
     */
    val messageId: String

    /**
     * Message type (protocol-specific).
     */
    val messageType: String

    /**
     * Sender identifier (DID, URL, etc.).
     */
    val from: String?

    /**
     * List of recipient identifiers.
     */
    val to: List<String>

    /**
     * Creation timestamp (ISO 8601).
     */
    val created: String?

    /**
     * Expiration timestamp (ISO 8601).
     */
    val expiresTime: String?

    /**
     * Thread/conversation identifier.
     */
    val threadId: String?

    /**
     * Parent thread identifier (for threading).
     */
    val parentThreadId: String?
}

