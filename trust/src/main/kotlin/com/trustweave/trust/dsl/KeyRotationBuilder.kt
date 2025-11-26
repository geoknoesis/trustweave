package com.trustweave.trust.dsl

import com.trustweave.did.DidDocument
import com.trustweave.did.DidMethod
import com.trustweave.did.VerificationMethod
import com.trustweave.trust.dsl.did.DidDslProvider
import com.trustweave.kms.services.KmsService
import com.trustweave.kms.KeyManagementService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Key Rotation Builder DSL.
 * 
 * Provides a fluent API for rotating keys in DID documents.
 * Automatically generates new keys and updates DID documents.
 * 
 * This is in the trust module because it requires both DID and KMS operations.
 * 
 * **Example Usage**:
 * ```kotlin
 * val trustWeave: TrustWeave = ...
 * val updatedDoc = trustWeave.rotateKey {
 *     did("did:key:issuer")
 *     algorithm("Ed25519")
 *     removeOldKey("key-1")
 * }
 * ```
 */
class KeyRotationBuilder(
    private val didProvider: DidDslProvider,
    private val kms: KeyManagementService,
    private val kmsService: KmsService
) {
    private var did: String? = null
    private var method: String? = null
    private var algorithm: String = "Ed25519"
    private val oldKeyIds = mutableListOf<String>()
    
    /**
     * Set DID to rotate keys for.
     */
    fun did(did: String) {
        this.did = did
    }
    
    /**
     * Set DID method (auto-detected from DID if not provided).
     */
    fun method(method: String) {
        this.method = method
    }
    
    /**
     * Set key algorithm for new key.
     */
    fun algorithm(algorithm: String) {
        this.algorithm = algorithm
    }
    
    /**
     * Remove old key after rotation.
     */
    fun removeOldKey(keyId: String) {
        oldKeyIds.add(keyId)
    }
    
    /**
     * Rotate the key.
     * 
     * @return Updated DID document
     */
    suspend fun rotate(): DidDocument = withContext(Dispatchers.IO) {
        val targetDid = did ?: throw IllegalStateException(
            "DID is required. Use did(\"did:key:...\")"
        )
        
        // Detect method from DID if not provided
        val methodName = method ?: run {
            if (targetDid.startsWith("did:")) {
                val parts = targetDid.substring(4).split(":", limit = 2)
                if (parts.isNotEmpty()) parts[0] else null
            } else null
        } ?: throw IllegalStateException(
            "Could not determine DID method. Use method(\"key\") or provide a valid DID"
        )
        
        // Get DID method from provider
        val didMethod = didProvider.getDidMethod(methodName) as? DidMethod
            ?: throw IllegalStateException(
                "DID method '$methodName' is not configured. " +
                "Configure it in TrustWeave.build { did { method(\"$methodName\") { ... } } }"
            )
        
        // Generate new key using KMS
        val newKeyHandle = kmsService.generateKey(kms, algorithm, emptyMap())
        val publicKeyJwk = kmsService.getPublicKeyJwk(newKeyHandle)
        val keyId = kmsService.getKeyId(newKeyHandle)
        
        // Update DID document using DidMethod directly
        val updatedDoc = didMethod.updateDid(targetDid) { currentDoc ->
            updateDocumentForKeyRotation(
                currentDoc, targetDid, keyId, algorithm, 
                publicKeyJwk, oldKeyIds
            )
        }
        
        updatedDoc
    }
    
    /**
     * Update document for key rotation using direct types.
     */
    private fun updateDocumentForKeyRotation(
        currentDoc: DidDocument,
        targetDid: String,
        keyId: String,
        algorithm: String,
        publicKeyJwk: Map<String, Any?>?,
        oldKeyIds: List<String>
    ): DidDocument {
        // Filter out old keys
        val filteredVm = currentDoc.verificationMethod.filter { vm ->
            !oldKeyIds.any { oldId -> vm.id.contains(oldId) }
        }
        
        val filteredAuth = currentDoc.authentication.filter { auth ->
            !oldKeyIds.any { oldId -> auth.contains(oldId) }
        }
        
        val filteredAssertion = currentDoc.assertionMethod.filter { assertion ->
            !oldKeyIds.any { oldId -> assertion.contains(oldId) }
        }
        
        // Create new verification method
        val newVmId = "$targetDid#$keyId"
        val vmType = when (algorithm.uppercase()) {
            "ED25519" -> "Ed25519VerificationKey2020"
            "SECP256K1" -> "EcdsaSecp256k1VerificationKey2019"
            else -> "JsonWebKey2020"
        }
        
        val newVm = VerificationMethod(
            id = newVmId,
            type = vmType,
            controller = targetDid,
            publicKeyJwk = publicKeyJwk,
            publicKeyMultibase = null
        )
        
        // Create updated document using copy
        return currentDoc.copy(
            verificationMethod = filteredVm + newVm,
            authentication = filteredAuth + newVmId,
            assertionMethod = filteredAssertion + newVmId
        )
    }
}

