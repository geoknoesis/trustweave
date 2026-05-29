package org.trustweave.integrations.entra

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

// ============================================================================
// Token endpoint
// ============================================================================

/**
 * Response from `POST https://login.microsoftonline.com/{tenantId}/oauth2/v2.0/token`
 * for the `client_credentials` grant. Fields documented in MS identity platform docs.
 */
@Serializable
data class EntraTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String = "Bearer",
    @SerialName("expires_in") val expiresInSeconds: Long = 3600,
    @SerialName("ext_expires_in") val extExpiresInSeconds: Long? = null,
)

/**
 * Error response from the token endpoint, e.g.
 * `{"error":"invalid_client","error_description":"..."}`.
 */
@Serializable
data class EntraTokenError(
    val error: String,
    @SerialName("error_description") val errorDescription: String? = null,
    @SerialName("error_codes") val errorCodes: List<Long>? = null,
    @SerialName("correlation_id") val correlationId: String? = null,
)

// ============================================================================
// Issuance request
// ============================================================================

/**
 * Request body for `POST /v1.0/verifiableCredentials/createIssuanceRequest`.
 *
 * Mirrors the Verified ID Request Service contract. Fields that the caller usually
 * does not need are nullable so the kotlinx-serialization encoder omits them.
 */
@Serializable
data class EntraIssuanceRequestBody(
    val authority: String,
    val callback: EntraCallback,
    val registration: EntraRegistration,
    val type: String,
    val manifest: String,
    val claims: Map<String, String>? = null,
    val pin: EntraPin? = null,
    val expirationDate: String? = null,
    val includeQRCode: Boolean = true,
    @SerialName("includeReceipt") val includeReceipt: Boolean = false,
)

@Serializable
data class EntraRegistration(
    val clientName: String,
    val purpose: String? = null,
    val logoUrl: String? = null,
    val termsOfServiceUrl: String? = null,
)

@Serializable
data class EntraCallback(
    val url: String,
    val state: String,
    val headers: Map<String, String>? = null,
)

@Serializable
data class EntraPin(
    val value: String,
    val length: Int = 4,
    val type: String = "numeric",
)

/**
 * Response from `createIssuanceRequest`.
 */
@Serializable
data class EntraIssuanceRequestResponse(
    val requestId: String,
    val url: String,
    val expiry: Long,
    val qrCode: String? = null,
)

// ============================================================================
// Presentation request
// ============================================================================

@Serializable
data class EntraPresentationRequestBody(
    val authority: String,
    val callback: EntraCallback,
    val registration: EntraRegistration,
    val includeQRCode: Boolean = true,
    val includeReceipt: Boolean = false,
    val requestedCredentials: List<EntraRequestedCredential>,
)

@Serializable
data class EntraRequestedCredential(
    val type: String,
    val purpose: String? = null,
    val acceptedIssuers: List<String>? = null,
    val configuration: EntraRequestedCredentialConfiguration? = null,
)

@Serializable
data class EntraRequestedCredentialConfiguration(
    val validation: EntraValidationConfig? = null,
)

@Serializable
data class EntraValidationConfig(
    val allowRevoked: Boolean = false,
    val validateLinkedDomain: Boolean = true,
)

@Serializable
data class EntraPresentationRequestResponse(
    val requestId: String,
    val url: String,
    val expiry: Long,
    val qrCode: String? = null,
)

// ============================================================================
// Callback payloads (delivered by Entra Verified ID to caller's webhook)
// ============================================================================

/**
 * Callback payload delivered to the caller's webhook for both issuance and presentation
 * flows. The `requestStatus` field discriminates the lifecycle event; the `verifiedCredentialsData`
 * field is populated only on successful presentation.
 */
@Serializable
data class EntraCallbackPayload(
    val requestId: String,
    val requestStatus: String,
    val state: String? = null,
    val subject: String? = null,
    val verifiedCredentialsData: List<EntraVerifiedCredentialData>? = null,
    val receipt: JsonObject? = null,
    val error: EntraCallbackError? = null,
)

@Serializable
data class EntraCallbackError(
    val code: String,
    val message: String,
)

@Serializable
data class EntraVerifiedCredentialData(
    val issuer: String,
    val type: List<String>,
    val claims: Map<String, JsonElement>,
    val credentialState: EntraCredentialState? = null,
    val domainValidation: EntraDomainValidation? = null,
    val expirationDate: String? = null,
    val issuanceDate: String? = null,
)

@Serializable
data class EntraCredentialState(
    val revocationStatus: String,
    val reason: String? = null,
)

@Serializable
data class EntraDomainValidation(
    val url: String,
)

/**
 * Discriminator values for [EntraCallbackPayload.requestStatus].
 */
object EntraRequestStatus {
    const val REQUEST_RETRIEVED = "request_retrieved"
    const val ISSUANCE_SUCCESSFUL = "issuance_successful"
    const val ISSUANCE_ERROR = "issuance_error"
    const val PRESENTATION_VERIFIED = "presentation_verified"
    const val PRESENTATION_ERROR = "presentation_error"
}
