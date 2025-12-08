package com.trustweave.did

import com.trustweave.did.identifiers.Did
import com.trustweave.did.identifiers.VerificationMethodId
import com.trustweave.did.model.DidDocument
import com.trustweave.did.model.DidDocumentMetadata
import com.trustweave.did.model.DidService
import com.trustweave.did.model.VerificationMethod
import com.trustweave.did.resolver.DidResolutionResult
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.*
import com.trustweave.did.DidCreationOptions
import com.trustweave.did.didCreationOptions

/**
 * Comprehensive interface contract tests for DidMethod.
 * Tests all methods, branches, and edge cases.
 */
class DidMethodInterfaceContractTest {

    @Test
    fun `test DidMethod method returns method name`() = runBlocking {
        val method = createMockMethod("key")

        assertEquals("key", method.method)
    }

    @Test
    fun `test DidMethod createDid returns DID document`() = runBlocking {
        val method = createMockMethod("key")

        val document = method.createDid()

        assertNotNull(document)
        assertTrue(document.id.value.startsWith("did:key:"))
    }

    @Test
    fun `test DidMethod createDid with options`() = runBlocking {
        val method = createMockMethod("key")
        val options = didCreationOptions {
            property("keyType", "Ed25519")
        }

        val document = method.createDid(options)

        assertNotNull(document)
    }

    @Test
    fun `test DidMethod resolveDid returns resolution result`() = runBlocking {
        val method = createMockMethod("key")
        val document = method.createDid()
        val did = document.id

        val result = method.resolveDid(did)

        assertNotNull(result)
        assertTrue(result is DidResolutionResult.Success)
        assertNotNull(result.document)
        assertEquals(did, result.document.id)
    }

    @Test
    fun `test DidMethod resolveDid returns null document for non-existent DID`() = runBlocking {
        val method = createMockMethod("key")
        val did = Did("did:key:nonexistent")

        val result = method.resolveDid(did)

        assertTrue(result is DidResolutionResult.Failure.NotFound)
        assertTrue(result.resolutionMetadata.containsKey("error"))
    }

    @Test
    fun `test DidMethod updateDid returns updated document`() = runBlocking {
        val method = createMockMethod("key")
        val document = method.createDid()
        val did = document.id

        val updated = method.updateDid(did) { doc ->
            doc.copy(
                service = doc.service + DidService(
                    id = "${did.value}#service-1",
                    type = "LinkedDomains",
                    serviceEndpoint = "https://example.com"
                )
            )
        }

        assertNotNull(updated)
        assertTrue(updated.service.isNotEmpty())
    }

    @Test
    fun `test DidMethod deactivateDid returns true`() = runBlocking {
        val method = createMockMethod("key")
        val document = method.createDid()
        val did = document.id

        val deactivated = method.deactivateDid(did)

        assertTrue(deactivated)
    }

    @Test
    fun `test DidMethod deactivateDid returns false for non-existent DID`() = runBlocking {
        val method = createMockMethod("key")

        val deactivated = method.deactivateDid(Did("did:key:nonexistent"))

        assertFalse(deactivated)
    }

    @Test
    fun `test DidMethod createDid then resolveDid`() = runBlocking {
        val method = createMockMethod("key")
        val document = method.createDid()
        val did = document.id

        val result = method.resolveDid(did)

        assertTrue(result is DidResolutionResult.Success)
        assertNotNull(result.document)
        assertEquals(document.id, result.document.id)
    }

    @Test
    fun `test DidMethod updateDid with empty updater`() = runBlocking {
        val method = createMockMethod("key")
        val document = method.createDid()
        val did = document.id

        val updated = method.updateDid(did) { it }

        assertNotNull(updated)
    }

    @Test
    fun `test DidMethod updateDid adds verification method`() = runBlocking {
        val method = createMockMethod("key")
        val document = method.createDid()
        val did = document.id

        val updated = method.updateDid(did) { doc ->
            doc.copy(
                verificationMethod = doc.verificationMethod + VerificationMethod(
                    id = VerificationMethodId.parse("${did.value}#key-2"),
                    type = "Ed25519VerificationKey2020",
                    controller = did
                )
            )
        }

        assertTrue(updated.verificationMethod.size > document.verificationMethod.size)
    }

    private fun createMockMethod(methodName: String): DidMethod {
        return object : DidMethod {
            override val method: String = methodName
            private val documents = mutableMapOf<String, DidDocument>()
            private val deactivated = mutableSetOf<String>()

            override suspend fun createDid(options: DidCreationOptions): DidDocument {
                val idString = "did:$methodName:${java.util.UUID.randomUUID().toString().take(8)}"
                val id = Did(idString)
                val doc = DidDocument(
                    id = id,
                    verificationMethod = listOf(
                        VerificationMethod(
                            id = VerificationMethodId.parse("$idString#key-1"),
                            type = "Ed25519VerificationKey2020",
                            controller = id
                        )
                    )
                )
                documents[idString] = doc
                return doc
            }

            override suspend fun resolveDid(did: Did): DidResolutionResult {
                val didString = did.value
                if (deactivated.contains(didString)) {
                    return DidResolutionResult.Failure.NotFound(
                        did = did,
                        reason = "deactivated",
                        resolutionMetadata = mapOf("error" to "deactivated")
                    )
                }

                val doc = documents[didString]
                return if (doc != null) {
                    DidResolutionResult.Success(
                        document = doc,
                        documentMetadata = DidDocumentMetadata(),
                        resolutionMetadata = emptyMap()
                    )
                } else {
                    DidResolutionResult.Failure.NotFound(
                        did = did,
                        reason = "notFound",
                        resolutionMetadata = mapOf("error" to "notFound")
                    )
                }
            }

            override suspend fun updateDid(
                did: Did,
                updater: (DidDocument) -> DidDocument
            ): DidDocument {
                val current = documents[did.value] ?: throw IllegalArgumentException("DID not found: ${did.value}")
                val updated = updater(current)
                documents[did.value] = updated
                return updated
            }

            override suspend fun deactivateDid(did: Did): Boolean {
                return if (documents.containsKey(did.value)) {
                    deactivated.add(did.value)
                    documents.remove(did.value)
                    true
                } else {
                    false
                }
            }
        }
    }
}

