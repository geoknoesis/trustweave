package org.trustweave.trust.services

import org.trustweave.did.DidMethod
import org.trustweave.did.model.DidDocument
import org.trustweave.did.identifiers.Did
import org.trustweave.did.registry.DidMethodRegistry
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.verifier.DelegationChainResult
import org.trustweave.kms.KeyManagementService
import org.trustweave.kms.services.KmsService
import org.trustweave.kms.results.GenerateKeyResult
import org.trustweave.trust.context.DidDslContext
import org.trustweave.trust.types.DidCreationWithKeyResult
import org.trustweave.trust.dsl.KeyRotationBuilder
import org.trustweave.trust.dsl.did.DidBuilder
import org.trustweave.trust.dsl.did.DidDocumentBuilder
import org.trustweave.trust.dsl.did.DelegationBuilder
import org.trustweave.trust.types.DidCreationResult
import org.trustweave.trust.types.DidResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Domain service for DID management operations.
 *
 * Extracted from [TrustWeave] to separate the DID management responsibility into a
 * focused service class. Handles DID creation, resolution, updating, delegation, and
 * key rotation.
 */
class DidManagementService(
    private val didContext: DidDslContext,
    private val didRegistry: DidMethodRegistry,
    private val kms: KeyManagementService,
    private val kmsService: KmsService?,
    private val defaultDidMethod: String?,
    private val ioDispatcher: CoroutineDispatcher
) {
    /**
     * Create a new DID.
     *
     * @param method DID method to use (default: from config or "key")
     * @param timeout Maximum time to wait (default: 10 seconds)
     * @param block Optional DSL configuration block
     * @return Sealed result type
     */
    suspend fun createDid(
        method: String? = null,
        timeout: Duration = 10.seconds,
        block: DidBuilder.() -> Unit = {}
    ): DidCreationResult = withTimeout(timeout) {
        withContext(ioDispatcher) {
            val resolvedMethod = method ?: defaultDidMethod ?: "key"
            val builder = DidBuilder(didContext, ioDispatcher)
            builder.method(resolvedMethod)
            builder.block()
            builder.build()
        }
    }

    /**
     * Create a DID and return both the DID and extracted key ID.
     *
     * @param method DID method to use (default: from config or "key")
     * @param timeout Maximum time to wait (default: 10 seconds)
     * @param block Optional DSL configuration block
     * @return [org.trustweave.trust.types.DidCreationWithKeyResult] with DID and key id, or structured failure
     */
    suspend fun createDidWithKey(
        method: String? = null,
        timeout: Duration = 10.seconds,
        block: DidBuilder.() -> Unit = {}
    ): DidCreationWithKeyResult = withContext(ioDispatcher) {
        when (val result = createDid(method, timeout, block)) {
            is DidCreationResult.Success -> {
                val keyResult = getKeyId(result.did)
                val ex = keyResult.exceptionOrNull()
                if (ex is CancellationException) throw ex
                keyResult.fold(
                    onSuccess = { keyId ->
                        DidCreationWithKeyResult.Success(result.did, keyId)
                    },
                    onFailure = { e ->
                        DidCreationWithKeyResult.Failure.KeyExtractionFailed(
                            did = result.did,
                            reason = e.message ?: "Key extraction failed",
                            cause = e
                        )
                    }
                )
            }
            is DidCreationResult.Failure ->
                DidCreationWithKeyResult.Failure.FromCreation(result)
        }
    }

    /**
     * Get the first key ID from a DID document.
     *
     * @param did The DID to extract the key ID from
     * @return Result wrapping the key ID string, or a failure with a descriptive message
     */
    suspend fun getKeyId(did: Did): Result<String> = try {
        val document = when (val r = resolveDid(did)) {
            is DidResolutionResult.Success -> r.document
            else -> throw IllegalStateException("Failed to resolve DID: ${did.value}")
        }
        val keyId =
            document.verificationMethod.firstOrNull()?.let { vm ->
                vm.id.value.takeIf { it.isNotEmpty() }
                    ?: throw IllegalStateException("No key ID found in verification method: ${vm.id.value}")
            } ?: throw IllegalStateException("No verification method found for DID: ${did.value}")
        Result.success(keyId)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    /**
     * Resolve a DID to a DID document.
     *
     * @param did The DID string to resolve
     * @param timeout Maximum time to wait (default: 30 seconds)
     * @return Sealed result type
     */
    suspend fun resolveDid(
        did: String,
        timeout: Duration = 30.seconds
    ): DidResolutionResult = withTimeout(timeout) {
        withContext(ioDispatcher) {
            try {
                didRegistry.resolve(did)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                DidResolutionResult.Failure.ResolutionError(
                    did = Did(did),
                    reason = e.message ?: "Unknown resolution error",
                    cause = e
                )
            }
        }
    }

    /**
     * Resolve a DID to a DID document.
     *
     * @param did Type-safe Did identifier
     * @param timeout Maximum time to wait (default: 30 seconds)
     * @return Sealed result type
     */
    suspend fun resolveDid(
        did: Did,
        timeout: Duration = 30.seconds
    ): DidResolutionResult = resolveDid(did.value, timeout)

    /**
     * Update a DID document.
     *
     * @param timeout Maximum time to wait (default: 30 seconds)
     * @param block DSL block for specifying the update
     * @return Sealed result type with the updated DID document or failure details
     */
    suspend fun updateDid(
        timeout: Duration = 30.seconds,
        block: DidDocumentBuilder.() -> Unit
    ): DidResult = try {
        val document = withTimeout(timeout) {
            withContext(ioDispatcher) {
                val builder = DidDocumentBuilder(didContext)
                builder.block()
                builder.update()
            }
        }
        val did = document.id
        DidResult.Success(did = did, document = document)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        DidResult.Failure.UpdateFailed(
            did = null,
            reason = e.message ?: "DID update failed",
            cause = e
        )
    }

    /**
     * Delegate authority to another DID.
     *
     * @param timeout Maximum time to wait (default: 30 seconds)
     * @param block DSL block for specifying the delegation
     * @return The delegation chain result
     */
    suspend fun delegate(
        timeout: Duration = 30.seconds,
        block: suspend DelegationBuilder.() -> Unit
    ): DelegationChainResult = withTimeout(timeout) {
        withContext(ioDispatcher) {
            val builder = DelegationBuilder(didContext)
            builder.block()
            builder.verify()
        }
    }

    /**
     * Rotate a key in a DID document.
     *
     * @param timeout Maximum time to wait (default: 30 seconds)
     * @param block DSL block for specifying the key rotation
     * @return Sealed result type with the updated DID document (rotated key) or failure details
     */
    suspend fun rotateKey(
        timeout: Duration = 30.seconds,
        block: KeyRotationBuilder.() -> Unit
    ): DidResult = try {
        val document = withTimeout(timeout) {
            withContext(ioDispatcher) {
                val service = kmsService
                    ?: throw IllegalStateException(
                        "KmsService is not configured. Configure it in TrustWeave.build { keys { ... } }"
                    )
                val builder = KeyRotationBuilder(didContext, kms, service, ioDispatcher)
                builder.block()
                builder.rotate()
            }
        }
        val did = document.id
        DidResult.Success(did = did, document = document)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        DidResult.Failure.UpdateFailed(
            did = null,
            reason = e.message ?: "Key rotation failed",
            cause = e
        )
    }
}
