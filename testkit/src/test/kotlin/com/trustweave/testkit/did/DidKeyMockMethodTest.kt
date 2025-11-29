package com.trustweave.testkit.did

import com.trustweave.did.*
import com.trustweave.did.VerificationMethod
import com.trustweave.did.resolver.DidResolutionResult
import com.trustweave.did.resolver.getDocumentOrNull
import com.trustweave.kms.KeyManagementService
import com.trustweave.kms.KeyHandle
import com.trustweave.kms.exception.KmsException
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

        assertTrue(result is DidResolutionResult.Success)
        val successResult = result as DidResolutionResult.Success
        assertEquals(document, successResult.document)
        assertNotNull(successResult.documentMetadata.created)
        assertNotNull(successResult.documentMetadata.updated)
        assertEquals("key", successResult.resolutionMetadata["method"])
        assertEquals(true, successResult.resolutionMetadata["mock"])
    }

    @Test
    fun `test resolve non-existent DID`() = runBlocking {
        val result = didMethod.resolveDid("did:key:zNonexistent")

        assertNull(result.getDocumentOrNull())
        assertTrue(result is DidResolutionResult.Failure)
    }

    @Test
    fun `test update DID`() = runBlocking {
        val document = didMethod.createDid()
        val did = document.id

        val updated = didMethod.updateDid(did) { doc ->
            doc.copy(
                verificationMethod = doc.verificationMethod + VerificationMethod(
                    id = "$did#key-2",
                    type = "Ed25519VerificationKey2020",
                    controller = did,
                    publicKeyJwk = emptyMap()
                )
            )
        }

        assertEquals(2, updated.verificationMethod.size)
        val resolved = didMethod.resolveDid(did)
        val resolvedDoc = resolved.getDocumentOrNull()
        assertNotNull(resolvedDoc)
        assertEquals(2, resolvedDoc.verificationMethod.size)
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
        assertNull(resolved.getDocumentOrNull())
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

        assertNull(didMethod.resolveDid(doc1.id).getDocumentOrNull())
        assertNull(didMethod.resolveDid(doc2.id).getDocumentOrNull())
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

        override suspend fun getSupportedAlgorithms(): Set<com.trustweave.kms.Algorithm> {
            return setOf(
                com.trustweave.kms.Algorithm.Ed25519,
                com.trustweave.kms.Algorithm.Secp256k1,
                com.trustweave.kms.Algorithm.P256,
                com.trustweave.kms.Algorithm.P384,
                com.trustweave.kms.Algorithm.P521
            )
        }

        override suspend fun generateKey(algorithm: com.trustweave.kms.Algorithm, options: Map<String, Any?>): KeyHandle {
            val keyId = options["keyId"] as? String ?: "key-${System.currentTimeMillis()}"
            val handle = KeyHandle(
                id = com.trustweave.core.types.KeyId(keyId),
                algorithm = algorithm.name,
                publicKeyJwk = when (algorithm) {
                    is com.trustweave.kms.Algorithm.Ed25519 -> mapOf(
                        "kty" to "OKP",
                        "crv" to "Ed25519",
                        "x" to "mock-public-key"
                    )
                    is com.trustweave.kms.Algorithm.Secp256k1 -> mapOf(
                        "kty" to "EC",
                        "crv" to "secp256k1",
                        "x" to "mock-public-key-x",
                        "y" to "mock-public-key-y"
                    )
                    is com.trustweave.kms.Algorithm.P256 -> mapOf(
                        "kty" to "EC",
                        "crv" to "P-256",
                        "x" to "mock-public-key-x",
                        "y" to "mock-public-key-y"
                    )
                    else -> mapOf(
                        "kty" to "EC",
                        "crv" to algorithm.name,
                        "x" to "mock-public-key-x",
                        "y" to "mock-public-key-y"
                    )
                }
            )
            keys[keyId] = handle
            return handle
        }

        override suspend fun generateKey(algorithmName: String, options: Map<String, Any?>): KeyHandle {
            val algorithm = com.trustweave.kms.Algorithm.parse(algorithmName)
                ?: throw com.trustweave.kms.UnsupportedAlgorithmException("Unsupported algorithm: $algorithmName")
            return generateKey(algorithm, options)
        }

        override suspend fun getPublicKey(keyId: com.trustweave.core.types.KeyId): KeyHandle {
            return keys[keyId.value] ?: throw KmsException.KeyNotFound(keyId = keyId.value)
        }

        override suspend fun sign(keyId: com.trustweave.core.types.KeyId, data: ByteArray, algorithm: com.trustweave.kms.Algorithm?): ByteArray {
            return "signature".toByteArray()
        }

        override suspend fun deleteKey(keyId: com.trustweave.core.types.KeyId): Boolean {
            return keys.remove(keyId.value) != null
        }
    }
}
