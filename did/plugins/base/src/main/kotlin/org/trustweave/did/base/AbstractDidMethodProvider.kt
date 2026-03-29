package org.trustweave.did.base

import org.trustweave.did.DidCreationOptions
import org.trustweave.did.spi.DidMethodProvider
import org.trustweave.kms.KeyManagementService
import org.trustweave.kms.spi.KeyManagementServiceProvider
import java.util.ServiceLoader

/**
 * Abstract base for [DidMethodProvider] implementations that require a KMS.
 *
 * Eliminates the copy-pasted KMS resolution boilerplate found in every DID method provider.
 * The [resolveKms] helper first checks `options.additionalProperties["kms"]` for a directly
 * injected instance; if absent it auto-discovers a provider via Java ServiceLoader.
 *
 * Usage:
 * ```kotlin
 * class KeyDidMethodProvider : AbstractDidMethodProvider() {
 *     override val name = "key"
 *     override val supportedMethods = listOf("key")
 *
 *     override fun create(methodName: String, options: DidCreationOptions) =
 *         if (methodName != "key") null
 *         else KeyDidMethod(resolveKms(options))
 * }
 * ```
 */
abstract class AbstractDidMethodProvider : DidMethodProvider {

    /**
     * Resolves a [KeyManagementService] from the given [options].
     *
     * Resolution order:
     * 1. `options.additionalProperties["kms"]` — directly injected instance (takes priority)
     * 2. ServiceLoader discovery of [KeyManagementServiceProvider] on the classpath
     *
     * @throws IllegalStateException if no KMS can be found by either mechanism
     */
    protected fun resolveKms(options: DidCreationOptions): KeyManagementService {
        return (options.additionalProperties["kms"] as? KeyManagementService)
            ?: run {
                ServiceLoader.load(KeyManagementServiceProvider::class.java)
                    .firstOrNull()
                    ?.create(options.additionalProperties)
                    ?: throw IllegalStateException(
                        "No KeyManagementService available. " +
                            "Provide 'kms' in options or ensure a KMS provider is on the classpath."
                    )
            }
    }
}
