package org.trustweave.googlekms

import com.google.cloud.kms.v1.CryptoKeyVersion.CryptoKeyVersionAlgorithm
import org.trustweave.kms.Algorithm
import org.trustweave.kms.JwkKeyTypes
import org.trustweave.kms.JwkKeys
import java.math.BigInteger
import java.security.KeyFactory
import java.security.interfaces.ECPublicKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
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
    fun toGoogleKmsAlgorithm(algorithm: Algorithm): CryptoKeyVersionAlgorithm =
        when (algorithm) {
            // Note: Ed25519 may not be supported in all Google Cloud KMS versions
            // If not available, this will throw an exception
            is Algorithm.Ed25519 -> {
                // Try to use Ed25519, but it may not be available
                try {
                    CryptoKeyVersionAlgorithm.valueOf("EC_SIGN_ED25519")
                } catch (e: IllegalArgumentException) {
                    throw IllegalArgumentException(
                        "Ed25519 is not supported by this Google Cloud KMS version. Use secp256k1 or P-256 instead.",
                    )
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
                    // Google offers PKCS#1 v1.5 with SHA-512 only for 4096-bit keys and has no SHA-384 variant
                    // for 3072-bit keys, so 3072 stays on SHA-256. Existing 4096/SHA-256 keys remain readable.
                    4096 -> CryptoKeyVersionAlgorithm.RSA_SIGN_PKCS1_4096_SHA512
                    else -> throw IllegalArgumentException("Unsupported RSA key size: ${algorithm.keySize}")
                }
            }
            else -> throw IllegalArgumentException("Algorithm ${algorithm.name} is not supported by Google Cloud KMS")
        }

    /**
     * Parses algorithm from Google Cloud KMS CryptoKeyVersionAlgorithm.
     *
     * @param algorithm Google Cloud KMS algorithm
     * @return TrustWeave Algorithm, or null if not supported
     */
    fun fromGoogleKmsAlgorithm(algorithm: CryptoKeyVersionAlgorithm): Algorithm? =
        try {
            when (algorithm.name) {
                "EC_SIGN_ED25519" -> Algorithm.Ed25519
                "EC_SIGN_SECP256K1_SHA256" -> Algorithm.Secp256k1
                "EC_SIGN_P256_SHA256" -> Algorithm.P256
                "EC_SIGN_P384_SHA384" -> Algorithm.P384
                "EC_SIGN_P521_SHA512" -> Algorithm.P521
                "RSA_SIGN_PKCS1_2048_SHA256" -> Algorithm.RSA.RSA_2048
                "RSA_SIGN_PKCS1_3072_SHA256" -> Algorithm.RSA.RSA_3072
                "RSA_SIGN_PKCS1_4096_SHA256", "RSA_SIGN_PKCS1_4096_SHA512" -> Algorithm.RSA.RSA_4096
                else -> null
            }
        } catch (e: Exception) {
            null
        }

    /**
     * The JCA digest name the key version's algorithm requires for `AsymmetricSign`, or `null` when the
     * algorithm signs the message itself (pure Ed25519: "taking data as input").
     *
     * Google's `AsymmetricSignRequest.digest` "must be produced with the same digest algorithm as
     * specified by the key version's algorithm", which is encoded in the algorithm's name.
     */
    fun digestAlgorithmFor(algorithm: CryptoKeyVersionAlgorithm): String? =
        when (algorithm.name) {
            "EC_SIGN_ED25519" -> null
            "EC_SIGN_P384_SHA384" -> "SHA-384"
            else ->
                when {
                    algorithm.name.endsWith("_SHA512") -> "SHA-512"
                    algorithm.name.endsWith("_SHA384") -> "SHA-384"
                    algorithm.name.endsWith("_SHA256") -> "SHA-256"
                    else -> throw IllegalArgumentException("Cannot determine the digest for Google KMS algorithm ${algorithm.name}")
                }
        }

    /**
     * Converts Google Cloud KMS public key (DER-encoded) to JWK format.
     *
     * @param publicKeyBytes DER-encoded public key from Google Cloud KMS
     * @param algorithm The algorithm type
     * @return JWK map representation
     */
    fun publicKeyToJwk(
        publicKeyBytes: ByteArray,
        algorithm: Algorithm,
    ): Map<String, Any?> {
        return try {
            when (algorithm) {
                is Algorithm.Ed25519 -> {
                    // Google returns an RFC 8410 SubjectPublicKeyInfo (12-byte header + the 32-byte key);
                    // the JWK "x" is the raw 32-byte key only.
                    val ed25519SpkiPrefix =
                        byteArrayOf(
                            0x30,
                            0x2a,
                            0x30,
                            0x05,
                            0x06,
                            0x03,
                            0x2b,
                            0x65,
                            0x70,
                            0x03,
                            0x21,
                            0x00,
                        )
                    val rawKey =
                        when {
                            publicKeyBytes.size == 32 -> publicKeyBytes
                            publicKeyBytes.size == 44 && publicKeyBytes.copyOfRange(0, 12).contentEquals(ed25519SpkiPrefix) ->
                                publicKeyBytes.copyOfRange(12, 44)
                            else -> throw IllegalArgumentException("Not a 32-byte Ed25519 public key or its SubjectPublicKeyInfo")
                        }
                    mapOf(
                        JwkKeys.KTY to JwkKeyTypes.OKP,
                        JwkKeys.CRV to Algorithm.Ed25519.curveName,
                        JwkKeys.X to Base64.getUrlEncoder().withoutPadding().encodeToString(rawKey),
                    )
                }
                is Algorithm.Secp256k1, is Algorithm.P256, is Algorithm.P384, is Algorithm.P521 -> {
                    val keyFactory = KeyFactory.getInstance("EC")
                    val publicKey = keyFactory.generatePublic(X509EncodedKeySpec(publicKeyBytes)) as ECPublicKey
                    val point = publicKey.w

                    val curveName =
                        algorithm.curveName
                            ?: throw IllegalArgumentException("Unsupported EC algorithm: ${algorithm.name}")

                    // Extract x and y coordinates from ECPoint
                    // ECPoint has affineX and affineY as BigInteger
                    val affineX = point.affineX
                    val affineY = point.affineY
                    val coordinateLength =
                        when (algorithm) {
                            is Algorithm.Secp256k1, is Algorithm.P256 -> 32
                            is Algorithm.P384 -> 48
                            is Algorithm.P521 -> 66
                            else -> 32
                        }

                    // Convert BigInteger to byte array (unsigned, big-endian)
                    fun toUnsignedByteArray(
                        bigInt: BigInteger,
                        length: Int,
                    ): ByteArray {
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
                        JwkKeys.Y to Base64.getUrlEncoder().withoutPadding().encodeToString(y),
                    )
                }
                is Algorithm.RSA -> {
                    val keyFactory = KeyFactory.getInstance("RSA")
                    val publicKey = keyFactory.generatePublic(X509EncodedKeySpec(publicKeyBytes)) as RSAPublicKey
                    val modulus = publicKey.modulus
                    val exponent = publicKey.publicExponent

                    // JWK integers are unsigned big-endian: drop BigInteger's leading sign byte.
                    fun unsigned(value: BigInteger): ByteArray {
                        val signed = value.toByteArray()
                        return if (signed.size > 1 && signed[0] == 0.toByte()) signed.copyOfRange(1, signed.size) else signed
                    }

                    mapOf(
                        JwkKeys.KTY to JwkKeyTypes.RSA,
                        JwkKeys.N to Base64.getUrlEncoder().withoutPadding().encodeToString(unsigned(modulus)),
                        JwkKeys.E to Base64.getUrlEncoder().withoutPadding().encodeToString(unsigned(exponent)),
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
    fun resolveKeyName(
        keyId: String,
        config: GoogleKmsConfig,
    ): String {
        // If it's already a full resource name, return as-is
        if (keyId.startsWith("projects/")) {
            return keyId
        }

        // Otherwise, construct from config
        val keyRing =
            config.keyRing
                ?: throw IllegalArgumentException("Key ring must be specified in config or key ID must be a full resource name")

        return "projects/${config.projectId}/locations/${config.location}/keyRings/$keyRing/cryptoKeys/$keyId"
    }

    /**
     * Extracts key name from a full resource name.
     *
     * @param resourceName Full resource name
     * @return Short key name
     */
    fun extractKeyName(resourceName: String): String =
        if (resourceName.startsWith("projects/")) {
            resourceName.substringAfterLast("/")
        } else {
            resourceName
        }
}
