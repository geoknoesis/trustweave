package org.trustweave.credential.internal.infrastructure

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.JWSObject
import com.nimbusds.jose.crypto.ECDSAVerifier
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.util.Base64URL
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.slf4j.LoggerFactory
import org.trustweave.credential.internal.SecurityConstants
import org.trustweave.credential.proof.internal.engines.ProofEngineUtils
import org.trustweave.credential.spi.proof.SignatureVerificationPort
import org.trustweave.did.model.VerificationMethod
import java.security.Provider
import java.security.Security

/**
 * [SignatureVerificationPort] for the JsonWebSignature2020 W3C Data Integrity cryptosuite.
 *
 * The `proofValue` in a JsonWebSignature2020 proof is a detached JWS compact serialization —
 * `<header>..<signature>` — where the payload (the canonicalized document) is detached and
 * must be re-attached before verification.
 *
 * Supports ES256, ES256K, ES384, ES512 (ECDSA on P-256/secp256k1/P-384/P-521) and
 * EdDSA (Ed25519). ES256K uses the Bouncy Castle provider because secp256k1 was removed
 * from the JDK's built-in SunEC provider (JDK 16+).
 *
 * EdDSA verification intentionally uses the Java Security API (`Signature("Ed25519")`)
 * instead of Nimbus' `Ed25519Verifier`: the latter requires the OPTIONAL
 * `com.google.crypto.tink` dependency at runtime — not declared by this module — and
 * would throw [NoClassDefFoundError] during verification.
 */
internal class DefaultJsonWebSignature2020Adapter : SignatureVerificationPort {
    private val logger = LoggerFactory.getLogger(DefaultJsonWebSignature2020Adapter::class.java)

    override fun verify(
        documentBytes: ByteArray,
        signatureBytes: ByteArray,
        verificationMethod: VerificationMethod,
        proofType: String,
    ): Boolean {
        // signatureBytes here is the raw detached-JWS string bytes (passed as UTF-8)
        val detachedJws = signatureBytes.toString(Charsets.UTF_8)
        return verifyDetachedJws(detachedJws, documentBytes, verificationMethod)
    }

    /**
     * Verify a detached-payload JWS. Re-attaches [payloadBytes] as the payload before verify.
     */
    fun verifyDetachedJws(
        detachedJws: String,
        payloadBytes: ByteArray,
        verificationMethod: VerificationMethod,
    ): Boolean {
        return try {
            val parts = detachedJws.split(".")
            if (parts.size != 3) {
                logger.warn("Invalid JWS compact serialization: expected 3 parts, got {}", parts.size)
                return false
            }
            val header = JWSHeader.parse(Base64URL(parts[0]))
            val payloadBase64 = Base64URL.encode(payloadBytes).toString()

            when {
                header.algorithm == JWSAlgorithm.EdDSA ->
                    verifyEd25519(parts[0], payloadBase64, parts[2], verificationMethod)
                header.algorithm in
                    setOf(
                        JWSAlgorithm.ES256,
                        JWSAlgorithm.ES256K,
                        JWSAlgorithm.ES384,
                        JWSAlgorithm.ES512,
                    )
                -> {
                    val jwsObject = JWSObject.parse("${parts[0]}.$payloadBase64.${parts[2]}")
                    val verifier = buildEcdsaVerifier(header.algorithm, verificationMethod) ?: return false
                    jwsObject.verify(verifier)
                }
                else -> {
                    logger.warn("Unsupported JWS algorithm for JsonWebSignature2020: {}", header.algorithm)
                    false
                }
            }
        } catch (e: Exception) {
            logger.error("JWS verification failed: {}", e.message, e)
            false
        }
    }

    /**
     * Verify an EdDSA (Ed25519) JWS signature with the Java Security API.
     *
     * The JWS signing input is `ASCII(BASE64URL(header) || '.' || BASE64URL(payload))`.
     * Fail-closed: any error (unsupported key, malformed signature) yields `false`.
     */
    private fun verifyEd25519(
        headerBase64: String,
        payloadBase64: String,
        signatureBase64: String,
        verificationMethod: VerificationMethod,
    ): Boolean {
        val publicKey = ProofEngineUtils.extractPublicKey(verificationMethod)
        if (publicKey == null) {
            logger.warn(
                "Failed to extract Ed25519 public key from verification method: {}",
                verificationMethod.id.value,
            )
            return false
        }
        return try {
            val signatureBytes = Base64URL(signatureBase64).decode()
            if (signatureBytes.size != SecurityConstants.ED25519_SIGNATURE_LENGTH_BYTES) {
                logger.warn("Invalid Ed25519 signature length: {}", signatureBytes.size)
                return false
            }
            val signingInput = "$headerBase64.$payloadBase64".toByteArray(Charsets.US_ASCII)
            val signature = java.security.Signature.getInstance("Ed25519")
            signature.initVerify(publicKey)
            signature.update(signingInput)
            signature.verify(signatureBytes)
        } catch (e: Exception) {
            logger.error("Ed25519 JWS verification failed: {}", e.message, e)
            false
        }
    }

