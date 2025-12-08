package com.trustweave.did

import com.trustweave.did.model.DidDocument

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
 *     forAuthentication()
 *     forAssertion()
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
 *
 * Provides a fluent API for constructing DID creation options.
 *
 * **Example Usage:**
 * ```kotlin
 * val options = didCreationOptions {
 *     algorithm = KeyAlgorithm.ED25519
 *     forAuthentication()
 *     forAssertion()
 *     forKeyAgreement()
 * }
 * ```
 */
class DidCreationOptionsBuilder {
    var algorithm: KeyAlgorithm = KeyAlgorithm.ED25519
    private val purposes = mutableListOf<KeyPurpose>()
    private val properties = mutableMapOf<String, Any?>()

    /**
     * Adds a key purpose.
     */
    fun purpose(purpose: KeyPurpose) {
        purposes.add(purpose)
    }

    /**
     * Adds multiple key purposes.
     */
    fun purposes(vararg purposes: KeyPurpose) {
        this.purposes.addAll(purposes)
    }

    /**
     * Adds authentication purpose (fluent method).
     */
    fun forAuthentication() {
        purposes.add(KeyPurpose.AUTHENTICATION)
    }

    /**
     * Adds assertion purpose (fluent method).
     */
    fun forAssertion() {
        purposes.add(KeyPurpose.ASSERTION)
    }

    /**
     * Adds key agreement purpose (fluent method).
     */
    fun forKeyAgreement() {
        purposes.add(KeyPurpose.KEY_AGREEMENT)
    }

    /**
     * Adds capability invocation purpose (fluent method).
     */
    fun forCapabilityInvocation() {
        purposes.add(KeyPurpose.CAPABILITY_INVOCATION)
    }

    /**
     * Adds capability delegation purpose (fluent method).
     */
    fun forCapabilityDelegation() {
        purposes.add(KeyPurpose.CAPABILITY_DELEGATION)
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
            purposes = if (purposes.isEmpty()) listOf(KeyPurpose.AUTHENTICATION) else purposes,
            additionalProperties = properties
        )
    }
}

