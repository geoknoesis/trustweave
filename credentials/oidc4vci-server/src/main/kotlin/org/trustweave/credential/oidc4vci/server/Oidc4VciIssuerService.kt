package org.trustweave.credential.oidc4vci.server

import kotlinx.serialization.json.*
import org.trustweave.credential.oidc4vci.Oidc4VciService
import org.trustweave.credential.oidc4vci.models.*
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class OfferState(
    val credentialTypes: List<String>,
    val txCode: TxCode?,
    val txCodeValue: String?,
)

data class TokenEntry(val offerState: OfferState, val issuedAt: Long = System.currentTimeMillis())

class Oidc4VciIssuerService(
    val baseUrl: String,
    val issuerDid: String,
    val supportedConfigurations: Map<String, CredentialConfiguration> = emptyMap(),
) {
    private val pendingOffers = ConcurrentHashMap<String, OfferState>()
    private val activeTokens = ConcurrentHashMap<String, TokenEntry>()
    private val deferredCredentials = ConcurrentHashMap<String, String>() // transactionId -> credentialJson

    fun getMetadata(): CredentialIssuerMetadata = CredentialIssuerMetadata(
        credentialIssuer = baseUrl,
        credentialEndpoint = "$baseUrl/credential",
        tokenEndpoint = "$baseUrl/token",
        deferredCredentialEndpoint = "$baseUrl/deferred_credential",
        notificationEndpoint = "$baseUrl/notification",
        credentialConfigurationsSupported = supportedConfigurations,
    )

    fun createOffer(credentialTypes: List<String>, txCode: TxCode? = null, txCodeValue: String? = null): CreateOfferResponse {
        val preAuthCode = UUID.randomUUID().toString()
        pendingOffers[preAuthCode] = OfferState(credentialTypes, txCode, txCodeValue)
        return CreateOfferResponse(buildCredentialOfferUri(credentialTypes, preAuthCode, txCode), preAuthCode)
    }

    /**
     * Builds a spec-format credential offer URI (OID4VCI v1.0 §4.1): a single
     * `credential_offer` query parameter carrying the URL-encoded offer JSON, with the
     * pre-authorized code grant (and `tx_code` requirement, §4.1.1) embedded in `grants`.
     *
     * Mirrors the wallet-side parser/builder in
     * [org.trustweave.credential.oidc4vci.Oidc4VciService].
     */
    private fun buildCredentialOfferUri(
        credentialTypes: List<String>,
        preAuthCode: String,
        txCode: TxCode?,
    ): String {
        val preAuthGrant = buildJsonObject {
            put("pre-authorized_code", preAuthCode)
            if (txCode != null) {
                // encodeDefaults so the defaulted input_mode ("numeric") is still emitted
                // in the offer; explicitNulls=false drops absent length/description.
                val json = Json {
                    encodeDefaults = true
                    explicitNulls = false
                }
                put("tx_code", json.encodeToJsonElement(TxCode.serializer(), txCode))
            }
        }
        val offerJson = buildJsonObject {
            put("credential_issuer", baseUrl)
            put("credential_configuration_ids", JsonArray(credentialTypes.map { JsonPrimitive(it) }))
            put("grants", buildJsonObject {
                put(Oidc4VciService.PRE_AUTHORIZED_CODE_GRANT_TYPE, preAuthGrant)
            })
        }
        val encodedOffer = URLEncoder.encode(
            Json.encodeToString(JsonObject.serializer(), offerJson),
            "UTF-8"
        )
        return "openid-credential-offer://?credential_offer=$encodedOffer"
    }

    fun exchangePreAuthCode(preAuthCode: String, txCodeValue: String?): TokenResponse {
        val offerState = pendingOffers.remove(preAuthCode)
            ?: throw IllegalArgumentException("Unknown or expired pre-authorized_code")
        if (offerState.txCode != null) {
            require(txCodeValue == offerState.txCodeValue) { "Invalid tx_code" }
        }
        val accessToken = UUID.randomUUID().toString()
        activeTokens[accessToken] = TokenEntry(offerState)
        return TokenResponse(accessToken = accessToken)
    }

    fun issueCredential(accessToken: String, format: String, credentialTypes: List<String>): CredentialServerResponse {
        val entry = activeTokens[accessToken]
            ?: throw SecurityException("Invalid or expired access_token")
        val credentialJson = buildMinimalCredential(entry.offerState.credentialTypes.ifEmpty { credentialTypes }, format)
        return CredentialServerResponse(credential = credentialJson, format = format)
    }

    fun getDeferredCredential(transactionId: String, accessToken: String): CredentialServerResponse? {
        activeTokens[accessToken] ?: return null
        val cred = deferredCredentials.remove(transactionId) ?: return null
        return CredentialServerResponse(credential = cred)
    }

    fun recordNotification(notification: Oidc4VciNotification) {
        // no-op — extend to persist/emit events
    }

    private fun buildMinimalCredential(types: List<String>, format: String): String =
        """{"@context":["https://www.w3.org/2018/credentials/v1"],"type":${types.map { "\"$it\"" }},"issuer":"$issuerDid","issuanceDate":"${java.time.Instant.now()}","credentialSubject":{}}"""
            .replace("\\[", "[").replace("\\]", "]")
}

data class CreateOfferResponse(val offerUri: String, val preAuthCode: String)
data class TokenResponse(val accessToken: String, val tokenType: String = "Bearer", val expiresIn: Int = 3600)
data class CredentialServerResponse(val credential: String? = null, val transactionId: String? = null, val format: String? = null)
