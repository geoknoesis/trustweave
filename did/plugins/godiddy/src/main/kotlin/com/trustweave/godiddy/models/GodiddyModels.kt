package com.trustweave.godiddy.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Data models for godiddy API requests and responses.
 */

/**
 * Universal Resolver response for DID resolution.
 * Note: This model is kept for reference but the actual response structure
 * may vary. The resolver handles the response directly as JsonObject.
 */
@Serializable
data class GodiddyResolutionResponse(
    val didDocument: JsonElement? = null,
    val didDocumentMetadata: Map<String, JsonElement>? = null,
    val didResolutionMetadata: Map<String, JsonElement>? = null
)

/**
 * Universal Registrar request for DID creation.
 */
@Serializable
data class GodiddyCreateDidRequest(
    val method: String,
    val options: Map<String, JsonElement> = emptyMap()
)

/**
 * Universal Registrar response for DID creation.
 */
@Serializable
data class GodiddyCreateDidResponse(
    val did: String? = null,
    val didDocument: JsonElement? = null,
    val jobId: String? = null
)

/**
 * Universal Registrar request for DID update.
 */
@Serializable
data class GodiddyUpdateDidRequest(
    val did: String,
    val didDocument: JsonElement,
    val options: Map<String, JsonElement> = emptyMap()
)

/**
 * Universal Registrar request for DID deactivation.
 */
@Serializable
data class GodiddyDeactivateDidRequest(
    val did: String,
    val options: Map<String, JsonElement> = emptyMap()
)

/**
 * Universal Registrar operation response.
 */
@Serializable
data class GodiddyOperationResponse(
    val success: Boolean,
    val did: String? = null,
    val didDocument: JsonElement? = null,
    val jobId: String? = null,
    val error: String? = null
)

/**
 * Universal Issuer request for credential issuance.
 */
@Serializable
data class GodiddyIssueCredentialRequest(
    val credential: JsonElement,
    val options: Map<String, JsonElement> = emptyMap()
)

/**
 * Universal Issuer response for credential issuance.
 */
@Serializable
data class GodiddyIssueCredentialResponse(
    val credential: JsonElement? = null,
    val error: String? = null
)

/**
 * Universal Verifier request for credential verification.
 */
@Serializable
data class GodiddyVerifyCredentialRequest(
    val credential: JsonElement,
    val options: Map<String, JsonElement> = emptyMap()
)

/**
 * Universal Verifier response for credential verification.
 */
@Serializable
data class GodiddyVerifyCredentialResponse(
    val verified: Boolean,
    val error: String? = null,
    val checks: Map<String, Boolean>? = null
)

/**
 * Error response from godiddy API.
 */
@Serializable
data class GodiddyErrorResponse(
    val error: String,
    val message: String? = null,
    val code: String? = null
)

