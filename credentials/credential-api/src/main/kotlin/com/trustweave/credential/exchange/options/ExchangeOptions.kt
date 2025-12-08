package com.trustweave.credential.exchange.options

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * Exchange operation options.
 * 
 * Provides a type-safe way to pass protocol-specific options while keeping
 * the API protocol-agnostic.
 * 
 * **Design Principles:**
 * 1. Protocol-agnostic base with common options
 * 2. Protocol-specific options via extensions or metadata map
 * 3. Type-safe accessors for common fields
 * 4. Builder pattern for ergonomic construction
 * 
 * **Usage:**
 * ```kotlin
 * // Using builder
 * val options = ExchangeOptions.builder()
 *     .timeout(30.seconds)
 *     .addMetadata("goalCode", JsonPrimitive("issue-vc"))
 *     .build()
 * 
 * // Using protocol-specific extensions
 * val didCommOptions = options.asDidComm()  // Extension in plugin
 * ```
 */
data class ExchangeOptions(
    /**
     * Operation timeout.
     */
    val timeoutMillis: Long? = null,
    
    /**
     * Whether to require acknowledgment.
     */
    val requireAck: Boolean = false,
    
    /**
     * Thread ID for message threading.
     */
    val threadId: String? = null,
    
    /**
     * Protocol-specific metadata.
     * 
     * Protocols can store their specific options here (e.g., goalCode for DIDComm,
     * scopes for OIDC4VCI). Access via extension functions in protocol plugins.
     */
    val metadata: Map<String, JsonElement> = emptyMap()
) {
    /**
     * Get metadata as JsonObject.
     */
    fun metadataAsJsonObject(): JsonObject {
        return if (metadata.isEmpty()) {
            JsonObject(emptyMap())
        } else {
            buildJsonObject {
                metadata.forEach { (key, value) ->
                    put(key, value)
                }
            }
        }
    }
    
    /**
     * Get a metadata value by key.
     */
    fun getMetadata(key: String): JsonElement? = metadata[key]
    
    /**
     * Get a metadata value as string.
     */
    fun getMetadataString(key: String): String? = 
        metadata[key]?.let { 
            if (it is kotlinx.serialization.json.JsonPrimitive) it.content else null
        }
    
    /**
     * Check if metadata contains a key.
     */
    fun hasMetadata(key: String): Boolean = metadata.containsKey(key)
    
    companion object {
        /**
         * Empty options.
         */
        val Empty = ExchangeOptions()
        
        /**
         * Create a builder.
         */
        fun builder(): Builder = Builder()
        
        /**
         * Create from metadata map.
         */
        fun fromMetadata(metadata: Map<String, JsonElement>): ExchangeOptions =
            ExchangeOptions(metadata = metadata)
    }
    
    /**
     * Builder for ExchangeOptions.
     */
    class Builder {
        private var timeoutMillis: Long? = null
        private var requireAck: Boolean = false
        private var threadId: String? = null
        private val metadata = mutableMapOf<String, JsonElement>()
        
        /**
         * Set timeout in milliseconds.
         */
        fun timeoutMillis(value: Long): Builder {
            this.timeoutMillis = value
            return this
        }
        
        /**
         * Set timeout in seconds.
         */
        fun timeoutSeconds(value: Long): Builder {
            this.timeoutMillis = value * 1000
            return this
        }
        
        /**
         * Set timeout as Duration.
         */
        fun timeout(value: java.time.Duration): Builder {
            this.timeoutMillis = value.toMillis()
            return this
        }
        
        /**
         * Set timeout as kotlin.time.Duration.
         */
        fun timeout(value: kotlin.time.Duration): Builder {
            this.timeoutMillis = value.inWholeMilliseconds
            return this
        }
        
        /**
         * Require acknowledgment.
         */
        fun requireAck(value: Boolean = true): Builder {
            this.requireAck = value
            return this
        }
        
        /**
         * Set thread ID.
         */
        fun threadId(value: String): Builder {
            this.threadId = value
            return this
        }
        
        /**
         * Add metadata entry.
         */
        fun addMetadata(key: String, value: JsonElement): Builder {
            this.metadata[key] = value
            return this
        }
        
        /**
         * Add metadata entry as string.
         */
        fun addMetadata(key: String, value: String): Builder {
            this.metadata[key] = kotlinx.serialization.json.JsonPrimitive(value)
            return this
        }
        
        /**
         * Add metadata entry as number.
         */
        fun addMetadata(key: String, value: Number): Builder {
            this.metadata[key] = kotlinx.serialization.json.JsonPrimitive(value)
            return this
        }
        
        /**
         * Add metadata entry as boolean.
         */
        fun addMetadata(key: String, value: Boolean): Builder {
            this.metadata[key] = kotlinx.serialization.json.JsonPrimitive(value)
            return this
        }
        
        /**
         * Add all metadata entries.
         */
        fun addAllMetadata(metadata: Map<String, JsonElement>): Builder {
            this.metadata.putAll(metadata)
            return this
        }
        
        /**
         * Build the ExchangeOptions instance.
         */
        fun build(): ExchangeOptions = ExchangeOptions(
            timeoutMillis = timeoutMillis,
            requireAck = requireAck,
            threadId = threadId,
            metadata = metadata.toMap()
        )
    }
}

/**
 * Extension function to merge options.
 */
fun ExchangeOptions.merge(other: ExchangeOptions): ExchangeOptions {
    return ExchangeOptions(
        timeoutMillis = other.timeoutMillis ?: this.timeoutMillis,
        requireAck = other.requireAck || this.requireAck,
        threadId = other.threadId ?: this.threadId,
        metadata = (this.metadata + other.metadata).toMap()
    )
}

