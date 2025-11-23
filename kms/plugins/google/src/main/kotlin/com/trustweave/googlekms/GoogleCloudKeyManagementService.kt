package com.trustweave.googlekms

import com.trustweave.core.TrustWeaveException
import com.trustweave.kms.Algorithm
import com.trustweave.kms.KeyHandle
import com.trustweave.kms.KeyManagementService
import com.trustweave.kms.KeyNotFoundException
import com.trustweave.kms.UnsupportedAlgorithmException
import com.trustweave.core.PluginLifecycle
import com.google.api.gax.rpc.NotFoundException
import com.google.api.gax.rpc.PermissionDeniedException
import com.google.cloud.kms.v1.CryptoKey
import com.google.cloud.kms.v1.CryptoKey.CryptoKeyPurpose
import com.google.cloud.kms.v1.CryptoKeyVersion
import com.google.cloud.kms.v1.CryptoKeyVersion.CryptoKeyVersionState
import com.google.cloud.kms.v1.CryptoKeyVersionName
import com.google.cloud.kms.v1.CryptoKeyVersionTemplate
import com.google.cloud.kms.v1.KeyManagementServiceClient
import com.google.cloud.kms.v1.KeyRingName
import com.google.protobuf.ByteString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Base64

/**
 * Google Cloud KMS implementation of KeyManagementService.
 * 
 * Supports all Google Cloud KMS-compatible algorithms:
 * - Ed25519 (EC_SIGN_ED25519)
 * - secp256k1 (EC_SIGN_SECP256K1_SHA256)
 * - P-256, P-384, P-521 (EC_SIGN_P256_SHA256, EC_SIGN_P384_SHA384, EC_SIGN_P521_SHA512)
 * - RSA-2048, RSA-3072, RSA-4096 (RSA_SIGN_PKCS1_2048_SHA256, etc.)
 * 
 * **Example:**
 * ```kotlin
 * val config = GoogleKmsConfig.builder()
 *     .projectId("my-project")
 *     .location("us-east1")
 *     .keyRing("my-key-ring")
 *     .build()
 * val kms = GoogleCloudKeyManagementService(config)
 * val key = kms.generateKey(Algorithm.Ed25519)
 * ```
 */
