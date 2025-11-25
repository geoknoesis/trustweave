package com.trustweave.credential.exception

import com.trustweave.core.exception.TrustWeaveException

/**
 * Credential-related exception types.
 * 
 * These exceptions provide structured error codes and context for credential operations.
 */
sealed class CredentialException(
    override val code: String,
    override val message: String,
    override val context: Map<String, Any?> = emptyMap(),
    override val cause: Throwable? = null
) : TrustWeaveException(code, message, context, cause) {
    
    data class CredentialInvalid(
        val reason: String,
        val credentialId: String? = null,
        val field: String? = null
    ) : CredentialException(
        code = "CREDENTIAL_INVALID",
        message = credentialId?.let { "Credential '$it' is invalid: $reason" } 
            ?: "Credential is invalid: $reason",
        context = mapOf(
            "reason" to reason,
            "credentialId" to credentialId,
            "field" to field
        ).filterValues { it != null }
    )
    
    data class CredentialIssuanceFailed(
        val reason: String,
        val issuerDid: String? = null
    ) : CredentialException(
        code = "CREDENTIAL_ISSUANCE_FAILED",
        message = "Credential issuance failed: $reason",
        context = mapOf(
            "reason" to reason,
            "issuerDid" to issuerDid
        ).filterValues { it != null }
    )
}

