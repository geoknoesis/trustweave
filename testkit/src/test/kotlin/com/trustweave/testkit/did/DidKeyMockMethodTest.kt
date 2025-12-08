package com.trustweave.testkit.did

import com.trustweave.did.*
import com.trustweave.did.model.VerificationMethod
import com.trustweave.did.resolver.DidResolutionResult
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
        assertTrue(document.id.value.startsWith("did:key:z"))
        assertEquals(1, document.verificationMethod.size)
        assertEquals(1, document.authentication.size)
        assertEquals(1, document.assertionMethod.size)
        assertTrue(document.authentication.first().value.startsWith(document.id.value))
        assertTrue(document.assertionMethod.first().value.startsWith(document.id.value))
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
                algorithm = com.trustweave.did.KeyAlgorithm.SECP256K1
            }
        )

        assertNotNull(document)
        assertEquals("EcdsaSecp256k1VerificationKey2019", document.verificationMethod.first().type)
    }

    @Test
    fun `test create DID with fallback algorithm`() = runBlocking {
        val document = didMethod.createDid(
            didCreationOptions {
                algorithm = com.trustweave.did.KeyAlgorithm.P256
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
        val result = didMethod.resolveDid(com.trustweave.did.identifiers.Did("did:key:zNonexistent"))

        assertNull((result as? DidResolutionResult.Success)?.document)
        assertTrue(result is DidResolutionResult.Failure)
    }

    @Test
    fun `test update DID`() = runBlocking {
        val document = didMethod.createDid()
        val did = document.id

        val updated = didMethod.updateDid(did) { doc ->
            doc.copy(
                verificationMethod = doc.verificationMethod + VerificationMethod(
                    id = com.trustweave.did.identifiers.VerificationMethodId.parse("${did.value}#key-2"),
                    type = "Ed25519VerificationKey2020",
                    controller = did,
                    publicKeyJwk = emptyMap()
                )
            )
        }

        assertEquals(2, updated.verificationMethod.size)
        val resolved = didMethod.resolveDid(did)
        val resolvedDoc = (resolved as? DidResolutionResult.Success)?.document
        assertNotNull(resolvedDoc)
        assertEquals(2, resolvedDoc.verificationMethod.size)
    }

    @Test
    fun `test update non-existent DID throws exception`() = runBlocking {
        assertFailsWith<IllegalArgumentException> {
            didMethod.updateDid(com.trustweave.did.identifiers.Did("did:key:zNonexistent")) { it }
        }
    }

    @Test
    fun `test deactivate DID`() = runBlocking {
        val document = didMethod.createDid()
        val did = document.id

        val result = didMethod.deactivateDid(did)

        assertTrue(result)
        val resolved = didMethod.resolveDid(did)
        assertNull((resolved as? DidResolutionResult.Success)?.document)
    }

    @Test
    fun `test deactivate non-existent DID`() = runBlocking {
        val result = didMethod.deactivateDid(com.trustweave.did.identifiers.Did("did:key:zNonexistent"))

        assertFalse(result)
    }

    @Test
    fun `test clear all DIDs`() = runBlocking {
        val doc1 = didMethod.createDid()
        val doc2 = didMethod.createDid()

        didMethod.clear()

        assertNull((didMethod.resolveDid(doc1.id) as? DidResolutionResult.Success)?.document)
        assertNull((didMethod.resolveDid(doc2.id) as? DidResolutionResult.Success)?.document)
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
            assertTrue(vm.id.value.startsWith(document.id.value))
            assertTrue(vm.id.value.contains("#"))
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

        override suspend fun generateKey(algorithm: com.trustweave.kms.Algorithm, options: Map<String, Any?>): com.trustweave.kms.results.GenerateKeyResult {
            val keyId = options["keyId"] as? String ?: "key-${System.currentTimeMillis()}"
            val handle = KeyHandle(
                id = com.trustweave.core.identifiers.KeyId(keyId),
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
            return com.trustweave.kms.results.GenerateKeyResult.Success(handle)
        }

        override suspend fun getPublicKey(keyId: com.trustweave.core.identifiers.KeyId): com.trustweave.kms.results.GetPublicKeyResult {
            val handle = keys[keyId.value]
            return if (handle != null) {
                com.trustweave.kms.results.GetPublicKeyResult.Success(handle)
            } else {
                com.trustweave.kms.results.GetPublicKeyResult.Failure.KeyNotFound(keyId = keyId)
            }
        }

        override suspend fun sign(keyId: com.trustweave.core.identifiers.KeyId, data: ByteArray, algorithm: com.trustweave.kms.Algorithm?): com.trustweave.kms.results.SignResult {
            return com.trustweave.kms.results.SignResult.Success("signature".toByteArray())
        }

        override suspend fun deleteKey(keyId: com.trustweave.core.identifiers.KeyId): com.trustweave.kms.results.DeleteKeyResult {
            val existed = keys.remove(keyId.value) != null
            return if (existed) {
                com.trustweave.kms.results.DeleteKeyResult.Deleted
            } else {
                com.trustweave.kms.results.DeleteKeyResult.NotFound
            }
        }
    }
}
