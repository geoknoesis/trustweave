package org.trustweave.credential.oidc4vci.models

import org.trustweave.credential.model.vc.VerifiableCredential
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * OIDC4VCI credential offer.
 */
data class Oidc4VciOffer(
    val offerId: String,
    val credentialIssuer: String,
    val credentialTypes: List<String>,
    val offerUri: String,
    val grants: Map<String, Any?> = emptyMap(),
    val txCode: TxCode? = null,
)

/**
 * Transaction code (PIN) for pre-authorized code grant.
 *
 * OID4VCI v1.0 §4.1.1 — `tx_code` object in credential offer.
 */
@Serializable
data class TxCode(
    @SerialName("input_mode")
    val inputMode: String = "numeric",
    val length: Int? = null,
    val description: String? = null,
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
    val accessToken: String? = null,
    val txCodeValue: String? = null,
)

/**
 * OIDC4VCI credential issue result.
 *
 * When `transactionId` is non-null the credential is deferred — call
 * [org.trustweave.credential.oidc4vci.Oidc4VciService.pollDeferredCredential] to retrieve it.
 */
data class Oidc4VciIssueResult(
    val issueId: String,
    val credential: VerifiableCredential?,
    val transactionId: String? = null,
    val credentialResponse: Map<String, Any?>,
)

/**
 * Deferred credential request — OID4VCI v1.0 §9.
 */
data class DeferredCredentialRequest(
    val transactionId: String,
    val accessToken: String,
)

/**
 * Notification event sent to the issuer's notification endpoint.
 *
 * OID4VCI v1.0 §10.
 */
@Serializable
data class Oidc4VciNotification(
    @SerialName("notification_id")
    val notificationId: String,
    val event: NotificationEvent,
    @SerialName("event_description")
    val eventDescription: String? = null,
)

/** Notification event type. */
@Serializable
enum class NotificationEvent {
    @SerialName("credential_accepted") CREDENTIAL_ACCEPTED,
    @SerialName("credential_failure") CREDENTIAL_FAILURE,
    @SerialName("credential_deleted") CREDENTIAL_DELETED,
}

/**
 * `authorization_details` entry for RFC 9396 fine-grained authorization.
 *
 * OID4VCI v1.0 §5.1.1.
 */
@Serializable
data class OpenIdCredentialAuthorizationDetail(
    val type: String = "openid_credential",
    val format: String? = null,
    @SerialName("credential_configuration_id")
    val credentialConfigurationId: String? = null,
    @SerialName("credential_definition")
    val credentialDefinition: CredentialDefinition? = null,
)

/** Credential definition within authorization_details. */
@Serializable
data class CredentialDefinition(
    val type: List<String> = emptyList(),
    @SerialName("credentialSubject")
    val credentialSubject: Map<String, String> = emptyMap(),
)

/**
 * Batch credential request — OID4VCI v1.0 §8.
 *
 * Allows a holder to request multiple credentials in a single round-trip.
 */
data class BatchCredentialRequest(
    val credentialRequests: List<Oidc4VciCredentialRequest>,
    val accessToken: String,
)

/**
 * A single item within a [BatchCredentialResponse].
 *
 * Either [credential] or [transactionId] is non-null; never both.
 */
data class BatchCredentialResponseItem(
    val credential: VerifiableCredential? = null,
    val transactionId: String? = null,
)

/**
 * Batch credential response — OID4VCI v1.0 §8.
 */
data class BatchCredentialResponse(
    val credentialResponses: List<BatchCredentialResponseItem>,
)

/**
 * Credential issuer metadata.
 *
 * Retrieved from /.well-known/openid-credential-issuer
 */
@Serializable
data class CredentialIssuerMetadata(
    @SerialName("credential_issuer")
    val credentialIssuer: String,
    @SerialName("authorization_server")
    val authorizationServer: String? = null,
    @SerialName("credential_endpoint")
    val credentialEndpoint: String,
    @SerialName("token_endpoint")
    val tokenEndpoint: String,
    @SerialName("batch_credential_endpoint")
    val batchCredentialEndpoint: String? = null,
    @SerialName("deferred_credential_endpoint")
    val deferredCredentialEndpoint: String? = null,
    @SerialName("notification_endpoint")
    val notificationEndpoint: String? = null,
    @SerialName("credential_configurations_supported")
    val credentialConfigurationsSupported: Map<String, CredentialConfiguration> = emptyMap(),
    val display: List<Display>? = null,
)

/**
 * Credential configuration.
 */
@Serializable
data class CredentialConfiguration(
    val format: String,
    val scope: String? = null,
    @SerialName("cryptographic_binding_methods_supported")
    val cryptographicBindingMethodsSupported: List<String>? = null,
    @SerialName("cryptographic_suites_supported")
    val cryptographicSuitesSupported: List<String>? = null,
    @SerialName("proof_types_supported")
    val proofTypesSupported: Map<String, ProofTypeMetadata>? = null,
    val display: List<Display>? = null,
)

/** Proof type metadata within a credential configuration. */
@Serializable
data class ProofTypeMetadata(
    @SerialName("proof_signing_alg_values_supported")
    val proofSigningAlgValuesSupported: List<String> = emptyList(),
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
    @SerialName("background_color")
    val backgroundColor: String? = null,
    @SerialName("text_color")
    val textColor: String? = null,
)
