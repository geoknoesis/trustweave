package org.trustweave.did.rotation

import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument
import org.trustweave.did.model.VerificationMethod
import org.trustweave.did.registrar.DidRegistrar
import org.trustweave.did.registrar.model.CreateDidOptions
import org.trustweave.did.registrar.model.KeyManagementMode
import org.trustweave.did.resolver.DidResolver
import org.trustweave.did.resolver.DidResolutionResult
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * DID Rotation Service.
 *
 * Handles rotation of DIDs and their verification methods, following best practices
 * for key rotation and DID lifecycle management.
 *
 * **Rotation Types:**
 * - **Key Rotation**: Rotate individual verification methods
 * - **DID Rotation**: Rotate entire DID (create new DID, migrate relationships)
 * - **Controller Rotation**: Change controller of a DID
 *
 * **Best Practices:**
 * - Maintain backward compatibility during rotation
 * - Preserve relationships and services
 * - Update all dependent systems
 * - Maintain audit trail
 *
 * **Example Usage:**
 * ```kotlin
 * val rotationService = DefaultDidRotationService(
 *     registrar = registrar,
 *     resolver = resolver
 * )
 *
 * // Rotate a verification method
 * val result = rotationService.rotateVerificationMethod(
 *     did = Did("did:web:example.com"),
 *     oldKeyId = "did:web:example.com#key-1",
 *     newKey = newVerificationMethod
 * )
 * ```
 */
interface DidRotationService {
    /**
     * Rotate a verification method in a DID document.
     *
     * @param did The DID to update
     * @param oldKeyId The ID of the verification method to replace
     * @param newKey The new verification method
     * @return Rotation result with updated document
     */
    suspend fun rotateVerificationMethod(
        did: Did,
        oldKeyId: String,
        newKey: VerificationMethod
    ): RotationResult
    
    /**
     * Rotate the entire DID (create new DID and migrate).
     *
     * @param oldDid The old DID
     * @param newDidOptions Options for creating the new DID
     * @return Rotation result with new DID and migration information
     */
    suspend fun rotateDid(
        oldDid: Did,
        newDidOptions: NewDidOptions
    ): DidRotationResult
    
    /**
     * Update controller of a DID.
     *
     * @param did The DID to update
     * @param newController The new controller DID
     * @return Rotation result
     */
    suspend fun rotateController(
        did: Did,
        newController: Did
    ): RotationResult
}

/**
 * Options for creating a new DID during rotation.
 */
data class NewDidOptions(
    val method: String,
    val options: Map<String, JsonElement> = emptyMap()
)

/**
 * Result of a rotation operation.
 */
data class RotationResult(
    val success: Boolean,
    val updatedDocument: DidDocument? = null,
    val error: String? = null,
    val rotatedAt: kotlinx.datetime.Instant = Clock.System.now()
)

/**
 * Result of a full DID rotation.
 */
data class DidRotationResult(
    val success: Boolean,
    val oldDid: Did,
    val newDid: Did? = null,
    val migrationGuide: MigrationGuide? = null,
    val error: String? = null,
    val rotatedAt: kotlinx.datetime.Instant = Clock.System.now()
)

/**
 * Migration guide for DID rotation.
 */
data class MigrationGuide(
    val relationshipsToUpdate: List<String> = emptyList(),
    val servicesToUpdate: List<String> = emptyList(),
    val credentialsToUpdate: List<String> = emptyList()
)

/**
 * Default implementation of DID rotation service.
 */
