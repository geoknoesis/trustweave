package com.trustweave.credential.didcomm.protocol

import com.trustweave.credential.didcomm.models.DidCommMessage
import com.trustweave.credential.didcomm.models.DidCommMessageTypes
import kotlinx.serialization.json.*
import java.time.Instant
import java.util.*

/**
 * Helper functions for Basic Message Protocol.
 * 
 * Basic messages are simple text messages for general communication.
 */
object BasicMessageProtocol {
    /**
     * Creates a basic message.
     * 
     * @param fromDid Sender DID
     * @param toDid Recipient DID
     * @param content Message content
     * @param locale Optional locale (e.g., "en", "es")
     * @param thid Optional thread ID
     * @return Basic message
     */
    fun createBasicMessage(
        fromDid: String,
        toDid: String,
        content: String,
        locale: String? = null,
        thid: String? = null
    ): DidCommMessage {
        val body = buildJsonObject {
            put("content", content)
            locale?.let { put("locale", it) }
        }
        
        return DidCommMessage(
            id = UUID.randomUUID().toString(),
            type = DidCommMessageTypes.BASIC_MESSAGE,
            from = fromDid,
            to = listOf(toDid),
            body = body,
            created = Instant.now().toString(),
            thid = thid
        )
    }

    /**
     * Extracts content from a basic message.
     * 
     * @param message The basic message
     * @return The message content, or null if not found
     */
    fun extractContent(message: DidCommMessage): String? {
        return message.body["content"]?.jsonPrimitive?.content
    }
}

