package com.trustweave.kms.fortanix

import com.trustweave.kms.Algorithm
import com.trustweave.kms.JwkKeys
import com.trustweave.kms.JwkKeyTypes
import java.math.BigInteger
import java.security.KeyFactory
import java.security.interfaces.ECPublicKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Utilities for mapping between TrustWeave Algorithm types and Fortanix DSM types.
 */
object AlgorithmMapping {
    /**
     * Maps TrustWeave Algorithm to Fortanix DSM key type.
     *
     * @param algorithm TrustWeave algorithm
     * @return Fortanix DSM key type string
     * @throws IllegalArgumentException if algorithm is not supported by Fortanix DSM
     */
    fun toFortanixKeyType(algorithm: Algorithm): String {
        return when (algorithm) {
            is Algorithm.Ed25519 -> "EC"
            is Algorithm.Secp256k1 -> "EC"
            is Algorithm.P256 -> "EC"
            is Algorithm.P384 -> "EC"
            is Algorithm.P521 -> "EC"
            is Algorithm.RSA -> "RSA"
            else -> throw IllegalArgumentException("Algorithm ${algorithm.name} is not supported by Fortanix DSM")
        }
    }

    /**
     * Maps TrustWeave Algorithm to Fortanix DSM curve name.
     *
     * @param algorithm TrustWeave algorithm
     * @return Fortanix DSM curve name, or null for RSA
     */
    fun toFortanixCurve(algorithm: Algorithm): String? {
        return when (algorithm) {
            is Algorithm.Ed25519 -> "Ed25519"
            is Algorithm.Secp256k1 -> "secp256k1"
            is Algorithm.P256 -> "secp256r1"
            is Algorithm.P384 -> "secp384r1"
            is Algorithm.P521 -> "secp521r1"
            is Algorithm.RSA -> null
            else -> null
        }
    }

    /**
     * Maps TrustWeave Algorithm to Fortanix DSM key size (for RSA).
     *
     * @param algorithm TrustWeave algorithm
     * @return Key size in bits, or null for EC algorithms
     */
    fun toFortanixKeySize(algorithm: Algorithm): Int? {
        return when (algorithm) {
            is Algorithm.RSA -> algorithm.keySize
            else -> null
        }
    }

    /**
     * Parses Fortanix DSM key type to TrustWeave Algorithm.
     *
     * @param keyType Fortanix DSM key type
     * @param curve Curve name (for EC keys)
     * @param keySize Key size (for RSA keys)
     * @return TrustWeave Algorithm, or null if not recognized
     */
    fun fromFortanixKeyType(keyType: String, curve: String?, keySize: Int?): Algorithm? {
        return when (keyType.uppercase()) {
            "EC" -> {
                when (curve?.uppercase()) {
                    "ED25519" -> Algorithm.Ed25519
                    "SECP256K1" -> Algorithm.Secp256k1
                    "SECP256R1", "P256" -> Algorithm.P256
                    "SECP384R1", "P384" -> Algorithm.P384
                    "SECP521R1", "P521" -> Algorithm.P521
                    else -> null
                }
            }
            "RSA" -> {
                when (keySize) {
                    2048 -> Algorithm.RSA.RSA_2048
                    3072 -> Algorithm.RSA.RSA_3072
                    4096 -> Algorithm.RSA.RSA_4096
                    else -> null
                }
            }
            else -> null
        }
    }

    /**
     * Maps TrustWeave Algorithm to Fortanix DSM signing algorithm.
     *
     * @param algorithm TrustWeave algorithm
     * @return Fortanix DSM signing algorithm string
     */
    fun toFortanixSigningAlgorithm(algorithm: Algorithm): String {
        return when (algorithm) {
            is Algorithm.Ed25519 -> "EdDSA"
            is Algorithm.Secp256k1 -> "ECDSA"
            is Algorithm.P256 -> "ECDSA"
            is Algorithm.P384 -> "ECDSA"
            is Algorithm.P521 -> "ECDSA"
            is Algorithm.RSA -> "PKCS1v15"
            else -> "ECDSA"
        }
    }

    /**
     * Converts Fortanix DSM public key to JWK format.
     *
     * @param publicKeyBytes Public key bytes from Fortanix DSM
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
            throw IllegalArgumentException("Failed to convert Fortanix DSM public key to JWK: ${e.message}", e)
        }
    }

    /**
     * Resolves a key identifier to Fortanix DSM key ID.
     *
     * @param keyId Key identifier
     * @return Resolved key identifier for Fortanix API
     */
    fun resolveKeyId(keyId: String): String {
        return keyId
    }
}

