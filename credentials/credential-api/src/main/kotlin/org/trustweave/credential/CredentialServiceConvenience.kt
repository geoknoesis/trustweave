package org.trustweave.credential

import org.trustweave.credential.format.ProofSuiteId
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.requests.IssuanceRequest
import org.trustweave.credential.requests.credentialTypes
import org.trustweave.credential.results.IssuanceResult
import org.trustweave.did.identifiers.Did
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import java.time.Duration as JavaDuration
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlinx.datetime.Clock

/**
 * Convenience overloads for common issuance patterns.
 * 
 * These extensions provide simpler APIs for the most common use cases,
 * reducing boilerplate while maintaining type safety.
 */

/**
 * Issue a credential with simplified parameters.
 * 
 * This is a convenience overload for the most common issuance pattern.
 * Automatically handles type wrapping and default values.
 * 
 * **Example:**
 * ```kotlin
 * val result = service.issue(
 *     format = ProofSuiteId.VC_LD,
 *     issuerDid = issuerDid,
 *     subjectDid = subjectDid,
 *     type = "PersonCredential",
 *     claims = mapOf("name" to "Alice")
 * )
 * ```
 * 
 * @param format Proof suite (use ProofSuiteId enum)
 * @param issuerDid Issuer DID
 * @param subjectDid Subject DID
 * @param type Credential type (automatically includes "VerifiableCredential")
 * @param claims Claims map (strings/numbers/booleans auto-converted to JsonPrimitive)
 * @param expiresIn Optional expiration duration from now
 * @return Issuance result
 */
suspend fun CredentialService.issue(
    format: ProofSuiteId,
    issuerDid: Did,
    subjectDid: Did,
    type: String,
    claims: Map<String, Any> = emptyMap(),
    expiresIn: Duration? = null
): IssuanceResult {
    // Convert claims to JsonElement
    val jsonClaims = claims.mapValues { (_, value) ->
        when (value) {
            is String -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            is JsonElement -> value
            else -> JsonPrimitive(value.toString())
        }
    }
    
    val request = IssuanceRequest(
        format = format,
        issuer = Issuer.fromDid(issuerDid),
        credentialSubject = CredentialSubject.fromDid(subjectDid, claims = jsonClaims),
        type = credentialTypes(type),
        issuedAt = Clock.System.now(),
        validUntil = expiresIn?.let { Clock.System.now().plus(Duration.parse(it.toString())) }
    )
    
    return issue(request)
}

/**
 * Issue a credential with multiple types.
 */
suspend fun CredentialService.issue(
    format: ProofSuiteId,
    issuerDid: Did,
    subjectDid: Did,
    types: List<String>,
    claims: Map<String, Any> = emptyMap(),
    expiresIn: Duration? = null
): IssuanceResult {
    val jsonClaims = claims.mapValues { (_, value) ->
        when (value) {
            is String -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            is JsonElement -> value
            else -> JsonPrimitive(value.toString())
        }
    }
    
    val request = IssuanceRequest(
        format = format,
        issuer = Issuer.fromDid(issuerDid),
        credentialSubject = CredentialSubject.fromDid(subjectDid, claims = jsonClaims),
        type = credentialTypes(*types.toTypedArray()),
        issuedAt = Clock.System.now(),
        validUntil = expiresIn?.let { Clock.System.now().plus(Duration.parse(it.toString())) }
    )
    
    return issue(request)
}

