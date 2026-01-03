package org.trustweave.testkit

import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.results.IssuanceResult
import org.trustweave.did.identifiers.Did
import org.trustweave.trust.types.DidCreationResult
import org.trustweave.trust.types.WalletCreationResult
import org.trustweave.wallet.Wallet
import kotlin.Result

/**
 * Extension functions for Result types to extract success values or throw exceptions.
 * These are test utility functions that simplify test code by throwing exceptions
 * on failure rather than requiring exhaustive when expressions.
 */

/**
 * Extract the DID from a successful DidCreationResult, or throw an exception.
 */
fun DidCreationResult.getOrFail(): Did {
    return when (this) {
        is DidCreationResult.Success -> this.did
        is DidCreationResult.Failure.MethodNotRegistered -> {
            throw IllegalStateException("DID method '${this.method}' not registered. Available: ${this.availableMethods.joinToString()}")
        }
        is DidCreationResult.Failure.KeyGenerationFailed -> {
            throw IllegalStateException("Key generation failed: ${this.reason}")
        }
        is DidCreationResult.Failure.DocumentCreationFailed -> {
            throw IllegalStateException("Document creation failed: ${this.reason}")
        }
        is DidCreationResult.Failure.InvalidConfiguration -> {
            throw IllegalStateException("Invalid configuration: ${this.reason}")
        }
        is DidCreationResult.Failure.Other -> {
            throw IllegalStateException("DID creation failed: ${this.reason}", this.cause)
        }
    }
}

/**
 * Extract the credential from a successful IssuanceResult, or throw an exception.
 */
fun IssuanceResult.getOrFail(): VerifiableCredential {
    return when (this) {
        is IssuanceResult.Success -> this.credential
        is IssuanceResult.Failure.UnsupportedFormat -> {
            throw IllegalStateException("Unsupported format '${this.format.value}'. Supported: ${this.supportedFormats.joinToString { it.value }}")
        }
        is IssuanceResult.Failure.AdapterNotReady -> {
            throw IllegalStateException("Adapter not ready: ${this.reason ?: "Unknown reason"}")
        }
        is IssuanceResult.Failure.InvalidRequest -> {
            throw IllegalStateException("Invalid request: field '${this.field}' - ${this.reason}")
        }
        is IssuanceResult.Failure.AdapterError -> {
            throw IllegalStateException("Adapter error: ${this.reason}")
        }
        is IssuanceResult.Failure.MultipleFailures -> {
            throw IllegalStateException("Multiple failures: ${this.errors.joinToString("; ")}")
        }
    }
}

/**
 * Extract the wallet from a successful WalletCreationResult, or throw an exception.
 */
fun WalletCreationResult.getOrFail(): Wallet {
    return when (this) {
        is WalletCreationResult.Success -> this.wallet
        is WalletCreationResult.Failure.InvalidHolderDid -> {
            throw IllegalStateException("Invalid holder DID '${this.holderDid}': ${this.reason}")
        }
        is WalletCreationResult.Failure.FactoryNotConfigured -> {
            throw IllegalStateException("Wallet factory not configured: ${this.reason}")
        }
        is WalletCreationResult.Failure.StorageFailed -> {
            throw IllegalStateException("Storage failed: ${this.reason}", this.cause)
        }
        is WalletCreationResult.Failure.Other -> {
            throw IllegalStateException("Wallet creation failed: ${this.reason}", this.cause)
        }
    }
}

/**
 * Extract the value from a successful Result, or throw an exception.
 */
fun <T> Result<T>.getOrFail(): T {
    return getOrElse { throw it }
}

