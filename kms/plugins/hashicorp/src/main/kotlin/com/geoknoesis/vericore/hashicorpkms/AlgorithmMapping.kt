package com.geoknoesis.vericore.hashicorpkms

import com.geoknoesis.vericore.kms.Algorithm
import java.util.Base64

/**
 * Utilities for mapping between VeriCore Algorithm types and HashiCorp Vault Transit key types.
 */
object AlgorithmMapping {
    /**
     * Maps VeriCore Algorithm to Vault Transit key type.
     * 
     * @param algorithm VeriCore algorithm
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
     * Parses Vault Transit key type to VeriCore Algorithm.
     * 
     * @param keyType Vault Transit key type string
     * @return VeriCore Algorithm, or null if not recognized
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
     * Maps VeriCore Algorithm to Vault Transit hash algorithm for signing.
     * 
     * @param algorithm VeriCore algorithm
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
        return if (keyId.startsWith(transitPrefix)) {
            keyId.substringAfter(transitPrefix)
        } else if (keyId.startsWith("/")) {
            keyId.substring(1)
        } else {
            keyId
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
                        "kty" to "OKP",
                        "crv" to "Ed25519",
                        "x" to Base64.getUrlEncoder().withoutPadding().encodeToString(rawKey)
                    )
                }
                is Algorithm.Secp256k1, is Algorithm.P256, is Algorithm.P384, is Algorithm.P521 -> {
                    // EC keys - would need proper PEM parsing
                    // For now, return a placeholder that indicates the key type
                    val curveName = when (algorithm) {
                        is Algorithm.Secp256k1 -> "secp256k1"
                        is Algorithm.P256 -> "P-256"
                        is Algorithm.P384 -> "P-384"
                        is Algorithm.P521 -> "P-521"
                        else -> "unknown"
                    }
                    
                    // In production, parse PEM properly using BouncyCastle or similar
                    return mapOf(
                        "kty" to "EC",
                        "crv" to curveName,
                        "x" to "", // Would be extracted from PEM
                        "y" to ""  // Would be extracted from PEM
                    )
                }
                is Algorithm.RSA -> {
                    // RSA keys - would need proper PEM parsing
                    // In production, parse PEM properly
                    return mapOf(
                        "kty" to "RSA",
                        "n" to "", // Would be extracted from PEM
                        "e" to ""  // Would be extracted from PEM
                    )
                }
                else -> throw IllegalArgumentException("Unsupported algorithm for JWK conversion: ${algorithm.name}")
            }
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to convert Vault public key to JWK: ${e.message}", e)
        }
    }
}

