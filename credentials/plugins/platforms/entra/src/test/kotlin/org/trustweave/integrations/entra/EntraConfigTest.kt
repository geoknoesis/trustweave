package org.trustweave.integrations.entra

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EntraConfigTest {

    @Test
    fun `rejects blank tenantId`() {
        assertThrows(IllegalArgumentException::class.java) {
            EntraConfig(
                tenantId = "",
                clientId = "c",
                clientSecret = "s",
                authorityDid = "did:web:example.com",
            )
        }
    }

    @Test
    fun `rejects blank authorityDid`() {
        assertThrows(IllegalArgumentException::class.java) {
            EntraConfig(
                tenantId = "t",
                clientId = "c",
                clientSecret = "s",
                authorityDid = "",
            )
        }
    }

    @Test
    fun `rejects non-http apiBaseUrl`() {
        assertThrows(IllegalArgumentException::class.java) {
            EntraConfig(
                tenantId = "t",
                clientId = "c",
                clientSecret = "s",
                authorityDid = "did:web:e.com",
                apiBaseUrl = "ftp://example.com",
            )
        }
    }

    @Test
    fun `token endpoint URL is composed from base + tenant`() {
        val cfg = EntraConfig(
            tenantId = "tenant-123",
            clientId = "c",
            clientSecret = "s",
            authorityDid = "did:web:authority.example.com",
            tokenEndpointBaseUrl = "https://login.microsoftonline.com",
        )
        assertEquals(
            "https://login.microsoftonline.com/tenant-123/oauth2/v2.0/token",
            cfg.tokenEndpointUrl,
        )
    }

    @Test
    fun `request URLs use API base with trailing slash trimmed`() {
        val cfg = EntraConfig(
            tenantId = "tenant-123",
            clientId = "c",
            clientSecret = "s",
            authorityDid = "did:web:authority.example.com",
            apiBaseUrl = "https://verifiedid.did.msidentity.com/",
        )
        assertEquals(
            "https://verifiedid.did.msidentity.com/v1.0/verifiableCredentials/createIssuanceRequest",
            cfg.issuanceRequestUrl,
        )
        assertEquals(
            "https://verifiedid.did.msidentity.com/v1.0/verifiableCredentials/createPresentationRequest",
            cfg.presentationRequestUrl,
        )
    }

    @Test
    fun `default scope matches Verified ID resource id`() {
        assertTrue(EntraConfig.DEFAULT_SCOPE.endsWith("/.default"))
        assertTrue(EntraConfig.DEFAULT_SCOPE.startsWith("3db474b9-6a0c-4840-96ac-1fceb342124f"))
    }
}
