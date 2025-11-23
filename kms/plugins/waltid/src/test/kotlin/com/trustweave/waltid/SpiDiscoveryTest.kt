package com.trustweave.waltid

import com.trustweave.did.spi.DidMethodProvider
import com.trustweave.kms.spi.KeyManagementServiceProvider
import org.junit.jupiter.api.Test
import java.util.ServiceLoader
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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
    fun spi_shouldDiscoverWaltIdDidMethodProvider() {
        val providers = ServiceLoader.load(DidMethodProvider::class.java)
        val waltIdProvider = providers.find { it.name == "waltid" }
        
        assertNotNull(waltIdProvider, "walt.id DID method provider should be discoverable via SPI")
        assertEquals("waltid", waltIdProvider?.name)
        assertTrue(waltIdProvider?.supportedMethods?.contains("key") == true)
        assertTrue(waltIdProvider?.supportedMethods?.contains("web") == true)
    }

    @Test
    fun `SPI should discover all providers`() {
        val kmsProviders = ServiceLoader.load(KeyManagementServiceProvider::class.java).toList()
        val didProviders = ServiceLoader.load(DidMethodProvider::class.java).toList()
        
        assertTrue(kmsProviders.isNotEmpty(), "At least one KMS provider should be discoverable")
        assertTrue(didProviders.isNotEmpty(), "At least one DID method provider should be discoverable")
        
        val waltIdKms = kmsProviders.find { it.name == "waltid" }
        val waltIdDid = didProviders.find { it.name == "waltid" }
        
        assertNotNull(waltIdKms, "walt.id KMS provider should be present")
        assertNotNull(waltIdDid, "walt.id DID provider should be present")
    }

    @Test
    fun `META-INF services files should exist`() {
        val kmsServiceFile = this::class.java.classLoader
            .getResource("META-INF/services/com.trustweave.kms.spi.KeyManagementServiceProvider")
        assertNotNull(kmsServiceFile, "KMS provider service file should exist in META-INF/services")
        
        val didServiceFile = this::class.java.classLoader
            .getResource("META-INF/services/com.trustweave.did.spi.DidMethodProvider")
        assertNotNull(didServiceFile, "DID method provider service file should exist in META-INF/services")
        
        // Verify file contents
        val kmsContent = kmsServiceFile?.readText()
        assertNotNull(kmsContent)
        assertTrue(kmsContent.contains("WaltIdKeyManagementServiceProvider"), 
            "Service file should contain provider class name")
        
        val didContent = didServiceFile?.readText()
        assertNotNull(didContent)
        assertTrue(didContent.contains("WaltIdDidMethodProvider"),
            "Service file should contain provider class name")
    }
}

