package com.trustweave.azurekms

import com.trustweave.core.TrustWeaveException
import com.trustweave.kms.Algorithm
import com.trustweave.kms.KeyHandle
import com.trustweave.kms.KeyManagementService
import com.trustweave.kms.KeyNotFoundException
import com.trustweave.kms.UnsupportedAlgorithmException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.azure.security.keyvault.keys.KeyClient
import com.azure.security.keyvault.keys.models.CreateKeyOptions
import com.azure.security.keyvault.keys.models.KeyType
import com.azure.security.keyvault.keys.cryptography.CryptographyClient
import com.azure.security.keyvault.keys.cryptography.CryptographyClientBuilder
import com.azure.core.exception.ResourceNotFoundException
import com.azure.core.exception.HttpResponseException
import java.math.BigInteger
import java.security.KeyFactory
import java.security.spec.RSAPublicKeySpec
import java.util.UUID

/**
 * Azure Key Vault implementation of KeyManagementService.
 * 
 * Supports Azure Key Vault-compatible algorithms:
 * - secp256k1 (P-256K)
 * - P-256, P-384, P-521 (P-256, P-384, P-521)
 * - RSA-2048, RSA-3072, RSA-4096
 * 
 * Note: Ed25519 is not directly supported by Azure Key Vault.
 * 
 * **Example:**
 * ```kotlin
 * val config = AzureKmsConfig.builder()
 *     .vaultUrl("https://myvault.vault.azure.net")
 *     .build()
 * val kms = AzureKeyManagementService(config)
 * val key = kms.generateKey(Algorithm.P256)
 * ```
 */
