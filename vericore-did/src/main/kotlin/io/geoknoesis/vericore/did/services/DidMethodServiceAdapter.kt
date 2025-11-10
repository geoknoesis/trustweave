package io.geoknoesis.vericore.did.services

import io.geoknoesis.vericore.spi.services.DidMethodService
import io.geoknoesis.vericore.did.DidMethod
import io.geoknoesis.vericore.did.DidDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED

/**
 * Adapter that provides DID method operations without reflection.
 */
class DidMethodServiceAdapter : DidMethodService {
    
    override suspend fun createDid(
        didMethod: Any,
        options: Map<String, Any?>
    ): Any = withContext(Dispatchers.IO) {
        val method = didMethod as? DidMethod
            ?: throw IllegalArgumentException("Expected DidMethod, got ${didMethod.javaClass.name}")
        
        // Call createDid directly since we have the actual type
        val document = method.createDid(options)
        return@withContext document as Any
    }
    
    override suspend fun updateDid(
        didMethod: Any,
        did: String,
        updater: (Any) -> Any
    ): Any = withContext(Dispatchers.IO) {
        val method = didMethod as? DidMethod
            ?: throw IllegalArgumentException("Expected DidMethod, got ${didMethod.javaClass.name}")
        
        // Call updateDid directly with a typed updater
        val document = method.updateDid(did) { doc ->
            updater(doc as Any) as DidDocument
        }
        return@withContext document as Any
    }
    
    override fun getDidId(document: Any): String {
        val doc = document as? DidDocument
            ?: throw IllegalArgumentException("Expected DidDocument, got ${document.javaClass.name}")
        return doc.id
    }
    
}

