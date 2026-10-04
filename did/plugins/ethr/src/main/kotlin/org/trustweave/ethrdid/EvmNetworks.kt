package org.trustweave.ethrdid

/**
 * Well-known EVM chain ids and the network names the did:ethr family uses for them, so a
 * configuration whose CAIP-2 chain id and network name disagree is rejected up front instead of
 * silently resolving one chain's DIDs against another chain's registry.
 */
object EvmNetworks {
    private val KNOWN: Map<Long, Set<String>> =
        mapOf(
            1L to setOf("mainnet"),
            5L to setOf("goerli"),
            11155111L to setOf("sepolia"),
            17000L to setOf("holesky"),
            137L to setOf("polygon", "mainnet"),
            80002L to setOf("amoy"),
            80001L to setOf("mumbai"),
        )

    /**
     * Throws [IllegalArgumentException] when [chainId] (`eip155:<n>`) is a known chain and
     * [network] is set to a name that does not belong to it. Unknown chain ids and a null network
     * are not determinable and pass.
     */
    @JvmStatic
    fun requireConsistent(
        chainId: String,
        network: String?,
        what: String,
    ) {
        if (network == null) return
        val id = chainId.removePrefix("eip155:").toLongOrNull() ?: return
        val expected = KNOWN[id] ?: return
        require(network.lowercase() in expected) {
            "$what is configured with chainId $chainId but network '$network' (expected one of $expected)"
        }
    }
}
