package com.trustweave.azurekms

import com.trustweave.kms.Algorithm
import com.azure.security.keyvault.keys.models.KeyType
import com.azure.security.keyvault.keys.models.KeyCurveName
import com.azure.security.keyvault.keys.cryptography.models.SignatureAlgorithm
import java.security.KeyFactory
import java.security.interfaces.ECPublicKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Utilities for mapping between TrustWeave Algorithm types and Azure Key Vault types.
 */
object AlgorithmMapping {
    /**
     * Maps TrustWeave Algorithm to Azure Key Vault KeyType and KeyCurveName.
     *
     * @param algorithm TrustWeave algorithm
     * @return Pair of KeyType and optional KeyCurveName (null for RSA)
     * @throws IllegalArgumentException if algorithm is not supported by Azure Key Vault
     */
    fun toAzureKeyType(algorithm: Algorithm): Pair<KeyType, KeyCurveName?> {
        return when (algorithm) {
            is Algorithm.Ed25519 -> {
                // Azure Key Vault doesn't natively support Ed25519
                // We'll use P-256 as a fallback or throw an exception
                throw IllegalArgumentException("Ed25519 is not directly supported by Azure Key Vault. Use P-256, P-384, or P-521 instead.")
            }
            is Algorithm.Secp256k1 -> Pair(KeyType.EC, KeyCurveName.P_256K)
            is Algorithm.P256 -> Pair(KeyType.EC, KeyCurveName.P_256)
            is Algorithm.P384 -> Pair(KeyType.EC, KeyCurveName.P_384)
            is Algorithm.P521 -> Pair(KeyType.EC, KeyCurveName.P_521)
            is Algorithm.RSA -> {
                when (algorithm.keySize) {
                    2048, 3072, 4096 -> Pair(KeyType.RSA, null)
                    else -> throw IllegalArgumentException("Unsupported RSA key size: ${algorithm.keySize}. Azure Key Vault supports 2048, 3072, and 4096.")
                }
            }
            else -> throw IllegalArgumentException("Algorithm ${algorithm.name} is not supported by Azure Key Vault")
        }
    }

    /**
     * Maps TrustWeave Algorithm to Azure Key Vault SignatureAlgorithm.
     *
     * @param algorithm TrustWeave algorithm
     * @return Azure Key Vault SignatureAlgorithm
     * @throws IllegalArgumentException if algorithm is not supported by Azure Key Vault
     */
    fun toAzureSignatureAlgorithm(algorithm: Algorithm): SignatureAlgorithm {
        return when (algorithm) {
            is Algorithm.Ed25519 -> {
                throw IllegalArgumentException("Ed25519 is not directly supported by Azure Key Vault")
            }
            is Algorithm.Secp256k1 -> SignatureAlgorithm.ES256K
            is Algorithm.P256 -> SignatureAlgorithm.ES256
            is Algorithm.P384 -> SignatureAlgorithm.ES384
            is Algorithm.P521 -> SignatureAlgorithm.ES512
            is Algorithm.RSA -> {
                when (algorithm.keySize) {
                    2048 -> SignatureAlgorithm.RS256
                    3072 -> SignatureAlgorithm.RS256
                    4096 -> SignatureAlgorithm.RS256
                    else -> throw IllegalArgumentException("Unsupported RSA key size: ${algorithm.keySize}")
                }
            }
            else -> throw IllegalArgumentException("Algorithm ${algorithm.name} is not supported by Azure Key Vault")
        }
    }

