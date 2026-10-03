package org.trustweave.integrations.salesforce

import org.trustweave.core.exception.TrustWeaveException

/**
 * Salesforce integration for trustweave.
 *
 * Provides integration with Salesforce for:
 * - Verifiable Credential issuance and verification
 * - DID management within Salesforce
 * - Credential storage in Salesforce objects
 * - Integration with Salesforce Shield Platform Encryption
 *
 * **Note:** This is a placeholder implementation. Full implementation requires
 * Salesforce REST API integration and custom object schema design.
 *
 * **Example:**
 * ```kotlin
 * val integration = SalesforceIntegration(
 *     instanceUrl = "https://instance.salesforce.com",
 *     clientId = "client-id",
 *     clientSecret = "client-secret",
 *     username = "user@example.com",
 *     password = "password"
 * )
 *
 * // Issue credential to Salesforce record
 * val credential = integration.issueCredential(
 *     objectName = "Contact",
 *     recordId = "003...",
 *     credentialType = "IdentityCredential"
 * )
 * ```
 *
 * ## Status: stub
 *
 * This module is marked `stub` in `trustweave-capabilities.json` and is deliberately **not**
 * exported by `distribution:bom`. Every method below throws; there is no Salesforce client
 * here, only the shape one would take. Depend on this module directly if the skeleton is
 * useful; do not expect it to do anything.
 */
@Deprecated(
    message =
        "Salesforce integration is a stub: every operation throws TrustWeaveException.InvalidOperation " +
            "(code SALESFORCE_INTEGRATION_NOT_IMPLEMENTED). It is not SPI-registered and not exported by the BOM.",
    level = DeprecationLevel.WARNING,
)
class SalesforceIntegration(
    val instanceUrl: String,
    val clientId: String,
    val clientSecret: String,
    val username: String,
    val password: String,
) {
    init {
        require(instanceUrl.isNotBlank()) { "Salesforce instance URL must be specified" }
        require(clientId.isNotBlank()) { "Salesforce client ID must be specified" }
        require(clientSecret.isNotBlank()) { "Salesforce client secret must be specified" }
        require(username.isNotBlank()) { "Salesforce username must be specified" }
        require(password.isNotBlank()) { "Salesforce password must be specified" }
    }

    /**
     * Issues a verifiable credential to a Salesforce record.
     *
     * @param objectName Salesforce object name (e.g., "Contact", "Account")
     * @param recordId Salesforce record ID
     * @param credentialType Type of credential to issue
     * @return never returns
     * @throws TrustWeaveException.InvalidOperation always — the integration is not implemented.
     */
    suspend fun issueCredential(
        objectName: String,
        recordId: String,
        credentialType: String,
    ): Nothing = throw notImplemented("issueCredential")

    /**
     * Verifies a verifiable credential from Salesforce.
     *
     * @param credentialId Salesforce credential record ID
     * @return never returns
     * @throws TrustWeaveException.InvalidOperation always — the integration is not implemented.
     */
    suspend fun verifyCredential(credentialId: String): Nothing = throw notImplemented("verifyCredential")

    private fun notImplemented(operation: String): TrustWeaveException =
        TrustWeaveException.InvalidOperation(
            code = "SALESFORCE_INTEGRATION_NOT_IMPLEMENTED",
            message = "Salesforce integration is not implemented: $operation has no Salesforce REST API client behind it",
            context = mapOf("integration" to "salesforce", "operation" to operation),
        )
}
