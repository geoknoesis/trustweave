package org.trustweave.keydid

import org.trustweave.core.util.decodeBase58
import org.trustweave.core.util.encodeBase58
import org.trustweave.did.*
import org.trustweave.did.KeyAlgorithm
import org.trustweave.did.identifiers.Did
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class KeyDidMethodTest {

    private lateinit var kms: InMemoryKeyManagementService
    private lateinit var method: KeyDidMethod

    @BeforeEach
    fun setup() {
        kms = InMemoryKeyManagementService()
        method = KeyDidMethod(kms)
    }

    @Test
    fun `test method name is key`() {
        assertEquals("key", method.method)
    }

    @Test
    fun `test create DID with Ed25519`() = runBlocking {
        val document = method.createDid(
            didCreationOptions {
                algorithm = KeyAlgorithm.ED25519
            }
        )

        assertNotNull(document)
        assertTrue(document.id.value.startsWith("did:key:z"))
        assertEquals(1, document.verificationMethod.size)
        assertEquals(1, document.authentication.size)
    }

    @Test
    fun `test resolve DID after creation`() = runBlocking {
        val document = method.createDid(
            didCreationOptions {
                algorithm = KeyAlgorithm.ED25519
            }
        )

        val result = method.resolveDid(document.id)

        assertTrue(result is DidResolutionResult.Success)
        val successResult = result as DidResolutionResult.Success
        assertEquals(document.id, successResult.document.id)
        assertEquals("key", successResult.resolutionMetadata.pattern)
    }

    @Test
    fun `test DID format includes multibase prefix`() = runBlocking {
        val document = method.createDid(
            didCreationOptions {
                algorithm = KeyAlgorithm.ED25519
            }
        )

        // did:key should start with z (base58btc multibase prefix)
        assertTrue(document.id.value.matches(Regex("^did:key:z[a-zA-Z0-9]+$")))
    }

    // ─── Spec compliance: EC keys must be COMPRESSED SEC1 points (multicodec table) ───

    @Test
    fun `test created secp256k1 did encodes a compressed point`() = runBlocking {
        val document = method.createDid(
            didCreationOptions {
                algorithm = KeyAlgorithm.SECP256K1
            }
        )

        val multibase = document.id.value.removePrefix("did:key:")
        assertTrue(multibase.startsWith("z"))
        val prefixedKey = multibase.substring(1).decodeBase58()

        // secp256k1-pub multicodec prefix (0xe7 varint-encoded)
        assertEquals(0xe7.toByte(), prefixedKey[0])
        assertEquals(0x01.toByte(), prefixedKey[1])

        // 33-byte compressed SEC1 point with 0x02/0x03 prefix
        val point = prefixedKey.sliceArray(2 until prefixedKey.size)
        assertEquals(33, point.size)
        assertTrue(point[0] == 0x02.toByte() || point[0] == 0x03.toByte())
    }

    @Test
    fun `test resolve compressed secp256k1 did from did-key test vectors`() = runBlocking {
        // Cross-stack fixture from the W3C did:key test vectors (compressed secp256k1 point).
        val did = Did("did:key:zQ3shokFTS3brHcDQrn82RUDfCZESWL1ZdCEJwekUDPQiYBme")

        val result = method.resolveDid(did)
        assertTrue(result is DidResolutionResult.Success)
        val document = (result as DidResolutionResult.Success).document

        val jwk = document.verificationMethod.first().publicKeyJwk
        assertNotNull(jwk, "compressed key must be decompressed into a full JWK")
        assertEquals("EC", jwk["kty"])
        assertEquals("secp256k1", jwk["crv"])
        // Decompressed coordinates (verified against the curve equation)
        assertEquals("h0wVx_2iDlOcblulc8E5iEw1EYh5n1RYtLQfeSTyNc0", jwk["x"])
        assertEquals("O2EATIGbu6DezKFptj5scAIRntgfecanVNXxat1rnwE", jwk["y"])
    }

    @Test
    fun `test resolve compressed P-256 did from did-key test vectors`() = runBlocking {
        val did = Did("did:key:zDnaerDaTF5BXEavCrfRZEk316dpbLsfPDZ3WJ5hRTPFU2169")

        val result = method.resolveDid(did)
        assertTrue(result is DidResolutionResult.Success)
        val document = (result as DidResolutionResult.Success).document

        val jwk = document.verificationMethod.first().publicKeyJwk
        assertNotNull(jwk)
        assertEquals("EC", jwk["kty"])
        assertEquals("P-256", jwk["crv"])
        assertEquals("fyNYMN0976ci7xqiSdag3buk-ZCwgXU4kz9XNkBlNUI", jwk["x"])
        assertEquals("hW2ojTNfH7Jbi8--CJUo3OCbH3y5n91g-IMA9MLMbTU", jwk["y"])
    }

    @Test
    fun `test legacy uncompressed secp256k1 did is still accepted on parse`() = runBlocking {
        // Same point as the zQ3shok... fixture, but in legacy uncompressed (0x04) form.
        val did = Did(
            "did:key:z7r8orBc5GYWTuwPZ8WeGtjkLynA7cUcFnXWLgWWSwn6apr3DKiiRxHYkD7N5KzKzYKWCSxezzdBayD2jdkM6cumBJxcG"
        )

        val result = method.resolveDid(did)
        assertTrue(result is DidResolutionResult.Success)
        val document = (result as DidResolutionResult.Success).document

        val jwk = document.verificationMethod.first().publicKeyJwk
        assertNotNull(jwk)
        assertEquals("secp256k1", jwk["crv"])
        assertEquals("h0wVx_2iDlOcblulc8E5iEw1EYh5n1RYtLQfeSTyNc0", jwk["x"])
        assertEquals("O2EATIGbu6DezKFptj5scAIRntgfecanVNXxat1rnwE", jwk["y"])
    }

    // ─── Spec compliance: x25519-pub (0xec) multicodec support ───

    @Test
    fun `test resolve x25519 did produces key agreement only document`() = runBlocking {
        // 0xec 0x01 multicodec prefix + deterministic 32-byte X25519 public key
        val keyBytes = ByteArray(32) { (it + 1).toByte() }
        val multibase = "z" + (byteArrayOf(0xec.toByte(), 0x01) + keyBytes).encodeBase58()
        val did = Did("did:key:$multibase")

        val result = method.resolveDid(did)
        assertTrue(result is DidResolutionResult.Success)
        val document = (result as DidResolutionResult.Success).document

        // X25519 keys are key-agreement-only: no authentication/assertion relationships
        assertEquals(1, document.keyAgreement.size)
        assertTrue(document.authentication.isEmpty())
        assertTrue(document.assertionMethod.isEmpty())

        val vm = document.verificationMethod.first()
        assertEquals("X25519KeyAgreementKey2020", vm.type)
        val jwk = vm.publicKeyJwk
        assertNotNull(jwk)
        assertEquals("OKP", jwk["kty"])
        assertEquals("X25519", jwk["crv"])
        assertEquals("AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA", jwk["x"])
    }

    @Test
    fun `test created secp256k1 did round-trips through resolution`() = runBlocking {
        val document = method.createDid(
            didCreationOptions {
                algorithm = KeyAlgorithm.SECP256K1
            }
        )

        val result = method.resolveDid(document.id)
        assertTrue(result is DidResolutionResult.Success)
        assertEquals(document.id, (result as DidResolutionResult.Success).document.id)
    }
}

