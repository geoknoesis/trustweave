package com.trustweave.did.registrar.model

import com.trustweave.did.DidDocument
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Options for DID creation operations according to DID Registration specification.
 * 
 * Supports both Internal Secret Mode and External Secret Mode (Client-managed).
 * 
 * @see https://identity.foundation/did-registration/
 */
@Serializable
data class CreateDidOptions(
    /**
     * Key management mode.
     * 
     * - `INTERNAL_SECRET`: Registrar generates and manages keys internally
     * - `EXTERNAL_SECRET`: Client provides keys via external wallet/KMS
     * 
     * Default: `INTERNAL_SECRET`
     */
    val keyManagementMode: KeyManagementMode = KeyManagementMode.INTERNAL_SECRET,
    
    /**
     * Whether the registrar should store the generated secrets.
     * 
     * Only applicable in Internal Secret Mode.
     * Default: `false` (secrets are not stored by default)
     */
    val storeSecrets: Boolean = false,
    
    /**
     * Whether the registrar should return secrets to the client.
     * 
     * Only applicable in Internal Secret Mode.
     * When `true`, the `didState.secret` field will be populated in the response.
     * Default: `false` (secrets are not returned by default)
     */
    val returnSecrets: Boolean = false,
    
    /**
     * Secret material provided by the client.
     * 
     * Required in External Secret Mode.
     * Contains keys that the client manages externally.
     */
    val secret: Secret? = null,
    
    /**
     * Pre-created DID Document.
     * 
     * If provided, the registrar will use this document instead of generating one.
     * Useful when the client wants to control the document structure.
     */
    @Contextual
    val didDocument: DidDocument? = null,
    
    /**
     * Method-specific options.
     * 
     * Each DID method may define additional options:
     * - Network selection (mainnet, testnet)
     * - Key algorithm preferences
     * - Service endpoints
     * - Method-specific configuration
     */
    val methodSpecificOptions: Map<String, JsonElement> = emptyMap()
)

/**
 * Key management mode for DID operations.
 */
@Serializable
enum class KeyManagementMode {
    /**
     * Internal Secret Mode: Registrar generates and manages keys internally.
     * 
     * Options:
     * - `storeSecrets`: Whether registrar stores keys
     * - `returnSecrets`: Whether registrar returns keys to client
     */
    INTERNAL_SECRET,
    
    /**
     * External Secret Mode: Client manages keys via external wallet/KMS.
     * 
     * Registrar performs cryptographic operations through wallet interface
     * without direct access to private keys.
     */
    EXTERNAL_SECRET
}

