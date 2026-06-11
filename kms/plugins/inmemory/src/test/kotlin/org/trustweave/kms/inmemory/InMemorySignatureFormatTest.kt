package org.trustweave.kms.inmemory

import org.trustweave.kms.Algorithm
import org.trustweave.kms.JwkKeys
import org.trustweave.kms.results.GenerateKeyResult
import org.trustweave.kms.results.SignResult
import org.trustweave.kms.util.EcdsaSignatureCodec
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Cross-plugin signature-format consistency tests.
 *
 * The [org.trustweave.kms.KeyManagementService.sign] contract requires every provider to
 * return EC signatures in IEEE P1363 (raw `r || s`) form, with low-s normalization for
 * secp256k1, regardless of what the backing crypto engine emits natively. The in-memory KMS
 * is the reference implementation, so these tests pin the contract:
 *
 *  - P-256/P-384/P-521/secp256k1 signature sizes are exactly 64/96/132/64 bytes,
 *  - signatures verify against the key's JWK public key after P1363 -> DER transcoding,
 *  - secp256k1 signatures have `s <= n/2`.
 */
class InMemorySignatureFormatTest {

    private val kms = InMemoryKeyManagementService()
    private val data = "trustweave-signature-format-contract".toByteArray(Charsets.UTF_8)

    private val secp256k1HalfOrder = BigInteger(
        "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141",
        16
    ).shiftRight(1)

    private fun generateAndSign(algorithm: Algorithm): Pair<ByteArray, Map<String, Any?>> = runBlocking {
        val gen = kms.generateKey(algorithm)
        assertTrue(gen is GenerateKeyResult.Success, "expected Success, got $gen")
        val handle = (gen as GenerateKeyResult.Success).keyHandle
        val sign = kms.sign(handle.id, data)
        assertTrue(sign is SignResult.Success, "expected Success, got $sign")
        (sign as SignResult.Success).signature to handle.publicKeyJwk!!
    }

    /**
     * Rebuilds a JCA EC [PublicKey] from the EC JWK the KMS returned, using the named curve.
     *
     * @param providerName JCA provider to use; secp256k1 was removed from SunEC in modern JDKs,
     *        so that curve must be resolved through BouncyCastle.
     */
    private fun ecPublicKeyFromJwk(
        jwk: Map<String, Any?>,
        jcaCurveName: String,
        providerName: String? = null
    ): PublicKey {
        val decoder = Base64.getUrlDecoder()
        val x = BigInteger(1, decoder.decode(jwk[JwkKeys.X] as String))
        val y = BigInteger(1, decoder.decode(jwk[JwkKeys.Y] as String))
        val params = (
            if (providerName != null) AlgorithmParameters.getInstance("EC", providerName)
            else AlgorithmParameters.getInstance("EC")
            ).apply { init(ECGenParameterSpec(jcaCurveName)) }
        val spec = params.getParameterSpec(java.security.spec.ECParameterSpec::class.java)
        val keyFactory =
            if (providerName != null) KeyFactory.getInstance("EC", providerName)
            else KeyFactory.getInstance("EC")
        return keyFactory.generatePublic(ECPublicKeySpec(ECPoint(x, y), spec))
    }

    private fun assertEcContract(
        algorithm: Algorithm,
        jcaCurveName: String,
        expectedSize: Int,
        jcaScheme: String
    ) {
        val (signature, jwk) = generateAndSign(algorithm)

        assertEquals(
            expectedSize,
            signature.size,
            "${algorithm.name} signature must be $expectedSize-byte P1363 (raw r||s)"
        )

        // P1363 -> DER, then verify with plain JCA against the JWK public key.
        val verifier = Signature.getInstance(jcaScheme)
        verifier.initVerify(ecPublicKeyFromJwk(jwk, jcaCurveName))
        verifier.update(data)
        assertTrue(
            verifier.verify(EcdsaSignatureCodec.p1363ToDer(signature)),
            "${algorithm.name} P1363 signature must verify after transcoding to DER"
        )
    }

    @Test
    fun `P-256 signature is 64-byte P1363 and verifies`() {
        assertEcContract(Algorithm.P256, "secp256r1", 64, "SHA256withECDSA")
    }

    @Test
    fun `P-384 signature is 96-byte P1363 and verifies`() {
        assertEcContract(Algorithm.P384, "secp384r1", 96, "SHA384withECDSA")
    }

    @Test
    fun `P-521 signature is 132-byte P1363 and verifies`() {
        assertEcContract(Algorithm.P521, "secp521r1", 132, "SHA512withECDSA")
    }

    @Test
    fun `secp256k1 signature is 64-byte P1363 with low s and verifies`() {
        // ECDSA s is uniformly random in (0, n); a single signature has ~50% chance of being
        // naturally low-s. Sign several times so a missing low-s normalization is caught with
        // overwhelming probability (~1 in 2^16).
        repeat(16) {
            val (signature, jwk) = generateAndSign(Algorithm.Secp256k1)

            assertEquals(64, signature.size, "secp256k1 signature must be 64-byte P1363 (raw r||s)")

            val s = BigInteger(1, signature.copyOfRange(32, 64))
            assertTrue(
                s <= secp256k1HalfOrder,
                "secp256k1 signature must be low-s (EIP-2 / Bitcoin), got s > n/2"
            )

            val bcName = org.bouncycastle.jce.provider.BouncyCastleProvider.PROVIDER_NAME
            val verifier = Signature.getInstance("SHA256withECDSA", bcName)
            verifier.initVerify(ecPublicKeyFromJwk(jwk, "secp256k1", providerName = bcName))
            verifier.update(data)
            assertTrue(
                verifier.verify(EcdsaSignatureCodec.p1363ToDer(signature)),
                "secp256k1 P1363 low-s signature must verify after transcoding to DER"
            )
        }
    }

    @Test
    fun `Ed25519 signature is raw 64 bytes`() {
        val (signature, _) = generateAndSign(Algorithm.Ed25519)
        assertEquals(64, signature.size, "Ed25519 signature must be the raw 64-byte RFC 8032 form")
    }
}
