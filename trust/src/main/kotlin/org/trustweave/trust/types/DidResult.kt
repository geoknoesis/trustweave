package org.trustweave.trust.types

import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument

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

/**
 * Result of creating a DID and extracting the first verification key id (convenience API).
 */
sealed class DidCreationWithKeyResult {
    data class Success(
        val did: Did,
        val keyId: String
    ) : DidCreationWithKeyResult()

    sealed class Failure : DidCreationWithKeyResult() {
        /**
         * Underlying [DidCreationResult] did not succeed.
         */
        data class FromCreation(val failure: DidCreationResult.Failure) : Failure()

        /**
         * DID was created but key id could not be read from the document (e.g. resolution or VM shape).
         */
        data class KeyExtractionFailed(
            val did: Did,
            val reason: String,
            val cause: Throwable? = null
        ) : Failure()
    }
}

/**
 * Extract [Did] and key id or throw with contextual [IllegalStateException] (convenience for examples/tests).
 */
fun DidCreationWithKeyResult.getOrThrow(): Pair<Did, String> {
    return when (this) {
        is DidCreationWithKeyResult.Success -> did to keyId
        is DidCreationWithKeyResult.Failure.FromCreation -> {
            val f = failure
            throw when (f) {
                is DidCreationResult.Failure.MethodNotRegistered -> IllegalStateException(
                    "DID method '${f.method}' not registered. Available: ${f.availableMethods.joinToString()}"
                )
                is DidCreationResult.Failure.KeyGenerationFailed -> IllegalStateException("Key generation failed: ${f.reason}")
                is DidCreationResult.Failure.DocumentCreationFailed -> IllegalStateException("Document creation failed: ${f.reason}")
                is DidCreationResult.Failure.InvalidConfiguration -> IllegalStateException("Invalid configuration: ${f.reason}")
                is DidCreationResult.Failure.Other -> IllegalStateException("DID creation failed: ${f.reason}", f.cause)
            }
        }
        is DidCreationWithKeyResult.Failure.KeyExtractionFailed -> {
            throw IllegalStateException("Key extraction failed for ${did.value}: $reason", cause)
        }
    }
}

