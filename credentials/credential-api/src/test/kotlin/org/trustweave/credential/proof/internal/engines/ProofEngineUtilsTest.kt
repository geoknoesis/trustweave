@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package org.trustweave.credential.proof.internal.engines

import org.trustweave.credential.proof.internal.engines.ProofEngineUtils
import org.trustweave.core.identifiers.Iri
import org.trustweave.core.identifiers.KeyId
import org.trustweave.did.identifiers.Did
import org.trustweave.did.identifiers.VerificationMethodId
import org.trustweave.did.model.DidDocument
import org.trustweave.did.model.VerificationMethod
import org.trustweave.did.resolver.DidResolver
import org.trustweave.did.resolver.DidResolutionResult
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull

/**
 * Tests for ProofEngineUtils utility functions.
 */
class ProofEngineUtilsTest {
    
    @Test
    fun `test extractKeyId with full verification method ID`() {
        val verificationMethodId = "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK#key-1"
        val keyId = ProofEngineUtils.extractKeyId(verificationMethodId)
        
        assertEquals("key-1", keyId, "Should extract key ID from full verification method ID")
    }
    
    @Test
    fun `test extractKeyId with fragment only`() {
        val verificationMethodId = "key-1"
        val keyId = ProofEngineUtils.extractKeyId(verificationMethodId)
        
        assertEquals("key-1", keyId, "Should return fragment as-is when no # separator")
    }
    
    @Test
    fun `test extractKeyId with null`() {
        val keyId = ProofEngineUtils.extractKeyId(null)
        
        assertNull(keyId, "Should return null for null input")
    }
    
    @Test
    fun `test extractKeyId with multiple hash separators`() {
        val verificationMethodId = "did:key:test#key#1"
        val keyId = ProofEngineUtils.extractKeyId(verificationMethodId)
        
        assertEquals("key#1", keyId, "Should extract everything after first #")
    }
    
    @Test
    fun `test resolveVerificationMethod with valid DID and resolver`() = runBlocking {
        val issuerDid = Did("did:key:test")
        val issuerIri = Iri(issuerDid.value)
        val verificationMethodId = "did:key:test#key-1"
        
        val verificationMethod = VerificationMethod(
            id = VerificationMethodId(issuerDid, KeyId("key-1")),
            type = "Ed25519VerificationKey2020",
            controller = issuerDid,
            publicKeyJwk = null,
            publicKeyMultibase = null
        )
        
        val didDocument = DidDocument(
            id = issuerDid,
            verificationMethod = listOf(verificationMethod),
            assertionMethod = emptyList(),
            authentication = emptyList()
        )
        
        val didResolver = object : DidResolver {
            override suspend fun resolve(did: Did): DidResolutionResult {
                return if (did.value == issuerDid.value) {
                    DidResolutionResult.Success(didDocument)
                } else {
                    DidResolutionResult.Failure.NotFound(did, "Not found")
                }
            }
        }
        
        val result = ProofEngineUtils.resolveVerificationMethod(
            issuerIri = issuerIri,
            verificationMethodId = verificationMethodId,
            didResolver = didResolver
        )
        
        assertNotNull(result, "Should resolve verification method")
        assertEquals(verificationMethod.id, result?.id)
    }
    
    @Test
    fun `test resolveVerificationMethod with non-DID IRI`() = runBlocking {
        val issuerIri = Iri("https://example.com/issuer")
        val didResolver = object : DidResolver {
            override suspend fun resolve(did: Did): DidResolutionResult {
                throw NotImplementedError()
            }
        }
        
        val result = ProofEngineUtils.resolveVerificationMethod(
            issuerIri = issuerIri,
            verificationMethodId = "key-1",
            didResolver = didResolver
        )
        
        assertNull(result, "Should return null for non-DID IRI")
    }
    
    @Test
    fun `test resolveVerificationMethod with null resolver`() = runBlocking {
        val issuerDid = Did("did:key:test")
        val issuerIri = Iri(issuerDid.value)
        
        val result = ProofEngineUtils.resolveVerificationMethod(
            issuerIri = issuerIri,
            verificationMethodId = "key-1",
            didResolver = null
        )
        
        assertNull(result, "Should return null when resolver is null")
    }
    
    @Test
    fun `test resolveVerificationMethod with DID resolution failure`() = runBlocking {
        val issuerDid = Did("did:key:test")
        val issuerIri = Iri(issuerDid.value)
        
        val didResolver = object : DidResolver {
            override suspend fun resolve(did: Did): DidResolutionResult {
                return DidResolutionResult.Failure.NotFound(did, "Not found")
            }
        }
        
        val result = ProofEngineUtils.resolveVerificationMethod(
            issuerIri = issuerIri,
            verificationMethodId = "key-1",
            didResolver = didResolver
        )
        
        assertNull(result, "Should return null when DID resolution fails")
    }
    
