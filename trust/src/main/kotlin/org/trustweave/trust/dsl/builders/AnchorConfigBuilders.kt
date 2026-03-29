package org.trustweave.trust.dsl.builders

/**
 * Anchor layer (blockchain) configuration builder.
 */
class AnchorConfigBuilder {
    val chains = mutableMapOf<String, AnchorConfig>()

    fun chain(chainId: String, block: AnchorChainConfigBuilder.() -> Unit) {
        chains[chainId] = AnchorChainConfigBuilder().apply(block).build()
    }
}

/**
 * Anchor configuration data.
 */
data class AnchorConfig(
    val provider: String? = null,
    val options: Map<String, Any?> = emptyMap()
)

/**
 * Builder for a single blockchain anchor chain configuration.
 */
class AnchorChainConfigBuilder {
    private var provider: String? = null
    private val options = mutableMapOf<String, Any?>()

    fun provider(name: String) {
        provider = name
    }

    fun inMemory(contract: String? = null) {
        provider = "inMemory"
        if (contract != null) {
            options["contract"] = contract
        }
    }

    fun options(block: OptionsBuilder.() -> Unit) {
        options.putAll(OptionsBuilder().apply(block).options)
    }

    fun build(): AnchorConfig = AnchorConfig(provider = provider, options = options)
}

/**
 * Generic options builder.
 */
class OptionsBuilder {
    val options = mutableMapOf<String, Any?>()

    infix fun String.to(value: Any?) {
        options[this] = value
    }
}
