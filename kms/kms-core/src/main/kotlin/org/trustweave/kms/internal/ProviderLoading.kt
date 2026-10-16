package org.trustweave.kms.internal

import org.trustweave.kms.spi.KeyManagementServiceProvider
import java.util.ServiceConfigurationError
import java.util.ServiceLoader

/**
 * Loads [KeyManagementServiceProvider]s via [ServiceLoader], tolerating broken providers.
 *
 * A broken provider jar (bad `META-INF/services` entry, missing class, failing static
 * initializer) surfaces as a [ServiceConfigurationError] from the loader's iterator;
 * `toList()` would let that one error make every KMS creation and discovery fail. Each
 * iterator step is therefore guarded and loading continues with the next provider
 * (ServiceLoader's iterator makes a best effort to do so after an error).
 */
internal object ProviderLoading {
    private const val MAX_FAILURES = 100

    fun loadAll(): List<KeyManagementServiceProvider> {
        val loader = ServiceLoader.load(KeyManagementServiceProvider::class.java)
        return collect(loader.iterator())
    }

    fun collect(providers: Iterator<KeyManagementServiceProvider>): List<KeyManagementServiceProvider> {
        val loaded = mutableListOf<KeyManagementServiceProvider>()
        var failures = 0
        while (failures < MAX_FAILURES) {
            try {
                if (!providers.hasNext()) break
                loaded += providers.next()
            } catch (e: ServiceConfigurationError) {
                failures++
                System.getLogger(ProviderLoading::class.java.name).log(
                    System.Logger.Level.WARNING,
                    "Skipping a KMS provider that failed to load: ${e.message}",
                    e,
                )
            }
        }
        return loaded
    }
}
