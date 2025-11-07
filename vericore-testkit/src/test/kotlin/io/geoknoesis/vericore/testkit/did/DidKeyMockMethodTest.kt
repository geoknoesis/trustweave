package io.geoknoesis.vericore.testkit.did

import io.geoknoesis.vericore.did.*
import io.geoknoesis.vericore.testkit.kms.InMemoryKeyManagementService
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DidKeyMockMethodTest {

    @Test
    fun `createDid should create DID document`() = runBlocking {
        val kms = InMemoryKeyManagementService()
        val method = DidKeyMockMethod(kms)

        val document = method.createDid()

        assertNotNull(document.id)
        assertTrue(document.id.startsWith("did:key:"))
        assertEquals(1, document.verificationMethod.size)
        assertEquals(1, document.authentication.size)
    }

    @Test
    fun `resolveDid should return created document`() = runBlocking {
        val kms = InMemoryKeyManagementService()
        val method = DidKeyMockMethod(kms)
        val document = method.createDid()

        val result = method.resolveDid(document.id)

        assertNotNull(result.document)
        assertEquals(document.id, result.document?.id)
        assertEquals("key", result.resolutionMetadata["method"])
    }

    @Test
    fun `updateDid should modify document`() = runBlocking {
        val kms = InMemoryKeyManagementService()
        val method = DidKeyMockMethod(kms)
        val document = method.createDid()

        val updated = method.updateDid(document.id) { doc ->
            doc.copy(alsoKnownAs = listOf("did:web:example.com"))
        }

        assertEquals(1, updated.alsoKnownAs.size)
        val resolved = method.resolveDid(document.id)
        assertEquals(1, resolved.document?.alsoKnownAs?.size)
    }

    @Test
    fun `deactivateDid should remove document`() = runBlocking {
        val kms = InMemoryKeyManagementService()
        val method = DidKeyMockMethod(kms)
        val document = method.createDid()

        val deactivated = method.deactivateDid(document.id)

        assertTrue(deactivated)
        val result = method.resolveDid(document.id)
        assertEquals(null, result.document)
    }
}