    /**
     * Converts Azure Key Vault public key (JWK or raw bytes) to JWK format.
     *
     * @param publicKeyBytes Public key bytes from Azure Key Vault
     * @param algorithm The algorithm type
     * @return JWK map representation
     */
    fun publicKeyToJwk(publicKeyBytes: ByteArray, algorithm: Algorithm): Map<String, Any?> {
        return try {
            when (algorithm) {
                is Algorithm.Secp256k1, is Algorithm.P256, is Algorithm.P384, is Algorithm.P521 -> {
                    val keyFactory = KeyFactory.getInstance("EC")
                    val publicKey = keyFactory.generatePublic(X509EncodedKeySpec(publicKeyBytes)) as ECPublicKey
                    val params = publicKey.params
                    val point = publicKey.w

                    val curveName = when (algorithm) {
                        is Algorithm.Secp256k1 -> "secp256k1"
                        is Algorithm.P256 -> "P-256"
                        is Algorithm.P384 -> "P-384"
                        is Algorithm.P521 -> "P-521"
                        else -> throw IllegalArgumentException("Unsupported EC algorithm")
                    }

                    // Extract x and y coordinates from affine coordinates
                    val affineX = point.affineX
                    val affineY = point.affineY
                    val coordinateLength = when (algorithm) {
                        is Algorithm.Secp256k1, is Algorithm.P256 -> 32
                        is Algorithm.P384 -> 48
                        is Algorithm.P521 -> 66
                        else -> 32
                    }

                    // Convert BigInteger to byte array (big-endian, right-aligned)
                    val xBytes = affineX.toByteArray()
                    val yBytes = affineY.toByteArray()

                    val x = ByteArray(coordinateLength)
                    val y = ByteArray(coordinateLength)

                    // Copy right-aligned (big-endian), padding with zeros on the left if needed
                    val xStart = maxOf(0, xBytes.size - coordinateLength)
                    val yStart = maxOf(0, yBytes.size - coordinateLength)
                    val xOffset = maxOf(0, coordinateLength - xBytes.size)
                    val yOffset = maxOf(0, coordinateLength - yBytes.size)

                    System.arraycopy(xBytes, xStart, x, xOffset, minOf(xBytes.size, coordinateLength))
                    System.arraycopy(yBytes, yStart, y, yOffset, minOf(yBytes.size, coordinateLength))

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

                    mapOf(
                        "kty" to "RSA",
                        "n" to Base64.getUrlEncoder().withoutPadding().encodeToString(modulus.toByteArray()),
                        "e" to Base64.getUrlEncoder().withoutPadding().encodeToString(exponent.toByteArray())
                    )
                }
                else -> throw IllegalArgumentException("Unsupported algorithm for JWK conversion: ${algorithm.name}")
            }
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to convert Azure Key Vault public key to JWK: ${e.message}", e)
        }
    }

    /**
     * Resolves a key identifier (name, name with version, or full URL) to a format Azure Key Vault accepts.
     *
     * @param keyId Key identifier (name, name/version, or full URL)
     * @return Normalized key identifier
     * - For URLs: returns "keyname" or "keyname/version" if version is present in URL
     * - For non-URLs: returns just the key name (without version)
     */
    fun resolveKeyId(keyId: String): String {
        // Azure Key Vault accepts:
        // - Key name: "mykey"
        // - Key name with version: "mykey/abc123def456"
        // - Full URL: "https://myvault.vault.azure.net/keys/mykey" or "https://myvault.vault.azure.net/keys/mykey/abc123def456"
        return keyId.trim().let { id ->
            if (id.startsWith("https://")) {
                // Extract key name and version from URL
                val parts = id.split("/")
                val keyIndex = parts.indexOf("keys")
                if (keyIndex >= 0 && keyIndex < parts.size - 1) {
                    val keyName = parts[keyIndex + 1]
                    val version = if (keyIndex + 2 < parts.size) parts[keyIndex + 2] else null
                    if (version != null) "$keyName/$version" else keyName
                } else {
                    // If URL format is unexpected, try to extract from path
                    id.substringAfterLast("/")
                }
            } else {
                // For non-URL format, return just the key name (before first slash if present)
                id.substringBefore("/")
            }
        }
    }

    /**
     * Parses algorithm from Azure Key Vault key type and curve name.
     *
     * @param keyType Azure Key Vault KeyType
     * @param curveName Optional curve name for EC keys
     * @param keySize Optional key size for RSA keys
     * @return TrustWeave Algorithm, or null if not recognized or if parameters are incompatible
     */
    fun parseAlgorithmFromKeyType(keyType: KeyType, curveName: KeyCurveName?, keySize: Int?): Algorithm? {
        return when (keyType) {
            KeyType.EC -> {
                // For EC keys, keySize should be null
                if (keySize != null) {
                    return null // Invalid: EC keys don't have keySize parameter
                }
                when (curveName) {
                    KeyCurveName.P_256K -> Algorithm.Secp256k1
                    KeyCurveName.P_256 -> Algorithm.P256
                    KeyCurveName.P_384 -> Algorithm.P384
                    KeyCurveName.P_521 -> Algorithm.P521
                    else -> null
                }
            }
            KeyType.RSA -> {
                // For RSA keys, curveName should be null
                if (curveName != null) {
                    return null // Invalid: RSA keys don't have curveName parameter
                }
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
}

