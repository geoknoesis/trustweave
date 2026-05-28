package org.trustweave.conformance

import kotlinx.coroutines.runBlocking
import org.trustweave.did.DidCreationOptions
import org.trustweave.did.KeyAlgorithm
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.keydid.KeyDidMethod
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Tag("conformance")
@Tag("did-core-1.1")
class DidCore11ConformanceTest {

    private val kms = InMemoryKeyManagementService()
    private val method = KeyDidMethod(kms)

    @Test
    fun `TC-01 did-key resolution returns a DID document`() = runBlocking {
        val doc = method.createDid(DidCreationOptions(algorithm = KeyAlgorithm.ED25519))
        val result = method.resolveDid(doc.id)
        val success = result as? DidResolutionResult.Success
        assertNotNull(success, "Resolution must succeed")
        assertNotNull(success.document)
    }

    @Test
    fun `TC-02 DID document has context`() = runBlocking {
        val doc = method.createDid(DidCreationOptions(algorithm = KeyAlgorithm.ED25519))
        val success = method.resolveDid(doc.id) as DidResolutionResult.Success
        assertTrue(success.document.context.isNotEmpty(), "DID document must have @context")
    }

    @Test
    fun `TC-03 DID document id matches the resolved DID`() = runBlocking {
        val doc = method.createDid(DidCreationOptions(algorithm = KeyAlgorithm.ED25519))
        val success = method.resolveDid(doc.id) as DidResolutionResult.Success
        assertEquals(doc.id, success.document.id, "Document id must match the DID used for resolution")
    }

    @Test
    fun `TC-04 DID document verification method has required fields`() = runBlocking {
        val doc = method.createDid(DidCreationOptions(algorithm = KeyAlgorithm.ED25519))
        val success = method.resolveDid(doc.id) as DidResolutionResult.Success
        val document = success.document
        assertTrue(document.verificationMethod.isNotEmpty(), "Must have at least one verification method")
        val vm = document.verificationMethod.first()
        assertNotNull(vm.id, "verificationMethod.id must be present")
        assertNotNull(vm.type, "verificationMethod.type must be present")
        assertNotNull(vm.controller, "verificationMethod.controller must be present")
    }

    @Test
    fun `TC-05 DID document authentication references a verification method`() = runBlocking {
        val doc = method.createDid(DidCreationOptions(algorithm = KeyAlgorithm.ED25519))
        val success = method.resolveDid(doc.id) as DidResolutionResult.Success
        assertTrue(success.document.authentication.isNotEmpty(), "authentication must not be empty")
    }
}
