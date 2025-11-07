package io.geoknoesis.vericore.waltid

import io.geoknoesis.vericore.did.DidRegistry
import io.geoknoesis.vericore.testkit.kms.InMemoryKeyManagementService
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WaltIdIntegrationTest {

    @AfterEach
    fun cleanup() {
        DidRegistry.clear()
    }

    @Test
    fun setup_shouldRegisterDidMethodsWithProvidedKMS() {
        val kms = InMemoryKeyManagementService()
        val result = WaltIdIntegration.setup(kms, listOf("key", "web"))

        assertEquals(kms, result.kms)
        assertTrue(result.registeredDidMethods.contains("key"))
        assertTrue(result.registeredDidMethods.contains("web"))
        
        // Verify methods are registered
        assertNotNull(DidRegistry.get("key"))
        assertNotNull(DidRegistry.get("web"))
    }

    @Test
    fun setup_shouldOnlyRegisterRequestedMethods() {
        val kms = InMemoryKeyManagementService()
        val result = WaltIdIntegration.setup(kms, listOf("key"))

        assertEquals(1, result.registeredDidMethods.size)
        assertTrue(result.registeredDidMethods.contains("key"))
        assertNotNull(DidRegistry.get("key"))
    }

    @Test
    fun integrationTest_createAndResolveDIDUsingWaltIdAdapters() = runBlocking {
        val kms = InMemoryKeyManagementService()
        val result = WaltIdIntegration.setup(kms, listOf("key"))

        val keyMethod = DidRegistry.get("key")
        assertNotNull(keyMethod)

        val document = keyMethod!!.createDid(mapOf("algorithm" to "Ed25519"))
        assertNotNull(document.id)
        assertTrue(document.id.startsWith("did:key:"))

        val resolutionResult = DidRegistry.resolve(document.id)
        assertNotNull(resolutionResult.document)
        assertEquals(document.id, resolutionResult.document?.id)
    }
}

