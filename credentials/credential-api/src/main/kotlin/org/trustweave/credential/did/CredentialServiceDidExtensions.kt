package org.trustweave.credential.did

import org.trustweave.credential.CredentialService
import org.trustweave.credential.format.ProofSuiteId
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.Claims
import org.trustweave.credential.requests.IssuanceRequest
import org.trustweave.credential.requests.VerificationOptions
import org.trustweave.credential.results.IssuanceResult
import org.trustweave.credential.results.VerificationResult
import org.trustweave.core.identifiers.Iri
import org.trustweave.did.identifiers.Did
import org.trustweave.did.identifiers.VerificationMethodId
import org.trustweave.did.resolver.DidResolver
import org.trustweave.did.resolver.DidResolutionResult
import kotlinx.serialization.json.JsonElement
import kotlinx.datetime.Instant
import kotlinx.datetime.Clock

/**
 * DID-linked credential service extensions.
 * 
 * Provides high-level helper functions for working with Verifiable Credentials linked to DIDs,
 * including DID resolution and validation.
 * 
 * **Example Usage:**
 * ```kotlin
 * val service = credentialService(didResolver = didResolver)
 * 
 * // Issue credential for a DID
 * val result = service.issueForDid(
 *     didResolver = didResolver,
 *     subjectDid = Did("did:key:..."),
 *     issuerDid = Did("did:key:issuer"),
 *     type = listOf(CredentialType.VerifiableCredential, CredentialType.Person),
 *     claims = mapOf(
 *         "name" to JsonPrimitive("John Doe"),
 *         "email" to JsonPrimitive("john@example.com")
 *     ),
 *     format = ProofSuiteId.VC_LD
 * )
 * 
 * // Resolve credential subject DID
 * val subjectDid = service.resolveSubjectDid(didResolver, credential)
 * ```
 */

/**
 * Issue a Verifiable Credential for a DID subject.
 * 
 * Resolves the subject DID to verify it exists, then issues a credential with
 * the DID as the subject.
 * 
 * @param didResolver DID resolver for resolving subject DID
 * @param subjectDid DID of the credential subject
 * @param issuerDid DID of the issuer
 * @param issuerKeyId Optional key ID for signing
 * @param type Credential types
 * @param claims Claims to include in the credential
 * @param format Proof suite ID
 * @return Issuance result containing the issued VerifiableCredential or failure if DID cannot be resolved
 */
suspend fun CredentialService.issueForDid(
    didResolver: DidResolver,
    subjectDid: Did,
    issuerDid: Did,
    issuerKeyId: VerificationMethodId? = null,
    type: List<CredentialType>,
    claims: Claims,
    format: ProofSuiteId
): org.trustweave.credential.results.IssuanceResult {
    // Verify subject DID exists
    val resolutionResult = didResolver.resolve(subjectDid)
    
    // Return failure if DID cannot be resolved
    if (resolutionResult !is DidResolutionResult.Success) {
        return when (resolutionResult) {
            is DidResolutionResult.Failure.NotFound -> {
                IssuanceResult.Failure.InvalidRequest(
                    field = "subjectDid",
                    reason = "Subject DID not found: ${subjectDid.value}"
                )
            }
            is DidResolutionResult.Failure.InvalidFormat -> {
                IssuanceResult.Failure.InvalidRequest(
                    field = "subjectDid",
                    reason = "Subject DID is invalid: ${resolutionResult.reason ?: subjectDid.value}"
                )
            }
            is DidResolutionResult.Failure.ResolutionError -> {
                IssuanceResult.Failure.AdapterError(
                    format = format,
                    reason = "Failed to resolve subject DID: ${resolutionResult.reason ?: "Unknown error"}",
                    cause = resolutionResult.cause
                )
            }
            is DidResolutionResult.Failure.MethodNotRegistered -> {
                IssuanceResult.Failure.InvalidRequest(
                    field = "subjectDid",
                    reason = "Subject DID method not registered: ${resolutionResult.method}"
                )
            }
            else -> {
                IssuanceResult.Failure.InvalidRequest(
                    field = "subjectDid",
                    reason = "Subject DID not resolvable: ${subjectDid.value}"
                )
            }
        }
    }
    
    // Create issuance request
    val request = IssuanceRequest(
        format = format,
        issuer = Issuer.fromDid(issuerDid),
        issuerKeyId = issuerKeyId,
        credentialSubject = CredentialSubject.fromDid(subjectDid, claims = claims),
        type = type,
        issuedAt = Clock.System.now()
    )
    
    return issue(request)
}

/**
 * Resolve Verifiable Credential subject DID.
 * 
 * Extracts the DID from the credential subject and resolves it.
 * 
 * @param didResolver DID resolver for resolving subject DID
 * @param credential VerifiableCredential
 * @return Resolved DID, or null if subject is not a DID or cannot be resolved
 */
suspend fun CredentialService.resolveSubjectDid(
    didResolver: DidResolver,
    credential: VerifiableCredential
): Did? {
    val subjectId = credential.credentialSubject.id
    
    // Check if subject ID is a DID
    return if (subjectId.isDid) {
        try {
            val did = Did(subjectId.value)
            val resolutionResult = didResolver.resolve(did)
            when (resolutionResult) {
                is DidResolutionResult.Success -> did
                else -> null
            }
        } catch (e: IllegalArgumentException) {
            null
        }
    } else {
        null
    }
}

/**
 * Verify issuer DID is valid and resolvable.
 * 
 * @param didResolver DID resolver for resolving issuer DID
 * @param credential VerifiableCredential
 * @return true if issuer IRI is a DID and is valid and resolvable
 */
suspend fun CredentialService.verifyIssuerDid(
    didResolver: DidResolver,
    credential: VerifiableCredential
): Boolean {
    val issuerIri = credential.issuer.id
    
    // Check if issuer is a DID
    return if (issuerIri.isDid) {
        try {
            val issuerDid = Did(issuerIri.value)
            val resolutionResult = didResolver.resolve(issuerDid)
            resolutionResult is DidResolutionResult.Success
        } catch (e: Exception) {
            false
        }
    } else {
        false  // Not a DID, so cannot verify via DID resolution
    }
}

/**
 * Verify Verifiable Credential with issuer DID resolution.
 * 
 * First resolves the issuer DID to verify it exists, then performs normal verification.
 * 
 * @param didResolver DID resolver for resolving issuer DID
 * @param credential VerifiableCredential to verify
 * @param options Verification options
 * @return Verification result
 */
suspend fun CredentialService.verifyWithDidResolution(
    didResolver: DidResolver,
    credential: VerifiableCredential,
    options: VerificationOptions = VerificationOptions()
): VerificationResult {
    // Verify issuer DID first (if it's a DID)
    val issuerIri = credential.issuer.id
    if (issuerIri.isDid) {
        val issuerDidValid = verifyIssuerDid(didResolver, credential)
        if (!issuerDidValid) {
            return VerificationResult.Invalid.InvalidIssuer(
                credential = credential,
                issuerIri = issuerIri,
                reason = "Issuer DID not found or not resolvable",
                errors = listOf("Issuer DID cannot be resolved: ${issuerIri.value}"),
                warnings = emptyList()
            )
        }
    }
    
    // Perform normal verification
    return verify(credential, trustPolicy = null, options = options)
}
