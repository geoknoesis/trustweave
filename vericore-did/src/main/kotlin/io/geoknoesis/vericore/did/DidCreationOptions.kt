package io.geoknoesis.vericore.did

/**
 * Type-safe options for DID creation.
 * 
 * Replaces the error-prone `Map<String, Any?>` pattern with compile-time type safety.
 * 
 * **Example Usage:**
 * ```kotlin
 * // Using defaults
 * val options = DidCreationOptions()
 * 
 * // Custom configuration
 * val options = DidCreationOptions(
 *     algorithm = KeyAlgorithm.SECP256K1,
 *     purposes = listOf(KeyPurpose.AUTHENTICATION, KeyPurpose.ASSERTION)
 * )
 * 
 * val document = didMethod.createDid(options)
 * ```
 * 
 * @property algorithm Cryptographic algorithm for key generation
 * @property purposes Key purposes (authentication, assertion, etc.)
 * @property additionalProperties Additional method-specific properties
 */
data class DidCreationOptions(
    val algorithm: KeyAlgorithm = KeyAlgorithm.ED25519,
    val purposes: List<KeyPurpose> = listOf(KeyPurpose.AUTHENTICATION),
    val additionalProperties: Map<String, Any?> = emptyMap()
) {
    
    /**
     * Converts to Map format for backward compatibility with existing DID methods.
     * 
     * @return Map representation of options
     */
    fun toMap(): Map<String, Any?> {
        return buildMap {
            put("algorithm", algorithm.algorithmName)
            put("purposes", purposes.map { it.purposeName })
            putAll(additionalProperties)
        }
    }
    
    /**
     * Supported cryptographic algorithms for DID key generation.
     */
    enum class KeyAlgorithm(val algorithmName: String) {
        /** Ed25519 signature algorithm (recommended) */
        ED25519("Ed25519"),
        
        /** secp256k1 (Bitcoin/Ethereum curve) */
        SECP256K1("secp256k1"),
        
        /** P-256 (NIST curve) */
        P256("P-256"),
        
        /** P-384 (NIST curve) */
        P384("P-384"),
        
        /** P-521 (NIST curve) */
        P521("P-521");
        
        companion object {
            /**
             * Gets algorithm by name (case-insensitive).
             * 
             * @param name Algorithm name
             * @return KeyAlgorithm or null if not found
             */
            fun fromName(name: String): KeyAlgorithm? {
                return values().firstOrNull { 
                    it.algorithmName.equals(name, ignoreCase = true) 
                }
            }
        }
    }
    
    /**
     * Key purposes as defined in DID Core spec.
     */
    enum class KeyPurpose(val purposeName: String) {
        /** For authentication (proving control of DID) */
        AUTHENTICATION("authentication"),
        
        /** For making assertions (issuing credentials) */
        ASSERTION("assertionMethod"),
        
        /** For key agreement (encryption) */
        KEY_AGREEMENT("keyAgreement"),
        
        /** For invoking capabilities */
        CAPABILITY_INVOCATION("capabilityInvocation"),
        
        /** For delegating capabilities */
        CAPABILITY_DELEGATION("capabilityDelegation");
        
        companion object {
            /**
             * Gets purpose by name (case-insensitive).
             * 
             * @param name Purpose name
             * @return KeyPurpose or null if not found
             */
            fun fromName(name: String): KeyPurpose? {
                return values().firstOrNull { 
                    it.purposeName.equals(name, ignoreCase = true) 
                }
            }
        }
    }
    
    companion object {
        /**
         * Creates options from legacy Map format.
         * 
         * For backward compatibility with existing code.
         * 
         * @param map Legacy map options
         * @return DidCreationOptions instance
         */
        fun fromMap(map: Map<String, Any?>): DidCreationOptions {
            val algorithmName = map["algorithm"] as? String ?: "Ed25519"
            val algorithm = KeyAlgorithm.fromName(algorithmName) ?: KeyAlgorithm.ED25519
            
            val purposeNames = when (val purposes = map["purposes"]) {
                is List<*> -> purposes.filterIsInstance<String>()
                is String -> listOf(purposes)
                else -> emptyList()
            }
            
            val purposes = purposeNames.mapNotNull { KeyPurpose.fromName(it) }
                .ifEmpty { listOf(KeyPurpose.AUTHENTICATION) }
            
            val additionalProperties = map.filterKeys { it !in setOf("algorithm", "purposes") }
            
            return DidCreationOptions(
                algorithm = algorithm,
                purposes = purposes,
                additionalProperties = additionalProperties
            )
        }
    }
}

/**
 * Extension function to convert DidCreationOptions to Map for backward compatibility.
 */
fun DidCreationOptions.asMap(): Map<String, Any?> = toMap()

/**
 * Builder function for creating DidCreationOptions with a fluent API.
 * 
 * **Example:**
 * ```kotlin
 * val options = didCreationOptions {
 *     algorithm = KeyAlgorithm.ED25519
 *     purpose(KeyPurpose.AUTHENTICATION)
 *     purpose(KeyPurpose.ASSERTION)
 *     property("customKey", "customValue")
 * }
 * ```
 */
fun didCreationOptions(block: DidCreationOptionsBuilder.() -> Unit): DidCreationOptions {
    val builder = DidCreationOptionsBuilder()
    builder.block()
    return builder.build()
}

/**
 * Convenience extension for creating a DID using a builder DSL.
 */
suspend fun DidMethod.createDid(
    configure: DidCreationOptionsBuilder.() -> Unit
): DidDocument = createDid(didCreationOptions(configure))

/**
 * Builder for DidCreationOptions.
 */
class DidCreationOptionsBuilder {
    var algorithm: DidCreationOptions.KeyAlgorithm = DidCreationOptions.KeyAlgorithm.ED25519
    private val purposes = mutableListOf<DidCreationOptions.KeyPurpose>()
    private val properties = mutableMapOf<String, Any?>()
    
    /**
     * Adds a key purpose.
     */
    fun purpose(purpose: DidCreationOptions.KeyPurpose) {
        purposes.add(purpose)
    }
    
    /**
     * Adds multiple key purposes.
     */
    fun purposes(vararg purposes: DidCreationOptions.KeyPurpose) {
        this.purposes.addAll(purposes)
    }
    
    /**
     * Adds a custom property.
     */
    fun property(key: String, value: Any?) {
        properties[key] = value
    }
    
    /**
     * Builds the options.
     */
    fun build(): DidCreationOptions {
        return DidCreationOptions(
            algorithm = algorithm,
            purposes = if (purposes.isEmpty()) listOf(DidCreationOptions.KeyPurpose.AUTHENTICATION) else purposes,
            additionalProperties = properties
        )
    }
}

