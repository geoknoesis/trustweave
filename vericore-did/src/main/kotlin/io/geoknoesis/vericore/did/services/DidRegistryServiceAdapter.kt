package io.geoknoesis.vericore.did.services

import io.geoknoesis.vericore.spi.services.DidRegistryService
import io.geoknoesis.vericore.did.DidMethod
import io.geoknoesis.vericore.did.DidResolutionResult
import io.geoknoesis.vericore.did.DidRegistry
import kotlinx.coroutines.runBlocking

/**
 * Adapter that wraps DidRegistry to implement DidRegistryService.
 * 
 * This adapter allows vericore-core to access DidRegistry functionality
 * without direct dependency or reflection.
 */
class DidRegistryServiceAdapter : DidRegistryService {
    override suspend fun resolve(did: String): Any? {
        return try {
            DidRegistry.resolve(did) as Any
        } catch (e: IllegalArgumentException) {
            null // Method not registered
        }
    }
    
    override suspend fun getDocument(did: String): Any? {
        return try {
            val result = DidRegistry.resolve(did) as? DidResolutionResult
            result?.document
        } catch (e: IllegalArgumentException) {
            null // Method not registered
        }
    }
    
    override suspend fun register(method: Any) {
        DidRegistry.register(method as DidMethod)
    }
    
    fun get(methodName: String): Any? {
        return DidRegistry.get(methodName) as? Any
    }
}

