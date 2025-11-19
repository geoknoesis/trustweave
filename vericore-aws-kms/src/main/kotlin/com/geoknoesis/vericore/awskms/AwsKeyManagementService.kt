package com.geoknoesis.vericore.awskms

import com.geoknoesis.vericore.core.VeriCoreException
import com.geoknoesis.vericore.kms.Algorithm
import com.geoknoesis.vericore.kms.KeyHandle
import com.geoknoesis.vericore.kms.KeyManagementService
import com.geoknoesis.vericore.kms.KeyNotFoundException
import com.geoknoesis.vericore.kms.UnsupportedAlgorithmException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.services.kms.KmsClient
import software.amazon.awssdk.services.kms.model.*
import software.amazon.awssdk.awscore.exception.AwsServiceException

/**
 * AWS KMS implementation of KeyManagementService.
 * 
 * Supports all AWS KMS-compatible algorithms:
 * - Ed25519 (ECC_Ed25519) - Note: Not in current FIPS certificate, may use non-FIPS path
 * - secp256k1 (ECC_SECG_P256K1) - FIPS 140-3 Level 3 validated (blockchain use only)
 * - P-256, P-384, P-521 (ECC_NIST_P256/384/521) - FIPS 140-3 Level 3 validated
 * - RSA-2048, RSA-3072, RSA-4096 - FIPS 140-3 Level 3 validated
 * 
 * AWS KMS uses FIPS 140-3 Level 3 validated hardware security modules.
 * See [NIST Certificate #4884](https://csrc.nist.gov/projects/cryptographic-module-validation-program/certificate/4884)
 * for validation details.
 * 
 * **Example:**
 * ```kotlin
 * val config = AwsKmsConfig.builder()
 *     .region("us-east-1")
 *     .build()
 * val kms = AwsKeyManagementService(config)
 * val key = kms.generateKey(Algorithm.Ed25519)
 * ```
 */
