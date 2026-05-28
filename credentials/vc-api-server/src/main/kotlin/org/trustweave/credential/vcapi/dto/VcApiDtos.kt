package org.trustweave.credential.vcapi.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * W3C VC API — POST /credentials/issue request body.
 * https://w3c-ccg.github.io/vc-api/#issue-credential
 */
@Serializable
data class IssueCredentialRequest(
    val credential: JsonObject,
    val options: IssueOptions? = null,
)

@Serializable
data class IssueOptions(
    /** Proof format identifier: "vc-ld", "vc-jwt", "sd-jwt-vc". Defaults to "vc-ld". */
    val format: String? = null,
    /** Verification method URI used for signing. */
    val verificationMethod: String? = null,
    val proofPurpose: String? = null,
    val created: String? = null,
    val challenge: String? = null,
    val domain: String? = null,
)

/**
 * W3C VC API — POST /credentials/issue response body.
 */
@Serializable
data class IssueCredentialResponse(
    val verifiableCredential: JsonObject,
)

/**
 * W3C VC API — POST /credentials/verify request body.
 */
@Serializable
data class VerifyCredentialRequest(
    val verifiableCredential: JsonObject,
    val options: VerifyOptions? = null,
)

@Serializable
data class VerifyOptions(
    val challenge: String? = null,
    val domain: String? = null,
    val checkRevocation: Boolean = true,
    val checkExpiration: Boolean = true,
)

/**
 * W3C VC API — POST /credentials/verify response body.
 *
 * `verified = true` iff the credential passed all enabled checks.
 */
@Serializable
data class VerifyCredentialResponse(
    val verified: Boolean,
    val checks: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val errors: List<String> = emptyList(),
)

/**
 * W3C VC API — POST /presentations/prove request body.
 */
@Serializable
data class ProvePresentationRequest(
    val presentation: JsonObject,
    val options: ProveOptions? = null,
)

@Serializable
data class ProveOptions(
    val verificationMethod: String? = null,
    val proofPurpose: String? = null,
    val challenge: String? = null,
    val domain: String? = null,
    val format: String? = null,
)

/**
 * W3C VC API — POST /presentations/prove response body.
 */
@Serializable
data class ProvePresentationResponse(
    val verifiablePresentation: JsonObject,
)

/**
 * W3C VC API — POST /presentations/verify request body.
 */
@Serializable
data class VerifyPresentationRequest(
    val verifiablePresentation: JsonObject,
    val options: VerifyOptions? = null,
)

/**
 * W3C VC API — POST /presentations/verify response body.
 */
@Serializable
data class VerifyPresentationResponse(
    val verified: Boolean,
    val checks: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val errors: List<String> = emptyList(),
)

/** Generic error envelope used for 4xx/5xx responses. */
@Serializable
data class VcApiErrorResponse(
    val error: String,
    val message: String,
)
