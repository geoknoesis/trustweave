package org.trustweave.did.orb

import org.trustweave.did.DidCreationOptions
import org.trustweave.did.DidMethod
import org.trustweave.did.base.AbstractDidMethodProvider

/**
 * SPI provider for the did:orb method.
 *
 * Discovered via Java [java.util.ServiceLoader] from
 * `META-INF/services/org.trustweave.did.spi.DidMethodProvider`.
 *
 * Required configuration keys in [DidCreationOptions.additionalProperties]:
 *  - `baseUrl` (String) — Orb node base URL, e.g. `https://orb.example.com`.
 *
 * Optional keys: `namespace`, `operationsPath`, `identifiersPath`,
 * `authHeaderName`, `authHeaderValue`, `timeoutSeconds`. See [OrbDidConfig.fromMap].
 */
class OrbIntegration : AbstractDidMethodProvider() {

    override val name: String = "orb"

    override val supportedMethods: List<String> = listOf("orb")

    override val requiredEnvironmentVariables: List<String> = listOf("?ORB_BASE_URL")

    override fun create(methodName: String, options: DidCreationOptions): DidMethod? {
        if (methodName.lowercase() != "orb") return null
        val effective = mergeBaseUrlFromEnv(options.additionalProperties)
        val config = OrbDidConfig.fromMap(effective)
        return OrbDidMethod(resolveKms(options), config)
    }

    /**
     * If `baseUrl` is absent from options but `ORB_BASE_URL` is set in the
     * environment, inject it. This keeps the SPI usable in test environments
     * that pass an empty options map.
     */
    private fun mergeBaseUrlFromEnv(map: Map<String, Any?>): Map<String, Any?> {
        if (map.containsKey("baseUrl") || map.containsKey("orbBaseUrl")) return map
        val envUrl = System.getenv("ORB_BASE_URL") ?: return map
        return map + ("baseUrl" to envUrl)
    }
}
