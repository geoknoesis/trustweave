package org.trustweave.ebsidid.spi

import org.trustweave.did.DidCreationOptions
import org.trustweave.did.DidMethod
import org.trustweave.did.base.AbstractDidMethodProvider
import org.trustweave.ebsidid.EbsiDidConfig
import org.trustweave.ebsidid.EbsiDidMethod

/**
 * SPI provider for the did:ebsi method.
 *
 * Registers the EBSI DID method for automatic discovery via Java [java.util.ServiceLoader]
 * when this module is on the classpath.
 *
 * Configuration can be supplied via `options.additionalProperties`:
 * - `kms`           — a pre-configured [org.trustweave.kms.KeyManagementService] instance
 * - `apiBaseUrl`    — override the EBSI REST API base URL
 * - `network`       — EBSI network name (`PILOT`, `CONFORMANCE`, or `PRODUCTION`)
 * - `bearerToken`   — bearer token for write operations
 * - `timeoutSeconds`— HTTP timeout
 *
 * If no configuration is supplied the provider defaults to the EBSI pilot environment
 * in resolve-only mode (no bearer token).
 */
class EbsiDidMethodProvider : AbstractDidMethodProvider() {

    override val name: String = "ebsi"

    override val supportedMethods: List<String> = listOf("ebsi")

    override fun create(methodName: String, options: DidCreationOptions): DidMethod? {
        if (methodName.lowercase() != "ebsi") return null
        val config = EbsiDidConfig.fromMap(options.additionalProperties)
        return EbsiDidMethod(resolveKms(options), config)
    }
}
