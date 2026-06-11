package org.trustweave.credential.oidc4vp.models

import org.trustweave.credential.model.vc.VerifiableCredential
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

// ---------------------------------------------------------------------------
// DCQL (Digital Credentials Query Language) — OID4VP v1.0 §6.4
// https://openid.net/specs/openid-4-verifiable-presentations-1_0.html#section-6.4
// ---------------------------------------------------------------------------

/**
 * Top-level DCQL query. Contains a list of credential queries and optional
 * credential-set constraints expressing which combinations are acceptable.
 */
@Serializable
data class DcqlQuery(
    val credentials: List<DcqlCredentialQuery> = emptyList(),
    @SerialName("credential_sets")
    val credentialSets: List<DcqlCredentialSetQuery>? = null,
)

/** Query for a single credential within a [DcqlQuery]. */
@Serializable
data class DcqlCredentialQuery(
    /** Unique identifier for this credential query within the [DcqlQuery]. */
    val id: String,
    /** Credential format (e.g. `"dc+sd-jwt"`, `"mso_mdoc"`, `"jwt_vc_json"`). */
    val format: String,
    /** Format-specific metadata constraints. */
    val meta: JsonObject? = null,
    /** Claims to request from the credential. */
    val claims: List<DcqlClaimsQuery>? = null,
    @SerialName("trusted_authorities")
    val trustedAuthorities: List<JsonObject>? = null,
    @SerialName("require_cryptographic_holder_binding")
    val requireCryptographicHolderBinding: Boolean? = null,
)

/** Constraint on a single claim path within a [DcqlCredentialQuery]. */
@Serializable
data class DcqlClaimsQuery(
    /**
     * JSONPath-style path to the claim. Each element is a string (object key)
     * or integer (array index), encoded as [JsonElement] for flexibility across formats.
     */
    val path: List<JsonElement>? = null,
    /** Optional stable identifier for referencing this claim in a [DcqlCredentialSetQuery]. */
    val id: String? = null,
    /** Acceptable values for the claim (exact match). Null means any value is accepted. */
    val values: List<JsonElement>? = null,
)

/**
 * Expresses which combinations of credentials from the [DcqlQuery.credentials] list
 * are acceptable to satisfy the verifier's request.
 */
@Serializable
data class DcqlCredentialSetQuery(
    /**
     * Each inner list is a set of credential IDs (referencing [DcqlCredentialQuery.id])
     * that satisfies this requirement when all IDs in that set are presented.
     * At least one option must be satisfied.
     */
    val options: List<List<String>>,
    /** When `true` (default), this credential set is required. */
    val required: Boolean = true,
    /** Human-readable purpose for this set. */
    val purpose: JsonElement? = null,
)

/**
 * Client ID scheme for OID4VP v1.0 §5.7.
 */
enum class ClientIdScheme {
    PRE_REGISTERED,
    REDIRECT_URI,
    ENTITY_ID,
    DID,
    X509_SAN_DNS,
    X509_SAN_URI,
    VERIFIER_ATTESTATION,
    ;

    companion object {
        fun fromString(value: String): ClientIdScheme = when (value.lowercase()) {
            "pre-registered" -> PRE_REGISTERED
            "redirect_uri" -> REDIRECT_URI
            "entity_id" -> ENTITY_ID
            "did" -> DID
            "x509_san_dns" -> X509_SAN_DNS
            "x509_san_uri" -> X509_SAN_URI
            "verifier_attestation" -> VERIFIER_ATTESTATION
            else -> PRE_REGISTERED
        }
    }
}

/**
 * OIDC4VP authorization request (from verifier).
 *
 * Supports both redirect_uri (legacy) and response_uri (v1.0 direct_post).
 */
data class AuthorizationRequest(
    val responseUri: String? = null,
    val redirectUri: String? = null,
    val clientId: String? = null,
    val clientIdScheme: ClientIdScheme = ClientIdScheme.PRE_REGISTERED,
    val requestUri: String? = null,
    val presentationDefinition: JsonObject? = null,
    val nonce: String? = null,
    val state: String? = null,
    val responseMode: String? = null,
    val dcqlQuery: DcqlQuery? = null,
    /** Verifier metadata supplied in the request (`client_metadata`, OID4VP v1.0 §5.1). */
    val clientMetadata: JsonObject? = null,
) {
    /** The effective response endpoint: response_uri for direct_post, redirect_uri otherwise. */
    val effectiveResponseEndpoint: String?
        get() = responseUri ?: redirectUri

    /**
     * The audience for the holder's VP token: the verifier's `client_id` when present,
     * otherwise the response endpoint (`response_uri`/`redirect_uri`).
     */
    val audience: String?
        get() = clientId ?: effectiveResponseEndpoint
}

/**
 * OIDC4VP permission request (holder-side representation).
 */
data class PermissionRequest(
    val requestId: String,
    val authorizationRequest: AuthorizationRequest,
    val verifierUrl: String? = null,
    val requestedCredentialTypes: List<String> = emptyList(),
    val requestedClaims: Map<String, List<String>> = emptyMap(),
)

/**
 * Presentable credential (wrapper for credential selection).
 */
data class PresentableCredential(
    val credentialId: String,
    val credential: VerifiableCredential,
    val credentialType: String,
)

/**
 * OIDC4VP permission response (holder's response to permission request).
 */
data class PermissionResponse(
    val responseId: String,
    val requestId: String,
    val vpToken: String,
    val presentationSubmission: JsonObject? = null,
    val state: String? = null,
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
    val display: List<Display>? = null,
)

/** VP format support information. */
@Serializable
data class VpFormatSupported(
    val algValuesSupported: List<String>? = null,
    val proofTypesSupported: List<String>? = null,
)

/** Display information. */
@Serializable
data class Display(
    val name: String,
    val locale: String? = null,
    val logo: String? = null,
    val description: String? = null,
    val backgroundColor: String? = null,
    val textColor: String? = null,
)
