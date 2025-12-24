package org.trustweave.godiddy.spi

import org.trustweave.did.DidCreationOptions
import org.trustweave.did.DidMethod
import org.trustweave.did.spi.DidMethodProvider
import org.trustweave.godiddy.GodiddyClient
import org.trustweave.godiddy.GodiddyConfig
import org.trustweave.godiddy.did.GodiddyDidMethod
import org.trustweave.godiddy.issuer.GodiddyIssuer
import org.trustweave.godiddy.registrar.GodiddyRegistrar
import org.trustweave.godiddy.resolver.GodiddyResolver
import org.trustweave.godiddy.verifier.GodiddyVerifier

/**
 * SPI provider for godiddy DID methods.
 * Creates DidMethod instances that use Universal Resolver and Universal Registrar.
 */
class GodiddyDidMethodProvider : DidMethodProvider {

    override val name: String = "godiddy"

    /**
     * List of DID methods supported by Universal Resolver.
     * This is a representative list - Universal Resolver supports many more methods.
     * In practice, you could query the resolver for supported methods dynamically.
     */
    override val supportedMethods: List<String> = listOf(
        "key", "web", "ion", "algo", "ethr", "polygonid", "cheqd", "dock", "indy", "jwk",
        "peer", "plc", "pkh", "polygon", "eosio", "hedera", "everscale", "tezos",
        "tz", "v1", "v3", "web3", "zksync", "sol", "near", "ens", "ensip10",
        "ensip11", "ensip12", "ensip13", "ensip14", "ensip15", "ensip16", "ensip17",
        "ensip18", "ensip19", "ensip20", "ensip21", "ensip22", "ensip23", "ensip24",
        "ensip25", "ensip26", "ensip27", "ensip28", "ensip29", "ensip30"
    )

    override fun create(methodName: String, options: DidCreationOptions): DidMethod? {
        // Create configuration from options
        val config = GodiddyConfig.fromOptions(options)
        val client = GodiddyClient(config)

        // Create resolver (always available)
        val resolver = GodiddyResolver(client)

        // Create registrar (may not be available for all methods)
        // For now, we'll create it - in practice, you might want to check if the method supports registration
        val registrar = try {
            GodiddyRegistrar(client)
        } catch (e: Exception) {
            null // Registrar not available
        }

        // Create DID method instance
        return GodiddyDidMethod(
            method = methodName,
            resolver = resolver,
            registrar = registrar
        )
    }
}

