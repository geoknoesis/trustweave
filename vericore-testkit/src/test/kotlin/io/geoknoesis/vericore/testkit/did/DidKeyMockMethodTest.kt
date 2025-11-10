package io.geoknoesis.vericore.testkit.did

import io.geoknoesis.vericore.did.*
import io.geoknoesis.vericore.kms.KeyManagementService
import io.geoknoesis.vericore.kms.KeyHandle
import io.geoknoesis.vericore.kms.KeyNotFoundException
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class DidKeyMockMethodTest {

    private lateinit var kms: MockKeyManagementService
    private lateinit var didMethod: DidKeyMockMethod

    @BeforeTest
    fun setup() {
        kms = MockKeyManagementService()
        didMethod = DidKeyMockMethod(kms)
    }

    @Test
    fun `test method name is key`() {
        assertEquals("key", didMethod.method)
    }

    @Test
    fun `test create DID with default algorithm`() = runBlocking {
        val document = didMethod.createDid()

        assertNotNull(document)
        assertTrue(document.id.startsWith("did:key:z"))
        assertEquals(1, document.verificationMethod.size)
        assertEquals(1, document.authentication.size)
        assertEquals(1, document.assertionMethod.size)
        assertTrue(document.authentication.first().startsWith(document.id))
        assertTrue(document.assertionMethod.first().startsWith(document.id))
    }

    @Test
    fun `test create DID with Ed25519 algorithm`() = runBlocking {
        val document = didMethod.createDid()

        assertNotNull(document)
        assertEquals("Ed25519VerificationKey2020", document.verificationMethod.first().type)
    }

    @Test
    fun `test create DID with Secp256k1 algorithm`() = runBlocking {
        val document = didMethod.createDid(
            didCreationOptions {
                algorithm = DidCreationOptions.KeyAlgorithm.SECP256K1
            }
        )

        assertNotNull(document)
        assertEquals("EcdsaSecp256k1VerificationKey2019", document.verificationMethod.first().type)
    }

    @Test
    fun `test create DID with fallback algorithm`() = runBlocking {
        val document = didMethod.createDid(
            didCreationOptions {
                algorithm = DidCreationOptions.KeyAlgorithm.P256
            }
        )

        assertNotNull(document)
        assertEquals("JsonWebKey2020", document.verificationMethod.first().type)
    }

    @Test
    fun `test resolve DID after creation`() = runBlocking {
        val document = didMethod.createDid()
        val did = document.id

        val result = didMethod.resolveDid(did)

        assertNotNull(result.document)
        assertEquals(document, result.document)
        assertNotNull(result.documentMetadata.created)
        assertNotNull(result.documentMetadata.updated)
        assertEquals("key", result.resolutionMetadata["method"])
        assertEquals(true, result.resolutionMetadata["mock"])
    }

    @Test
    fun `test resolve non-existent DID`() = runBlocking {
        val result = didMethod.resolveDid("did:key:zNonexistent")

        assertNull(result.document)
        assertEquals("key", result.resolutionMetadata["method"])
    }

    @Test
    fun `test update DID`() = runBlocking {
        val document = didMethod.createDid()
        val did = document.id

        val updated = didMethod.updateDid(did) { doc ->
            doc.copy(
                verificationMethod = doc.verificationMethod + VerificationMethodRef(
                    id = "$did#key-2",
                    type = "Ed25519VerificationKey2020",
                    controller = did,
                    publicKeyJwk = emptyMap()
                )
            )
        }

        assertEquals(2, updated.verificationMethod.size)
        val resolved = didMethod.resolveDid(did)
        assertEquals(2, resolved.document?.verificationMethod?.size)
    }

    @Test
    fun `test update non-existent DID throws exception`() = runBlocking {
        assertFailsWith<IllegalArgumentException> {
            didMethod.updateDid("did:key:zNonexistent") { it }
        }
    }

    @Test
    fun `test deactivate DID`() = runBlocking {
        val document = didMethod.createDid()
        val did = document.id

        val result = didMethod.deactivateDid(did)

        assertTrue(result)
        val resolved = didMethod.resolveDid(did)
        assertNull(resolved.document)
    }

    @Test
    fun `test deactivate non-existent DID`() = runBlocking {
        val result = didMethod.deactivateDid("did:key:zNonexistent")

        assertFalse(result)
    }

    @Test
    fun `test clear all DIDs`() = runBlocking {
        val doc1 = didMethod.createDid()
        val doc2 = didMethod.createDid()

        didMethod.clear()

        assertNull(didMethod.resolveDid(doc1.id).document)
        assertNull(didMethod.resolveDid(doc2.id).document)
    }

    @Test
    fun `test verification method controller matches DID`() = runBlocking {
        val document = didMethod.createDid()

        document.verificationMethod.forEach { vm ->
            assertEquals(document.id, vm.controller)
        }
    }

    @Test
    fun `test verification method ID format`() = runBlocking {
        val document = didMethod.createDid()

        document.verificationMethod.forEach { vm ->
            assertTrue(vm.id.startsWith(document.id))
            assertTrue(vm.id.contains("#"))
        }
    }

    private class MockKeyManagementService : KeyManagementService {
        private val keys = mutableMapOf<String, KeyHandle>()

        override suspend fun generateKey(algorithm: String, options: Map<String, Any?>): KeyHandle {
            val keyId = options["keyId"] as? String ?: "key-${System.currentTimeMillis()}"
            val handle = KeyHandle(
                id = keyId,
                algorithm = algorithm,
                publicKeyJwk = mapOf(
                    "kty" to "OKP",
                    "crv" to "Ed25519",
                    "x" to "mock-public-key"
                )
            )
            keys[keyId] = handle
            return handle
        }

        override suspend fun getPublicKey(keyId: String): KeyHandle {
            return keys[keyId] ?: throw KeyNotFoundException("Key not found: $keyId")
        }

        override suspend fun sign(keyId: String, data: ByteArray, algorithm: String?): ByteArray {
            return "signature".toByteArray()
        }

        override suspend fun deleteKey(keyId: String): Boolean {
            return keys.remove(keyId) != null
        }
    }
}
