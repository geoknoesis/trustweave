package com.trustweave.webdid.spi

import com.trustweave.did.DidCreationOptions
import com.trustweave.did.DidMethod
import com.trustweave.did.spi.DidMethodProvider
import com.trustweave.kms.KeyManagementService
import com.trustweave.webdid.WebDidConfig
import com.trustweave.webdid.WebDidMethod
import okhttp3.OkHttpClient
import java.util.ServiceLoader

/**
 * SPI provider for did:web method.
 *
 * Automatically discovers did:web method when this module is on the classpath.
 */
class WebDidMethodProvider : DidMethodProvider {

    override val name: String = "web"

    override val supportedMethods: List<String> = listOf("web")

    override fun create(methodName: String, options: DidCreationOptions): DidMethod? {
        if (methodName.lowercase() != "web") {
            return null
        }

        // Get KMS from options or discover via SPI
        val kms = (options.additionalProperties["kms"] as? KeyManagementService)
            ?: run {
                val kmsProviders = ServiceLoader.load(
                    com.trustweave.kms.spi.KeyManagementServiceProvider::class.java
                )
                kmsProviders.firstOrNull()?.create(options.additionalProperties)
                    ?: throw IllegalStateException(
                        "No KeyManagementService available. Provide 'kms' in options or ensure a KMS provider is registered."
                    )
            }

        // Create HTTP client
        val httpClient = createHttpClient(options)

        // Create configuration from options
        val config = createConfig(options)

        return WebDidMethod(kms, httpClient, config)
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

        // Configure redirect following
        val followRedirects = options.additionalProperties["followRedirects"] as? Boolean ?: true
        builder.followRedirects(followRedirects)
        builder.followSslRedirects(followRedirects)

        return builder.build()
    }

    /**
     * Creates WebDidConfig from options.
     */
    private fun createConfig(options: DidCreationOptions): WebDidConfig {
        val configMap = options.additionalProperties.filterKeys {
            it in setOf("requireHttps", "documentPath", "timeoutSeconds", "followRedirects")
        }

        if (configMap.isEmpty()) {
            return WebDidConfig.default()
        }

        return WebDidConfig.fromMap(
            configMap + options.additionalProperties.filterKeys {
                it !in setOf("requireHttps", "documentPath", "timeoutSeconds", "followRedirects", "kms")
            }
        )
    }
}

