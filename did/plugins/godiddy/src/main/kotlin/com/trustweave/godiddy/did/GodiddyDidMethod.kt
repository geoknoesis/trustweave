package com.trustweave.godiddy.did

import com.trustweave.core.exception.TrustWeaveException
import com.trustweave.did.DidCreationOptions
import com.trustweave.did.DidDocument
import com.trustweave.did.DidMethod
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
        registrar.createDid(method, options)
    }

    override suspend fun resolveDid(did: String): DidResolutionResult = withContext(Dispatchers.IO) {
        resolver.resolveDid(did)
    }

    override suspend fun updateDid(
        did: String,
        updater: (DidDocument) -> DidDocument
    ): DidDocument = withContext(Dispatchers.IO) {
        if (registrar == null) {
            throw com.trustweave.did.exception.DidException.DidMethodNotRegistered(
                method = method,
                availableMethods = emptyList()
            )
        }
        
        // First resolve the current document
        val resolutionResult = resolver.resolveDid(did)
        val currentDocument = resolutionResult.document
            ?: throw com.trustweave.did.exception.DidException.DidNotFound(
                did = did,
                availableMethods = emptyList()
            )
        
        // Apply the updater function
        val updatedDocument = updater(currentDocument)
        
        // Update via registrar
        registrar.updateDid(did, updatedDocument)
    }

    override suspend fun deactivateDid(did: String): Boolean = withContext(Dispatchers.IO) {
        if (registrar == null) {
            throw com.trustweave.did.exception.DidException.DidMethodNotRegistered(
                method = method,
                availableMethods = emptyList()
            )
        }
        registrar.deactivateDid(did)
    }
}