class DefaultDidRotationService(
    private val registrar: DidRegistrar,
    private val resolver: DidResolver
) : DidRotationService {
    
    override suspend fun rotateVerificationMethod(
        did: Did,
        oldKeyId: String,
        newKey: VerificationMethod
    ): RotationResult {
        // 1. Resolve current document
        val resolutionResult = resolver.resolve(did)
        val currentDocument = when (resolutionResult) {
            is DidResolutionResult.Success -> resolutionResult.document
            else -> return RotationResult(
                success = false,
                error = "Failed to resolve DID: ${(resolutionResult as? DidResolutionResult.Failure)?.let {
                    when (it) {
                        is DidResolutionResult.Failure.NotFound -> "DID not found"
                        is DidResolutionResult.Failure.ResolutionError -> it.reason
                        else -> "Unknown error"
                    }
                }}"
            )
        }
        
        // 2. Verify old key exists
        val oldKey = currentDocument.verificationMethod.find { it.id.value == oldKeyId }
            ?: return RotationResult(
                success = false,
                error = "Verification method not found: $oldKeyId"
            )
        
        // 3. Create updated document
        val updatedVerificationMethods = currentDocument.verificationMethod
            .filter { it.id.value != oldKeyId }
            .plus(newKey)
        
        // 4. Update references in relationship arrays
        val updatedDocument = currentDocument.copy(
            verificationMethod = updatedVerificationMethods,
            authentication = currentDocument.authentication.map { ref ->
                if (ref.value == oldKeyId) {
                    org.trustweave.did.identifiers.VerificationMethodId(
                        did = did,
                        keyId = org.trustweave.core.identifiers.KeyId(
                            newKey.id.value.substringAfter("#")
                        )
                    )
                } else {
                    ref
                }
            },
            assertionMethod = currentDocument.assertionMethod.map { ref ->
                if (ref.value == oldKeyId) {
                    org.trustweave.did.identifiers.VerificationMethodId(
                        did = did,
                        keyId = org.trustweave.core.identifiers.KeyId(
                            newKey.id.value.substringAfter("#")
                        )
                    )
                } else {
                    ref
                }
            }
            // Similar updates for other relationship arrays...
        )
        
        // 5. Update via registrar
        return try {
            val updateResult = registrar.updateDid(
                did = did.value,
                document = updatedDocument
            )
            
            RotationResult(
                success = updateResult.didState.state == org.trustweave.did.registrar.model.OperationState.FINISHED,
                updatedDocument = updatedDocument
            )
        } catch (e: Exception) {
            RotationResult(
                success = false,
                error = "Failed to update DID: ${e.message}"
            )
        }
    }
    
    override suspend fun rotateDid(
        oldDid: Did,
        newDidOptions: NewDidOptions
    ): DidRotationResult {
        // 1. Resolve old DID
        val oldResolution = resolver.resolve(oldDid)
        val oldDocument = when (oldResolution) {
            is DidResolutionResult.Success -> oldResolution.document
            else -> return DidRotationResult(
                success = false,
                oldDid = oldDid,
                error = "Failed to resolve old DID"
            )
        }
        
        // 2. Create new DID
        val createResult = registrar.createDid(
            method = newDidOptions.method,
            options = CreateDidOptions(
                keyManagementMode = KeyManagementMode.INTERNAL_SECRET,
                methodSpecificOptions = newDidOptions.options.mapValues { 
                    kotlinx.serialization.json.JsonPrimitive(it.value.toString()) 
                }
            )
        )
        
        val newDid = when (val state = createResult.didState.state) {
            org.trustweave.did.registrar.model.OperationState.FINISHED -> {
                createResult.didState.did?.let { org.trustweave.did.identifiers.Did(it) }
            }
            else -> return DidRotationResult(
                success = false,
                oldDid = oldDid,
                error = "Failed to create new DID: state is $state"
            )
        } ?: return DidRotationResult(
            success = false,
            oldDid = oldDid,
            error = "Failed to create new DID: no DID returned"
        )
        
        // 3. Generate migration guide
        val migrationGuide = MigrationGuide(
            relationshipsToUpdate = oldDocument.authentication.map { it.value } +
                oldDocument.assertionMethod.map { it.value },
            servicesToUpdate = oldDocument.service.map { it.id }
        )
        
        return DidRotationResult(
            success = true,
            oldDid = oldDid,
            newDid = newDid,
            migrationGuide = migrationGuide
        )
    }
    
    override suspend fun rotateController(
        did: Did,
        newController: Did
    ): RotationResult {
        // 1. Resolve current document
        val resolutionResult = resolver.resolve(did)
        val currentDocument = when (resolutionResult) {
            is DidResolutionResult.Success -> resolutionResult.document
            else -> return RotationResult(
                success = false,
                error = "Failed to resolve DID"
            )
        }
        
        // 2. Update controller
        val updatedDocument = currentDocument.copy(
            controller = listOf(newController)
        )
        
        // 3. Update via registrar
        return try {
            val updateResult = registrar.updateDid(
                did = did.value,
                document = updatedDocument
            )
            
            RotationResult(
                success = updateResult.didState.state == org.trustweave.did.registrar.model.OperationState.FINISHED,
                updatedDocument = updatedDocument
            )
        } catch (e: Exception) {
            RotationResult(
                success = false,
                error = "Failed to update controller: ${e.message}"
            )
        }
    }
}

