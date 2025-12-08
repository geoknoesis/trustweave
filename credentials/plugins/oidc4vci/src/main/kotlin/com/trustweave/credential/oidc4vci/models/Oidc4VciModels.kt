package com.trustweave.credential.oidc4vci.models

import com.trustweave.credential.model.vc.VerifiableCredential
import kotlinx.serialization.Serializable

/**
 * OIDC4VCI credential offer.
 */
data class Oidc4VciOffer(
    val offerId: String,
    val credentialIssuer: String,
    val credentialTypes: List<String>,
    val offerUri: String,
    val grants: Map<String, Any?> = emptyMap()
)

/**
 * OIDC4VCI credential request.
 */
data class Oidc4VciCredentialRequest(
    val requestId: String,
    val holderDid: String,
    val offerId: String,
    val credentialIssuer: String,
    val credentialTypes: List<String>,
    val redirectUri: String? = null,
    val accessToken: String? = null
)

/**
 * OIDC4VCI credential issue result.
 */
data class Oidc4VciIssueResult(
    val issueId: String,
    val credential: VerifiableCredential,
    val credentialResponse: Map<String, Any?> // Contains credential, format, c_nonce, etc.
)

/**
 * Credential issuer metadata.
 *
 * Retrieved from /.well-known/openid-credential-issuer
 */
@Serializable
data class CredentialIssuerMetadata(
    val credentialIssuer: String,
    val authorizationServer: String? = null,
    val credentialEndpoint: String,
    val tokenEndpoint: String,
    val credentialConfigurationsSupported: Map<String, CredentialConfiguration> = emptyMap(),
    val display: List<Display>? = null
)

/**
 * Credential configuration.
 */
@Serializable
data class CredentialConfiguration(
    val format: String, // e.g., "vc+sd-jwt", "jwt_vc_json", "ldp_vc"
    val scope: String? = null,
    val cryptographicBindingMethodsSupported: List<String>? = null,
    val cryptographicSuitesSupported: List<String>? = null,
    val display: List<Display>? = null
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

