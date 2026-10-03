package org.trustweave.azurekms

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AzureKmsConfigRedactionTest {
    @Test
    fun `toString never contains credentials`() {
        val text =
            AzureKmsConfig(
                vaultUrl = "https://vault.vault.azure.net",
                clientId = "client",
                clientSecret = "SECRET-CLIENT",
            ).toString()

        assertFalse("SECRET-CLIENT" in text, text)
        assertTrue("<redacted>" in text, text)
        assertTrue("vault.vault.azure.net" in text, text)
        assertTrue("client" in text, text)
    }

    @Test
    fun `the endpoint override replaces the vault URL the client talks to`() {
        val config = AzureKmsConfig(vaultUrl = "https://vault.vault.azure.net", endpointOverride = "https://localhost:8443")

        kotlin.test.assertEquals("https://localhost:8443", AzureKmsClientFactory.effectiveVaultUrl(config))
        kotlin.test.assertEquals("https://localhost:8443", AzureKmsClientFactory.createClient(config).vaultUrl)
    }

    @Test
    fun `without an override the vault URL is used`() {
        val config = AzureKmsConfig(vaultUrl = "https://vault.vault.azure.net")
        kotlin.test.assertEquals("https://vault.vault.azure.net", AzureKmsClientFactory.effectiveVaultUrl(config))
    }

    @Test
    fun `a malformed endpoint override is rejected`() {
        val config = AzureKmsConfig(vaultUrl = "https://vault.vault.azure.net", endpointOverride = "localhost:8443")
        kotlin.test.assertFailsWith<IllegalArgumentException> { AzureKmsClientFactory.effectiveVaultUrl(config) }
    }
}
