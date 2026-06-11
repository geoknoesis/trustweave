package org.trustweave.testkit.kms

import kotlinx.coroutines.runBlocking
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.trustweave.kms.Algorithm
import org.trustweave.kms.results.GenerateKeyResult
import org.trustweave.kms.results.SignResult
import org.trustweave.kms.util.EcdsaSignatureCodec
import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Security
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the testkit [InMemoryKeyManagementService] to the
 * [org.trustweave.kms.KeyManagementService] contract, mirroring the production
 * `kms:plugins:inmemory` conformance:
 *
 *  - secp256k1 signatures are 64-byte P1363 (raw `r || s`) with low-s (EIP-2/Bitcoin),
 *  - the secp256k1 JWK carries the REAL affine x/y coordinates: a JCA public key
 *    rebuilt from the JWK must verify the signature,
 *  - Ed25519 signatures are the raw 64-byte RFC 8032 form,
 *  - algorithm compatibility follows [Algorithm.isCompatibleWith].
 */
class InMemoryKmsContractConformanceTest {

    private val kms = InMemoryKeyManagementService()
    private val data = "testkit-kms-contract".toByteArray(Charsets.UTF_8)

    private val secp256k1HalfOrder = BigInteger(
        "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141",
        16
    ).shiftRight(1)

    private fun generateAndSign(algorithm: Algorithm): Pair<ByteArray, Map<String, Any?>> = runBlocking {
        val gen = kms.generateKey(algorithm, emptyMap())
        assertTrue(gen is GenerateKeyResult.Success, "expected Success, got $gen")
        val handle = (gen as GenerateKeyResult.Success).keyHandle
        val sign = kms.sign(handle.id, data)
        assertTrue(sign is SignResult.Success, "expected Success, got $sign")
        (sign as SignResult.Success).signature to handle.publicKeyJwk!!
    }

    /** Rebuilds a JCA EC [PublicKey] from the secp256k1 JWK via BouncyCastle. */
    private fun secp256k1PublicKeyFromJwk(jwk: Map<String, Any?>): PublicKey {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
        val decoder = Base64.getUrlDecoder()
        val x = BigInteger(1, decoder.decode(jwk["x"] as String))
        val y = BigInteger(1, decoder.decode(jwk["y"] as String))
        val params = AlgorithmParameters.getInstance("EC", BouncyCastleProvider.PROVIDER_NAME)
            .apply { init(ECGenParameterSpec("secp256k1")) }
        val spec = params.getParameterSpec(java.security.spec.ECParameterSpec::class.java)
        return KeyFactory.getInstance("EC", BouncyCastleProvider.PROVIDER_NAME)
            .generatePublic(ECPublicKeySpec(ECPoint(x, y), spec))
    }

    @Test
    fun `secp256k1 signature is 64-byte P1363 with low s and verifies against the JWK`() {
        // ECDSA s is uniformly random in (0, n); a single signature has ~50% chance of
        // being naturally low-s. Repeat so a missing low-s normalization is caught with
        // overwhelming probability.
        repeat(16) {
            val (signature, jwk) = generateAndSign(Algorithm.Secp256k1)

            assertEquals(64, signature.size, "secp256k1 signature must be 64-byte P1363 (raw r||s)")

            val s = BigInteger(1, signature.copyOfRange(32, 64))
            assertTrue(
                s <= secp256k1HalfOrder,
                "secp256k1 signature must be low-s (EIP-2 / Bitcoin), got s > n/2"
            )

            // The JWK must carry the real public key: rebuild it and verify the signature.
            val verifier = Signature.getInstance("SHA256withECDSA", BouncyCastleProvider.PROVIDER_NAME)
            verifier.initVerify(secp256k1PublicKeyFromJwk(jwk))
            verifier.update(data)
            assertTrue(
                verifier.verify(EcdsaSignatureCodec.p1363ToDer(signature)),
                "secp256k1 signature must verify against the public key reconstructed from the JWK"
            )
        }
    }

    @Test
    fun `secp256k1 JWK coordinates are 32-byte field elements`() {
        val (_, jwk) = generateAndSign(Algorithm.Secp256k1)
        val decoder = Base64.getUrlDecoder()
        assertEquals("EC", jwk["kty"])
        assertEquals("secp256k1", jwk["crv"])
        assertEquals(32, decoder.decode(jwk["x"] as String).size, "x must be 32 bytes")
        assertEquals(32, decoder.decode(jwk["y"] as String).size, "y must be 32 bytes")
    }

    @Test
    fun `Ed25519 signature is raw 64 bytes and JWK x is the raw 32-byte key`() {
        val (signature, jwk) = generateAndSign(Algorithm.Ed25519)
        assertEquals(64, signature.size, "Ed25519 signature must be the raw 64-byte RFC 8032 form")
        assertEquals("OKP", jwk["kty"])
        assertEquals("Ed25519", jwk["crv"])
        assertEquals(32, Base64.getUrlDecoder().decode(jwk["x"] as String).size)
    }

    @Test
    fun `signing with an incompatible algorithm fails per isCompatibleWith`() = runBlocking {
        val gen = kms.generateKey(Algorithm.Ed25519, emptyMap())
        assertTrue(gen is GenerateKeyResult.Success, "expected Success, got $gen")
        val handle = (gen as GenerateKeyResult.Success).keyHandle

        val sign = kms.sign(handle.id, data, Algorithm.Secp256k1)
        assertTrue(
            sign is SignResult.Failure.UnsupportedAlgorithm,
            "Ed25519 key must not sign with secp256k1, got $sign"
        )
    }
}
