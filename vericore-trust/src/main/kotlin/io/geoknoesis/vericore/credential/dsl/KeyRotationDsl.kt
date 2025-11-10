package io.geoknoesis.vericore.credential.dsl

import io.geoknoesis.vericore.spi.services.KmsService
import io.geoknoesis.vericore.spi.services.DidMethodService
import io.geoknoesis.vericore.spi.services.DidDocumentAccess
import io.geoknoesis.vericore.spi.services.VerificationMethodAccess
import io.geoknoesis.vericore.spi.services.AdapterLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Key Rotation Builder DSL.
 * 
 * Provides a fluent API for rotating keys in DID documents.
 * Automatically generates new keys and updates DID documents.
 * 
 * **Example Usage**:
 * ```kotlin
 * val updatedDoc = trustLayer.rotateKey {
 *     did("did:key:issuer")
 *     algorithm("Ed25519")
 *     removeOldKey("key-1")
 * }
 * ```
 */
class KeyRotationBuilder(
    private val context: TrustLayerContext
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
    suspend fun rotate(): Any = withContext(Dispatchers.IO) { // Returns DidDocument but using Any to avoid dependency
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
        
        // Get DID method from trust layer
        val didMethod = context.getDidMethod(methodName)
            ?: throw IllegalStateException(
                "DID method '$methodName' is not configured in trust layer. " +
                "Configure it in trustLayer { did { method(\"$methodName\") { ... } } }"
            )
        
        // Get KMS
        val kms = context.getKms()
            ?: throw IllegalStateException("KMS is not configured in trust layer")
        
        // Generate new key using KmsService
        val kmsService = AdapterLoader.kmsService()
            ?: throw IllegalStateException(
                "KmsService not available. " +
                "Ensure vericore-testkit module is on classpath or provide a custom KmsService via factories."
            )
        
        val newKeyHandle = kmsService.generateKey(kms, algorithm, emptyMap())
        val publicKeyJwk = kmsService.getPublicKeyJwk(newKeyHandle)
        val keyId = kmsService.getKeyId(newKeyHandle)
        
        // Get service instances for DID document manipulation
        val docAccess = AdapterLoader.didDocumentAccess()
            ?: throw IllegalStateException(
                "DidDocumentAccess not available. " +
                "Ensure vericore-did module is on classpath or provide a custom DidDocumentAccess via factories."
            )
        val vmAccess = AdapterLoader.verificationMethodAccess()
            ?: throw IllegalStateException(
                "VerificationMethodAccess not available. " +
                "Ensure vericore-did module is on classpath or provide a custom VerificationMethodAccess via factories."
            )
        val didMethodService = AdapterLoader.didMethodService()
            ?: throw IllegalStateException(
                "DidMethodService not available. " +
                "Ensure vericore-did module is on classpath or provide a custom DidMethodService via factories."
            )
        
        // Update DID document using service interfaces
        val updatedDoc = didMethodService.updateDid(didMethod, targetDid) { currentDoc: Any ->
            // Create updater function using service interfaces
            updateDocumentForKeyRotation(
                currentDoc, targetDid, keyId, algorithm, 
                publicKeyJwk, oldKeyIds, docAccess, vmAccess
            )
        }
        
        updatedDoc
    }
    
    /**
     * Update document for key rotation using service interfaces.
     */
    private fun updateDocumentForKeyRotation(
        currentDoc: Any,
        targetDid: String,
        keyId: String,
        algorithm: String,
        publicKeyJwk: Map<String, Any?>?,
        oldKeyIds: List<String>,
        docAccess: DidDocumentAccess,
        vmAccess: VerificationMethodAccess
    ): Any {
        // Extract current verification methods using service interface
        val currentVm = docAccess.getVerificationMethod(currentDoc)
        
        // Extract current authentication and assertion using service interface
        val currentAuth = docAccess.getAuthentication(currentDoc)
        val currentAssertion = docAccess.getAssertionMethod(currentDoc)
        
        // Create new verification method
        val newVmId = "$targetDid#$keyId"
        val vmType = when (algorithm.uppercase()) {
            "ED25519" -> "Ed25519VerificationKey2020"
            "SECP256K1" -> "EcdsaSecp256k1VerificationKey2019"
            else -> "JsonWebKey2020"
        }
        
        // Filter out old keys using service interface
        val filteredVm = currentVm.filter { vm ->
            val vmId = vmAccess.getId(vm)
            !oldKeyIds.any { oldId -> vmId.contains(oldId) }
        }
        
        val filteredAuth = currentAuth.filter { auth ->
            !oldKeyIds.any { oldId -> auth.contains(oldId) }
        }
        
        val filteredAssertion = currentAssertion.filter { assertion ->
            !oldKeyIds.any { oldId -> assertion.contains(oldId) }
        }
        
        // Create new verification method object using service interface
        val newVm = docAccess.createVerificationMethod(
            id = newVmId,
            type = vmType,
            controller = targetDid,
            publicKeyJwk = publicKeyJwk,
            publicKeyMultibase = null
        )
        
        // Create updated document
        val updatedVm = filteredVm + newVm
        val updatedAuth = filteredAuth + newVmId
        val updatedAssertion = filteredAssertion + newVmId
        
        // Use copy method from docAccess
        return docAccess.copyDocument(
            doc = currentDoc,
            id = targetDid,
            verificationMethod = updatedVm,
            authentication = updatedAuth,
            assertionMethod = updatedAssertion
        )
    }
}

/**
 * Extension function to rotate a key using trust layer configuration.
 */
suspend fun TrustLayerContext.rotateKey(block: KeyRotationBuilder.() -> Unit): Any {
    val builder = KeyRotationBuilder(this)
    builder.block()
    return builder.rotate()
}

/**
 * Extension function for direct key rotation on trust layer config.
 */
suspend fun TrustLayerConfig.rotateKey(block: KeyRotationBuilder.() -> Unit): Any {
    return dsl().rotateKey(block)
}

