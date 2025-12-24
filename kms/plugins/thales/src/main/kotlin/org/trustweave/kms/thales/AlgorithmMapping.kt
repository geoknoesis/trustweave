package org.trustweave.kms.thales

import org.trustweave.kms.Algorithm
import org.trustweave.kms.JwkKeys
import org.trustweave.kms.JwkKeyTypes
import java.math.BigInteger
import java.security.KeyFactory
import java.security.interfaces.ECPublicKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Utilities for mapping between TrustWeave Algorithm types and Thales CipherTrust Manager types.
 */
object AlgorithmMapping {
    /**
     * Maps TrustWeave Algorithm to Thales CipherTrust key algorithm.
     *
     * @param algorithm TrustWeave algorithm
     * @return Thales CipherTrust key algorithm string
     * @throws IllegalArgumentException if algorithm is not supported by Thales CipherTrust
     */
    fun toThalesKeyAlgorithm(algorithm: Algorithm): String {
        return when (algorithm) {
            is Algorithm.Ed25519 -> "Ed25519"
            is Algorithm.Secp256k1 -> "secp256k1"
            is Algorithm.P256 -> "EC:secp256r1"
            is Algorithm.P384 -> "EC:secp384r1"
            is Algorithm.P521 -> "EC:secp521r1"
            is Algorithm.RSA -> {
                when (algorithm.keySize) {
                    2048 -> "RSA:2048"
                    3072 -> "RSA:3072"
                    4096 -> "RSA:4096"
                    else -> throw IllegalArgumentException("Unsupported RSA key size: ${algorithm.keySize}")
                }
            }
            else -> throw IllegalArgumentException("Algorithm ${algorithm.name} is not supported by Thales CipherTrust")
        }
    }

    /**
     * Parses Thales CipherTrust key algorithm to TrustWeave Algorithm.
     *
     * @param keyAlgorithm Thales CipherTrust key algorithm string
     * @return TrustWeave Algorithm, or null if not recognized
     */
    fun fromThalesKeyAlgorithm(keyAlgorithm: String): Algorithm? {
        return when (keyAlgorithm.uppercase()) {
            "ED25519" -> Algorithm.Ed25519
            "SECP256K1" -> Algorithm.Secp256k1
            "EC:SECP256R1", "EC-P256" -> Algorithm.P256
            "EC:SECP384R1", "EC-P384" -> Algorithm.P384
            "EC:SECP521R1", "EC-P521" -> Algorithm.P521
            "RSA:2048", "RSA-2048" -> Algorithm.RSA.RSA_2048
            "RSA:3072", "RSA-3072" -> Algorithm.RSA.RSA_3072
            "RSA:4096", "RSA-4096" -> Algorithm.RSA.RSA_4096
            else -> null
        }
    }

    /**
     * Maps TrustWeave Algorithm to Thales CipherTrust signing algorithm.
     *
     * @param algorithm TrustWeave algorithm
     * @return Thales CipherTrust signing algorithm string
     */
    fun toThalesSigningAlgorithm(algorithm: Algorithm): String {
        return when (algorithm) {
            is Algorithm.Ed25519 -> "EdDSA"
            is Algorithm.Secp256k1 -> "ECDSA"
            is Algorithm.P256 -> "ECDSA"
            is Algorithm.P384 -> "ECDSA"
            is Algorithm.P521 -> "ECDSA"
            is Algorithm.RSA -> "RSA_PKCS1_V1_5"
            else -> "ECDSA"
        }
    }

    /**
     * Converts Thales CipherTrust public key to JWK format.
     *
     * @param publicKeyBytes Public key bytes from Thales CipherTrust
     * @param algorithm The algorithm type
     * @return JWK map representation
     */
    fun publicKeyToJwk(publicKeyBytes: ByteArray, algorithm: Algorithm): Map<String, Any?> {
        return try {
            when (algorithm) {
                is Algorithm.Ed25519 -> {
                    val rawKey = if (publicKeyBytes.size == 32) {
                        publicKeyBytes
                    } else {
                        publicKeyBytes.takeLast(32).toByteArray()
                    }

                    mapOf(
                        JwkKeys.KTY to JwkKeyTypes.OKP,
                        JwkKeys.CRV to Algorithm.Ed25519.curveName,
                        JwkKeys.X to Base64.getUrlEncoder().withoutPadding().encodeToString(rawKey)
                    )
                }
                is Algorithm.Secp256k1, is Algorithm.P256, is Algorithm.P384, is Algorithm.P521 -> {
                    val keyFactory = KeyFactory.getInstance("EC")
                    val publicKey = keyFactory.generatePublic(X509EncodedKeySpec(publicKeyBytes)) as ECPublicKey
                    val point = publicKey.w
                    val curveName = algorithm.curveName
                        ?: throw IllegalArgumentException("Unsupported EC algorithm: ${algorithm.name}")

                    val affineX = point.affineX
                    val affineY = point.affineY
                    val coordinateLength = when (algorithm) {
                        is Algorithm.Secp256k1, is Algorithm.P256 -> 32
                        is Algorithm.P384 -> 48
                        is Algorithm.P521 -> 66
                        else -> 32
                    }

                    fun toUnsignedByteArray(bigInt: BigInteger, length: Int): ByteArray {
                        val bytes = bigInt.toByteArray()
                        val result = ByteArray(length)
                        val offset = length - bytes.size
                        if (offset >= 0) {
                            System.arraycopy(bytes, 0, result, offset, bytes.size)
                        } else {
                            System.arraycopy(bytes, bytes.size - length, result, 0, length)
                        }
                        return result
                    }

                    val x = toUnsignedByteArray(affineX, coordinateLength)
                    val y = toUnsignedByteArray(affineY, coordinateLength)

                    mapOf(
                        JwkKeys.KTY to JwkKeyTypes.EC,
                        JwkKeys.CRV to curveName,
                        JwkKeys.X to Base64.getUrlEncoder().withoutPadding().encodeToString(x),
                        JwkKeys.Y to Base64.getUrlEncoder().withoutPadding().encodeToString(y)
                    )
                }
                is Algorithm.RSA -> {
                    val keyFactory = KeyFactory.getInstance("RSA")
                    val publicKey = keyFactory.generatePublic(X509EncodedKeySpec(publicKeyBytes)) as RSAPublicKey
                    val modulus = publicKey.modulus
                    val exponent = publicKey.publicExponent

                    fun toUnsignedByteArray(bigInt: BigInteger): ByteArray {
                        val signed = bigInt.toByteArray()
                        if (signed.isNotEmpty() && signed[0] == 0.toByte()) {
                            return signed.sliceArray(1 until signed.size)
                        }
                        return signed
                    }

                    mapOf(
                        JwkKeys.KTY to JwkKeyTypes.RSA,
                        JwkKeys.N to Base64.getUrlEncoder().withoutPadding().encodeToString(toUnsignedByteArray(modulus)),
                        JwkKeys.E to Base64.getUrlEncoder().withoutPadding().encodeToString(toUnsignedByteArray(exponent))
                    )
                }
                else -> throw IllegalArgumentException("Unsupported algorithm for JWK conversion: ${algorithm.name}")
            }
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to convert Thales CipherTrust public key to JWK: ${e.message}", e)
        }
    }

    /**
     * Resolves a key identifier to Thales CipherTrust key ID.
     *
     * @param keyId Key identifier
     * @return Resolved key identifier for Thales API
     */
    fun resolveKeyId(keyId: String): String {
        return keyId
    }
}

