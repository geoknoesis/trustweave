package com.trustweave.waltid.did

import com.trustweave.did.*
import com.trustweave.did.resolver.DidResolutionResult
import com.trustweave.testkit.kms.InMemoryKeyManagementService
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WaltIdKeyMethodTest {

    @Test
    fun createDid_shouldCreateDidKeyDocument() = runBlocking {
        val kms = InMemoryKeyManagementService()
        val method = WaltIdKeyMethod(kms)

        val document = method.createDid()

        assertNotNull(document.id)
        assertTrue(document.id.value.startsWith("did:key:"))
        assertEquals(1, document.verificationMethod.size)
        assertEquals(1, document.authentication.size)
    }

    @Test
    fun resolveDid_shouldReturnCreatedDocument() = runBlocking {
        val kms = InMemoryKeyManagementService()
        val method = WaltIdKeyMethod(kms)
        val document = method.createDid()

        val result = method.resolveDid(document.id)

        assertTrue(result is DidResolutionResult.Success)
        val successResult = result as DidResolutionResult.Success
        assertEquals(document.id, successResult.document.id)
        assertEquals("key", successResult.resolutionMetadata["method"])
    }

    @Test
    fun updateDid_shouldModifyDocument() = runBlocking {
        val kms = InMemoryKeyManagementService()
        val method = WaltIdKeyMethod(kms)
        val document = method.createDid()

        val updated = method.updateDid(document.id) { doc ->
            doc.copy(alsoKnownAs = listOf(com.trustweave.did.identifiers.Did("did:web:example.com")))
        }

        assertEquals(1, updated.alsoKnownAs.size)
    }

    @Test
    fun deactivateDid_shouldRemoveDocument() = runBlocking {
        val kms = InMemoryKeyManagementService()
        val method = WaltIdKeyMethod(kms)
        val document = method.createDid()

        val deactivated = method.deactivateDid(document.id)

        assertTrue(deactivated)
        val result = method.resolveDid(document.id)
        assertTrue(result is DidResolutionResult.Failure.NotFound || result !is DidResolutionResult.Success)
    }
}

class WaltIdWebMethodTest {

    @Test
    fun createDid_shouldCreateDidWebDocument() = runBlocking {
        val kms = InMemoryKeyManagementService()
        val method = WaltIdWebMethod(kms)

        val document = method.createDid(
            didCreationOptions {
                property("domain", "example.com")
            }
        )

        assertNotNull(document.id)
        assertEquals("did:web:example.com", document.id.value)
        assertEquals(1, document.verificationMethod.size)
    }

    @Test
    fun createDid_shouldRequireDomainOption() = runBlocking {
        val kms = InMemoryKeyManagementService()
        val method = WaltIdWebMethod(kms)

        try {
            method.createDid()
            assert(false) { "Should have thrown IllegalArgumentException" }
        } catch (e: IllegalArgumentException) {
            assertNotNull(e.message)
        }
    }
}

class WaltIdDidMethodProviderTest {

    @Test
    fun provider_shouldCreateDidKeyMethod() {
        val kms = InMemoryKeyManagementService()
        val provider = WaltIdDidMethodProvider()

        assertEquals("waltid", provider.name)
        assertTrue(provider.supportedMethods.contains("key"))
        assertTrue(provider.supportedMethods.contains("web"))

        val method = provider.create("key", didCreationOptions { property("kms", kms) })
        assertNotNull(method)
        assertEquals("key", method.method)
    }

    @Test
    fun provider_shouldCreateDidWebMethod() {
        val kms = InMemoryKeyManagementService()
        val provider = WaltIdDidMethodProvider()

        val method = provider.create("web", didCreationOptions { property("kms", kms) })
        assertNotNull(method)
        assertEquals("web", method.method)
    }

    @Test
    fun provider_shouldReturnNullForUnsupportedMethod() {
        val kms = InMemoryKeyManagementService()
        val provider = WaltIdDidMethodProvider()

        val method = provider.create("unsupported", didCreationOptions { property("kms", kms) })
        assertEquals(null, method)
    }
}

