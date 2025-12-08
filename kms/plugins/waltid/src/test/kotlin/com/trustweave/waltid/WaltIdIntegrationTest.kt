package com.trustweave.waltid

import com.trustweave.did.registry.DidMethodRegistry
import com.trustweave.did.resolver.DidResolutionResult
import com.trustweave.testkit.kms.InMemoryKeyManagementService
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WaltIdIntegrationTest {

    @Test
    fun setup_shouldRegisterDidMethodsWithProvidedKMS() {
        val kms = InMemoryKeyManagementService()
        val registry = DidMethodRegistry()
        val result = WaltIdIntegration.setup(kms, registry, listOf("key", "web"))

        assertEquals(kms, result.kms)
        assertTrue(result.registeredDidMethods.contains("key"))
        assertTrue(result.registeredDidMethods.contains("web"))

        // Verify methods are registered
        assertNotNull(registry.get("key"))
        assertNotNull(registry.get("web"))
    }

    @Test
    fun setup_shouldOnlyRegisterRequestedMethods() {
        val kms = InMemoryKeyManagementService()
        val registry = DidMethodRegistry()
        val result = WaltIdIntegration.setup(kms, registry, listOf("key"))

        assertEquals(1, result.registeredDidMethods.size)
        assertTrue(result.registeredDidMethods.contains("key"))
        assertNotNull(registry.get("key"))
    }

    @Test
    fun integrationTest_createAndResolveDIDUsingWaltIdAdapters() = runBlocking {
        val kms = InMemoryKeyManagementService()
        val registry = DidMethodRegistry()
        val result = WaltIdIntegration.setup(kms, registry, listOf("key"))

        val keyMethod = registry.get("key")
        assertNotNull(keyMethod)

        val document = keyMethod!!.createDid()
        assertNotNull(document.id)
        assertTrue(document.id.value.startsWith("did:key:"))

        val resolutionResult = registry.resolve(document.id.value)
        assertTrue(resolutionResult is DidResolutionResult.Success)
        val successResult = resolutionResult as DidResolutionResult.Success
        assertEquals(document.id, successResult.document.id)
    }
}

