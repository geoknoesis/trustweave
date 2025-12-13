package com.trustweave.trust.types

import com.trustweave.credential.model.vc.VerifiableCredential
import com.trustweave.credential.results.IssuanceResult
import com.trustweave.did.identifiers.Did
import com.trustweave.did.model.DidDocument
import com.trustweave.wallet.Wallet

/**
 * Extension functions for result types to provide ergonomic error handling.
 * 
 * These extensions allow extracting success values with contextual error messages,
 * making the API more pleasant to use while maintaining type safety.
 */

/**
 * Extract the credential from a successful IssuanceResult, or throw with detailed error.
 * 
 * **Example:**
 * ```kotlin
 * val credential = trustWeave.issue { ... }.getOrThrow()
 * ```
 * 
 * @throws IllegalStateException with detailed error message if issuance failed
 */
fun IssuanceResult.getOrThrow(): VerifiableCredential {
    return when (this) {
        is IssuanceResult.Success -> credential
        is IssuanceResult.Failure.UnsupportedFormat -> {
            throw IllegalStateException(
                "Unsupported credential format '${format.value}'. " +
                "Supported formats: ${supportedFormats.joinToString { it.value }}"
            )
        }
        is IssuanceResult.Failure.AdapterNotReady -> {
            throw IllegalStateException(
                "Credential service adapter not ready: ${reason ?: "Unknown reason"}"
            )
        }
        is IssuanceResult.Failure.InvalidRequest -> {
            throw IllegalStateException(
                "Invalid issuance request: field '${field}' - ${reason}"
            )
        }
        is IssuanceResult.Failure.AdapterError -> {
            throw IllegalStateException(
                "Credential service error: ${reason}"
            )
        }
        is IssuanceResult.Failure.MultipleFailures -> {
            throw IllegalStateException(
                "Credential issuance failed with multiple errors: ${errors.joinToString("; ")}"
            )
        }
    }
}

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
        is VerificationResult.Invalid.IssuerResolutionFailed -> {
            throw IllegalStateException(
                "Failed to resolve issuer DID ${issuer.value}: ${reason}. " +
                "Errors: ${errors.joinToString("; ")}"
            )
        }
        is VerificationResult.Invalid.UntrustedIssuer -> {
            throw IllegalStateException(
                "Issuer ${issuer.value} is not trusted${credentialType?.let { " for credential type '$it'" } ?: ""}. " +
                "Errors: ${errors.joinToString("; ")}"
            )
        }
        is VerificationResult.Invalid.SchemaValidationFailed -> {
            throw IllegalStateException(
                "Schema validation failed: ${reason}. " +
                "Errors: ${errors.joinToString("; ")}"
            )
        }
        is VerificationResult.Invalid.MultipleFailures -> {
            throw IllegalStateException(
                "Credential verification failed with multiple errors: ${errors.joinToString("; ")}"
            )
        }
        is VerificationResult.Invalid.Other -> {
            throw IllegalStateException(
                "Credential verification failed: ${reason}. " +
                "Errors: ${errors.joinToString("; ")}"
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
            throw IllegalStateException(
                "DID method '${method}' is not registered. " +
                "Available methods: ${availableMethods.joinToString()}"
            )
        }
        is DidCreationResult.Failure.KeyGenerationFailed -> {
            throw IllegalStateException("Key generation failed: ${reason}")
        }
        is DidCreationResult.Failure.DocumentCreationFailed -> {
            throw IllegalStateException("DID document creation failed: ${reason}")
        }
        is DidCreationResult.Failure.InvalidConfiguration -> {
            val detailsStr = if (details.isEmpty()) "" else " Details: ${details}"
            throw IllegalStateException("Invalid DID configuration: ${reason}.${detailsStr}")
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
                "Wallet factory not configured: ${reason}"
            )
        }
        is WalletCreationResult.Failure.StorageFailed -> {
            throw IllegalStateException(
                "Wallet storage failed: ${reason}", cause
            )
        }
        is WalletCreationResult.Failure.Other -> {
            throw IllegalStateException(
                "Wallet creation failed: ${reason}", cause
            )
        }
    }
}

