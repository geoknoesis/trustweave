package com.geoknoesis.vericore.oidc4vci

import com.geoknoesis.vericore.credential.models.VerifiableCredential
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * OIDC4VCI (OpenID Connect for Verifiable Credential Issuance) service.
 * 
 * Implements the OIDC4VCI protocol for credential issuance and exchange.
 * 
 * **Example Usage:**
 * ```kotlin
 * val service = Oidc4VciService(issuerUrl = "https://issuer.example.com")
 * 
 * // Get issuer metadata
 * val metadata = service.getIssuerMetadata()
 * 
 * // Request credential
 * val credential = service.requestCredential(
 *     credentialOffer = offer,
 *     accessToken = token
 * )
 * ```
 */
interface Oidc4VciService {
    /**
     * Get issuer metadata.
     * 
     * @return Issuer metadata
     */
    suspend fun getIssuerMetadata(): IssuerMetadata
    
    /**
     * Request credential using OIDC4VCI flow.
     * 
     * @param credentialOffer Credential offer
     * @param accessToken Access token
     * @return Issued credential
     */
    suspend fun requestCredential(
        credentialOffer: CredentialOffer,
        accessToken: String
    ): VerifiableCredential
    
    /**
     * Create credential offer.
     * 
     * @param credentialTypes Credential types to offer
     * @param issuerDid Issuer DID
     * @return Credential offer
     */
    suspend fun createCredentialOffer(
        credentialTypes: List<String>,
        issuerDid: String
    ): CredentialOffer
}

/**
 * Issuer metadata (from /.well-known/openid-credential-issuer).
 */
@Serializable
data class IssuerMetadata(
    val credentialIssuer: String,
    val authorizationServer: String? = null,
    val credentialEndpoint: String,
    val tokenEndpoint: String? = null,
    val credentialTypesSupported: List<String> = emptyList(),
    val credentialFormatsSupported: Map<String, List<String>> = emptyMap()
)

/**
 * Credential offer.
 */
@Serializable
data class CredentialOffer(
    val credentialIssuer: String,
    val credentials: List<CredentialOfferItem>,
    val grants: Grants? = null
)

/**
 * Credential offer item.
 */
@Serializable
data class CredentialOfferItem(
    val format: String,
    val types: List<String>
)

/**
 * Grants.
 */
@Serializable
data class Grants(
    val authorizationCode: AuthorizationCodeGrant? = null,
    val preAuthorizedCode: PreAuthorizedCodeGrant? = null
)

/**
 * Authorization code grant.
 */
@Serializable
data class AuthorizationCodeGrant(
    val issuerState: String? = null
)

/**
 * Pre-authorized code grant.
 */
@Serializable
data class PreAuthorizedCodeGrant(
    val preAuthorizedCode: String,
    val userPinRequired: Boolean = false
)

/**
 * Simple OIDC4VCI service implementation.
 */
class SimpleOidc4VciService(
    private val issuerUrl: String,
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val json: Json = Json { prettyPrint = false; ignoreUnknownKeys = true }
) : Oidc4VciService {
    
    override suspend fun getIssuerMetadata(): IssuerMetadata = withContext(Dispatchers.IO) {
        val url = "$issuerUrl/.well-known/openid-credential-issuer"
        val request = Request.Builder().url(url).get().build()
        
        val response = httpClient.newCall(request).execute()
        val body = response.body?.string() ?: throw IllegalStateException("Empty response")
        
        json.decodeFromString(IssuerMetadata.serializer(), body)
    }
    
    override suspend fun requestCredential(
        credentialOffer: CredentialOffer,
        accessToken: String
    ): VerifiableCredential = withContext(Dispatchers.IO) {
        val metadata = getIssuerMetadata()
        val url = metadata.credentialEndpoint
        
        val credentialRequest = buildJsonObject {
            put("format", "ldp_vc")
            put("credential_definition", buildJsonObject {
                put("types", buildJsonArray {
                    credentialOffer.credentials.firstOrNull()?.types?.forEach { add(it) }
                })
            })
        }
        
        val body = json.encodeToString(credentialRequest).toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(body)
            .header("Authorization", "Bearer $accessToken")
            .header("Content-Type", "application/json")
            .build()
        
        val response = httpClient.newCall(request).execute()
        val responseBody = response.body?.string() ?: throw IllegalStateException("Empty response")
        
        val credentialResponse = json.decodeFromString<JsonObject>(responseBody)
        val credentialJson = credentialResponse["credential"] as? JsonObject
            ?: throw IllegalStateException("No credential in response")
        
        json.decodeFromJsonElement(VerifiableCredential.serializer(), credentialJson)
    }
    
    override suspend fun createCredentialOffer(
        credentialTypes: List<String>,
        issuerDid: String
    ): CredentialOffer = withContext(Dispatchers.IO) {
        CredentialOffer(
            credentialIssuer = issuerUrl,
            credentials = listOf(
                CredentialOfferItem(
                    format = "ldp_vc",
                    types = credentialTypes
                )
            ),
            grants = Grants(
                preAuthorizedCode = PreAuthorizedCodeGrant(
                    preAuthorizedCode = UUID.randomUUID().toString(),
                    userPinRequired = false
                )
            )
        )
    }
}

