package org.trustweave.did.dsl

import org.trustweave.did.resolver.DefaultUniversalResolver
import org.trustweave.did.resolver.RetryConfig
import org.trustweave.did.resolver.StandardUniversalResolverAdapter
import org.trustweave.did.resolver.UniversalResolverProtocolAdapter
import kotlin.DslMarker

/**
 * Builder DSL for creating [DefaultUniversalResolver] instances.
 *
 * Provides a fluent, idiomatic Kotlin API for resolver configuration.
 *
 * **Example Usage:**
 * ```kotlin
 * val resolver = universalResolver("https://dev.uniresolver.io") {
 *     timeout = 60
 *     apiKey = "my-api-key"
 *     retry {
 *         maxRetries = 3
 *         initialDelayMs = 200
 *     }
 *     protocolAdapter = StandardUniversalResolverAdapter()
 * }
 * ```
 */
@DslMarker
annotation class UniversalResolverDsl

/**
 * Creates a [DefaultUniversalResolver] using a builder DSL.
 */
inline fun universalResolver(
    baseUrl: String,
    block: UniversalResolverBuilder.() -> Unit = {}
): DefaultUniversalResolver {
    val builder = UniversalResolverBuilder(baseUrl)
    builder.block()
    return builder.build()
}

/**
 * Builder for [DefaultUniversalResolver].
 */
@UniversalResolverDsl
class UniversalResolverBuilder(
    val baseUrl: String
) {
    var timeout: Int = 30
    var apiKey: String? = null
    var protocolAdapter: UniversalResolverProtocolAdapter = StandardUniversalResolverAdapter()
    private var retryConfig: RetryConfig? = null

    /**
     * Configures retry behavior.
     */
    fun retry(block: RetryConfigBuilder.() -> Unit) {
        val retryBuilder = RetryConfigBuilder()
        retryBuilder.block()
        retryConfig = retryBuilder.build()
    }

    fun build(): DefaultUniversalResolver {
        return DefaultUniversalResolver(
            baseUrl = baseUrl,
            timeout = timeout,
            apiKey = apiKey,
            protocolAdapter = protocolAdapter,
            retryConfig = retryConfig ?: RetryConfig.default()
        )
    }
}

/**
 * Builder for [RetryConfig].
 */
@UniversalResolverDsl
class RetryConfigBuilder {
    var maxRetries: Int = 3
    var initialDelayMs: Long = 100
    var maxDelayMs: Long = 2000
    var retryableStatusCodes: Set<Int> = setOf(500, 502, 503, 504)

    fun build(): RetryConfig {
        return RetryConfig(
            maxRetries = maxRetries,
            initialDelayMs = initialDelayMs,
            maxDelayMs = maxDelayMs,
            retryableStatusCodes = retryableStatusCodes,
            retryableExceptions = setOf(
                java.net.ConnectException::class.java,
                java.net.SocketTimeoutException::class.java,
                java.io.IOException::class.java
            )
        )
    }
}

