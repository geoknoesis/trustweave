package org.trustweave.did.orb

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class OrbDidConfigTest {

    @Test
    fun `defaults match Sidetree REST paths`() {
        val cfg = OrbDidConfig(baseUrl = "https://orb.example.com")
        assertEquals("did:orb", cfg.namespace)
        assertEquals("/sidetree/v1/operations", cfg.operationsPath)
        assertEquals("/sidetree/v1/identifiers", cfg.identifiersPath)
        assertEquals("https://orb.example.com/sidetree/v1/operations", cfg.operationsUrl)
        assertEquals("https://orb.example.com/sidetree/v1/identifiers", cfg.identifiersUrl)
        assertNull(cfg.authHeader)
        assertEquals(30L, cfg.timeoutSeconds)
    }

    @Test
    fun `trailing slash on baseUrl is normalised`() {
        val cfg = OrbDidConfig(baseUrl = "https://orb.example.com/")
        assertEquals("https://orb.example.com/sidetree/v1/operations", cfg.operationsUrl)
    }

    @Test
    fun `blank baseUrl is rejected`() {
        assertFailsWith<IllegalArgumentException> { OrbDidConfig(baseUrl = "") }
    }

    @Test
    fun `non-did namespace is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            OrbDidConfig(baseUrl = "https://orb.example.com", namespace = "orb")
        }
    }

    @Test
    fun `fromMap reads baseUrl and optional fields`() {
        val cfg = OrbDidConfig.fromMap(
            mapOf(
                "baseUrl" to "https://orb2.example.com",
                "namespace" to "did:orb:abc",
                "operationsPath" to "/v2/ops",
                "identifiersPath" to "/v2/ids",
                "authHeaderName" to "X-Token",
                "authHeaderValue" to "secret",
                "timeoutSeconds" to 60,
            ),
        )
        assertEquals("https://orb2.example.com", cfg.baseUrl)
        assertEquals("did:orb:abc", cfg.namespace)
        assertEquals("/v2/ops", cfg.operationsPath)
        assertEquals("/v2/ids", cfg.identifiersPath)
        assertEquals("X-Token" to "secret", cfg.authHeader)
        assertEquals(60L, cfg.timeoutSeconds)
    }

    @Test
    fun `fromMap accepts legacy orbBaseUrl key`() {
        val cfg = OrbDidConfig.fromMap(mapOf("orbBaseUrl" to "https://orb.example.com"))
        assertEquals("https://orb.example.com", cfg.baseUrl)
    }

    @Test
    fun `fromMap requires a baseUrl`() {
        assertFailsWith<IllegalArgumentException> { OrbDidConfig.fromMap(emptyMap()) }
    }

    @Test
    fun `fromMap leaves authHeader null when only one of name or value is set`() {
        val cfg = OrbDidConfig.fromMap(
            mapOf("baseUrl" to "https://orb.example.com", "authHeaderName" to "X-Token"),
        )
        assertNull(cfg.authHeader)
        assertNotNull(cfg.baseUrl)
    }
}
