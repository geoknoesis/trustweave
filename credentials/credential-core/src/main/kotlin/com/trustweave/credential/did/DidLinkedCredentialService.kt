package com.trustweave.credential.did

import com.trustweave.credential.CredentialIssuanceOptions
import com.trustweave.credential.issuer.CredentialIssuer
import com.trustweave.credential.model.vc.VerifiableCredential
import com.trustweave.credential.model.vc.Issuer
import com.trustweave.credential.model.vc.CredentialSubject
import com.trustweave.credential.model.CredentialType
import com.trustweave.did.identifiers.Did
import com.trustweave.did.registry.DidMethodRegistry
import com.trustweave.did.resolver.DidResolutionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.datetime.Clock
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.datetime.Instant

/**
 * DID-linked credential service.
 *
 * Integrates credential issuance with DID resolution and management.
 * Provides high-level API for issuing credentials to DIDs and
 * resolving credential subjects.
 *
 * **Example Usage**:
 * ```kotlin
 * val service = DidLinkedCredentialService(
 *     didRegistry = didRegistry,
 *     credentialIssuer = credentialIssuer
 * )
 *
 * // Issue credential for a DID
 * val credential = service.issueCredentialForDid(
 *     subjectDid = "did:key:...",
 *     credentialType = "PersonCredential",
 *     claims = mapOf(
 *         "name" to "John Doe",
 *         "email" to "john@example.com"
 *     ),
 *     issuerDid = "did:key:issuer",
 *     keyId = "key-1"
 * )
 *
 * // Resolve credential subject DID
 * val subjectDid = service.resolveCredentialSubject(credential)
 * ```
 */
open class DidLinkedCredentialService(
    private val didRegistry: DidMethodRegistry,
    private val credentialIssuer: CredentialIssuer
) {
    /**
     * Issue a credential for a DID subject.
     *
     * Creates a credential with the DID as the subject and issues it.
     *
     * @param subjectDid DID of the credential subject
     * @param credentialType Credential type (e.g., "PersonCredential")
     * @param claims Claims to include in credentialSubject
     * @param issuerDid DID of the issuer
     * @param keyId Key ID for signing
     * @param options Additional issuance options
     * @return Issued verifiable credential
     */
    suspend fun issueCredentialForDid(
        subjectDid: String,
        credentialType: String,
        claims: Map<String, Any>,
        issuerDid: String,
        keyId: String,
        options: CredentialIssuanceOptions = CredentialIssuanceOptions()
    ): VerifiableCredential = withContext(Dispatchers.IO) {
        // Resolve subject DID to verify it exists
        resolveDid(subjectDid)

        // Build credentialSubject with DID as id
        val subjectDidObj = Did(subjectDid)
        val claimsJson = buildJsonObject {
            claims.forEach { (key, value) ->
                when (value) {
                    is String -> put(key, value)
                    is Number -> put(key, value)
                    is Boolean -> put(key, value)
                    else -> put(key, value.toString())
                }
            }
        }
        val credentialSubject = CredentialSubject.fromDid(subjectDidObj, claims = claimsJson)

        // Create credential
        val issuerDidObj = Did(issuerDid)
        val credential = VerifiableCredential(
            id = null,
            type = listOf(CredentialType.fromString("VerifiableCredential"), CredentialType.fromString(credentialType)),
            issuer = Issuer.fromDid(issuerDidObj),
            credentialSubject = credentialSubject,
            issuanceDate = Clock.System.now(),
            expirationDate = null,
            credentialStatus = null,
            credentialSchema = null,
            evidence = null,
            proof = null,
            termsOfUse = null,
            refreshService = null
        )

        // Issue credential
        credentialIssuer.issue(
            credential = credential,
            issuerDid = issuerDid,
            keyId = keyId,
            options = options
        )
    }

    /**
     * Resolve credential subject DID.
     *
     * Extracts the DID from credentialSubject.id and resolves it.
     *
     * @param credential Credential to extract subject from
     * @return Resolved DID document, or null if subject is not a DID
     */
    suspend fun resolveCredentialSubject(credential: VerifiableCredential): String? = withContext(Dispatchers.IO) {
        val subjectId = credential.credentialSubject.id

        // Check if subject is a DID
        if (!subjectId.isDid) {
            return@withContext null
        }

        // Resolve DID
        try {
            resolveDid(subjectId.value)
            subjectId.value
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Verify issuer DID is valid.
     *
     * @param credential Credential to check
     * @return true if issuer DID is valid and resolvable
     */
    suspend fun verifyIssuerDid(credential: VerifiableCredential): Boolean = withContext(Dispatchers.IO) {
        try {
            resolveDid(credential.issuer.id.value)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Resolve DID using registry.
     *
     * @param did DID to resolve
     * @throws IllegalArgumentException if DID cannot be resolved
     */
    private suspend fun resolveDid(did: String) {
        val result = didRegistry.resolve(did)
        when (result) {
            is DidResolutionResult.Success -> {
                // DID resolved successfully
            }
            else -> {
                throw IllegalArgumentException("DID not found: $did")
            }
        }
    }
}

