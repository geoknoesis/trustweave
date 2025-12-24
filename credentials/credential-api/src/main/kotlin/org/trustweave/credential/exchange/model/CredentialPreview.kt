package org.trustweave.credential.exchange.model

import org.trustweave.credential.exchange.options.ExchangeOptions

/**
 * Credential preview for offer messages.
 * 
 * Protocol-agnostic preview of credential attributes.
 * Protocol-specific metadata (goalCode, replacementId, etc.) goes in options.metadata.
 */
data class CredentialPreview(
    val attributes: List<CredentialAttribute>,
    val options: ExchangeOptions = ExchangeOptions.Empty  // Protocol-specific data here (goalCode, replacementId, etc.)
)

/**
 * Credential attribute in preview.
 */
data class CredentialAttribute(
    val name: String,
    val value: String,
    val mimeType: String? = null
)

