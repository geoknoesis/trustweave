package com.geoknoesis.vericore.godiddy.did

import com.geoknoesis.vericore.core.VeriCoreException
import com.geoknoesis.vericore.did.DidCreationOptions
import com.geoknoesis.vericore.did.DidDocument
import com.geoknoesis.vericore.did.DidMethod
import com.geoknoesis.vericore.did.DidResolutionResult
import com.geoknoesis.vericore.godiddy.registrar.GodiddyRegistrar
import com.geoknoesis.vericore.godiddy.resolver.GodiddyResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * godiddy-based DID method implementation.
 * Uses Universal Resolver for resolution and Universal Registrar for creation/updates.
 */
class GodiddyDidMethod(
    override val method: String,
    private val resolver: GodiddyResolver,
    private val registrar: GodiddyRegistrar?
) : DidMethod {

    override suspend fun createDid(options: DidCreationOptions): DidDocument = withContext(Dispatchers.IO) {
        if (registrar == null) {
            throw VeriCoreException("Universal Registrar not available. Cannot create DID with method $method")
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
            throw VeriCoreException("Universal Registrar not available. Cannot update DID $did")
        }
        
        // First resolve the current document
        val resolutionResult = resolver.resolveDid(did)
        val currentDocument = resolutionResult.document
            ?: throw VeriCoreException("DID not found: $did")
        
        // Apply the updater function
        val updatedDocument = updater(currentDocument)
        
        // Update via registrar
        registrar.updateDid(did, updatedDocument)
    }

    override suspend fun deactivateDid(did: String): Boolean = withContext(Dispatchers.IO) {
        if (registrar == null) {
            throw VeriCoreException("Universal Registrar not available. Cannot deactivate DID $did")
        }
        registrar.deactivateDid(did)
    }
}

