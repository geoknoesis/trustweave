package org.trustweave.waltid

import org.junit.jupiter.api.Test
import org.trustweave.did.spi.DidMethodProvider
import org.trustweave.kms.spi.KeyManagementServiceProvider
import java.util.ServiceLoader
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for SPI (Service Provider Interface) discovery of walt.id adapters.
 */
class SpiDiscoveryTest {
    @Test
    fun spi_shouldDiscoverWaltIdKmsProvider() {
        val providers = ServiceLoader.load(KeyManagementServiceProvider::class.java)
        val waltIdProvider = providers.find { it.name == "waltid" }

        assertNotNull(waltIdProvider, "walt.id KMS provider should be discoverable via SPI")
        assertEquals("waltid", waltIdProvider?.name)

        val kms = waltIdProvider?.create()
        assertNotNull(kms, "Provider should create a KeyManagementService instance")
    }

    @Test
    fun spi_shouldNotRegisterThePlaceholderDidMethodProvider() {
        val providers = ServiceLoader.load(DidMethodProvider::class.java)
        assertNull(
            providers.find { it.name == "waltid" },
            "the placeholder walt.id DID provider must not shadow the real did:key/did:web plugins",
        )
    }

    @Test
    fun `SPI should discover all providers`() {
        val kmsProviders = ServiceLoader.load(KeyManagementServiceProvider::class.java).toList()
        val didProviders = ServiceLoader.load(DidMethodProvider::class.java).toList()

        assertTrue(kmsProviders.isNotEmpty(), "At least one KMS provider should be discoverable")
        assertTrue(didProviders.isNotEmpty(), "At least one DID method provider should be discoverable")

        val waltIdKms = kmsProviders.find { it.name == "waltid" }

        assertNotNull(waltIdKms, "walt.id KMS provider should be present")
        assertTrue(didProviders.any { "key" in it.supportedMethods }, "the real did:key provider should be discoverable")
    }

    @Test
    fun `META-INF services files should exist`() {
        val kmsServiceFile =
            this::class.java.classLoader
                .getResource("META-INF/services/org.trustweave.kms.spi.KeyManagementServiceProvider")
        assertNotNull(kmsServiceFile, "KMS provider service file should exist in META-INF/services")

        // Verify file contents
        val kmsContent = kmsServiceFile?.readText()
        assertNotNull(kmsContent)
        assertTrue(
            kmsContent.contains("WaltIdKeyManagementServiceProvider"),
            "Service file should contain provider class name",
        )
    }
}
