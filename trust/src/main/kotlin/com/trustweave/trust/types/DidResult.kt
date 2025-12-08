package com.trustweave.trust.types

import com.trustweave.did.identifiers.Did
import com.trustweave.did.model.DidDocument

/**
 * Sealed result type for DID creation operations.
 */
sealed class DidCreationResult {
    /**
     * DID creation succeeded.
     */
    data class Success(
        val did: Did,
        val document: DidDocument
    ) : DidCreationResult()

    /**
     * DID creation failed.
     */
    sealed class Failure : DidCreationResult() {
        /**
         * DID method not registered.
         */
        data class MethodNotRegistered(
            val method: String,
            val availableMethods: List<String>
        ) : Failure()

        /**
         * Key generation failed.
         */
        data class KeyGenerationFailed(
            val reason: String
        ) : Failure()

        /**
         * Document creation failed.
         */
        data class DocumentCreationFailed(
            val reason: String
        ) : Failure()

        /**
         * Invalid configuration.
         */
        data class InvalidConfiguration(
            val reason: String,
            val details: Map<String, Any?> = emptyMap()
        ) : Failure()

        /**
         * Other unexpected error during DID creation.
         */
        data class Other(
            val reason: String,
            val cause: Throwable? = null
        ) : Failure()
    }
}

