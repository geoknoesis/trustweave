package org.trustweave.hashicorpkms

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
 * Utilities for mapping between TrustWeave Algorithm types and HashiCorp Vault Transit key types.
 */
object AlgorithmMapping {
    /**
     * Maps TrustWeave Algorithm to Vault Transit key type.
     *
     * @param algorithm TrustWeave algorithm
     * @return Vault Transit key type string
     * @throws IllegalArgumentException if algorithm is not supported by Vault Transit
     */
    fun toVaultKeyType(algorithm: Algorithm): String {
        return when (algorithm) {
            is Algorithm.Ed25519 -> "ed25519"
            is Algorithm.Secp256k1 -> "ecdsa-p256k1"
            is Algorithm.P256 -> "ecdsa-p256"
            is Algorithm.P384 -> "ecdsa-p384"
            is Algorithm.P521 -> "ecdsa-p521"
            is Algorithm.RSA -> {
                when (algorithm.keySize) {
                    2048 -> "rsa-2048"
                    3072 -> "rsa-3072"
                    4096 -> "rsa-4096"
                    else -> throw IllegalArgumentException("Unsupported RSA key size: ${algorithm.keySize}")
                }
            }
            else -> throw IllegalArgumentException("Algorithm ${algorithm.name} is not supported by Vault Transit")
        }
    }

    /**
     * Parses Vault Transit key type to TrustWeave Algorithm.
     *
     * @param keyType Vault Transit key type string
     * @return TrustWeave Algorithm, or null if not recognized
     */
    fun fromVaultKeyType(keyType: String): Algorithm? {
        return when (keyType.lowercase()) {
            "ed25519" -> Algorithm.Ed25519
            "ecdsa-p256k1" -> Algorithm.Secp256k1
            "ecdsa-p256" -> Algorithm.P256
            "ecdsa-p384" -> Algorithm.P384
            "ecdsa-p521" -> Algorithm.P521
            "rsa-2048" -> Algorithm.RSA.RSA_2048
            "rsa-3072" -> Algorithm.RSA.RSA_3072
            "rsa-4096" -> Algorithm.RSA.RSA_4096
            else -> null
        }
    }

    /**
     * Maps TrustWeave Algorithm to Vault Transit hash algorithm for signing.
     *
     * @param algorithm TrustWeave algorithm
     * @return Vault Transit hash algorithm string
     */
    fun toVaultHashAlgorithm(algorithm: Algorithm): String {
        return when (algorithm) {
            is Algorithm.Ed25519 -> "sha2-256" // Ed25519 uses SHA-256 internally
            is Algorithm.Secp256k1 -> "sha2-256"
            is Algorithm.P256 -> "sha2-256"
            is Algorithm.P384 -> "sha2-384"
            is Algorithm.P521 -> "sha2-512"
            is Algorithm.RSA -> "sha2-256"
            else -> "sha2-256"
        }
    }

    /**
     * Resolves a key identifier to a Vault Transit key name.
     *
     * Vault Transit uses key names (not IDs) to identify keys.
     * The key name should be URL-safe and descriptive.
     *
     * @param keyId Key identifier (can be key name or full path)
     * @param config Vault configuration
     * @return Resolved key name for Vault API
     */
    fun resolveKeyName(keyId: String, config: VaultKmsConfig): String {
        // If keyId already contains the transit path, extract just the key name
        val transitPrefix = "${config.transitPath}/keys/"
        val transitPrefixWithSlash = "/${config.transitPath}/keys/"

        return when {
            keyId.startsWith(transitPrefix) -> keyId.substringAfter(transitPrefix)
            keyId.startsWith(transitPrefixWithSlash) -> keyId.substringAfter(transitPrefixWithSlash)
            keyId.startsWith("/") -> keyId.substring(1)
            else -> keyId
        }
    }

