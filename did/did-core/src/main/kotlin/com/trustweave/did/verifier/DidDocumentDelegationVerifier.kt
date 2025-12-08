package com.trustweave.did.verifier

import com.trustweave.did.identifiers.Did
import com.trustweave.did.model.DidDocument
import com.trustweave.did.resolver.DidResolutionResult
import com.trustweave.did.resolver.DidResolver
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

    constructor(
        resolveDid: suspend (String) -> DidResolutionResult?
    ) : this(
        DidResolver { did: Did ->
            resolveDid(did.value) ?: DidResolutionResult.Failure.NotFound(
                did = did,
                reason = "DID not found"
            )
        }
    )

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
            val serviceType = service.type
            serviceType.contains("DelegationCredential", ignoreCase = true) ||
            serviceType.contains("VerifiableCredential", ignoreCase = true)
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
            val serviceType = service.type
            serviceType.contains("DelegationCredential", ignoreCase = true) ||
            serviceType.contains("VerifiableCredential", ignoreCase = true)
        }

        if (delegationServices.isEmpty()) {
            // No delegation credentials present - this is optional, so return true
            return@withContext true
        }

        // For each delegation credential service, verify it
        for (service in delegationServices) {
            val serviceEndpoint = service.serviceEndpoint

            // If service endpoint is a VerifiableCredential, verify it
            // Note: This is a simplified check. Full verification requires CredentialVerifier
            if (serviceEndpoint != null) {
                val credentialValid = verifyDelegationCredentialContent(
                    credential = serviceEndpoint,
                    delegatorDid = delegatorDoc.id.value,
                    delegateDid = delegateDid.value
                )

                if (!credentialValid) {
                    return@withContext false
                }
            }
        }

        true
    }

    /**
     * Verify the content of a delegation credential.
     *
     * Note: This performs basic structural validation only.
     * Full proof verification would require CredentialVerifier from credentials module.
     */
    private suspend fun verifyDelegationCredentialContent(
        credential: Any,
        delegatorDid: String,
        delegateDid: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // Basic validation - check if credential is a Map (JSON structure)
            when (credential) {
                is Map<*, *> -> {
                    val issuer = credential["issuer"] as? String
                    val subject = credential["credentialSubject"]

                    // Verify issuer is delegator
                    if (issuer != delegatorDid) {
                        return@withContext false
                    }

                    // Verify subject is delegate
                    val subjectId = when (subject) {
                        is Map<*, *> -> subject["id"] as? String
                        is String -> subject
                        else -> null
                    }

                    val subjectIdStr = subjectId?.toString() ?: return@withContext false
                    if (subjectIdStr != delegateDid && !subjectIdStr.startsWith("$delegateDid#")) {
                        return@withContext false
                    }

                    // Verify credential has proof
                    val proof = credential["proof"]
                    if (proof == null) {
                        return@withContext false
                    }

                    true
                }
                else -> {
                    // Unknown credential format - assume invalid
                    false
                }
            }
        } catch (e: Exception) {
            // If credential structure is invalid, return false
            false
        }
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

