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
import org.trustweave.core.util.encodeBase58
import org.trustweave.kms.util.EcdsaSignatureCodec
import com.nimbusds.jose.JWSAlgorithm
import kotlinx.coroutines.runBlocking
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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
    fun `test extractPublicKey with known did-key multibase value`() {
        // z6Mk... values are base58btc(0xED 0x01 || raw 32-byte Ed25519 key) — the format
        // used by did:key and Ed25519VerificationKey2020 documents.
        val verificationMethod = multibaseVerificationMethod(
            "z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK"
        )

        val result = ProofEngineUtils.extractPublicKey(verificationMethod)

        assertNotNull(result, "z6Mk... did:key multibase values must yield an Ed25519 public key")
        assertTrue(
            result.algorithm.equals("Ed25519", ignoreCase = true) ||
                result.algorithm.equals("EdDSA", ignoreCase = true),
            "Extracted key must be Ed25519, got ${result.algorithm}"
        )
    }

    @Test
    fun `extractPublicKey from base58btc multibase with ed25519 multicodec prefix verifies signatures`() {
        val (keyPair, rawPublicKey) = generateEd25519KeyPair()
        val multibase = "z" + (byteArrayOf(0xED.toByte(), 0x01) + rawPublicKey).encodeBase58()

        val publicKey = ProofEngineUtils.extractPublicKey(multibaseVerificationMethod(multibase))
        assertNotNull(publicKey, "Multicodec-prefixed base58btc key must be extracted")

        val data = "multibase-test-payload".toByteArray()
        val signature = Signature.getInstance("Ed25519").apply {
            initSign(keyPair.private)
            update(data)
        }.sign()
        val verified = Signature.getInstance("Ed25519").apply {
            initVerify(publicKey)
            update(data)
        }.verify(signature)
        assertTrue(verified, "Key extracted from publicKeyMultibase must verify a genuine signature")
    }

    @Test
    fun `extractPublicKey from base58btc multibase with raw 32-byte key`() {
        val (_, rawPublicKey) = generateEd25519KeyPair()
        val multibase = "z" + rawPublicKey.encodeBase58()

        val publicKey = ProofEngineUtils.extractPublicKey(multibaseVerificationMethod(multibase))

        assertNotNull(publicKey, "Raw 32-byte Ed25519 multibase keys (no multicodec prefix) must be accepted")
    }

    @Test
    fun `extractPublicKey from base64url multibase`() {
        val (_, rawPublicKey) = generateEd25519KeyPair()
        val multibase = "u" + Base64.getUrlEncoder().withoutPadding()
            .encodeToString(byteArrayOf(0xED.toByte(), 0x01) + rawPublicKey)

        val publicKey = ProofEngineUtils.extractPublicKey(multibaseVerificationMethod(multibase))

        assertNotNull(publicKey, "'u' (base64url) multibase keys must be accepted")
    }

    @Test
    fun `extractPublicKey rejects multibase with non-ed25519 multicodec prefix`() {
        val (_, rawPublicKey) = generateEd25519KeyPair()
        // 0xE7 0x01 is the multicodec secp256k1-pub prefix — not an Ed25519 key.
        val multibase = "z" + (byteArrayOf(0xE7.toByte(), 0x01) + rawPublicKey).encodeBase58()

        val publicKey = ProofEngineUtils.extractPublicKey(multibaseVerificationMethod(multibase))

        assertNull(publicKey, "A non-ed25519 multicodec prefix must be rejected (fail-closed)")
    }

    @Test
    fun `extractPublicKey rejects unsupported multibase prefix`() {
        val publicKey = ProofEngineUtils.extractPublicKey(
            multibaseVerificationMethod("fdeadbeefdeadbeef")
        )

        assertNull(publicKey, "Unsupported multibase prefixes must be rejected")
    }

    @Test
    fun `extractPublicKey rejects malformed base58 payload`() {
        // '0', 'O', 'I' and 'l' are not in the base58btc alphabet.
        val publicKey = ProofEngineUtils.extractPublicKey(
            multibaseVerificationMethod("z0OIl0OIl0OIl")
        )

        assertNull(publicKey, "Malformed base58 must be rejected, not throw")
    }

    // --- ECDSA JWS signature normalization (Finding 13) --------------------------------

    @Test
    fun `ensureP1363EcdsaJwsSignature transcodes DER input and preserves the signature`() {
        val keyPairGenerator = KeyPairGenerator.getInstance("EC")
        keyPairGenerator.initialize(ECGenParameterSpec("secp256r1"))
        val keyPair = keyPairGenerator.generateKeyPair()

        val data = "der-transcode-payload".toByteArray()
        // JCA emits ASN.1 DER for ECDSA.
        val derSignature = Signature.getInstance("SHA256withECDSA").apply {
            initSign(keyPair.private)
            update(data)
        }.sign()
        assertEquals(0x30, derSignature[0].toInt() and 0xFF, "JCA ECDSA signatures are DER SEQUENCEs")

        val p1363 = ProofEngineUtils.ensureP1363EcdsaJwsSignature(derSignature, JWSAlgorithm.ES256)

        assertEquals(64, p1363.size, "ES256 JWS signatures must be 64-byte P1363 r||s")
        // Transcoding must preserve (r, s): converting back to DER must still verify.
        val verified = Signature.getInstance("SHA256withECDSA").apply {
            initVerify(keyPair.public)
            update(data)
        }.verify(EcdsaSignatureCodec.p1363ToDer(p1363))
        assertTrue(verified, "DER -> P1363 -> DER round-trip must preserve the signature")
    }

    @Test
    fun `ensureP1363EcdsaJwsSignature passes through correctly sized P1363 input`() {
        val p1363 = ByteArray(64) { (it + 1).toByte() }

        val result = ProofEngineUtils.ensureP1363EcdsaJwsSignature(p1363, JWSAlgorithm.ES256)

        assertContentEquals(p1363, result, "P1363-sized input must pass through unchanged")
    }

    @Test
    fun `ensureP1363EcdsaJwsSignature fails closed on garbage input`() {
        val garbage = ByteArray(71) { 0x42 }

        assertFailsWith<IllegalStateException> {
            ProofEngineUtils.ensureP1363EcdsaJwsSignature(garbage, JWSAlgorithm.ES256)
        }
    }

    @Test
    fun `ensureP1363EcdsaJwsSignature rejects non-ECDSA algorithms`() {
        assertFailsWith<IllegalArgumentException> {
            ProofEngineUtils.ensureP1363EcdsaJwsSignature(ByteArray(64), JWSAlgorithm.EdDSA)
        }
    }

    // --- helpers ------------------------------------------------------------------------

    private fun multibaseVerificationMethod(multibase: String): VerificationMethod =
        VerificationMethod(
            id = VerificationMethodId(Did("did:key:test"), KeyId("key-1")),
            type = "Ed25519VerificationKey2020",
            controller = Did("did:key:test"),
            publicKeyJwk = null,
            publicKeyMultibase = multibase
        )

    private fun generateEd25519KeyPair(): Pair<KeyPair, ByteArray> {
        val keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        // X.509 SubjectPublicKeyInfo for Ed25519: 12-byte header + 32-byte raw key.
        val encoded = keyPair.public.encoded
        return keyPair to encoded.copyOfRange(encoded.size - 32, encoded.size)
    }
}

