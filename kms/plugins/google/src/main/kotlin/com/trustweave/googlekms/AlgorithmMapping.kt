package com.trustweave.googlekms

import com.trustweave.kms.Algorithm
import com.trustweave.kms.JwkKeys
import com.trustweave.kms.JwkKeyTypes
import com.google.cloud.kms.v1.CryptoKeyVersion.CryptoKeyVersionAlgorithm
import java.security.KeyFactory
import java.security.interfaces.ECPublicKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import java.math.BigInteger
import java.util.Base64

/**
 * Utilities for mapping between TrustWeave Algorithm types and Google Cloud KMS types.
 */
object AlgorithmMapping {
    /**
     * Maps TrustWeave Algorithm to Google Cloud KMS CryptoKeyVersionAlgorithm.
     *
     * @param algorithm TrustWeave algorithm
     * @return Google Cloud KMS CryptoKeyVersionAlgorithm
     * @throws IllegalArgumentException if algorithm is not supported by Google Cloud KMS
     */
    fun toGoogleKmsAlgorithm(algorithm: Algorithm): CryptoKeyVersionAlgorithm {
        return when (algorithm) {
            // Note: Ed25519 may not be supported in all Google Cloud KMS versions
            // If not available, this will throw an exception
            is Algorithm.Ed25519 -> {
                // Try to use Ed25519, but it may not be available
                try {
                    CryptoKeyVersionAlgorithm.valueOf("EC_SIGN_ED25519")
                } catch (e: IllegalArgumentException) {
                    throw IllegalArgumentException("Ed25519 is not supported by this Google Cloud KMS version. Use secp256k1 or P-256 instead.")
                }
            }
            is Algorithm.Secp256k1 -> CryptoKeyVersionAlgorithm.EC_SIGN_SECP256K1_SHA256
            is Algorithm.P256 -> CryptoKeyVersionAlgorithm.EC_SIGN_P256_SHA256
            is Algorithm.P384 -> CryptoKeyVersionAlgorithm.EC_SIGN_P384_SHA384
            is Algorithm.P521 -> {
                // P-521 may use a different enum name
                try {
                    CryptoKeyVersionAlgorithm.valueOf("EC_SIGN_P521_SHA512")
                } catch (e: IllegalArgumentException) {
                    throw IllegalArgumentException("P-521 is not supported by this Google Cloud KMS version. Use P-256 or P-384 instead.")
                }
            }
            is Algorithm.RSA -> {
                when (algorithm.keySize) {
                    2048 -> CryptoKeyVersionAlgorithm.RSA_SIGN_PKCS1_2048_SHA256
                    3072 -> CryptoKeyVersionAlgorithm.RSA_SIGN_PKCS1_3072_SHA256
                    4096 -> CryptoKeyVersionAlgorithm.RSA_SIGN_PKCS1_4096_SHA256
                    else -> throw IllegalArgumentException("Unsupported RSA key size: ${algorithm.keySize}")
                }
            }
            else -> throw IllegalArgumentException("Algorithm ${algorithm.name} is not supported by Google Cloud KMS")
        }
    }