    /**
     * Converts Vault Transit public key (PEM format) to JWK format.
     *
     * @param publicKeyPem PEM-encoded public key from Vault
     * @param algorithm The algorithm type
     * @return JWK map representation
     */
    fun publicKeyPemToJwk(publicKeyPem: String, algorithm: Algorithm): Map<String, Any?> {
        // Note: This is a simplified conversion. In production, use a proper PEM parser.
        // For now, we'll extract the base64 portion and convert based on algorithm type.
        try {
            when (algorithm) {
                is Algorithm.Ed25519 -> {
                    // Ed25519 public key in PEM format
                    val base64Key = publicKeyPem
                        .replace("-----BEGIN PUBLIC KEY-----", "")
                        .replace("-----END PUBLIC KEY-----", "")
                        .replace("\n", "")
                        .replace(" ", "")

                    val keyBytes = Base64.getDecoder().decode(base64Key)
                    // Ed25519 public key is 32 bytes, typically at the end of the DER structure
                    val rawKey = if (keyBytes.size >= 32) {
                        keyBytes.takeLast(32).toByteArray()
                    } else {
                        keyBytes
                    }

                    return mapOf(
                        JwkKeys.KTY to JwkKeyTypes.OKP,
                        JwkKeys.CRV to Algorithm.Ed25519.curveName,
                        JwkKeys.X to Base64.getUrlEncoder().withoutPadding().encodeToString(rawKey)
                    )
                }
                is Algorithm.Secp256k1, is Algorithm.P256, is Algorithm.P384, is Algorithm.P521 -> {
                    // Parse EC key from PEM format
                    val base64Key = publicKeyPem
                        .replace("-----BEGIN PUBLIC KEY-----", "")
                        .replace("-----END PUBLIC KEY-----", "")
                        .replace("-----BEGIN EC PUBLIC KEY-----", "")
                        .replace("-----END EC PUBLIC KEY-----", "")
                        .replace("\n", "")
                        .replace(" ", "")

                    val keyBytes = Base64.getDecoder().decode(base64Key)
                    
                    // Parse DER-encoded EC public key
                    val keyFactory = KeyFactory.getInstance("EC")
                    val publicKey = keyFactory.generatePublic(X509EncodedKeySpec(keyBytes)) as ECPublicKey
                    val point = publicKey.w
                    
                    val curveName = algorithm.curveName
                        ?: throw IllegalArgumentException("Unsupported EC algorithm: ${algorithm.name}")

                    val coordinateLength = when (algorithm) {
                        is Algorithm.Secp256k1, is Algorithm.P256 -> 32
                        is Algorithm.P384 -> 48
                        is Algorithm.P521 -> 66
                        else -> 32
                    }

                    // Convert BigInteger to unsigned byte array
                    fun toUnsignedByteArray(bigInt: java.math.BigInteger, length: Int): ByteArray {
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

                    val x = toUnsignedByteArray(point.affineX, coordinateLength)
                    val y = toUnsignedByteArray(point.affineY, coordinateLength)

                    return mapOf(
                        JwkKeys.KTY to JwkKeyTypes.EC,
                        JwkKeys.CRV to curveName,
                        JwkKeys.X to Base64.getUrlEncoder().withoutPadding().encodeToString(x),
                        JwkKeys.Y to Base64.getUrlEncoder().withoutPadding().encodeToString(y)
                    )
                }
                is Algorithm.RSA -> {
                    // Parse RSA key from PEM format
                    val base64Key = publicKeyPem
                        .replace("-----BEGIN PUBLIC KEY-----", "")
                        .replace("-----END PUBLIC KEY-----", "")
                        .replace("-----BEGIN RSA PUBLIC KEY-----", "")
                        .replace("-----END RSA PUBLIC KEY-----", "")
                        .replace("\n", "")
                        .replace(" ", "")

                    val keyBytes = Base64.getDecoder().decode(base64Key)
                    
                    // Parse DER-encoded RSA public key
                    val keyFactory = KeyFactory.getInstance("RSA")
                    val publicKey = keyFactory.generatePublic(X509EncodedKeySpec(keyBytes)) as RSAPublicKey
                    val modulus = publicKey.modulus
                    val exponent = publicKey.publicExponent

                    // Convert BigInteger to unsigned byte array
                    fun toUnsignedByteArray(bigInt: java.math.BigInteger): ByteArray {
                        val signed = bigInt.toByteArray()
                        if (signed.isNotEmpty() && signed[0] == 0.toByte()) {
                            return signed.sliceArray(1 until signed.size)
                        }
                        return signed
                    }

                    return mapOf(
                        JwkKeys.KTY to JwkKeyTypes.RSA,
                        JwkKeys.N to Base64.getUrlEncoder().withoutPadding().encodeToString(toUnsignedByteArray(modulus)),
                        JwkKeys.E to Base64.getUrlEncoder().withoutPadding().encodeToString(toUnsignedByteArray(exponent))
                    )
                }
                else -> throw IllegalArgumentException("Unsupported algorithm for JWK conversion: ${algorithm.name}")
            }
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to convert Vault public key to JWK: ${e.message}", e)
        }
    }
}

