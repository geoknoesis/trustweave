package com.geoknoesis.vericore.did.services

import com.geoknoesis.vericore.spi.services.DidMethodService
import com.geoknoesis.vericore.did.DidCreationOptions
import com.geoknoesis.vericore.did.DidMethod
import com.geoknoesis.vericore.did.DidDocument
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
        options: Any?
    ): Any = withContext(Dispatchers.IO) {
        val method = didMethod as? DidMethod
            ?: throw IllegalArgumentException("Expected DidMethod, got ${didMethod.javaClass.name}")
        
        val creationOptions = when (options) {
            null -> DidCreationOptions()
            is DidCreationOptions -> options
            else -> error("Expected DidCreationOptions, got ${options::class.qualifiedName}")
        }

        val document = method.createDid(creationOptions)
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

