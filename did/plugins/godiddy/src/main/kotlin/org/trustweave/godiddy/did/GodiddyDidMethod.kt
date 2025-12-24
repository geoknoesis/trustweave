package org.trustweave.godiddy.did

import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.did.DidCreationOptions
import org.trustweave.did.model.DidDocument
import org.trustweave.did.DidMethod
import org.trustweave.did.identifiers.Did
import org.trustweave.did.registrar.DidRegistrar
import org.trustweave.did.registrar.model.CreateDidOptions
import org.trustweave.did.registrar.model.DeactivateDidOptions
import org.trustweave.did.registrar.model.KeyManagementMode
import org.trustweave.did.registrar.model.OperationState
import org.trustweave.did.registrar.model.UpdateDidOptions
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.resolver.UniversalResolver
import org.trustweave.godiddy.resolver.GodiddyResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * godiddy-based DID method implementation.
 * Uses Universal Resolver for resolution and Universal Registrar for creation/updates.
 */
class GodiddyDidMethod(
    override val method: String,
    private val resolver: UniversalResolver,
    private val registrar: DidRegistrar?
) : DidMethod {

    override suspend fun createDid(options: DidCreationOptions): DidDocument = withContext(Dispatchers.IO) {
        if (registrar == null) {
            throw org.trustweave.did.exception.DidException.DidMethodNotRegistered(
                method = method,
                availableMethods = emptyList()
            )
        }
        // Convert DidCreationOptions to CreateDidOptions
        val createOptions = CreateDidOptions(
            keyManagementMode = KeyManagementMode.INTERNAL_SECRET,
            storeSecrets = false,
            returnSecrets = false,
            didDocument = null,
            methodSpecificOptions = emptyMap()
        )
        val response = registrar.createDid(method, createOptions)
        // Extract document from response
        response.didState.didDocument ?: throw org.trustweave.core.exception.TrustWeaveException.Unknown(
            code = "DID_CREATION_FAILED",
            message = "DID creation failed: ${response.didState.reason ?: "Unknown error"}"
        )
    }

    override suspend fun resolveDid(did: Did): DidResolutionResult = withContext(Dispatchers.IO) {
        resolver.resolveDid(did.value)
    }

    override suspend fun updateDid(
        did: Did,
        updater: (DidDocument) -> DidDocument
    ): DidDocument = withContext(Dispatchers.IO) {
        if (registrar == null) {
            throw org.trustweave.did.exception.DidException.DidMethodNotRegistered(
                method = method,
                availableMethods = emptyList()
            )
        }

        val didString = did.value
        // First resolve the current document
        val resolutionResult = resolver.resolveDid(didString)
        val currentDocument = when (resolutionResult) {
            is DidResolutionResult.Success -> resolutionResult.document
            else -> throw org.trustweave.did.exception.DidException.DidNotFound(
                did = did,
                availableMethods = emptyList()
            )
        }

        // Apply the updater function
        val updatedDocument = updater(currentDocument)

        // Update via registrar
        val updateOptions = UpdateDidOptions(
            methodSpecificOptions = emptyMap()
        )
        val updateResponse = registrar.updateDid(didString, updatedDocument, updateOptions)
        // Extract document from response
        updateResponse.didState.didDocument ?: updatedDocument
    }

    override suspend fun deactivateDid(did: Did): Boolean = withContext(Dispatchers.IO) {
        if (registrar == null) {
            throw org.trustweave.did.exception.DidException.DidMethodNotRegistered(
                method = method,
                availableMethods = emptyList()
            )
        }
        val didString = did.value
        val deactivateOptions = DeactivateDidOptions(
            methodSpecificOptions = emptyMap()
        )
        val response = registrar.deactivateDid(didString, deactivateOptions)
        response.didState.state == OperationState.FINISHED
    }
}

