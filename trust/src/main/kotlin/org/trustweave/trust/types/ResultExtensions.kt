package org.trustweave.trust.types

import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.results.VerificationResult
import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument
import org.trustweave.wallet.Wallet

/**
 * Extension functions for result types to provide ergonomic error handling.
 * 
 * These extensions allow extracting success values with contextual error messages,
 * making the API more pleasant to use while maintaining type safety.
 */

/**
 * Extract the credential from a successful VerificationResult, or throw with detailed error.
 * 
 * **Example:**
 * ```kotlin
 * val verification = trustWeave.verify { credential(cred) }.getOrThrow()
 * ```
 * 
 * @throws IllegalStateException with detailed error message if verification failed
 */
fun VerificationResult.getOrThrow(): VerifiableCredential {
    return when (this) {
        is VerificationResult.Valid -> credential
        is VerificationResult.Invalid.Expired -> {
            throw IllegalStateException(
                "Credential expired at ${expiredAt}. " +
                "Errors: ${errors.joinToString("; ")}"
            )
        }
        is VerificationResult.Invalid.Revoked -> {
            throw IllegalStateException(
                "Credential has been revoked${revokedAt?.let { " at $it" } ?: ""}. " +
                "Errors: ${errors.joinToString("; ")}"
            )
        }
        is VerificationResult.Invalid.InvalidProof -> {
            throw IllegalStateException(
                "Credential proof is invalid: ${reason}. " +
                "Errors: ${errors.joinToString("; ")}"
            )
        }
        is VerificationResult.Invalid.InvalidIssuer -> {
            val suggestion = " Ensure the issuer DID is valid and the DID method is properly configured. " +
                "If using a custom DID method, ensure it's registered in TrustWeave.build { did { method(\"...\") } }"
            throw IllegalStateException(
                "Failed to resolve issuer ${issuerIri.value}: ${reason}. " +
                "Errors: ${errors.joinToString("; ")}.$suggestion"
            )
        }
        is VerificationResult.Invalid.UntrustedIssuer -> {
            val suggestion = " To trust this issuer, add it to the trust registry: " +
                "trustWeave.trust { addAnchor(\"${issuerDid.value}\") { ... } }"
            throw IllegalStateException(
                "Issuer ${issuerDid.value} is not trusted. " +
                "Errors: ${errors.joinToString("; ")}. $suggestion"
            )
        }
        is VerificationResult.Invalid.NotYetValid -> {
            throw IllegalStateException(
                "Credential is not yet valid (valid from: ${validFrom}). " +
                "Errors: ${errors.joinToString("; ")}"
            )
        }
        is VerificationResult.Invalid.UnsupportedFormat -> {
            throw IllegalStateException(
                "Credential format '${format.value}' is not supported. " +
                "Errors: ${errors.joinToString("; ")}"
            )
        }
        is VerificationResult.Invalid.AdapterNotReady -> {
            throw IllegalStateException(errors.joinToString("; ").ifEmpty { reason ?: "Credential verification service is not available." })
        }
        is VerificationResult.Invalid.SchemaValidationFailed -> {
            val suggestion = " Ensure the credential schema is valid and matches the credential structure. " +
                "You can skip schema validation using: verify { credential(cred); skipSchema() }"
            throw IllegalStateException(
                "Schema validation failed. " +
                "Errors: ${errors.joinToString("; ")}.$suggestion"
            )
        }
        is VerificationResult.Invalid.MultipleFailures -> {
            throw IllegalStateException(
                "Credential verification failed with multiple errors: ${errors.joinToString("; ")}"
            )
        }
    }
}

/**
 * Extract the DID and document from a successful DidCreationResult, or throw with detailed error.
 * 
 * **Example:**
 * ```kotlin
 * val (did, document) = trustWeave.createDid { method("key") }.getOrThrow()
 * ```
 * 
 * @throws IllegalStateException with detailed error message if DID creation failed
 */
