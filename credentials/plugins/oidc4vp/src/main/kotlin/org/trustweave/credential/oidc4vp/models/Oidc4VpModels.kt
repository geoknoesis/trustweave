package org.trustweave.credential.oidc4vp.models

import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.model.vc.VerifiablePresentation
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

/**
 * OIDC4VP authorization request (from verifier).
 *
 * This represents a request for credential presentation from a verifier.
 * Typically fetched from a request_uri in the authorization URL.
 *
 * Note: presentationDefinition is stored as JsonObject (not @Serializable)
 * because it's parsed directly from JSON and doesn't need serialization.
 */
data class AuthorizationRequest(
    val responseUri: String,
    val clientId: String? = null,
    val requestUri: String? = null,
    val presentationDefinition: JsonObject? = null,
    val nonce: String? = null,
    val state: String? = null,
    val redirectUri: String? = null
)

/**
 * OIDC4VP permission request (holder-side representation).
 *
 * This is created from an authorization request and contains the information
 * needed for the holder to select credentials and create a permission response.
 */
data class PermissionRequest(
    val requestId: String,
    val authorizationRequest: AuthorizationRequest,
    val verifierUrl: String? = null,
    val requestedCredentialTypes: List<String> = emptyList(),
    val requestedClaims: Map<String, List<String>> = emptyMap() // claim name -> list of requested fields
)

/**
 * Presentable credential (wrapper for credential selection).
 *
 * Represents a credential that can be selected for presentation.
 */
data class PresentableCredential(
    val credentialId: String,
    val credential: VerifiableCredential,
    val credentialType: String
)

/**
 * OIDC4VP permission response (holder's response to permission request).
 *
 * Contains the selected credentials and fields that the holder wishes to share.
 */
data class PermissionResponse(
    val responseId: String,
    val requestId: String,
    val vpToken: String, // JWT containing the VerifiablePresentation
    val presentationSubmission: JsonObject? = null,
    val state: String? = null
)

/**
 * Verifier metadata.
 *
 * Retrieved from /.well-known/openid-credential-verifier
 */
@Serializable
data class VerifierMetadata(
    @SerialName("credential_verifier")
    val credentialVerifier: String,
    @SerialName("authorization_endpoint")
    val authorizationEndpoint: String? = null,
    @SerialName("token_endpoint")
    val tokenEndpoint: String? = null,
    @SerialName("response_types_supported")
    val responseTypesSupported: List<String> = emptyList(),
    @SerialName("scopes_supported")
    val scopesSupported: List<String> = emptyList(),
    @SerialName("vp_formats_supported")
    val vpFormatsSupported: Map<String, VpFormatSupported> = emptyMap(),
    val display: List<Display>? = null
)

/**
 * VP format support information.
 */
@Serializable
data class VpFormatSupported(
    val algValuesSupported: List<String>? = null,
    val proofTypesSupported: List<String>? = null
)

/**
 * Display information.
 */
@Serializable
data class Display(
    val name: String,
    val locale: String? = null,
    val logo: String? = null,
    val description: String? = null,
    val backgroundColor: String? = null,
    val textColor: String? = null
)

