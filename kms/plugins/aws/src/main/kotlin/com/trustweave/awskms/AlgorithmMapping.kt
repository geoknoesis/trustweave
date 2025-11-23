package com.trustweave.awskms

import com.trustweave.kms.Algorithm
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
            is Algorithm.Ed25519 -> {
                // Try to use Ed25519 if available, otherwise fall back to P256
                try {
                    KeySpec.valueOf("ECC_ED25519")
                } catch (e: IllegalArgumentException) {
                    // Ed25519 may not be available in all SDK versions
                    // Fall back to P256 for compatibility
                    KeySpec.ECC_NIST_P256
                }
            }
            is Algorithm.Secp256k1 -> {
                try {
                    KeySpec.valueOf("ECC_SECG_P256K1")
                } catch (e: IllegalArgumentException) {
                    // Fallback if enum name is different
                    KeySpec.ECC_NIST_P256
                }
            }
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
            is Algorithm.Ed25519 -> SigningAlgorithmSpec.ECDSA_SHA_256 // AWS uses ECDSA_SHA_256 for Ed25519
            is Algorithm.Secp256k1 -> SigningAlgorithmSpec.ECDSA_SHA_256
            is Algorithm.P256 -> SigningAlgorithmSpec.ECDSA_SHA_256
            is Algorithm.P384 -> SigningAlgorithmSpec.ECDSA_SHA_384
            is Algorithm.P521 -> SigningAlgorithmSpec.ECDSA_SHA_512
            is Algorithm.RSA -> {
                when (algorithm.keySize) {
                    2048, 3072, 4096 -> SigningAlgorithmSpec.RSASSA_PKCS1_V1_5_SHA_256
                    else -> throw IllegalArgumentException("Unsupported RSA key size: ${algorithm.keySize}")
                }
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
                is Algorithm.Ed25519 -> {
                    // Ed25519 keys from AWS KMS are in raw format (32 bytes)
                    // If it's DER-encoded, we need to extract the raw key
                    val rawKey = if (publicKeyBytes.size == 32) {
                        publicKeyBytes
                    } else {
                        // Try to extract from DER format
                        // Ed25519 public key in DER: 30 2A 30 05 06 03 2B 65 70 03 21 00 [32 bytes]
                        if (publicKeyBytes.size >= 44 && publicKeyBytes[0] == 0x30.toByte()) {
                            publicKeyBytes.sliceArray(12 until 44)
                        } else {
                            publicKeyBytes.takeLast(32).toByteArray()
                        }
                    }
                    mapOf(
                        "kty" to "OKP",
                        "crv" to "Ed25519",
                        "x" to Base64.getUrlEncoder().withoutPadding().encodeToString(rawKey)
                    )
                }
                is Algorithm.Secp256k1, is Algorithm.P256, is Algorithm.P384, is Algorithm.P521 -> {
                    val keyFactory = KeyFactory.getInstance("EC")
                    val publicKey = keyFactory.generatePublic(X509EncodedKeySpec(publicKeyBytes)) as ECPublicKey
                    val point = publicKey.w
                    
                    val curveName = when (algorithm) {
                        is Algorithm.Secp256k1 -> "secp256k1"
                        is Algorithm.P256 -> "P-256"
                        is Algorithm.P384 -> "P-384"
                        is Algorithm.P521 -> "P-521"
                        else -> throw IllegalArgumentException("Unsupported EC algorithm")
                    }
                    
                    // Extract x and y coordinates
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
                        "kty" to "EC",
                        "crv" to curveName,
                        "x" to Base64.getUrlEncoder().withoutPadding().encodeToString(x),
                        "y" to Base64.getUrlEncoder().withoutPadding().encodeToString(y)
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
                        "kty" to "RSA",
                        "n" to Base64.getUrlEncoder().withoutPadding().encodeToString(toUnsignedByteArray(modulus)),
                        "e" to Base64.getUrlEncoder().withoutPadding().encodeToString(toUnsignedByteArray(exponent))
                    )
                }
                else -> throw IllegalArgumentException("Unsupported algorithm for JWK conversion: ${algorithm.name}")
            }
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

