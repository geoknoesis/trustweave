package org.trustweave.did.sidetree

import java.security.KeyPairGenerator
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.util.Base64

/**
 * P-256 (secp256r1) keypair represented as JWKs per RFC 7517 / 7518.
 *
 * Sidetree update and recovery keys are P-256 by convention (matching the ION /
 * Orb reference implementations). Both halves are kept as JWK maps so they can
 * be stored, persisted, and embedded in operations without further conversion.
 *
 *  - [publicJwk] keys: `kty=EC, crv=P-256, x, y`.
 *  - [privateJwk] keys: same plus `d` (the private scalar, base64url).
 */
data class SidetreeP256KeyPair(
    val privateJwk: Map<String, Any?>,
    val publicJwk: Map<String, Any?>,
) {
    companion object {

        private val b64url: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()

        /**
         * Generates a fresh P-256 keypair using JCA's default EC provider.
         */
        fun generate(): SidetreeP256KeyPair {
            val gen = KeyPairGenerator.getInstance("EC")
            gen.initialize(ECGenParameterSpec("secp256r1"))
            val kp = gen.generateKeyPair()
            val pub = kp.public as ECPublicKey
            val priv = kp.private as ECPrivateKey

            val w = pub.w
            val xBytes = w.affineX.toByteArray().padCoord()
            val yBytes = w.affineY.toByteArray().padCoord()
            val dBytes = priv.s.toByteArray().padCoord()

            val publicJwk: Map<String, Any?> = mapOf(
                "kty" to "EC",
                "crv" to "P-256",
                "x" to b64url.encodeToString(xBytes),
                "y" to b64url.encodeToString(yBytes),
            )
            val privateJwk = publicJwk + ("d" to b64url.encodeToString(dBytes))
            return SidetreeP256KeyPair(privateJwk = privateJwk, publicJwk = publicJwk)
        }

        private fun ByteArray.padCoord(): ByteArray = when {
            size > 32 -> sliceArray(size - 32 until size)
            size < 32 -> ByteArray(32 - size) + this
            else -> this
        }
    }
}

/**
 * Strip the `d` (private scalar) component from a JWK before embedding it in a
 * publicly-anchored Sidetree document patch.
 */
fun Map<String, Any?>.withoutPrivateD(): Map<String, Any?> =
    if (containsKey("d")) filterKeys { it != "d" } else this
