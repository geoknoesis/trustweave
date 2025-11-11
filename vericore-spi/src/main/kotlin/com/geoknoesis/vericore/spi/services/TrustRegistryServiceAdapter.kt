package com.geoknoesis.vericore.spi.services

/**
 * Adapter wrapper that converts a TrustRegistry instance to TrustRegistryService.
 *
 * This allows TrustRegistry implementations to be used as TrustRegistryService
 * without requiring them to implement the interface directly.
 */
class TrustRegistryServiceAdapter(
    private val trustRegistry: Any // TrustRegistry - using Any to avoid dependency
) : TrustRegistryService {
    override suspend fun isTrustedIssuer(issuerDid: String, credentialType: String): Boolean {
        // Try to call the method directly if it's already a TrustRegistryService
        if (trustRegistry is TrustRegistryService) {
            return trustRegistry.isTrustedIssuer(issuerDid, credentialType)
        }

        // Fallback to reflection for backward compatibility
        try {
            val isTrustedMethod = trustRegistry.javaClass.getMethod(
                "isTrustedIssuer",
                String::class.java,
                String::class.java,
                kotlin.coroutines.Continuation::class.java
            )

            return kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn<Boolean> { cont ->
                try {
                    val result = isTrustedMethod.invoke(trustRegistry, issuerDid, credentialType, cont)
                    if (result === kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED) {
                        kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
                    } else {
                        cont.resumeWith(Result.success(result as Boolean))
                        kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
                    }
                } catch (e: Exception) {
                    cont.resumeWith(Result.failure(e))
                    kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
                }
            } ?: false
        } catch (e: Exception) {
            throw IllegalStateException(
                "Failed to call isTrustedIssuer on TrustRegistry: ${e.message}",
                e
            )
        }
    }
}


