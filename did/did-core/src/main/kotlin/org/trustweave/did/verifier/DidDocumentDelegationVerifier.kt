package org.trustweave.did.verifier

import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument
import org.trustweave.did.model.serviceEndpointAsObject
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.resolver.DidResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Verifies DID document delegation relationships and chains.
 *
 * This verifier checks that a delegate DID has been granted capability delegation
 * by a delegator DID according to W3C DID Core specification. It validates that
 * the delegator's DID document contains the delegate in its `capabilityDelegation`
 * relationship list.
 *
 * **Example Usage**:
 * ```kotlin
 * val verifier = DidDocumentDelegationVerifier(didResolver)
 *
 * val result = verifier.verify(
 *     delegatorDid = Did("did:key:delegator"),
 *     delegateDid = Did("did:key:delegate")
 * )
 *
 * if (result.valid) {
 *     println("Delegation chain is valid: ${result.path}")
 * }
 * ```
 */
class DidDocumentDelegationVerifier(
    private val didResolver: DidResolver
) {

    /**
     * Verifies a DID document delegation relationship between a delegator and delegate.
     *
     * Checks that the delegator's DID document contains the delegate in its
     * `capabilityDelegation` relationship list, following W3C DID Core spec.
     *
     * @param delegatorDid Type-safe DID that grants the delegation
     * @param delegateDid Type-safe DID that receives the delegation
     * @return DelegationChainResult with validity and path information
     */
    suspend fun verify(
        delegatorDid: Did,
        delegateDid: Did
    ): DelegationChainResult = withContext(Dispatchers.IO) {
        val path = mutableListOf<String>()

        // Resolve delegator DID document
        val delegatorResult = didResolver.resolve(delegatorDid)
        val delegatorDoc = when (delegatorResult) {
            is DidResolutionResult.Success -> delegatorResult.document
            else -> {
                return@withContext DelegationChainResult(
                    valid = false,
                    path = emptyList(),
                    errors = listOf("Failed to resolve delegator DID: ${delegatorDid.value}")
                )
            }
        }
        path.add(delegatorDid.value)

        // Check if delegator has capabilityDelegation relationships
        val capabilityDelegation = delegatorDoc.capabilityDelegation

        if (capabilityDelegation.isEmpty()) {
            return@withContext DelegationChainResult(
                valid = false,
                path = path,
                errors = listOf("Delegator '${delegatorDid.value}' has no capabilityDelegation relationships")
            )
        }

        // Resolve delegate DID document
        val delegateResult = didResolver.resolve(delegateDid)
        val delegateDoc = when (delegateResult) {
            is DidResolutionResult.Success -> delegateResult.document
            else -> {
                return@withContext DelegationChainResult(
                    valid = false,
                    path = path,
                    errors = listOf("Failed to resolve delegate DID: ${delegateDid.value}")
                )
            }
        }
        path.add(delegateDid.value)

        // Check if delegate is in delegator's capabilityDelegation list
        val isDelegated = capabilityDelegation.any { ref ->
            ref.value == delegateDid.value ||
            ref.value.startsWith("${delegateDid.value}#") ||
            // Check if any verification method matches
            delegateDoc.verificationMethod.any { vm ->
                vm.id == ref && vm.controller.value == delegateDid.value
            }
        }

        if (!isDelegated) {
            return@withContext DelegationChainResult(
                valid = false,
                path = path,
                errors = listOf("Delegate '${delegateDid.value}' is not in delegator '${delegatorDid.value}' capabilityDelegation list")
            )
        }

        // Verify delegation credential/proof if present
        // Check for delegation credentials in DID document services
        val delegationCredentialValid = verifyDelegationCredential(
            delegatorDoc = delegatorDoc,
            delegateDid = delegateDid
        )

        if (!delegationCredentialValid && hasDelegationCredential(delegatorDoc)) {
            // If delegation credentials exist but verification failed, return error
            return@withContext DelegationChainResult(
                valid = false,
                path = path,
                errors = listOf("Delegation credential verification failed for ${delegatorDid.value} -> ${delegateDid.value}")
            )
        }

        DelegationChainResult(
            valid = true,
            path = path,
            errors = emptyList()
        )
    }

    /**
     * Verifies a multi-hop DID document delegation chain.
     *
     * Verifies each link in a chain of delegations, ensuring each DID delegates
     * to the next in sequence.
     *
     * @param chain List of type-safe DIDs forming the delegation chain (first is delegator, last is delegate)
     * @return DelegationChainResult with validity and path information
     */
    suspend fun verifyChain(
        chain: List<Did>
    ): DelegationChainResult = withContext(Dispatchers.IO) {
        if (chain.size < 2) {
            return@withContext DelegationChainResult(
                valid = false,
                path = chain.map { it.value },
                errors = listOf("Delegation chain must have at least 2 DIDs")
            )
        }

        val errors = mutableListOf<String>()
        val path = mutableListOf<String>()

        // Verify each link in the chain
        for (i in 0 until chain.size - 1) {
            val delegator = chain[i]
            val delegate = chain[i + 1]

            val linkResult = verify(delegator, delegate)
            if (!linkResult.valid) {
                errors.addAll(linkResult.errors)
                errors.add("Failed delegation link: ${delegator.value} -> ${delegate.value}")
                return@withContext DelegationChainResult(
                    valid = false,
                    path = chain.map { it.value },
                    errors = errors
                )
            }

            if (i == 0) {
                path.addAll(linkResult.path)
            } else {
                path.add(delegate.value)
            }
        }

        DelegationChainResult(
            valid = true,
            path = path,
            errors = emptyList()
        )
    }

    /**
     * Check if DID document has delegation credentials in services.
     */
    private fun hasDelegationCredential(doc: DidDocument): Boolean {
        return doc.service.any { service ->
            service.type.any { t ->
                t.contains("DelegationCredential", ignoreCase = true) ||
                t.contains("VerifiableCredential", ignoreCase = true)
            }
        }
    }

    /**
     * Verify delegation credential if present.
     *
     * Checks for VerifiableCredentials in DID document services that prove delegation.
     * Returns true if no delegation credentials are present (optional verification),
     * or true if delegation credentials are present and valid.
     *
     * Note: Full credential verification would require CredentialVerifier from credentials module.
     * This method performs basic structural validation only.
     */
    private suspend fun verifyDelegationCredential(
        delegatorDoc: DidDocument,
        delegateDid: Did
    ): Boolean = withContext(Dispatchers.IO) {
        val delegationServices = delegatorDoc.service.filter { service ->
            service.type.any { t ->
                t.contains("DelegationCredential", ignoreCase = true) ||
                t.contains("VerifiableCredential", ignoreCase = true)
            }
        }

        if (delegationServices.isEmpty()) {
            // No delegation credentials present - this is optional, so return true
            return@withContext true
        }

        // For each delegation credential service, verify it
        for (service in delegationServices) {
            // The embedded credential must be an object (JSON structure) to be
            // structurally verifiable. Non-object endpoints (e.g. a plain URL
            // string) cannot be validated here, so treat them as failing the
            // content check — matching the previous behaviour for non-Map endpoints.
            // Note: This is a structural check only. Full verification requires CredentialVerifier.
            val endpointObject = service.serviceEndpointAsObject()
                ?: return@withContext false

            val credentialValid = verifyDelegationCredentialContent(
                credential = EmbeddedDelegationCredential.fromEndpointObject(endpointObject),
                delegatorDid = delegatorDoc.id.value,
                delegateDid = delegateDid.value
            )

            if (!credentialValid) {
                return@withContext false
            }
        }

        true
    }

    /**
     * Verify the structural content of a delegation credential embedded in a DID
     * document service endpoint.
     *
     * Performs **structural validation only**: the issuer matches the delegator, the
     * subject matches the delegate, and a proof is present. Full cryptographic proof
     * verification is the responsibility of the credentials module's CredentialVerifier
     * — did-core intentionally does not depend on it (see [EmbeddedDelegationCredential]).
     *
     * @param credential Structural view of the embedded delegation credential
     * @param delegatorDid Expected issuer DID
     * @param delegateDid Expected subject DID
     */
    private fun verifyDelegationCredentialContent(
        credential: EmbeddedDelegationCredential,
        delegatorDid: String,
        delegateDid: String
    ): Boolean {
        // Issuer must be the delegator
        if (credential.issuer != delegatorDid) return false

        // Subject must be the delegate
        val subjectId = credential.subjectId ?: return false
        if (subjectId != delegateDid && !subjectId.startsWith("$delegateDid#")) return false

        // Credential must carry a proof (structural presence check only)
        return credential.hasProof
    }
}