    @Test
    fun `test resolveVerificationMethod with null verification method ID uses first assertion method`() = runBlocking {
        val issuerDid = Did("did:key:test")
        val issuerIri = Iri(issuerDid.value)
        
        val verificationMethod = VerificationMethod(
            id = VerificationMethodId(issuerDid, KeyId("key-1")),
            type = "Ed25519VerificationKey2020",
            controller = issuerDid,
            publicKeyJwk = null,
            publicKeyMultibase = null
        )
        
        val didDocument = DidDocument(
            id = issuerDid,
            verificationMethod = listOf(verificationMethod),
            assertionMethod = listOf(verificationMethod.id),
            authentication = emptyList()
        )
        
        val didResolver = object : DidResolver {
            override suspend fun resolve(did: Did): DidResolutionResult {
                return DidResolutionResult.Success(didDocument)
            }
        }
        
        val result = ProofEngineUtils.resolveVerificationMethod(
            issuerIri = issuerIri,
            verificationMethodId = null,
            didResolver = didResolver
        )
        
        assertNotNull(result, "Should use first assertion method when verification method ID is null")
        assertEquals(verificationMethod.id, result?.id)
    }
    
    @Test
    fun `test resolveVerificationMethod with null verification method ID uses first verification method if no assertion method`() = runBlocking {
        val issuerDid = Did("did:key:test")
        val issuerIri = Iri(issuerDid.value)
        
        val verificationMethod = VerificationMethod(
            id = VerificationMethodId(issuerDid, KeyId("key-1")),
            type = "Ed25519VerificationKey2020",
            controller = issuerDid,
            publicKeyJwk = null,
            publicKeyMultibase = null
        )
        
        val didDocument = DidDocument(
            id = issuerDid,
            verificationMethod = listOf(verificationMethod),
            assertionMethod = emptyList(),
            authentication = emptyList()
        )
        
        val didResolver = object : DidResolver {
            override suspend fun resolve(did: Did): DidResolutionResult {
                return DidResolutionResult.Success(didDocument)
            }
        }
        
        val result = ProofEngineUtils.resolveVerificationMethod(
            issuerIri = issuerIri,
            verificationMethodId = null,
            didResolver = didResolver
        )
        
        assertNotNull(result, "Should use first verification method when no assertion method")
        assertEquals(verificationMethod.id, result?.id)
    }
    
    @Test
    fun `test resolveVerificationMethod with null verification method ID returns null if no methods`() = runBlocking {
        val issuerDid = Did("did:key:test")
        val issuerIri = Iri(issuerDid.value)
        
        val didDocument = DidDocument(
            id = issuerDid,
            verificationMethod = emptyList(),
            assertionMethod = emptyList(),
            authentication = emptyList()
        )
        
        val didResolver = object : DidResolver {
            override suspend fun resolve(did: Did): DidResolutionResult {
                return DidResolutionResult.Success(didDocument)
            }
        }
        
        val result = ProofEngineUtils.resolveVerificationMethod(
            issuerIri = issuerIri,
            verificationMethodId = null,
            didResolver = didResolver
        )
        
        assertNull(result, "Should return null when no verification methods available")
    }
    
    @Test
    fun `test extractPublicKey with JWK`() {
        // Note: This test would require actual JWK data and public key extraction
        // For now, we test the null case and basic structure
        val verificationMethod = VerificationMethod(
            id = VerificationMethodId(Did("did:key:test"), KeyId("key-1")),
            type = "Ed25519VerificationKey2020",
            controller = Did("did:key:test"),
            publicKeyJwk = null,
            publicKeyMultibase = null
        )
        
        val result = ProofEngineUtils.extractPublicKey(verificationMethod)
        
        // Without JWK or multibase, should return null
        assertNull(result, "Should return null when no public key data available")
    }
    
    @Test
    fun `test extractPublicKey with multibase`() {
        val verificationMethod = VerificationMethod(
            id = VerificationMethodId(Did("did:key:test"), KeyId("key-1")),
            type = "Ed25519VerificationKey2020",
            controller = Did("did:key:test"),
            publicKeyJwk = null,
            publicKeyMultibase = "z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK"
        )
        
        val result = ProofEngineUtils.extractPublicKey(verificationMethod)
        
        // Should attempt to extract from multibase
        // Result depends on multibase format and implementation
        // This test verifies the function doesn't crash
        // Actual extraction testing would require valid multibase data
    }
}

