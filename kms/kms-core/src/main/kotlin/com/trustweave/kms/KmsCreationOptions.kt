package com.trustweave.kms

/**
 * Structured configuration passed to [com.trustweave.kms.spi.KeyManagementServiceProvider.create].
 *
 * Keeps common toggles explicit while still allowing provider-specific
 * properties through [additionalProperties].
 *
 * **Example:**
 * ```kotlin
 * val options = KmsCreationOptions(
 *     enabled = true,
 *     priority = 10,
 *     additionalProperties = mapOf("region" to "us-east-1")
 * )
 * ```
 */
data class KmsCreationOptions(
    val enabled: Boolean = true,
    val priority: Int? = null,
    val additionalProperties: Map<String, Any?> = emptyMap()
) {
    /**
     * Convert to legacy Map format for backward compatibility.
     */
    fun toMap(): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        if (!enabled) map["enabled"] = false
        priority?.let { map["priority"] = it }
        map.putAll(additionalProperties)
        return map
    }
}

/**
 * Builder for KmsCreationOptions.
 */
class KmsCreationOptionsBuilder {
    var enabled: Boolean = true
    var priority: Int? = null
    private val properties = mutableMapOf<String, Any?>()

    /**
     * Add a provider-specific property.
     */
    fun property(key: String, value: Any?) {
        properties[key] = value
    }

    fun build(): KmsCreationOptions =
        KmsCreationOptions(
            enabled = enabled,
            priority = priority,
            additionalProperties = properties.toMap()
        )
}

/**
 * DSL builder function for KmsCreationOptions.
 */
fun kmsCreationOptions(
    block: KmsCreationOptionsBuilder.() -> Unit
): KmsCreationOptions {
    val builder = KmsCreationOptionsBuilder()
    builder.block()
    return builder.build()
}

