package com.trustweave.azurekms

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.*

class AzureKmsConfigTest {

    @Test
    fun `test AzureKmsConfig from map`() {
        val config = AzureKmsConfig.fromMap(mapOf(
            "vaultUrl" to "https://myvault.vault.azure.net",
            "clientId" to "client-id",
            "clientSecret" to "client-secret",
            "tenantId" to "tenant-id",
            "endpointOverride" to "https://localhost:8443"
        ))

        assertEquals("https://myvault.vault.azure.net", config.vaultUrl)
        assertEquals("client-id", config.clientId)
        assertEquals("client-secret", config.clientSecret)
        assertEquals("tenant-id", config.tenantId)
        assertEquals("https://localhost:8443", config.endpointOverride)
    }

    @Test
    fun `test AzureKmsConfig from map without vaultUrl throws exception`() {
        assertThrows<IllegalArgumentException> {
            AzureKmsConfig.fromMap(emptyMap())
        }
    }

    @Test
    fun `test AzureKmsConfig builder`() {
        val config = AzureKmsConfig.builder()
            .vaultUrl("https://testvault.vault.azure.net")
            .clientId("test-client-id")
            .build()

        assertEquals("https://testvault.vault.azure.net", config.vaultUrl)
        assertEquals("test-client-id", config.clientId)
    }

    @Test
    fun `test AzureKmsConfig builder without vaultUrl throws exception`() {
        assertThrows<IllegalArgumentException> {
            AzureKmsConfig.builder()
                .clientId("test-client-id")
                .build()
        }
    }

    @Test
    fun `test AzureKmsConfig validates HTTPS URL`() {
        assertThrows<IllegalArgumentException> {
            AzureKmsConfig.builder()
                .vaultUrl("http://myvault.vault.azure.net")
                .build()
        }
    }

    @Test
    fun `test AzureKmsConfig validates non-blank URL`() {
        assertThrows<IllegalArgumentException> {
            AzureKmsConfig.builder()
                .vaultUrl("")
                .build()
        }
    }
}