    /**
     * Builds the ECDSA verifier only when the key agrees with the algorithm.
     *
     * `alg` travels inside the JWS being verified, so it is attacker-controlled, while the
     * verification method's JWK states what the key actually is. Choosing the curve from `alg`
     * alone reinterprets the key material on terms the attacker picked and never consults `kty` or
     * `crv` — the same class of mistake as algorithm confusion. Any disagreement fails closed.
     */
    private fun buildEcdsaVerifier(
        algorithm: JWSAlgorithm,
        verificationMethod: VerificationMethod,
    ): ECDSAVerifier? =
        try {
            val jwkMap = verificationMethod.publicKeyJwk
            val curve = curveForAlgorithm(algorithm)
            val declaredKeyType = jwkMap?.get("kty") as? String
            val declaredCurve = (jwkMap?.get("crv") as? String)?.let(::parseCurve)

            if (jwkMap == null) {
                null
            } else if (curve == null) {
                logger.warn("Unsupported ECDSA algorithm for JsonWebSignature2020: {}", algorithm)
                null
            } else if (declaredKeyType != EC_KEY_TYPE) {
                logger.warn(
                    "Verification method {} declares kty={} but an ECDSA signature was presented",
                    verificationMethod.id.value,
                    declaredKeyType,
                )
                null
            } else if (declaredCurve != curve) {
                logger.warn(
                    "Verification method {} declares crv={} but the JWS alg {} requires {}",
                    verificationMethod.id.value,
                    declaredCurve?.name ?: jwkMap["crv"],
                    algorithm,
                    curve.name,
                )
                null
            } else {
                val x = jwkMap["x"] as? String
                val y = jwkMap["y"] as? String
                if (x == null || y == null) {
                    null
                } else {
                    val ecKey = ECKey.Builder(curve, Base64URL(x), Base64URL(y)).build()
                    if (algorithm == JWSAlgorithm.ES256K) {
                        // secp256k1 was removed from the JDK's SunEC provider (JDK 16+):
                        // both key construction and signature verification must go through
                        // Bouncy Castle (bcprov is a declared dependency of this module).
                        val bc = bouncyCastleProvider()
                        ECDSAVerifier(ecKey.toECPublicKey(bc)).apply { jcaContext.provider = bc }
                    } else {
                        ECDSAVerifier(ecKey.toECPublicKey())
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to build ECDSA JWS verifier: {}", e.message, e)
            null
        }

    /** The curve an ECDSA JWS algorithm requires; null for anything not supported here. */
    private fun curveForAlgorithm(algorithm: JWSAlgorithm): Curve? =
        when (algorithm) {
            JWSAlgorithm.ES256 -> Curve.P_256
            JWSAlgorithm.ES256K -> Curve.SECP256K1
            JWSAlgorithm.ES384 -> Curve.P_384
            JWSAlgorithm.ES512 -> Curve.P_521
            else -> null
        }

    /**
     * Parses a JWK `crv` value, tolerating `P-256K` as a legacy spelling of `secp256k1` so keys
     * published by older stacks still resolve to the same curve.
     */
    private fun parseCurve(crv: String): Curve? =
        when (crv) {
            LEGACY_SECP256K1_CURVE_NAME -> Curve.SECP256K1
            else -> runCatching { Curve.parse(crv) }.getOrNull()
        }

    /**
     * Return the registered Bouncy Castle JCA provider, registering it if necessary.
     */
    private fun bouncyCastleProvider(): Provider {
        Security.getProvider(BouncyCastleProvider.PROVIDER_NAME)?.let { return it }
        val provider = BouncyCastleProvider()
        Security.addProvider(provider)
        return provider
    }

    private companion object {
        const val EC_KEY_TYPE = "EC"

        /** Pre-RFC 8812 spelling of the secp256k1 curve, still emitted by some stacks. */
        const val LEGACY_SECP256K1_CURVE_NAME = "P-256K"
    }
}
