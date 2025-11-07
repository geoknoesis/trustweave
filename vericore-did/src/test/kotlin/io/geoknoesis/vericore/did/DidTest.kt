package io.geoknoesis.vericore.did

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DidTest {

    @Test
    fun `Did toString should format correctly`() {
        val did = Did(method = "web", id = "example.com")
        assertEquals("did:web:example.com", did.toString())
    }

    @Test
    fun `Did parse should parse valid DID strings`() {
        val did = Did.parse("did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK")
        assertEquals("key", did.method)
        assertEquals("z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK", did.id)
    }

    @Test
    fun `Did parse should throw for invalid format`() {
        try {
            Did.parse("not-a-did")
            assert(false) { "Should have thrown IllegalArgumentException" }
        } catch (e: IllegalArgumentException) {
            assertNotNull(e.message)
        }
    }
}

class DidRegistryTest {

    @Test
    fun `DidRegistry should register and retrieve methods`() {
        val mockMethod = object : DidMethod {
            override val method = "test"
            override suspend fun createDid(options: Map<String, Any?>) = TODO()
            override suspend fun resolveDid(did: String) = TODO()
            override suspend fun updateDid(did: String, updater: (DidDocument) -> DidDocument) = TODO()
            override suspend fun deactivateDid(did: String) = TODO()
        }

        DidRegistry.register(mockMethod)
        assertEquals(mockMethod, DidRegistry.get("test"))
        assertNull(DidRegistry.get("nonexistent"))

        DidRegistry.clear()
    }
}

