package org.trustweave.integrations.servicenow

import org.trustweave.core.exception.TrustWeaveException

/**
 * ServiceNow integration for trustweave.
 *
 * Provides integration with ServiceNow for:
 * - Verifiable Credential issuance and verification
 * - DID management within ServiceNow
 * - Credential storage in ServiceNow tables
 *
 * **Note:** This is a placeholder implementation. Full implementation requires
 * ServiceNow REST API integration and table schema design.
 *
 * **Example:**
 * ```kotlin
 * val integration = ServiceNowIntegration(
 *     instanceUrl = "https://instance.service-now.com",
 *     username = "admin",
 *     password = "password"
 * )
 *
 * // Issue credential to ServiceNow record
 * val credential = integration.issueCredential(
 *     tableName = "sys_user",
 *     recordId = "user-123",
 *     credentialType = "EmployeeCredential"
 * )
 * ```
 *
 * ## Status: stub
 *
 * This module is marked `stub` in `trustweave-capabilities.json` and is deliberately **not**
 * exported by `distribution:bom`. Every method below throws; there is no ServiceNow client
 * here, only the shape one would take. Depend on this module directly if the skeleton is
 * useful; do not expect it to do anything.
 */
@Deprecated(
    message =
        "ServiceNow integration is a stub: every operation throws TrustWeaveException.InvalidOperation " +
            "(code SERVICENOW_INTEGRATION_NOT_IMPLEMENTED). It is not SPI-registered and not exported by the BOM.",
    level = DeprecationLevel.WARNING,
)
class ServiceNowIntegration(
    val instanceUrl: String,
    val username: String,
    val password: String,
) {
    init {
        require(instanceUrl.isNotBlank()) { "ServiceNow instance URL must be specified" }
        require(username.isNotBlank()) { "ServiceNow username must be specified" }
        require(password.isNotBlank()) { "ServiceNow password must be specified" }
    }

    /**
     * Issues a verifiable credential to a ServiceNow record.
     *
     * @param tableName ServiceNow table name (e.g., "sys_user")
     * @param recordId ServiceNow record sys_id
     * @param credentialType Type of credential to issue
     * @return never returns
     * @throws TrustWeaveException.InvalidOperation always — the integration is not implemented.
     */
    suspend fun issueCredential(
        tableName: String,
        recordId: String,
        credentialType: String,
    ): Nothing = throw notImplemented("issueCredential")

    /**
     * Verifies a verifiable credential from ServiceNow.
     *
     * @param credentialId ServiceNow credential record ID
     * @return never returns
     * @throws TrustWeaveException.InvalidOperation always — the integration is not implemented.
     */
    suspend fun verifyCredential(credentialId: String): Nothing = throw notImplemented("verifyCredential")

    private fun notImplemented(operation: String): TrustWeaveException =
        TrustWeaveException.InvalidOperation(
            code = "SERVICENOW_INTEGRATION_NOT_IMPLEMENTED",
            message = "ServiceNow integration is not implemented: $operation has no ServiceNow REST API client behind it",
            context = mapOf("integration" to "servicenow", "operation" to operation),
        )
}
