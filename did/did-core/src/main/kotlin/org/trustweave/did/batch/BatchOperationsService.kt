package org.trustweave.did.batch

import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument
import org.trustweave.did.resolver.DidResolver
import org.trustweave.did.resolver.DidResolutionResult
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Batch Operations Service.
 *
 * Provides efficient batch processing for DID operations, enabling
 * parallel resolution and updates of multiple DIDs.
 *
 * **Use Cases:**
 * - Resolving multiple DIDs in parallel
 * - Bulk updates
 * - Batch verification
 * - Performance optimization
 *
 * **Example Usage:**
 * ```kotlin
 * val batchService = DefaultBatchOperationsService(resolver)
 *
 * // Resolve multiple DIDs
 * val results = batchService.resolveBatch(
 *     dids = listOf(
 *         Did("did:web:example.com"),
 *         Did("did:key:z6Mk...")
 *     )
 * )
 *
 * // Process results
 * results.forEach { (did, result) ->
 *     when (result) {
 *         is DidResolutionResult.Success -> println("Resolved: $did")
 *         else -> println("Failed: $did")
 *     }
 * }
 * ```
 */
interface BatchOperationsService {
    /**
     * Resolve multiple DIDs in parallel.
     *
     * @param dids List of DIDs to resolve
     * @param maxConcurrency Maximum number of concurrent operations
     * @return Map of DID to resolution result
     */
    suspend fun resolveBatch(
        dids: List<Did>,
        maxConcurrency: Int = 10
    ): Map<Did, DidResolutionResult>
    
    /**
     * Verify multiple DID documents.
     *
     * @param documents List of documents to verify
     * @param maxConcurrency Maximum number of concurrent operations
     * @return Map of DID to verification result
     */
    suspend fun verifyBatch(
        documents: List<DidDocument>,
        maxConcurrency: Int = 10
    ): Map<Did, Boolean>
}

/**
 * Default implementation of batch operations service.
 */
class DefaultBatchOperationsService(
    private val resolver: DidResolver
) : BatchOperationsService {
    
    override suspend fun resolveBatch(
        dids: List<Did>,
        maxConcurrency: Int
    ): Map<Did, DidResolutionResult> {
        return coroutineScope {
            dids.chunked(maxConcurrency).flatMap { chunk ->
                chunk.map { did ->
                    async {
                        did to resolver.resolve(did)
                    }
                }.awaitAll()
            }.toMap()
        }
    }
    
    override suspend fun verifyBatch(
        documents: List<DidDocument>,
        maxConcurrency: Int
    ): Map<Did, Boolean> {
        return coroutineScope {
            documents.chunked(maxConcurrency).flatMap { chunk ->
                chunk.map { document ->
                    async {
                        val result = resolver.resolve(document.id)
                        document.id to (result is DidResolutionResult.Success)
                    }
                }.awaitAll()
            }.toMap()
        }
    }
}