class GoogleCloudKeyManagementService(
    private val config: GoogleKmsConfig,
    private val kmsClient: KeyManagementServiceClient = GoogleKmsClientFactory.createClient(config)
) : KeyManagementService, PluginLifecycle, AutoCloseable {

    companion object {
        /**
         * Algorithms supported by Google Cloud KMS.
         * Note: Ed25519 and P521 support may vary by Google Cloud KMS version.
         */
        val SUPPORTED_ALGORITHMS = setOf(
            Algorithm.Ed25519, // Note: may not be available in all Google Cloud KMS versions
            Algorithm.Secp256k1,
            Algorithm.P256,
            Algorithm.P384,
            Algorithm.P521, // Note: may not be available in all Google Cloud KMS versions
            Algorithm.RSA.RSA_2048,
            Algorithm.RSA.RSA_3072,
            Algorithm.RSA.RSA_4096
        )
    }

    override suspend fun getSupportedAlgorithms(): Set<Algorithm> = SUPPORTED_ALGORITHMS

    override suspend fun initialize(config: Map<String, Any?>): Boolean {
        // Google Cloud KMS client is already initialized
        // Could verify connection here if needed
        return true
    }

    override suspend fun start(): Boolean {
        // No startup needed for Google Cloud KMS
        return true
    }

    override suspend fun stop(): Boolean {
        // No stop needed for Google Cloud KMS
        return true
    }

    override suspend fun cleanup() {
        close()
    }

    override suspend fun generateKey(
        algorithm: Algorithm,
        options: Map<String, Any?>
    ): KeyHandle = withContext(Dispatchers.IO) {
        if (!supportsAlgorithm(algorithm)) {
            throw UnsupportedAlgorithmException(
                "Algorithm '${algorithm.name}' is not supported by Google Cloud KMS. " +
                "Supported: ${SUPPORTED_ALGORITHMS.joinToString(", ") { it.name }}"
            )
        }

        try {
            val keyRing = (options["keyRing"] as? String) ?: config.keyRing
                ?: throw IllegalArgumentException("Key ring must be specified in config or options")
            
            val keyId = (options["keyId"] as? String) ?: generateKeyId()
            val keyRingName = KeyRingName.of(config.projectId, config.location, keyRing)
            
            val googleKmsAlgorithm = AlgorithmMapping.toGoogleKmsAlgorithm(algorithm)
            
            // Create CryptoKeyVersionTemplate
            val versionTemplate = CryptoKeyVersionTemplate.newBuilder()
                .setAlgorithm(googleKmsAlgorithm)
                .build()
            
            // Create CryptoKey builder
            val cryptoKeyBuilder = CryptoKey.newBuilder()
                .setPurpose(CryptoKeyPurpose.ASYMMETRIC_SIGN)
                .setVersionTemplate(versionTemplate)
            
            // Add labels if provided
            val labels = options["labels"] as? Map<String, String>
            labels?.forEach { (key, value) ->
                cryptoKeyBuilder.putLabels(key, value)
            }
            
            val createRequest = com.google.cloud.kms.v1.CreateCryptoKeyRequest.newBuilder()
                .setParent(keyRingName.toString())
                .setCryptoKeyId(keyId)
                .setCryptoKey(cryptoKeyBuilder)
                .build()
            
            val createdKey = kmsClient.createCryptoKey(createRequest)
            val keyResourceName = createdKey.name
            
            // Get the primary version to retrieve public key
            val primaryVersionName = createdKey.primary.name
            val publicKeyResponse = kmsClient.getPublicKey(
                com.google.cloud.kms.v1.GetPublicKeyRequest.newBuilder()
                    .setName(primaryVersionName)
                    .build()
            )
            
            // Google Cloud KMS returns PEM format, need to convert to DER
            val derBytes = convertPemToDer(publicKeyResponse.pem)
            val publicKeyJwk = AlgorithmMapping.publicKeyToJwk(derBytes, algorithm)

            KeyHandle(
                id = keyResourceName,
                algorithm = algorithm.name,
                publicKeyJwk = publicKeyJwk
            )
        } catch (e: com.google.api.gax.rpc.AlreadyExistsException) {
            throw TrustWeaveException("Key already exists: ${e.message}", e)
        } catch (e: PermissionDeniedException) {
            throw TrustWeaveException(
                "Permission denied to Google Cloud KMS. Check IAM permissions: ${e.message}", e
            )
        } catch (e: Exception) {
            throw TrustWeaveException("Failed to generate key: ${e.message}", e)
        }
    }

    override suspend fun getPublicKey(keyId: String): KeyHandle = withContext(Dispatchers.IO) {
        try {
            val resolvedKeyName = AlgorithmMapping.resolveKeyName(keyId, config)
            
            // Get the key to find the primary version
            val cryptoKey = kmsClient.getCryptoKey(
                com.google.cloud.kms.v1.GetCryptoKeyRequest.newBuilder()
                    .setName(resolvedKeyName)
                    .build()
            )
            
            val primaryVersionName = cryptoKey.primary.name
            val publicKeyResponse = kmsClient.getPublicKey(
                com.google.cloud.kms.v1.GetPublicKeyRequest.newBuilder()
                    .setName(primaryVersionName)
                    .build()
            )
            
            val algorithm = AlgorithmMapping.fromGoogleKmsAlgorithm(publicKeyResponse.algorithm)
                ?: throw TrustWeaveException("Unknown algorithm: ${publicKeyResponse.algorithm}")
            
            val derBytes = convertPemToDer(publicKeyResponse.pem)
            val publicKeyJwk = AlgorithmMapping.publicKeyToJwk(derBytes, algorithm)

            KeyHandle(
                id = resolvedKeyName,
                algorithm = algorithm.name,
                publicKeyJwk = publicKeyJwk
            )
        } catch (e: NotFoundException) {
            throw KeyNotFoundException("Key not found: $keyId", e)
        } catch (e: PermissionDeniedException) {
            throw TrustWeaveException(
                "Permission denied to Google Cloud KMS. Check IAM permissions: ${e.message}", e
            )
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
            val resolvedKeyName = AlgorithmMapping.resolveKeyName(keyId, config)
            
            // Get the key to find the primary version
            val cryptoKey = kmsClient.getCryptoKey(
                com.google.cloud.kms.v1.GetCryptoKeyRequest.newBuilder()
                    .setName(resolvedKeyName)
                    .build()
            )
            
            val primaryVersionName = cryptoKey.primary.name
            
            // Determine signing algorithm
            val signingAlgorithm = algorithm ?: run {
                val publicKeyResponse = kmsClient.getPublicKey(
                    com.google.cloud.kms.v1.GetPublicKeyRequest.newBuilder()
                        .setName(primaryVersionName)
                        .build()
                )
                AlgorithmMapping.fromGoogleKmsAlgorithm(publicKeyResponse.algorithm)
                    ?: throw TrustWeaveException("Cannot determine signing algorithm for key: $keyId")
            }
            
            val googleKmsAlgorithm = AlgorithmMapping.toGoogleKmsAlgorithm(signingAlgorithm)
            
            val signRequest = com.google.cloud.kms.v1.AsymmetricSignRequest.newBuilder()
                .setName(primaryVersionName)
                .setData(ByteString.copyFrom(data))
                .build()
            
            val signResponse = kmsClient.asymmetricSign(signRequest)
            signResponse.signature.toByteArray()
        } catch (e: NotFoundException) {
            throw KeyNotFoundException("Key not found: $keyId", e)
        } catch (e: PermissionDeniedException) {
            throw TrustWeaveException(
                "Permission denied to Google Cloud KMS. Check IAM permissions: ${e.message}", e
            )
        } catch (e: Exception) {
            throw TrustWeaveException("Failed to sign data: ${e.message}", e)
        }
    }

    override suspend fun deleteKey(keyId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val resolvedKeyName = AlgorithmMapping.resolveKeyName(keyId, config)
            
            // Get the key to find the primary version
            val cryptoKey = kmsClient.getCryptoKey(
                com.google.cloud.kms.v1.GetCryptoKeyRequest.newBuilder()
                    .setName(resolvedKeyName)
                    .build()
            )
            
            val primaryVersionName = cryptoKey.primary.name
            
            // Schedule key version destruction (30 day waiting period by default)
            val destroyRequest = com.google.cloud.kms.v1.DestroyCryptoKeyVersionRequest.newBuilder()
                .setName(primaryVersionName)
                .build()
            
            // Note: Google Cloud KMS doesn't have a direct delete for CryptoKey
            // We destroy the primary version, which effectively makes the key unusable
            // For full deletion, use scheduleDestroyCryptoKeyVersion with a schedule
            kmsClient.destroyCryptoKeyVersion(destroyRequest)
            
            true
        } catch (e: NotFoundException) {
            false // Key doesn't exist
        } catch (e: PermissionDeniedException) {
            throw TrustWeaveException(
                "Permission denied to Google Cloud KMS. Check IAM permissions: ${e.message}", e
            )
        } catch (e: Exception) {
            throw TrustWeaveException("Failed to delete key: ${e.message}", e)
        }
    }

    /**
     * Generates a unique key ID.
     */
    private fun generateKeyId(): String {
        return "key-${System.currentTimeMillis()}-${(0..9999).random()}"
    }

    /**
     * Converts PEM format to DER format.
     * Google Cloud KMS returns public keys in PEM format, but we need DER for JWK conversion.
     */
    private fun convertPemToDer(pem: String): ByteArray {
        // Remove PEM headers and whitespace
        val base64 = pem
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("-----BEGIN RSA PUBLIC KEY-----", "")
            .replace("-----END RSA PUBLIC KEY-----", "")
            .replace("\n", "")
            .replace("\r", "")
            .replace(" ", "")
        
        return Base64.getDecoder().decode(base64)
    }

    override fun close() {
        kmsClient.close()
    }
}