class AwsKeyManagementService(
    private val config: AwsKmsConfig,
    private val kmsClient: KmsClient = AwsKmsClientFactory.createClient(config)
) : KeyManagementService, AutoCloseable {

    companion object {
        /**
         * Algorithms supported by AWS KMS.
         */
        val SUPPORTED_ALGORITHMS = setOf(
            Algorithm.Ed25519,
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
                "Algorithm '${algorithm.name}' is not supported by AWS KMS. " +
                "Supported: ${SUPPORTED_ALGORITHMS.joinToString(", ") { it.name }}"
            )
        }

        try {
            val keySpec = AlgorithmMapping.toAwsKeySpec(algorithm)
            
            val requestBuilder = CreateKeyRequest.builder()
                .keySpec(keySpec)
                .keyUsage(KeyUsageType.SIGN_VERIFY)
            
            // Add description if provided
            (options["description"] as? String)?.let {
                requestBuilder.description(it)
            }
            
            // Add tags if provided
            val tags = options["tags"] as? Map<String, String>
            tags?.let {
                val tagList = it.map { (key, value) ->
                    Tag.builder().tagKey(key).tagValue(value).build()
                }
                requestBuilder.tags(tagList)
            }

            val createResponse = kmsClient.createKey(requestBuilder.build())
            val keyId = createResponse.keyMetadata().keyId()
            val keyArn = createResponse.keyMetadata().arn()
            
            // Enable automatic rotation if requested
            if (options["enableAutomaticRotation"] == true) {
                try {
                    kmsClient.enableKeyRotation(
                        EnableKeyRotationRequest.builder()
                            .keyId(keyId)
                            .build()
                    )
                } catch (e: Exception) {
                    // Log warning but don't fail key creation
                    // Automatic rotation may not be available for all key types
                }
            }
            
            // Create alias if provided
            val alias = options["alias"] as? String
            alias?.let { aliasName ->
                try {
                    val aliasValue = if (aliasName.startsWith("alias/")) aliasName else "alias/$aliasName"
                    kmsClient.createAlias(
                        CreateAliasRequest.builder()
                            .aliasName(aliasValue)
                            .targetKeyId(keyId)
                            .build()
                    )
                } catch (e: Exception) {
                    // If alias creation fails, continue with key ID
                    // The key is still usable by its ID/ARN
                }
            }

            // Get public key to include in KeyHandle
            val publicKeyResponse = kmsClient.getPublicKey(
                GetPublicKeyRequest.builder()
                    .keyId(keyId)
                    .build()
            )
            
            val publicKeyBytes = publicKeyResponse.publicKey().asByteArray()
            val publicKeyJwk = AlgorithmMapping.publicKeyToJwk(publicKeyBytes, algorithm)

            KeyHandle(
                id = keyArn ?: keyId, // Prefer ARN for full identification
                algorithm = algorithm.name,
                publicKeyJwk = publicKeyJwk
            )
        } catch (e: AwsServiceException) {
            throw mapAwsException(e, "Failed to generate key")
        } catch (e: Exception) {
            throw VeriCoreException("Failed to generate key: ${e.message}", e)
        }
    }

    override suspend fun getPublicKey(keyId: String): KeyHandle = withContext(Dispatchers.IO) {
        try {
            val resolvedKeyId = AlgorithmMapping.resolveKeyId(keyId)
            
            // Get key metadata to determine algorithm
            val describeResponse = kmsClient.describeKey(
                DescribeKeyRequest.builder()
                    .keyId(resolvedKeyId)
                    .build()
            )
            val keyMetadata = describeResponse.keyMetadata()
            val keySpec = keyMetadata.keySpec()
            val algorithmName = keySpec.toString()
            val algorithm = parseAlgorithmFromKeySpec(algorithmName)
                ?: throw VeriCoreException("Unknown key spec: $algorithmName")
            
            // Get public key
            val publicKeyResponse = kmsClient.getPublicKey(
                GetPublicKeyRequest.builder()
                    .keyId(resolvedKeyId)
                    .build()
            )
            val publicKeyBytes = publicKeyResponse.publicKey().asByteArray()
            val publicKeyJwk = AlgorithmMapping.publicKeyToJwk(publicKeyBytes, algorithm)

            KeyHandle(
                id = keyMetadata.arn() ?: keyMetadata.keyId(),
                algorithm = algorithm.name,
                publicKeyJwk = publicKeyJwk
            )
        } catch (e: AwsServiceException) {
            if (e.statusCode() == 404 || e.awsErrorDetails()?.errorCode() == "NotFoundException") {
                throw KeyNotFoundException("Key not found: $keyId", e)
            }
            throw mapAwsException(e, "Failed to get public key")
        } catch (e: Exception) {
            throw VeriCoreException("Failed to get public key: ${e.message}", e)
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
                val describeResponse = kmsClient.describeKey(
                    DescribeKeyRequest.builder()
                        .keyId(resolvedKeyId)
                        .build()
                )
                val keySpec = describeResponse.keyMetadata().keySpec().toString()
                parseAlgorithmFromKeySpec(keySpec)
                    ?: throw VeriCoreException("Cannot determine signing algorithm for key: $keyId")
            }
            
            val awsSigningAlgorithm = AlgorithmMapping.toAwsSigningAlgorithm(signingAlgorithm)
            
            val signRequest = SignRequest.builder()
                .keyId(resolvedKeyId)
                .message(SdkBytes.fromByteArray(data))
                .signingAlgorithm(awsSigningAlgorithm)
                .build()
            
            val signResponse = kmsClient.sign(signRequest)
            signResponse.signature().asByteArray()
        } catch (e: AwsServiceException) {
            if (e.statusCode() == 404 || e.awsErrorDetails()?.errorCode() == "NotFoundException") {
                throw KeyNotFoundException("Key not found: $keyId", e)
            }
            throw mapAwsException(e, "Failed to sign data")
        } catch (e: Exception) {
            throw VeriCoreException("Failed to sign data: ${e.message}", e)
        }
    }

    override suspend fun deleteKey(keyId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val resolvedKeyId = AlgorithmMapping.resolveKeyId(keyId)
            
            // Get pending window from options or use default (30 days)
            val pendingWindowInDays = 30
            
            kmsClient.scheduleKeyDeletion(
                ScheduleKeyDeletionRequest.builder()
                    .keyId(resolvedKeyId)
                    .pendingWindowInDays(pendingWindowInDays)
                    .build()
            )
            
            true
        } catch (e: AwsServiceException) {
            if (e.statusCode() == 404 || e.awsErrorDetails()?.errorCode() == "NotFoundException") {
                return@withContext false // Key doesn't exist
            }
            throw mapAwsException(e, "Failed to delete key")
        } catch (e: Exception) {
            throw VeriCoreException("Failed to delete key: ${e.message}", e)
        }
    }

    /**
     * Parses algorithm from AWS KMS key spec string.
     */
    private fun parseAlgorithmFromKeySpec(keySpec: String): Algorithm? {
        return when (keySpec.uppercase()) {
            "ECC_ED25519" -> Algorithm.Ed25519
            "ECC_SECG_P256K1" -> Algorithm.Secp256k1
            "ECC_NIST_P256" -> Algorithm.P256
            "ECC_NIST_P384" -> Algorithm.P384
            "ECC_NIST_P521" -> Algorithm.P521
            "RSA_2048" -> Algorithm.RSA.RSA_2048
            "RSA_3072" -> Algorithm.RSA.RSA_3072
            "RSA_4096" -> Algorithm.RSA.RSA_4096
            else -> null
        }
    }

    /**
     * Maps AWS KMS exceptions to VeriCore exceptions.
     */
    private fun mapAwsException(e: AwsServiceException, operation: String): Exception {
        val errorCode = e.awsErrorDetails()?.errorCode()
        return when (errorCode) {
            "NotFoundException" -> KeyNotFoundException("Key not found: ${e.message}", e)
            "InvalidKeyUsageException" -> UnsupportedAlgorithmException(
                "Invalid key usage: ${e.message}", e
            )
            "AccessDeniedException" -> VeriCoreException(
                "Access denied to AWS KMS. Check IAM permissions: ${e.message}", e
            )
            else -> VeriCoreException("$operation: ${e.message}", e)
        }
    }

    override fun close() {
        kmsClient.close()
    }
}