fun DidCreationResult.getOrThrow(): Pair<Did, DidDocument> {
    return when (this) {
        is DidCreationResult.Success -> did to document
        is DidCreationResult.Failure.MethodNotRegistered -> {
            val suggestion = if (availableMethods.isNotEmpty()) {
                " To fix this, register the DID method in TrustWeave.build { did { method(\"$method\") { ... } } } " +
                "or use one of the available methods: ${availableMethods.joinToString(", ")}"
            } else {
                " No DID methods are registered. Configure at least one DID method in TrustWeave.build { did { method(\"key\") { ... } } }"
            }
            throw IllegalStateException(
                "DID method '${method}' is not registered.$suggestion"
            )
        }
        is DidCreationResult.Failure.KeyGenerationFailed -> {
            throw IllegalStateException(
                "Key generation failed: ${reason}. " +
                "This may indicate a KMS configuration issue. " +
                "Ensure KMS is properly configured in TrustWeave.build { keys { provider(\"...\") } }"
            )
        }
        is DidCreationResult.Failure.DocumentCreationFailed -> {
            throw IllegalStateException(
                "DID document creation failed: ${reason}. " +
                "This may indicate an issue with the DID method implementation or configuration."
            )
        }
        is DidCreationResult.Failure.InvalidConfiguration -> {
            val detailsStr = if (details.isEmpty()) {
                ""
            } else {
                " Details: ${details.entries.joinToString("; ") { "${it.key}=${it.value}" }}"
            }
            val suggestion = " Check your DID method configuration in TrustWeave.build { did { method(\"...\") { ... } } }"
            throw IllegalStateException(
                "Invalid DID configuration: ${reason}.${detailsStr}${suggestion}"
            )
        }
        is DidCreationResult.Failure.Other -> {
            throw IllegalStateException("DID creation failed: ${reason}", cause)
        }
    }
}

/**
 * Extract just the DID from a successful DidCreationResult, or throw with detailed error.
 * 
 * **Example:**
 * ```kotlin
 * val did = trustWeave.createDid { method("key") }.getOrThrowDid()
 * ```
 */
fun DidCreationResult.getOrThrowDid(): Did {
    return getOrThrow().first
}

/**
 * Extract just the document from a successful DidCreationResult, or throw with detailed error.
 * 
 * **Example:**
 * ```kotlin
 * val document = trustWeave.createDid { method("key") }.getOrThrowDocument()
 * ```
 */
fun DidCreationResult.getOrThrowDocument(): DidDocument {
    return getOrThrow().second
}

/**
 * Extract the wallet from a successful WalletCreationResult, or throw with detailed error.
 * 
 * **Example:**
 * ```kotlin
 * val wallet = trustWeave.wallet { holderDid(did) }.getOrThrow()
 * ```
 * 
 * @throws IllegalStateException with detailed error message if wallet creation failed
 */
fun WalletCreationResult.getOrThrow(): Wallet {
    return when (this) {
        is WalletCreationResult.Success -> wallet
        is WalletCreationResult.Failure.InvalidHolderDid -> {
            throw IllegalStateException(
                "Invalid holder DID '${holderDid}': ${reason}"
            )
        }
        is WalletCreationResult.Failure.FactoryNotConfigured -> {
            throw IllegalStateException(
                "Wallet factory not configured: ${reason}. " +
                "Configure a wallet factory in TrustWeave.build { wallet { factory(...) } }"
            )
        }
        is WalletCreationResult.Failure.StorageFailed -> {
            throw IllegalStateException(
                "Wallet storage failed: ${reason}. " +
                "This may indicate an issue with the wallet storage backend or configuration.", cause
            )
        }
        is WalletCreationResult.Failure.Other -> {
            throw IllegalStateException(
                "Wallet creation failed: ${reason}", cause
            )
        }
    }
}

/**
 * Unwrap a successful presentation or throw [TrustWeaveException.InvalidState] with stable `code` values
 * (`PRESENTATION_ADAPTER_NOT_READY`, `PRESENTATION_INVALID_REQUEST`, `PRESENTATION_ADAPTER_ERROR`).
 */
fun PresentationResult.getOrThrow(): org.trustweave.credential.model.vc.VerifiablePresentation {
    return when (this) {
        is PresentationResult.Success -> presentation
        is PresentationResult.Failure.AdapterNotReady -> {
            throw TrustWeaveException.InvalidState(
                code = "PRESENTATION_ADAPTER_NOT_READY",
                message = errors.joinToString("; "),
                context = mapOf("errors" to errors),
            )
        }
        is PresentationResult.Failure.InvalidRequest -> {
            throw TrustWeaveException.InvalidState(
                code = "PRESENTATION_INVALID_REQUEST",
                message = errors.joinToString("; "),
                context = mapOf("errors" to errors),
            )
        }
        is PresentationResult.Failure.AdapterError -> {
            throw TrustWeaveException.InvalidState(
                code = "PRESENTATION_ADAPTER_ERROR",
                message = errors.joinToString("; "),
                context = mapOf("errors" to errors),
                cause = cause,
            )
        }
    }
}
