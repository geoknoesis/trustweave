package org.trustweave.credential.siop.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import org.trustweave.credential.pex.PresentationDefinition
import org.trustweave.credential.pex.PresentationSubmission

@Serializable
enum class SiopClientIdScheme {
    @SerialName("pre-registered") PRE_REGISTERED,
    @SerialName("redirect_uri") REDIRECT_URI,
    @SerialName("entity_id") ENTITY_ID,
    @SerialName("did") DID,
}

@Serializable
enum class SiopResponseMode {
    @SerialName("direct_post") DIRECT_POST,
    @SerialName("direct_post.jwt") DIRECT_POST_JWT,
    @SerialName("fragment") FRAGMENT,
    @SerialName("form_post") FORM_POST,
}

@Serializable
data class SiopV2AuthorizationRequest(
    @SerialName("response_type") val responseType: String,
    @SerialName("client_id") val clientId: String,
    @SerialName("client_id_scheme") val clientIdScheme: SiopClientIdScheme = SiopClientIdScheme.DID,
    val scope: String = "openid",
    @SerialName("redirect_uri") val redirectUri: String? = null,
    @SerialName("response_uri") val responseUri: String? = null,
    @SerialName("response_mode") val responseMode: SiopResponseMode = SiopResponseMode.DIRECT_POST,
    val nonce: String,
    val state: String? = null,
    @SerialName("presentation_definition") val presentationDefinition: PresentationDefinition? = null,
    @SerialName("presentation_definition_uri") val presentationDefinitionUri: String? = null,
    @SerialName("id_token_type") val idTokenType: List<String>? = null,
    @SerialName("client_metadata") val clientMetadata: JsonObject? = null,
    @SerialName("request_uri") val requestUri: String? = null,
)

@Serializable
data class SiopV2AuthorizationResponse(
    @SerialName("id_token") val idToken: String? = null,
    @SerialName("vp_token") val vpToken: String? = null,
    @SerialName("presentation_submission") val presentationSubmission: PresentationSubmission? = null,
    val state: String? = null,
    val error: String? = null,
    @SerialName("error_description") val errorDescription: String? = null,
)

data class SiopV2Session(
    val sessionId: String,
    val request: SiopV2AuthorizationRequest,
    val holderDid: String? = null,
)
