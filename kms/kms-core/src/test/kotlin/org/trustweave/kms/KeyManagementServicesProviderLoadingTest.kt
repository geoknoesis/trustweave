package org.trustweave.kms

import org.junit.jupiter.api.Test
import org.trustweave.kms.spi.KeyManagementServiceProvider
import kotlin.test.assertTrue

/** Listed in this module's test `META-INF/services`, right after a deliberately broken entry. */
class SpiLoadingTestProvider : KeyManagementServiceProvider {
    override val name = "spi-loading-test"
    override val supportedAlgorithms: Set<Algorithm> = emptySet()

    // Other tests in this module create the first available provider, so it must yield a usable
    // (if inert) instance.
    override fun create(options: Map<String, Any?>): KeyManagementService =
        java.lang.reflect.Proxy.newProxyInstance(
            KeyManagementService::class.java.classLoader,
            arrayOf(KeyManagementService::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                "toString" -> "SpiLoadingTestKms"
                else -> throw UnsupportedOperationException(method.name)
            }
        } as KeyManagementService
}

class KeyManagementServicesProviderLoadingTest {
    @Test
    fun `a broken service entry does not hide the providers after it`() {
        // The services file names a class that does not exist (ServiceConfigurationError from the
        // ServiceLoader iterator) before this provider.
        assertTrue("spi-loading-test" in KeyManagementServices.availableProviders())
    }
}