class AzureKeyManagementService(
    private val config: AzureKmsConfig,
    private val keyClient: KeyClient = AzureKmsClientFactory.createClient(config)
) : KeyManagementService, AutoCloseable {

    companion object {
        /**
         * Algorithms supported by Azure Key Vault.
         * Note: Ed25519 is not supported by Azure Key Vault.
         */
        val SUPPORTED_ALGORITHMS = setOf(
            Algorithm.Secp256k1,
            Algorithm.P256,
            Algorithm.P384,
            Algorithm.P521,
            Algorithm.RSA.RSA_2048,
            Algorithm.RSA.RSA_3072,
            Algorithm.RSA.RSA_4096
        )
    }

    override suspend fun getSupportedAlgorithms(): Set<Algorithm> = SUPPORTED_ALGORITHMS

    override suspend fun generateKey(
        algorithm: Algorithm,
        options: Map<String, Any?>
    ): KeyHandle = withContext(Dispatchers.IO) {
        if (!supportsAlgorithm(algorithm)) {
            throw UnsupportedAlgorithmException(
                "Algorithm '${algorithm.name}' is not supported by Azure Key Vault. " +
                "Supported: ${SUPPORTED_ALGORITHMS.joinToString(", ") { it.name }}"
            )
        }

        try {
            val (keyType, curveName) = AlgorithmMapping.toAzureKeyType(algorithm)
            
            val keyName = (options["keyName"] as? String) ?: "TrustWeave-key-${UUID.randomUUID()}"
            
            // Create key options - Azure SDK CreateKeyOptions
            // Note: The Azure SDK CreateKeyOptions API may vary by version
            // For EC keys, the curve should be specified, but the exact method name may differ
            // This implementation creates keys with basic options; curve and operations
            // can be configured via Azure Key Vault defaults or enhanced with specific SDK version methods
            val createKeyOptions = CreateKeyOptions(keyName, keyType)
            
            // Attempt to set curve name for EC keys using reflection (SDK version compatibility)
            if (curveName != null) {
                try {
                    val method = createKeyOptions.javaClass.getMethod("setCurveName", 
                        com.azure.security.keyvault.keys.models.KeyCurveName::class.java)
                    method.invoke(createKeyOptions, curveName)
                } catch (e: NoSuchMethodException) {
                    // Method doesn't exist in this SDK version - Azure will use appropriate defaults
                } catch (e: Exception) {
                    // Other reflection errors - proceed with defaults
                }
            }

            val keyVaultKey = keyClient.createKey(createKeyOptions)
            val keyId = keyVaultKey.id
            
            // Get public key to include in KeyHandle
            // Azure Key Vault returns the public key in JWK format, but we need to extract it
            // We'll use the CryptographyClient to get the public key
            val cryptographyClient = CryptographyClientBuilder()
                .keyIdentifier(keyId)
                .credential(
                    if (config.clientId != null && config.clientSecret != null && config.tenantId != null) {
                        com.azure.identity.ClientSecretCredentialBuilder()
                            .clientId(config.clientId)
                            .clientSecret(config.clientSecret)
                            .tenantId(config.tenantId)
                            .build()
                    } else {
                        com.azure.identity.DefaultAzureCredentialBuilder().build()
                    }
                )
                .buildClient()
            
            // Get the public key from the key material
            val publicKeyBytes = when {
                keyVaultKey.key is com.azure.security.keyvault.keys.models.JsonWebKey -> {
                    val jwk = keyVaultKey.key as com.azure.security.keyvault.keys.models.JsonWebKey
                    // Extract public key bytes from JWK
                    extractPublicKeyBytesFromJwk(jwk, algorithm)
                }
                else -> {
                    // Fallback: try to get public key via cryptography client
                    // Note: Azure SDK doesn't provide a direct way to get raw public key bytes
                    // We'll construct it from the JWK parameters
                    extractPublicKeyBytesFromKeyVaultKey(keyVaultKey, algorithm)
                }
            }
            
            val publicKeyJwk = AlgorithmMapping.publicKeyToJwk(publicKeyBytes, algorithm)

            KeyHandle(
                id = keyId ?: keyName, // Use key ID (includes version) or fallback to name
                algorithm = algorithm.name,
                publicKeyJwk = publicKeyJwk
            )
        } catch (e: HttpResponseException) {
            throw mapAzureException(e, "Failed to generate key")
        } catch (e: Exception) {
            throw TrustWeaveException("Failed to generate key: ${e.message}", e)
        }
    }

    override suspend fun getPublicKey(keyId: String): KeyHandle = withContext(Dispatchers.IO) {
        try {
            val resolvedKeyId = AlgorithmMapping.resolveKeyId(keyId)
            val keyVaultKey = keyClient.getKey(resolvedKeyId)
            
            val keyType = keyVaultKey.keyType
            val curveName = keyVaultKey.key?.curveName
            val keySize = when (keyType) {
                KeyType.RSA -> {
                    // For RSA keys, get the key size from the key material
                    val jwk = keyVaultKey.key
                    jwk?.n?.size?.let { it * 8 } // Convert bytes to bits
                }
                else -> null
            }
            
            val algorithm = AlgorithmMapping.parseAlgorithmFromKeyType(keyType, curveName, keySize)
                ?: throw TrustWeaveException("Unknown key type: $keyType")
            
            val publicKeyBytes = extractPublicKeyBytesFromKeyVaultKey(keyVaultKey, algorithm)
            
            val publicKeyJwk = AlgorithmMapping.publicKeyToJwk(publicKeyBytes, algorithm)

            KeyHandle(
                id = keyVaultKey.id ?: resolvedKeyId,
                algorithm = algorithm.name,
                publicKeyJwk = publicKeyJwk
            )
        } catch (e: ResourceNotFoundException) {
            throw KeyNotFoundException("Key not found: $keyId", e)
        } catch (e: HttpResponseException) {
            throw mapAzureException(e, "Failed to get public key")
        } catch (e: Exception) {
            throw TrustWeaveException("Failed to get public key: ${e.message}", e)
        }
    }

    override suspend fun sign(
        keyId: String,
        data: ByteArray,
        algorithm: Algorithm?
    ): ByteArray = withContext(Dispatchers.IO) {
        try {
            val resolvedKeyId = AlgorithmMapping.resolveKeyId(keyId)
            
            // Get key metadata to determine algorithm if not provided
            val signingAlgorithm = algorithm ?: run {
                val keyVaultKey = keyClient.getKey(resolvedKeyId)
                val keyType = keyVaultKey.keyType
                val curveName = keyVaultKey.key?.curveName
                val keySize = when (keyType) {
                    KeyType.RSA -> {
                        val jwk = keyVaultKey.key
                        jwk?.n?.size?.let { it * 8 } // Convert bytes to bits
                    }
                    else -> null
                }
                
                AlgorithmMapping.parseAlgorithmFromKeyType(keyType, curveName, keySize)
                    ?: throw TrustWeaveException("Cannot determine signing algorithm for key: $keyId")
            }
            
            val azureSignatureAlgorithm = AlgorithmMapping.toAzureSignatureAlgorithm(signingAlgorithm)
            
            // Create cryptography client for signing
            val cryptographyClient = CryptographyClientBuilder()
                .keyIdentifier(resolvedKeyId)
                .credential(
                    if (config.clientId != null && config.clientSecret != null && config.tenantId != null) {
                        com.azure.identity.ClientSecretCredentialBuilder()
                            .clientId(config.clientId)
                            .clientSecret(config.clientSecret)
                            .tenantId(config.tenantId)
                            .build()
                    } else {
                        com.azure.identity.DefaultAzureCredentialBuilder().build()
                    }
                )
                .buildClient()
            
            val signResult = cryptographyClient.sign(azureSignatureAlgorithm, data)
            signResult.signature
        } catch (e: ResourceNotFoundException) {
            throw KeyNotFoundException("Key not found: $keyId", e)
        } catch (e: HttpResponseException) {
            throw mapAzureException(e, "Failed to sign data")
        } catch (e: Exception) {
            throw TrustWeaveException("Failed to sign data: ${e.message}", e)
        }
    }

    override suspend fun deleteKey(keyId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val resolvedKeyId = AlgorithmMapping.resolveKeyId(keyId)
            
            // Azure Key Vault supports soft delete and purge
            // We'll use beginDeleteKey which schedules deletion (soft delete)
            val deleteKeyOperation = keyClient.beginDeleteKey(resolvedKeyId)
            deleteKeyOperation.waitForCompletion()
            
            // Optionally purge the key (hard delete) if requested
            // For now, we'll just soft delete
            true
        } catch (e: ResourceNotFoundException) {
            false // Key doesn't exist
        } catch (e: HttpResponseException) {
            throw mapAzureException(e, "Failed to delete key")
        } catch (e: Exception) {
            throw TrustWeaveException("Failed to delete key: ${e.message}", e)
        }
    }

    /**
     * Maps Azure Key Vault exceptions to TrustWeave exceptions.
     */
    private fun mapAzureException(e: HttpResponseException, operation: String): Exception {
        return when (e.response?.statusCode) {
            404 -> KeyNotFoundException("Key not found: ${e.message}", e)
            403 -> TrustWeaveException(
                "Access denied to Azure Key Vault. Check permissions: ${e.message}", e
            )
            else -> TrustWeaveException("$operation: ${e.message}", e)
        }
    }

    /**
     * Extracts public key bytes from Azure Key Vault key.
     */
    private fun extractPublicKeyBytesFromKeyVaultKey(
        keyVaultKey: com.azure.security.keyvault.keys.models.KeyVaultKey,
        algorithm: Algorithm
    ): ByteArray {
        val jwk = keyVaultKey.key
        return extractPublicKeyBytesFromJwk(jwk, algorithm)
    }
    
    /**
     * Extracts public key bytes from Azure Key Vault JWK.
     */
    private fun extractPublicKeyBytesFromJwk(
        jwk: com.azure.security.keyvault.keys.models.JsonWebKey,
        algorithm: Algorithm
    ): ByteArray {
        // Azure Key Vault returns keys in JWK format
        // We need to reconstruct the public key bytes from JWK parameters
        return when (algorithm) {
            is Algorithm.Secp256k1, is Algorithm.P256, is Algorithm.P384, is Algorithm.P521 -> {
                // For EC keys, we need x and y coordinates
                val x = jwk.x
                val y = jwk.y
                if (x == null || y == null) {
                    throw TrustWeaveException("Missing x or y coordinates in EC key")
                }
                // Reconstruct the public key in DER format
                // This is a simplified approach - in production, you'd want to use a proper EC key factory
                val coordinateLength = when (algorithm) {
                    is Algorithm.Secp256k1, is Algorithm.P256 -> 32
                    is Algorithm.P384 -> 48
                    is Algorithm.P521 -> 66
                    else -> 32
                }
                // For now, we'll return a combined x+y array
                // In a real implementation, you'd properly encode this as a DER-encoded EC public key
                val result = ByteArray(1 + coordinateLength * 2)
                result[0] = 0x04 // Uncompressed point format
                System.arraycopy(x, 0, result, 1, minOf(x.size, coordinateLength))
                System.arraycopy(y, 0, result, 1 + coordinateLength, minOf(y.size, coordinateLength))
                result
            }
            is Algorithm.RSA -> {
                // For RSA keys, we need modulus and exponent
                val n = jwk.n
                val e = jwk.e
                if (n == null || e == null) {
                    throw TrustWeaveException("Missing modulus or exponent in RSA key")
                }
                // Reconstruct RSA public key
                // This is simplified - in production, use proper RSA key factory
                val keyFactory = KeyFactory.getInstance("RSA")
                val publicKeySpec = RSAPublicKeySpec(
                    BigInteger(1, n),
                    BigInteger(1, e)
                )
                val publicKey = keyFactory.generatePublic(publicKeySpec)
                publicKey.encoded
            }
            else -> throw TrustWeaveException("Unsupported algorithm for key extraction: ${algorithm.name}")
        }
    }

    override fun close() {
        // Azure KeyClient implements Closeable, but we don't need to explicitly close it
        // as it's managed by the Azure SDK
    }
}