    /**
     * Parses algorithm from Google Cloud KMS CryptoKeyVersionAlgorithm.
     *
     * @param algorithm Google Cloud KMS algorithm
     * @return TrustWeave Algorithm, or null if not supported
     */
    fun fromGoogleKmsAlgorithm(algorithm: CryptoKeyVersionAlgorithm): Algorithm? {
        return try {
            when (algorithm.name) {
                "EC_SIGN_ED25519" -> Algorithm.Ed25519
                "EC_SIGN_SECP256K1_SHA256" -> Algorithm.Secp256k1
                "EC_SIGN_P256_SHA256" -> Algorithm.P256
                "EC_SIGN_P384_SHA384" -> Algorithm.P384
                "EC_SIGN_P521_SHA512" -> Algorithm.P521
                "RSA_SIGN_PKCS1_2048_SHA256" -> Algorithm.RSA.RSA_2048
                "RSA_SIGN_PKCS1_3072_SHA256" -> Algorithm.RSA.RSA_3072
                "RSA_SIGN_PKCS1_4096_SHA256" -> Algorithm.RSA.RSA_4096
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Converts Google Cloud KMS public key (DER-encoded) to JWK format.
     *
     * @param publicKeyBytes DER-encoded public key from Google Cloud KMS
     * @param algorithm The algorithm type
     * @return JWK map representation
     */
    fun publicKeyToJwk(publicKeyBytes: ByteArray, algorithm: Algorithm): Map<String, Any?> {
        return try {
            when (algorithm) {
                is Algorithm.Ed25519 -> {
                    // Ed25519 keys need special handling
                    // Google Cloud KMS returns Ed25519 keys in a specific format
                    mapOf(
                        JwkKeys.KTY to JwkKeyTypes.OKP,
                        JwkKeys.CRV to Algorithm.Ed25519.curveName,
                        JwkKeys.X to Base64.getUrlEncoder().withoutPadding().encodeToString(publicKeyBytes)
                    )
                }
                is Algorithm.Secp256k1, is Algorithm.P256, is Algorithm.P384, is Algorithm.P521 -> {
                    val keyFactory = KeyFactory.getInstance("EC")
                    val publicKey = keyFactory.generatePublic(X509EncodedKeySpec(publicKeyBytes)) as ECPublicKey
                    val point = publicKey.w

                    val curveName = algorithm.curveName
                        ?: throw IllegalArgumentException("Unsupported EC algorithm: ${algorithm.name}")

                    // Extract x and y coordinates from ECPoint
                    // ECPoint has affineX and affineY as BigInteger
                    val affineX = point.affineX
                    val affineY = point.affineY
                    val coordinateLength = when (algorithm) {
                        is Algorithm.Secp256k1, is Algorithm.P256 -> 32
                        is Algorithm.P384 -> 48
                        is Algorithm.P521 -> 66
                        else -> 32
                    }

                    // Convert BigInteger to byte array (unsigned, big-endian)
                    fun toUnsignedByteArray(bigInt: BigInteger, length: Int): ByteArray {
                        val bytes = bigInt.toByteArray()
                        val result = ByteArray(length)
                        val offset = length - bytes.size
                        if (offset >= 0) {
                            System.arraycopy(bytes, 0, result, offset, bytes.size)
                        } else {
                            // If bytes are longer, take the last 'length' bytes
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

                    mapOf(
                        JwkKeys.KTY to JwkKeyTypes.RSA,
                        JwkKeys.N to Base64.getUrlEncoder().withoutPadding().encodeToString(modulus.toByteArray()),
                        JwkKeys.E to Base64.getUrlEncoder().withoutPadding().encodeToString(exponent.toByteArray())
                    )
                }
                else -> throw IllegalArgumentException("Unsupported algorithm for JWK conversion: ${algorithm.name}")
            }
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to convert Google Cloud KMS public key to JWK: ${e.message}", e)
        }
    }

    /**
     * Resolves a key identifier to a full Google Cloud KMS resource name.
     *
     * Accepts:
     * - Full resource name: projects/{project}/locations/{location}/keyRings/{keyRing}/cryptoKeys/{key}
     * - Short name with defaults: {key} (uses config defaults)
     *
     * @param keyId Key identifier (full name or short name)
     * @param config Configuration with defaults
     * @return Full resource name
     */
    fun resolveKeyName(keyId: String, config: GoogleKmsConfig): String {
        // If it's already a full resource name, return as-is
        if (keyId.startsWith("projects/")) {
            return keyId
        }

        // Otherwise, construct from config
        val keyRing = config.keyRing
            ?: throw IllegalArgumentException("Key ring must be specified in config or key ID must be a full resource name")

        return "projects/${config.projectId}/locations/${config.location}/keyRings/$keyRing/cryptoKeys/$keyId"
    }

    /**
     * Extracts key name from a full resource name.
     *
     * @param resourceName Full resource name
     * @return Short key name
     */
    fun extractKeyName(resourceName: String): String {
        return if (resourceName.startsWith("projects/")) {
            resourceName.substringAfterLast("/")
        } else {
            resourceName
        }
    }
}

