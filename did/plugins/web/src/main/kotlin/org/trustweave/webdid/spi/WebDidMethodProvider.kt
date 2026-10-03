package org.trustweave.webdid.spi

import okhttp3.OkHttpClient
import org.trustweave.did.DidCreationOptions
import org.trustweave.did.DidMethod
import org.trustweave.did.base.AbstractDidMethodProvider
import org.trustweave.webdid.WebDidConfig
import org.trustweave.webdid.WebDidMethod

/**
 * SPI provider for did:web method.
 *
 * Automatically discovers did:web method when this module is on the classpath.
 */
class WebDidMethodProvider : AbstractDidMethodProvider() {
    override val name: String = "web"

    override val supportedMethods: List<String> = listOf("web")

    override fun create(
        methodName: String,
        options: DidCreationOptions,
    ): DidMethod? {
        if (methodName.lowercase() != "web") return null
        return WebDidMethod(resolveKms(options), createHttpClient(options), createConfig(options))
    }

    /**
     * Creates an OkHttpClient from options.
     */
    private fun createHttpClient(options: DidCreationOptions): OkHttpClient {
        val builder = OkHttpClient.Builder()

        // Configure timeout from options
        val timeoutSeconds = options.additionalProperties["timeoutSeconds"] as? Int ?: 30
        val timeoutMs = timeoutSeconds * 1000L

        builder.connectTimeout(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
        builder.readTimeout(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
        builder.writeTimeout(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)

        // Never let the client follow redirects on its own: an automatic redirect would skip the
        // SSRF guard and HTTPS check for the target. WebDidMethod follows redirects manually
        // (re-checking every hop) when WebDidConfig.followRedirects is enabled.
        builder.followRedirects(false)
        builder.followSslRedirects(false)

        return builder.build()
    }

    /**
     * Creates WebDidConfig from options.
     */
    private fun createConfig(options: DidCreationOptions): WebDidConfig {
        val configMap =
            options.additionalProperties.filterKeys {
                it in setOf("requireHttps", "documentPath", "timeoutSeconds", "followRedirects", WebDidMethod.MAX_STALE_CACHE_SECONDS)
            }

        if (configMap.isEmpty()) {
            return WebDidConfig.default()
        }

        return WebDidConfig.fromMap(
            configMap +
                options.additionalProperties.filterKeys {
                    it !in setOf("requireHttps", "documentPath", "timeoutSeconds", "followRedirects", "kms")
                },
        )
    }
}
