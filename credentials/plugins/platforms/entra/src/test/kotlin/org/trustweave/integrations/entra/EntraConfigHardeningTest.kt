package org.trustweave.integrations.entra

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class EntraConfigHardeningTest {
    private fun config(
        tenantId: String = "contoso.onmicrosoft.com",
        tokenBase: String = "https://login.microsoftonline.com",
    ) = EntraConfig(
        tenantId = tenantId,
        clientId = "client",
        clientSecret = "TOP-SECRET-VALUE",
        authorityDid = "did:web:a.example",
        tokenEndpointBaseUrl = tokenBase,
    )

    @Test
    fun `toString redacts the client secret`() {
        assertFalse(config().toString().contains("TOP-SECRET-VALUE"))
    }

    @Test
    fun `plain http token endpoint to a public host is refused`() {
        assertThrows(IllegalArgumentException::class.java) { config(tokenBase = "http://login.attacker.example") }
        assertThrows(IllegalArgumentException::class.java) { config(tokenBase = "http://127.evil.com") }
        config(tokenBase = "http://127.0.0.1:8080")
    }

    @Test
    fun `tenant id cannot smuggle path or authority characters`() {
        listOf("a/b", "a?b", "a#b", "a@evil.com", "..%2f", "a b").forEach {
            assertThrows(IllegalArgumentException::class.java, { config(tenantId = it) }, it)
        }
        config(tenantId = "72f988bf-86f1-41af-91ab-2d7cd011db47")
    }

    @Test
    fun `service error keeps only a short excerpt out of the context`() {
        val e = EntraException.RequestServiceError(500, "x".repeat(5000) + "\nsecret", "boom")
        assertFalse(e.responseBody.length > 300)
        assertFalse(e.context.containsKey("responseBody"))
    }
}
