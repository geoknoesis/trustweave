package org.trustweave.did.verification

import kotlinx.coroutines.runBlocking
import org.trustweave.core.identifiers.KeyId
import org.trustweave.core.util.encodeBase58
import org.trustweave.did.identifiers.Did
import org.trustweave.did.identifiers.VerificationMethodId
import org.trustweave.did.model.DidDocument
import org.trustweave.did.model.VerificationMethod
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Real Ed25519 verification in [DefaultDidDocumentVerificationService.verifyVerificationMethod]. */
class Ed25519VerificationMethodTest {
    private val did = Did("did:example:ed25519")
    private val data = "payload to sign".toByteArray()

    private val keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()

    /** The raw 32-byte key is the tail of the X.509 encoding. */
    private val rawPublicKey =
        keyPair.public.encoded
            .takeLast(32)
            .toByteArray()

    private fun sign(bytes: ByteArray): ByteArray =
        Signature.getInstance("Ed25519").run {
            initSign(keyPair.private)
            update(bytes)
            sign()
        }

    private val service =
        DefaultDidDocumentVerificationService(
            object : DidDocumentCanonicalizationService {
                override suspend fun canonicalize(document: DidDocument) = ""

                override suspend fun computeDigest(document: DidDocument) = ""

                override suspend fun normalize(document: DidDocument) = document
            },
        )

    private fun multikeyMethod(type: String = "Ed25519VerificationKey2020") =
        VerificationMethod(
            id = VerificationMethodId(did = did, keyId = KeyId("key-1")),
            type = type,
            controller = did,
            publicKeyMultibase = "z" + (byteArrayOf(0xed.toByte(), 0x01) + rawPublicKey).encodeBase58(),
        )

    private fun jwkMethod() =
        VerificationMethod(
            id = VerificationMethodId(did = did, keyId = KeyId("key-2")),
            type = "JsonWebKey2020",
            controller = did,
            publicKeyJwk =
                mapOf(
                    "kty" to "OKP",
                    "crv" to "Ed25519",
                    "x" to Base64.getUrlEncoder().withoutPadding().encodeToString(rawPublicKey),
                ),
        )

    private fun b64url(bytes: ByteArray) = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    @Test
    fun `a valid signature verifies with a multibase key`() =
        runBlocking<Unit> {
            assertTrue(service.verifyVerificationMethod(multikeyMethod(), b64url(sign(data)), data))
            assertTrue(service.verifyVerificationMethod(multikeyMethod("Multikey"), "z" + sign(data).encodeBase58(), data))
        }

    @Test
    fun `a valid signature verifies with a JWK key`() =
        runBlocking<Unit> {
            assertTrue(service.verifyVerificationMethod(jwkMethod(), "u" + b64url(sign(data)), data))
        }

    @Test
    fun `tampered data does not verify`() =
        runBlocking<Unit> {
            assertFalse(service.verifyVerificationMethod(multikeyMethod(), b64url(sign(data)), "other".toByteArray()))
        }

    @Test
    fun `a signature from another key does not verify`() =
        runBlocking<Unit> {
            val other = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
            val foreign =
                Signature.getInstance("Ed25519").run {
                    initSign(other.private)
                    update(data)
                    sign()
                }
            assertFalse(service.verifyVerificationMethod(multikeyMethod(), b64url(foreign), data))
        }

    @Test
    fun `a truncated signature is refused`() =
        runBlocking<Unit> {
            assertFalse(service.verifyVerificationMethod(multikeyMethod(), b64url(sign(data).copyOf(63)), data))
        }
}
