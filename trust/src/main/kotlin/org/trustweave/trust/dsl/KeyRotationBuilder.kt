package org.trustweave.trust.dsl

import org.trustweave.did.model.DidDocument
import org.trustweave.did.DidMethod
import org.trustweave.did.DidCreationOptions
import org.trustweave.did.KeyAlgorithm
import org.trustweave.did.model.VerificationMethod
import org.trustweave.did.identifiers.Did
import org.trustweave.did.identifiers.VerificationMethodId
import org.trustweave.trust.dsl.did.DidDslProvider
import org.trustweave.kms.services.KmsService
import org.trustweave.kms.KeyManagementService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineDispatcher
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
    private val kmsService: KmsService,
    /**
     * Coroutine dispatcher for I/O-bound operations.
     * Defaults to [Dispatchers.IO] if not provided.
     */
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private var did: String? = null
    private var method: String? = null
    private var algorithmString: String = "Ed25519"
    private var algorithmEnum: KeyAlgorithm? = null
    private val oldKeyIds = mutableListOf<String>()

    /**
     * Get the resolved algorithm string.
     * Prefers enum value if set, otherwise uses string value.
     */
    private val algorithm: String
        get() = algorithmEnum?.algorithmName ?: algorithmString

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
     * Set key algorithm for new key by string name.
     * 
     * For type safety, prefer using algorithm(value: DidCreationOptions.KeyAlgorithm).
     */
    fun algorithm(algorithm: String) {
        this.algorithmString = algorithm
        this.algorithmEnum = null
    }

    /**
     * Set key algorithm using type-safe enum.
     * 
     * This is the preferred method for compile-time type safety.
     */
    fun algorithm(value: KeyAlgorithm) {
        this.algorithmEnum = value
        this.algorithmString = value.algorithmName
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
     * This operation performs I/O-bound work (key generation, DID document updates)
     * and uses the configured dispatcher. It is non-blocking and can be cancelled.
     *
     * @return Updated DID document
     */
    suspend fun rotate(): DidDocument = withContext(ioDispatcher) {
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
        val targetDidObj = Did(targetDid)
        val updatedDoc = didMethod.updateDid(targetDidObj) { currentDoc ->
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
        val targetDidObj = Did(targetDid)
        
        // Filter out old keys
        val filteredVm = currentDoc.verificationMethod.filter { vm ->
            !oldKeyIds.any { oldId -> vm.id.value.contains(oldId) }
        }

        val filteredAuth = currentDoc.authentication.filter { auth ->
            !oldKeyIds.any { oldId -> auth.value.contains(oldId) }
        }

        val filteredAssertion = currentDoc.assertionMethod.filter { assertion ->
            !oldKeyIds.any { oldId -> assertion.value.contains(oldId) }
        }

        // Create new verification method
        val newVmIdString = "$targetDid#$keyId"
        val newVmId = VerificationMethodId.parse(newVmIdString, targetDidObj)
        val vmType = when (algorithm.uppercase()) {
            "ED25519" -> "Ed25519VerificationKey2020"
            "SECP256K1" -> "EcdsaSecp256k1VerificationKey2019"
            else -> "JsonWebKey2020"
        }

        val newVm = VerificationMethod(
            id = newVmId,
            type = vmType,
            controller = targetDidObj,
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