/**
 * Result of DID document delegation chain verification.
 *
 * @param valid Whether the delegation chain is valid
 * @param path List of DIDs forming the delegation chain
 * @param errors List of error messages if validation failed
 */
data class DelegationChainResult(
    val valid: Boolean,
    val path: List<String>,
    val errors: List<String> = emptyList()
)

/**
 * Structural view of a delegation credential embedded in a DID document service
 * endpoint object (W3C DID Core permits arbitrary JSON objects as service endpoints).
 *
 * did-core validates structure only (issuer / subject / proof presence). This type
 * deliberately does **not** reference the credentials module's `VerifiableCredential`:
 * `credentials:credential-api` already depends on `did:did-core`, so a back-dependency
 * would be circular. Full cryptographic verification is performed by the credentials
 * layer's CredentialVerifier, not here.
 */
internal data class EmbeddedDelegationCredential(
    val issuer: String?,
    val subjectId: String?,
    val hasProof: Boolean
) {
    companion object {
        /**
         * Extracts the structurally-relevant fields from a decoded service-endpoint
         * object. Parsing is total — unexpected shapes yield null fields rather than
         * throwing.
         */
        fun fromEndpointObject(endpoint: Map<String, Any?>): EmbeddedDelegationCredential {
            val subjectId = when (val subject = endpoint["credentialSubject"]) {
                is Map<*, *> -> subject["id"] as? String
                is String -> subject
                else -> null
            }
            return EmbeddedDelegationCredential(
                issuer = endpoint["issuer"] as? String,
                subjectId = subjectId,
                hasProof = endpoint["proof"] != null
            )
        }
    }
}

