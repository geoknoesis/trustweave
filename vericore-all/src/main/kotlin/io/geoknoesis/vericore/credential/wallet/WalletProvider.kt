package io.geoknoesis.vericore.credential.wallet

/**
 * Strongly typed wallet provider identifier used when creating wallets through the
 * VeriCore facade. This replaces stringly-typed provider names with well-defined
 * options while still allowing custom providers to be addressed when needed.
 */
sealed class WalletProvider private constructor(internal val id: String) {

    /**
     * Default in-memory wallet provider intended for evaluation and tests.
     */
    object InMemory : WalletProvider("inMemory") {
        override fun toString(): String = "InMemory"
    }

    /**
     * Represents a custom provider name. Use this when integrating external wallet
     * factories that register additional providers.
     */
    class Custom private constructor(private val providerName: String) : WalletProvider(providerName) {
        val name: String get() = providerName
        override fun toString(): String = providerName

        companion object {
            internal fun of(providerName: String): Custom = Custom(providerName)
        }
    }

    companion object {
        /**
         * Factory method that resolves well-known providers and falls back to [Custom].
         */
        fun named(providerName: String): WalletProvider = when (providerName.lowercase()) {
            InMemory.id.lowercase() -> InMemory
            else -> Custom.of(providerName)
        }

        /**
         * Creates a [Custom] provider wrapping the supplied provider name.
         */
        fun custom(providerName: String): WalletProvider = Custom.of(providerName)
    }
}


