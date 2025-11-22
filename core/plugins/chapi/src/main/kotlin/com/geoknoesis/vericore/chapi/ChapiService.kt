package com.geoknoesis.vericore.chapi

import com.geoknoesis.vericore.credential.models.VerifiableCredential
import com.geoknoesis.vericore.credential.models.VerifiablePresentation
import com.geoknoesis.vericore.credential.wallet.Wallet
import com.geoknoesis.vericore.credential.wallet.DidManagement
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * CHAPI (Credential Handler API) request types.
 */
object ChapiRequestTypes {
    const val GET = "web-credential/get"
    const val STORE = "web-credential/store"
}

/**
 * CHAPI credential request.
 */
@Serializable
data class ChapiCredentialRequest(
    val type: String,
    val query: ChapiQuery? = null,
    val challenge: String? = null,
    val domain: String? = null
)

/**
 * CHAPI query.
 */
@Serializable
data class ChapiQuery(
    val type: List<String>? = null,
    val issuer: String? = null,
    val credential: JsonObject? = null
)

/**
 * CHAPI credential response.
 */
@Serializable
data class ChapiCredentialResponse(
    val type: String,
    val dataType: String,
    val data: JsonElement
)

/**
 * CHAPI store request.
 */
@Serializable
data class ChapiStoreRequest(
    val type: String,
    val credential: VerifiableCredential
)

/**
 * CHAPI service for Credential Handler API support.
 * 
 * Provides CHAPI-compatible interfaces for credential requests and storage.
 * 
 * **Example Usage:**
 * ```kotlin
 * val service = ChapiService()
 * 
 * // Handle credential request
 * val request = ChapiCredentialRequest(
 *     type = ChapiRequestTypes.GET,
 *     query = ChapiQuery(type = listOf("VerifiableCredential", "PersonCredential"))
 * )
 * 
 * val response = service.handleRequest(request, wallet)
 * ```
 */
interface ChapiService {
    /**
     * Handle CHAPI credential request.
     * 
     * @param request CHAPI request
     * @param wallet Wallet to query
     * @return CHAPI response
     */
    suspend fun handleRequest(
        request: ChapiCredentialRequest,
        wallet: Wallet
    ): ChapiCredentialResponse?
    
    /**
     * Handle CHAPI store request.
     * 
     * @param request CHAPI store request
     * @param wallet Wallet to store in
     * @return true if stored successfully
     */
    suspend fun handleStore(
        request: ChapiStoreRequest,
        wallet: Wallet
    ): Boolean
    
    /**
     * Create CHAPI credential request.
     * 
     * @param types Credential types to request
     * @param issuer Optional issuer DID
     * @param challenge Optional challenge
     * @param domain Optional domain
     * @return CHAPI request
     */
    suspend fun createRequest(
        types: List<String>,
        issuer: String? = null,
        challenge: String? = null,
        domain: String? = null
    ): ChapiCredentialRequest
    
    /**
     * Create CHAPI store request.
     * 
     * @param credential Credential to store
     * @return CHAPI store request
     */
    suspend fun createStoreRequest(credential: VerifiableCredential): ChapiStoreRequest
}

/**
 * Simple CHAPI service implementation.
 */
class SimpleChapiService(
    private val json: Json = Json { prettyPrint = false; ignoreUnknownKeys = true }
) : ChapiService {
    
    override suspend fun handleRequest(
        request: ChapiCredentialRequest,
        wallet: Wallet
    ): ChapiCredentialResponse? = withContext(Dispatchers.IO) {
        if (request.type != ChapiRequestTypes.GET) {
            return@withContext null
        }
        
        val query = request.query ?: return@withContext null
        
        // Query wallet for matching credentials
        val credentials = wallet.query {
            query.type?.let { byType(it.first()) }
            query.issuer?.let { byIssuer(it) }
        }
        
        if (credentials.isEmpty()) {
            return@withContext null
        }
        
        // Create presentation from credentials
        val holderDid = if (wallet is DidManagement) wallet.holderDid else wallet.walletId
        val presentation = VerifiablePresentation(
            id = null,
            type = listOf("VerifiablePresentation"),
            holder = holderDid,
            verifiableCredential = credentials,
            proof = null
        )
        
        ChapiCredentialResponse(
            type = "VerifiablePresentation",
            dataType = "VerifiablePresentation",
            data = json.encodeToJsonElement(VerifiablePresentation.serializer(), presentation)
        )
    }
    
    override suspend fun handleStore(
        request: ChapiStoreRequest,
        wallet: Wallet
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            wallet.store(request.credential)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    override suspend fun createRequest(
        types: List<String>,
        issuer: String?,
        challenge: String?,
        domain: String?
    ): ChapiCredentialRequest = withContext(Dispatchers.IO) {
        ChapiCredentialRequest(
            type = ChapiRequestTypes.GET,
            query = ChapiQuery(
                type = types,
                issuer = issuer
            ),
            challenge = challenge,
            domain = domain
        )
    }
    
    override suspend fun createStoreRequest(credential: VerifiableCredential): ChapiStoreRequest = withContext(Dispatchers.IO) {
        ChapiStoreRequest(
            type = ChapiRequestTypes.STORE,
            credential = credential
        )
    }
}

