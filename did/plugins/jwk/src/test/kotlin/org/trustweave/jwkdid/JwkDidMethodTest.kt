package org.trustweave.jwkdid

import kotlinx.coroutines.test.runTest
import org.trustweave.did.DidCreationOptions
import org.trustweave.did.KeyAlgorithm
import org.trustweave.did.KeyPurpose
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.kms.inmemory.InMemoryKeyManagementService
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class JwkDidMethodTest {

    private val kms = InMemoryKeyManagementService()
    private val method = JwkDidMethod(kms)

    @Test
    fun `createDid produces did-jwk identifier`() = runTest {
        val doc = method.createDid(DidCreationOptions(algorithm = KeyAlgorithm.ED25519))
        assertTrue(doc.id.value.startsWith("did:jwk:"))
    }

    @Test
    fun `created DID has exactly one verification method`() = runTest {
        val doc = method.createDid(DidCreationOptions(algorithm = KeyAlgorithm.ED25519))
        assertEquals(1, doc.verificationMethod.size)
        assertTrue(doc.verificationMethod[0].id.value.endsWith("#0"))
    }

    @Test
    fun `verification method contains JWK`() = runTest {
        val doc = method.createDid(DidCreationOptions(algorithm = KeyAlgorithm.ED25519))
        val vm = doc.verificationMethod[0]
        assertNotNull(vm.publicKeyJwk)
        assertEquals("OKP", vm.publicKeyJwk!!["kty"])
        assertEquals("Ed25519", vm.publicKeyJwk!!["crv"])
    }

    @Test
    fun `resolveDid returns Success for valid did-jwk`() = runTest {
        val doc = method.createDid(DidCreationOptions(algorithm = KeyAlgorithm.ED25519))
        val result = method.resolveDid(doc.id)
        assertIs<DidResolutionResult.Success>(result)
        assertEquals(doc.id.value, result.document.id.value)
    }

    @Test
    fun `resolveDid reconstructs document from identifier without stored state`() = runTest {
        val kms2 = InMemoryKeyManagementService()
        val method2 = JwkDidMethod(kms2)
        val doc = method.createDid(DidCreationOptions(algorithm = KeyAlgorithm.ED25519))
        val result = method2.resolveDid(doc.id)
        assertIs<DidResolutionResult.Success>(result)
        assertEquals(1, result.document.verificationMethod.size)
        assertNotNull(result.document.verificationMethod[0].publicKeyJwk)
    }

    @Test
    fun `createDid with assertion purpose includes assertionMethod`() = runTest {
        val doc = method.createDid(
            DidCreationOptions(
                algorithm = KeyAlgorithm.ED25519,
                purposes = listOf(KeyPurpose.AUTHENTICATION, KeyPurpose.ASSERTION),
            ),
        )
        assertNotNull(doc.assertionMethod)
        assertTrue(doc.assertionMethod!!.isNotEmpty())
    }

    @Test
    fun `resolveDid returns error for invalid did`() = runTest {
        // A syntactically valid DID but with a method-specific-id that is not a valid base64url JWK.
        val result = method.resolveDid(org.trustweave.did.identifiers.Did("did:jwk:notavalidjwk"))
        assertIs<DidResolutionResult.Failure>(result)
    }
}
