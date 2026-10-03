package org.trustweave.hashicorpkms

import org.junit.jupiter.api.Test
import org.trustweave.kms.Algorithm
import java.security.KeyPairGenerator
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class VaultPublicKeyParsingTest {
    private fun pem(
        der: ByteArray,
        label: String = "PUBLIC KEY",
        newline: String = "\r\n",
    ): String =
        "-----BEGIN $label-----$newline" +
            Base64
                .getEncoder()
                .encodeToString(der)
                .chunked(64)
                .joinToString(newline) +
            "$newline-----END $label-----$newline"

    private fun ecKey(curve: String): ECPublicKey =
        KeyPairGenerator
            .getInstance("EC")
            .apply { initialize(ECGenParameterSpec(curve)) }
            .generateKeyPair()
            .public as ECPublicKey

    private fun coordinate(
        jwk: Map<String, Any?>,
        name: String,
    ) = Base64.getUrlDecoder().decode(jwk[name] as String)

    private fun unsigned(
        value: java.math.BigInteger,
        size: Int,
    ): ByteArray {
        val bytes = value.toByteArray().dropWhile { it == 0.toByte() }.toByteArray()
        return ByteArray(size - bytes.size) + bytes
    }

    @Test
    fun `EC keys parse with CRLF line endings and keep their coordinates`() {
        for ((curve, algorithm, size) in listOf(
            Triple("secp256r1", Algorithm.P256, 32),
            Triple("secp384r1", Algorithm.P384, 48),
            Triple("secp521r1", Algorithm.P521, 66),
        )) {
            val key = ecKey(curve)
            val jwk = AlgorithmMapping.publicKeyPemToJwk(pem(key.encoded), algorithm)
            assertContentEquals(unsigned(key.w.affineX, size), coordinate(jwk, "x"), curve)
            assertContentEquals(unsigned(key.w.affineY, size), coordinate(jwk, "y"), curve)
        }
    }

    @Test
    fun `an EC key on a different curve than expected is rejected`() {
        val p256 = pem(ecKey("secp256r1").encoded)
        assertFailsWith<IllegalArgumentException> { AlgorithmMapping.publicKeyPemToJwk(p256, Algorithm.P384) }
        assertFailsWith<IllegalArgumentException> { AlgorithmMapping.publicKeyPemToJwk(p256, Algorithm.Secp256k1) }
    }

    @Test
    fun `a secp256k1 key is parsed by its curve OID and checked to be on the curve`() {
        val x =
            java.util.HexFormat
                .of()
                .parseHex("79BE667EF9DCBBAC55A06295CE870B07029BFCDB2DCE28D959F2815B16F81798")
        val y =
            java.util.HexFormat
                .of()
                .parseHex("483ADA7726A3C4655DA4FBFC0E1108A8FD17B448A68554199C47D08FFB10D4B8")
        val header =
            java.util.HexFormat
                .of()
                .parseHex("3056301006072a8648ce3d020106052b8104000a034200")
        val spki = header + byteArrayOf(0x04) + x + y

        val jwk = AlgorithmMapping.publicKeyPemToJwk(pem(spki, newline = "\n"), Algorithm.Secp256k1)
        assertContentEquals(x, coordinate(jwk, "x"))
        assertContentEquals(y, coordinate(jwk, "y"))

        val offCurve = spki.copyOf().also { it[it.size - 1] = (it[it.size - 1] + 1).toByte() }
        assertFailsWith<IllegalArgumentException> { AlgorithmMapping.publicKeyPemToJwk(pem(offCurve), Algorithm.Secp256k1) }
    }

    @Test
    fun `an Ed25519 PEM with CRLF line endings parses`() {
        val key = KeyPairGenerator.getInstance("Ed25519").generateKeyPair().public
        val jwk = AlgorithmMapping.publicKeyPemToJwk(pem(key.encoded), Algorithm.Ed25519)
        assertContentEquals(key.encoded.takeLast(32).toByteArray(), coordinate(jwk, "x"))
    }

    @Test
    fun `an RSA key must have the expected size`() {
        val key =
            KeyPairGenerator
                .getInstance("RSA")
                .apply { initialize(2048) }
                .generateKeyPair()
                .public
        assertEquals("RSA", AlgorithmMapping.publicKeyPemToJwk(pem(key.encoded), Algorithm.RSA.RSA_2048)["kty"])
        assertFailsWith<IllegalArgumentException> { AlgorithmMapping.publicKeyPemToJwk(pem(key.encoded), Algorithm.RSA.RSA_3072) }
    }

    @Test
    fun `an EC key presented as RSA is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            AlgorithmMapping.publicKeyPemToJwk(pem(ecKey("secp256r1").encoded), Algorithm.RSA.RSA_2048)
        }
    }

    @Test
    fun `malformed armour is rejected`() {
        val der = ecKey("secp256r1").encoded
        val body = Base64.getEncoder().encodeToString(der)
        listOf(
            "-----BEGIN PUBLIC KEY-----\n$body\n-----END CERTIFICATE-----",
            "-----BEGIN PRIVATE KEY-----\n$body\n-----END PRIVATE KEY-----",
            pem(der) + pem(der),
            "junk " + pem(der),
        ).forEach { input ->
            assertFailsWith<IllegalArgumentException>(input) { AlgorithmMapping.publicKeyPemToJwk(input, Algorithm.P256) }
        }
    }
}
