package org.trustweave.awskms

import kotlinx.coroutines.CancellationException
import org.trustweave.kms.Algorithm
import org.trustweave.kms.JwkKeys
import org.trustweave.kms.JwkKeyTypes
import software.amazon.awssdk.services.kms.model.KeySpec
import software.amazon.awssdk.services.kms.model.SigningAlgorithmSpec
import java.math.BigInteger
import java.security.KeyFactory
import java.security.interfaces.ECPublicKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Utilities for mapping between TrustWeave Algorithm types and AWS KMS types.
 */
object AlgorithmMapping {
    /**
     * Maps TrustWeave Algorithm to AWS KMS KeySpec.
     *
     * @param algorithm TrustWeave algorithm
     * @return AWS KMS KeySpec
     * @throws IllegalArgumentException if algorithm is not supported by AWS KMS
     */
    fun toAwsKeySpec(algorithm: Algorithm): KeySpec {
        return when (algorithm) {
            is Algorithm.Ed25519 -> throw IllegalArgumentException(
                "Ed25519 is not supported by AWS KMS. Use P-256, P-384, P-521, or secp256k1."
            )
            is Algorithm.Secp256k1 -> KeySpec.ECC_SECG_P256_K1
            is Algorithm.P256 -> KeySpec.ECC_NIST_P256
            is Algorithm.P384 -> KeySpec.ECC_NIST_P384
            is Algorithm.P521 -> KeySpec.ECC_NIST_P521
            is Algorithm.RSA -> {
                when (algorithm.keySize) {
                    2048 -> KeySpec.RSA_2048
                    3072 -> KeySpec.RSA_3072
                    4096 -> KeySpec.RSA_4096
                    else -> throw IllegalArgumentException("Unsupported RSA key size: ${algorithm.keySize}")
                }
            }
            else -> throw IllegalArgumentException("Algorithm ${algorithm.name} is not supported by AWS KMS")
        }
    }

    /**
     * Maps TrustWeave Algorithm to AWS KMS SigningAlgorithmSpec.
     *
     * @param algorithm TrustWeave algorithm
     * @return AWS KMS SigningAlgorithmSpec
     * @throws IllegalArgumentException if algorithm is not supported by AWS KMS
     */
    fun toAwsSigningAlgorithm(algorithm: Algorithm): SigningAlgorithmSpec {
        return when (algorithm) {
            is Algorithm.Ed25519 -> throw IllegalArgumentException(
                "Ed25519 is not supported by AWS KMS."
            )
            is Algorithm.Secp256k1 -> SigningAlgorithmSpec.ECDSA_SHA_256
            is Algorithm.P256 -> SigningAlgorithmSpec.ECDSA_SHA_256
            is Algorithm.P384 -> SigningAlgorithmSpec.ECDSA_SHA_384
            is Algorithm.P521 -> SigningAlgorithmSpec.ECDSA_SHA_512
            is Algorithm.RSA -> when (algorithm.keySize) {
                2048 -> SigningAlgorithmSpec.RSASSA_PKCS1_V1_5_SHA_256
                3072 -> SigningAlgorithmSpec.RSASSA_PKCS1_V1_5_SHA_384
                4096 -> SigningAlgorithmSpec.RSASSA_PKCS1_V1_5_SHA_512
                else -> throw IllegalArgumentException(
                    "Unsupported RSA key size: ${algorithm.keySize}. Supported sizes are 2048, 3072, 4096."
                )
            }
            else -> throw IllegalArgumentException("Algorithm ${algorithm.name} is not supported by AWS KMS")
        }
    }

    /**
     * Converts AWS KMS public key (DER-encoded) to JWK format.
     *
     * @param publicKeyBytes DER-encoded public key from AWS KMS
     * @param algorithm The algorithm type
     * @return JWK map representation
     */
    fun publicKeyToJwk(publicKeyBytes: ByteArray, algorithm: Algorithm): Map<String, Any?> {
        return try {
            when (algorithm) {
                is Algorithm.Secp256k1, is Algorithm.P256, is Algorithm.P384, is Algorithm.P521 -> {
                    val keyFactory = KeyFactory.getInstance("EC")
                    val publicKey = keyFactory.generatePublic(X509EncodedKeySpec(publicKeyBytes)) as ECPublicKey
                    val point = publicKey.w

                    val curveName = algorithm.curveName
                        ?: throw IllegalArgumentException("Unsupported EC algorithm: ${algorithm.name}")

                    // Extract x and y coordinates
                    val affineX = point.affineX
                    val affineY = point.affineY
                    val coordinateLength = when (algorithm) {
                        is Algorithm.Secp256k1, is Algorithm.P256 -> 32
                        is Algorithm.P384 -> 48
                        is Algorithm.P521 -> 66
                        else -> 32
                    }

                    // Convert BigInteger to byte array (unsigned, big-endian, fixed length).
                    // BigInteger.toByteArray() prepends a 0x00 sign byte for positive values
                    // whose MSB is 1; strip it before right-aligning into the fixed-length result.
                    fun toUnsignedByteArray(bigInt: BigInteger, length: Int): ByteArray {
                        val signed = bigInt.toByteArray()
                        val bytes = if (signed.isNotEmpty() && signed[0] == 0.toByte()) signed.copyOfRange(1, signed.size) else signed
                        val result = ByteArray(length)
                        val srcOffset = maxOf(0, bytes.size - length)
                        val dstOffset = maxOf(0, length - bytes.size)
                        System.arraycopy(bytes, srcOffset, result, dstOffset, minOf(bytes.size, length))
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

                    // Convert BigInteger to unsigned byte array
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
                else -> throw IllegalArgumentException("Unsupported algorithm: ${algorithm.name}")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to convert AWS KMS public key to JWK: ${e.message}", e)
        }
    }

    /**
     * Resolves a key identifier (ID, ARN, or alias) to a format AWS KMS accepts.
     *
     * @param keyId Key ID, ARN, or alias
     * @return Normalized key identifier
     */
    fun resolveKeyId(keyId: String): String {
        // AWS KMS accepts:
        // - Key ID: 12345678-1234-1234-1234-123456789012
        // - Key ARN: arn:aws:kms:us-east-1:123456789012:key/12345678-1234-1234-1234-123456789012
        // - Alias: alias/MyAlias
        // - Alias ARN: arn:aws:kms:us-east-1:123456789012:alias/MyAlias
        return keyId.trim()
    }
}

