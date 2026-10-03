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
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            AzureKmsConfig(vaultUrl = "https://vault.vault.azure.net", endpointOverride = "localhost:8443")
        }
    }

    private fun overrideConfig(endpoint: String) = AzureKmsConfig(vaultUrl = "https://vault.vault.azure.net", endpointOverride = endpoint)

    @Test
    fun `https endpoint overrides are accepted for any host`() {
        overrideConfig("https://emulator.internal.example:8443")
    }

    @Test
    fun `cleartext http is accepted only for loopback hosts`() {
        listOf("http://localhost:8443", "http://127.0.0.1:8443", "http://127.5.5.5", "http://[::1]:8443", "http://kv.localhost")
            .forEach { overrideConfig(it) }
    }

    @Test
    fun `cleartext http to a non-loopback host is refused`() {
        listOf(
            "http://emulator.internal.example",
            "http://10.0.0.5:8443",
            "http://192.168.1.2",
            "http://169.254.169.254",
            "http://vault.vault.azure.net",
            "http://localhost.evil.example",
            "http://127.0.0.1.evil.example",
        ).forEach { endpoint ->
            val ex = kotlin.test.assertFailsWith<IllegalArgumentException>(endpoint) { overrideConfig(endpoint) }
            kotlin.test.assertTrue("cleartext" in ex.message.orEmpty(), ex.message)
        }
    }

    @Test
    fun `credentials embedded in the override are refused`() {
        kotlin.test.assertFailsWith<IllegalArgumentException> { overrideConfig("https://user:pw@localhost:8443") }
    }

    @Test
    fun `fromMap applies the same rule`() {
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            AzureKmsConfig.fromMap(mapOf("vaultUrl" to "https://v.vault.azure.net", "endpointOverride" to "http://example.com"))
        }
    }
}
