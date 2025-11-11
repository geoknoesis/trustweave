package com.geoknoesis.vericore.did

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.*
import java.time.Instant
import com.geoknoesis.vericore.did.DidCreationOptions
import com.geoknoesis.vericore.did.didCreationOptions

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
        assertTrue(document.id.startsWith("did:key:"))
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
        assertNotNull(result.document)
        assertEquals(did, result.document?.id)
    }

    @Test
    fun `test DidMethod resolveDid returns null document for non-existent DID`() = runBlocking {
        val method = createMockMethod("key")
        val did = "did:key:nonexistent"
        
        val result = method.resolveDid(did)
        
        assertNull(result.document)
        assertTrue(result.resolutionMetadata.containsKey("error"))
    }

    @Test
    fun `test DidMethod updateDid returns updated document`() = runBlocking {
        val method = createMockMethod("key")
        val document = method.createDid()
        val did = document.id
        
        val updated = method.updateDid(did) { doc ->
            doc.copy(
                service = doc.service + Service(
                    id = "$did#service-1",
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
        
        val deactivated = method.deactivateDid("did:key:nonexistent")
        
        assertFalse(deactivated)
    }

    @Test
    fun `test DidMethod createDid then resolveDid`() = runBlocking {
        val method = createMockMethod("key")
        val document = method.createDid()
        val did = document.id
        
        val result = method.resolveDid(did)
        
        assertNotNull(result.document)
        assertEquals(document.id, result.document?.id)
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
                verificationMethod = doc.verificationMethod + VerificationMethodRef(
                    id = "$did#key-2",
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
                val id = "did:$methodName:${java.util.UUID.randomUUID().toString().take(8)}"
                val doc = DidDocument(
                    id = id,
                    verificationMethod = listOf(
                        VerificationMethodRef(
                            id = "$id#key-1",
                            type = "Ed25519VerificationKey2020",
                            controller = id
                        )
                    )
                )
                documents[id] = doc
                return doc
            }
            
            override suspend fun resolveDid(did: String): DidResolutionResult {
                if (deactivated.contains(did)) {
                    return DidResolutionResult(
                        document = null,
                        resolutionMetadata = mapOf("error" to "deactivated")
                    )
                }
                
                val doc = documents[did]
                return if (doc != null) {
                    DidResolutionResult(
                        document = doc,
                        documentMetadata = DidDocumentMetadata(),
                        resolutionMetadata = emptyMap()
                    )
                } else {
                    DidResolutionResult(
                        document = null,
                        resolutionMetadata = mapOf("error" to "notFound")
                    )
                }
            }
            
            override suspend fun updateDid(
                did: String,
                updater: (DidDocument) -> DidDocument
            ): DidDocument {
                val current = documents[did] ?: throw IllegalArgumentException("DID not found: $did")
                val updated = updater(current)
                documents[did] = updated
                return updated
            }
            
            override suspend fun deactivateDid(did: String): Boolean {
                return if (documents.containsKey(did)) {
                    deactivated.add(did)
                    documents.remove(did)
                    true
                } else {
                    false
                }
            }
        }
    }
}

