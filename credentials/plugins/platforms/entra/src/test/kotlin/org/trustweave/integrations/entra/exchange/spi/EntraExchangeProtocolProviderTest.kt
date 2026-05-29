package org.trustweave.integrations.entra.exchange.spi

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.trustweave.credential.spi.exchange.CredentialExchangeProtocolProvider
import org.trustweave.integrations.entra.EntraConfig
import java.util.ServiceLoader

class EntraExchangeProtocolProviderTest {

    @Test
    fun `provider is registered via ServiceLoader`() {
        val providers = ServiceLoader.load(CredentialExchangeProtocolProvider::class.java)
            .toList()
        val entra = providers.firstOrNull { it.name == "entra" }
        assertNotNull(entra, "EntraExchangeProtocolProvider must be discoverable via META-INF/services")
        assertTrue("entra" in entra!!.supportedProtocols)
    }

    @Test
    fun `create returns null for unknown protocolName`() {
        val provider: CredentialExchangeProtocolProvider = EntraExchangeProtocolProvider()
        assertNull(provider.create("not-entra"))
    }

    @Test
    fun `create succeeds with explicit EntraConfig option`() {
        val provider = EntraExchangeProtocolProvider()
        val config = EntraConfig(
            tenantId = "t",
            clientId = "c",
            clientSecret = "s",
            authorityDid = "did:web:example.com",
        )
        val protocol = provider.create("entra", mapOf("config" to config))
        assertNotNull(protocol)
        assertEquals("entra", protocol!!.protocolName.value)
    }

    @Test
    fun `create succeeds with scalar options`() {
        val provider = EntraExchangeProtocolProvider()
        val protocol = provider.create(
            "entra",
            mapOf(
                "tenantId" to "t",
                "clientId" to "c",
                "clientSecret" to "s",
                "authorityDid" to "did:web:example.com",
            ),
        )
        assertNotNull(protocol)
    }

    @Test
    fun `create fails fast when required scalar option missing`() {
        val provider = EntraExchangeProtocolProvider()
        assertThrows(IllegalArgumentException::class.java) {
            provider.create("entra", mapOf("tenantId" to "t"))
        }
    }
}
