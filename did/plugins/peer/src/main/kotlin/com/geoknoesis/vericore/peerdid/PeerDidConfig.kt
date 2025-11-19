package com.geoknoesis.vericore.peerdid

/**
 * Configuration for did:peer method implementation.
 * 
 * Supports peer DIDs for peer-to-peer communication without external registries.
 * 
 * **Example Usage:**
 * ```kotlin
 * val config = PeerDidConfig.builder()
 *     .numalgo(2) // Use numalgo 2 (short-form with multibase)
 *     .build()
 * ```
 */
data class PeerDidConfig(
    /**
     * Numalgo version to use (0, 1, or 2).
     * - 0: Static numeric algorithm
     * - 1: Short-form with inception key
     * - 2: Short-form with multibase encoding (recommended)
     */
    val numalgo: Int = 2,
    
    /**
     * Whether to include service endpoints in DID document.
     */
    val includeServices: Boolean = true,
    
    /**
     * Additional configuration properties.
     */
    val additionalProperties: Map<String, Any?> = emptyMap()
) {
    
    companion object {
        /**
         * Numalgo versions.
         */
        const val NUMALGO_0 = 0 // Static numeric
        const val NUMALGO_1 = 1 // Short-form with inception key
        const val NUMALGO_2 = 2 // Short-form with multibase (recommended)
        
        /**
         * Creates configuration with numalgo 0.
         */
        fun numalgo0(): PeerDidConfig {
            return PeerDidConfig(numalgo = NUMALGO_0)
        }
        
        /**
         * Creates configuration with numalgo 1.
         */
        fun numalgo1(): PeerDidConfig {
            return PeerDidConfig(numalgo = NUMALGO_1)
        }
        
        /**
         * Creates configuration with numalgo 2 (recommended).
         */
        fun numalgo2(): PeerDidConfig {
            return PeerDidConfig(numalgo = NUMALGO_2)
        }
        
        /**
         * Creates configuration from a map (for backward compatibility).
         */
        fun fromMap(map: Map<String, Any?>): PeerDidConfig {
            return PeerDidConfig(
                numalgo = map["numalgo"] as? Int ?: NUMALGO_2,
                includeServices = map["includeServices"] as? Boolean ?: true,
                additionalProperties = map.filterKeys {
                    it !in setOf("numalgo", "includeServices")
                }
            )
        }
        
        /**
         * Builder for PeerDidConfig.
         */
        fun builder(): Builder {
            return Builder()
        }
    }
    
    /**
     * Builder for PeerDidConfig.
     */
    class Builder {
        private var numalgo: Int = NUMALGO_2
        private var includeServices: Boolean = true
        private val additionalProperties = mutableMapOf<String, Any?>()
        
        fun numalgo(value: Int): Builder {
            require(value in 0..2) { "Numalgo must be 0, 1, or 2" }
            this.numalgo = value
            return this
        }
        
        fun includeServices(value: Boolean): Builder {
            this.includeServices = value
            return this
        }
        
        fun property(key: String, value: Any?): Builder {
            this.additionalProperties[key] = value
            return this
        }
        
        fun build(): PeerDidConfig {
            return PeerDidConfig(
                numalgo = numalgo,
                includeServices = includeServices,
                additionalProperties = additionalProperties.toMap()
            )
        }
    }
    
    /**
     * Converts to map format.
     */
    fun toMap(): Map<String, Any?> {
        return buildMap {
            put("numalgo", numalgo)
            put("includeServices", includeServices)
            putAll(additionalProperties)
        }
    }
}

