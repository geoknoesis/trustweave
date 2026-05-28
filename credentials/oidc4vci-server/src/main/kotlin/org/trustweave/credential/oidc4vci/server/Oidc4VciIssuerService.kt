package org.trustweave.credential.oidc4vci.server

import org.trustweave.credential.oidc4vci.models.*
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
        val offerUri = buildString {
            append("openid-credential-offer://?credential_issuer=")
            append(java.net.URLEncoder.encode(baseUrl, "UTF-8"))
            append("&grants=%7B%22urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Apre-authorized_code%22%3A%7B%22pre-authorized_code%22%3A%22")
            append(preAuthCode)
            append("%22%7D%7D")
        }
        return CreateOfferResponse(offerUri, preAuthCode)
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
