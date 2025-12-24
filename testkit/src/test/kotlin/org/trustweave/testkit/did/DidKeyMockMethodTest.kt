package org.trustweave.testkit.did

import org.trustweave.core.identifiers.KeyId
import org.trustweave.did.*
import org.trustweave.did.identifiers.VerificationMethodId
import org.trustweave.did.model.VerificationMethod
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.kms.Algorithm
import org.trustweave.kms.KeyHandle
import org.trustweave.kms.KeyManagementService
import org.trustweave.kms.exception.KmsException
import org.trustweave.kms.results.DeleteKeyResult
import org.trustweave.kms.results.GenerateKeyResult
import org.trustweave.kms.results.GetPublicKeyResult
import org.trustweave.kms.results.SignResult
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
                algorithm = KeyAlgorithm.SECP256K1
            }
        )

        assertNotNull(document)
        assertEquals("EcdsaSecp256k1VerificationKey2019", document.verificationMethod.first().type)
    }

    @Test
    fun `test create DID with fallback algorithm`() = runBlocking {
        val document = didMethod.createDid(
            didCreationOptions {
                algorithm = KeyAlgorithm.P256
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
        val result = didMethod.resolveDid(org.trustweave.did.identifiers.Did("did:key:zNonexistent"))

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
                    id = VerificationMethodId.parse("${did.value}#key-2"),
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
            didMethod.updateDid(org.trustweave.did.identifiers.Did("did:key:zNonexistent")) { it }
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
        val result = didMethod.deactivateDid(org.trustweave.did.identifiers.Did("did:key:zNonexistent"))

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

        override suspend fun getSupportedAlgorithms(): Set<Algorithm> {
            return setOf(
                Algorithm.Ed25519,
                Algorithm.Secp256k1,
                Algorithm.P256,
                Algorithm.P384,
                Algorithm.P521
            )
        }

        override suspend fun generateKey(algorithm: Algorithm, options: Map<String, Any?>): GenerateKeyResult {
            val keyId = options["keyId"] as? String ?: "key-${System.currentTimeMillis()}"
            val handle = KeyHandle(
                id = KeyId(keyId),
                algorithm = algorithm.name,
                publicKeyJwk = when (algorithm) {
                    is Algorithm.Ed25519 -> mapOf(
                        "kty" to "OKP",
                        "crv" to "Ed25519",
                        "x" to "mock-public-key"
                    )
                    is Algorithm.Secp256k1 -> mapOf(
                        "kty" to "EC",
                        "crv" to "secp256k1",
                        "x" to "mock-public-key-x",
                        "y" to "mock-public-key-y"
                    )
                    is Algorithm.P256 -> mapOf(
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
            return GenerateKeyResult.Success(handle)
        }

        override suspend fun getPublicKey(keyId: KeyId): GetPublicKeyResult {
            val handle = keys[keyId.value]
            return if (handle != null) {
GetPublicKeyResult.Success(handle)
            } else {
GetPublicKeyResult.Failure.KeyNotFound(keyId = keyId)
            }
        }

        override suspend fun sign(keyId: KeyId, data: ByteArray, algorithm: Algorithm?): SignResult {
            return SignResult.Success("signature".toByteArray())
        }

        override suspend fun deleteKey(keyId: KeyId): DeleteKeyResult {
            val existed = keys.remove(keyId.value) != null
            return if (existed) {
DeleteKeyResult.Deleted
            } else {
DeleteKeyResult.NotFound
            }
        }
    }
}
