package com.trustweave.godiddy.did

import com.trustweave.core.exception.TrustWeaveException
import com.trustweave.did.DidCreationOptions
import com.trustweave.did.model.DidDocument
import com.trustweave.did.DidMethod
import com.trustweave.did.identifiers.Did
import com.trustweave.did.registrar.DidRegistrar
import com.trustweave.did.resolver.DidResolutionResult
import com.trustweave.did.resolver.UniversalResolver
import com.trustweave.godiddy.resolver.GodiddyResolver
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
            throw com.trustweave.did.exception.DidException.DidMethodNotRegistered(
                method = method,
                availableMethods = emptyList()
            )
        }
        // Convert DidCreationOptions to CreateDidOptions
        val createOptions = com.trustweave.did.registrar.model.CreateDidOptions(
            keyManagementMode = com.trustweave.did.registrar.model.KeyManagementMode.INTERNAL_SECRET,
            storeSecrets = false,
            returnSecrets = false,
            didDocument = null,
            methodSpecificOptions = emptyMap()
        )
        val response = registrar.createDid(method, createOptions)
        // Extract document from response
        response.didState.didDocument ?: throw com.trustweave.core.exception.TrustWeaveException.Unknown(
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
            throw com.trustweave.did.exception.DidException.DidMethodNotRegistered(
                method = method,
                availableMethods = emptyList()
            )
        }

        val didString = did.value
        // First resolve the current document
        val resolutionResult = resolver.resolveDid(didString)
        val currentDocument = when (resolutionResult) {
            is DidResolutionResult.Success -> resolutionResult.document
            else -> throw com.trustweave.did.exception.DidException.DidNotFound(
                did = did,
                availableMethods = emptyList()
            )
        }

        // Apply the updater function
        val updatedDocument = updater(currentDocument)

        // Update via registrar
        val updateOptions = com.trustweave.did.registrar.model.UpdateDidOptions(
            methodSpecificOptions = emptyMap()
        )
        val updateResponse = registrar.updateDid(didString, updatedDocument, updateOptions)
        // Extract document from response
        updateResponse.didState.didDocument ?: updatedDocument
    }

    override suspend fun deactivateDid(did: Did): Boolean = withContext(Dispatchers.IO) {
        if (registrar == null) {
            throw com.trustweave.did.exception.DidException.DidMethodNotRegistered(
                method = method,
                availableMethods = emptyList()
            )
        }
        val didString = did.value
        val deactivateOptions = com.trustweave.did.registrar.model.DeactivateDidOptions(
            methodSpecificOptions = emptyMap()
        )
        val response = registrar.deactivateDid(didString, deactivateOptions)
        response.didState.state == com.trustweave.did.registrar.model.OperationState.FINISHED
    }
}

