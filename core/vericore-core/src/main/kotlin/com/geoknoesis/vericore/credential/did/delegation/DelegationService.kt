package com.geoknoesis.vericore.did.delegation

import com.geoknoesis.vericore.credential.did.CredentialDidResolver
import com.geoknoesis.vericore.credential.did.asCredentialDidResolution
import com.geoknoesis.vericore.spi.services.DidDocumentAccess
import com.geoknoesis.vericore.spi.services.VerificationMethodAccess
import com.geoknoesis.vericore.spi.services.AdapterLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Service for verifying delegation chains between DIDs.
 * 
 * Verifies that a delegate DID has been granted capability delegation by a delegator DID.
 * This is used to verify that a DID has the authority to perform certain operations
 * on behalf of another DID.
 * 
 * **Example Usage**:
 * ```kotlin
 * val delegationService = DelegationService(didResolver)
 * 
 * val result = delegationService.verifyDelegationChain(
 *     delegatorDid = "did:key:delegator",
 *     delegateDid = "did:key:delegate",
 *     capability = "issueCredentials"
 * )
 * 
 * if (result.valid) {
 *     println("Delegation chain is valid: ${result.path}")
 * }
 * ```
 */
class DelegationService(
    private val didResolver: CredentialDidResolver,
    private val documentAccess: DidDocumentAccess? = null, // Optional - will attempt reflective adapter if not provided
    private val verificationMethodAccess: VerificationMethodAccess? = null // Optional - will attempt reflective adapter if not provided
) {

    constructor(
        resolveDid: suspend (String) -> Any?,
        documentAccess: DidDocumentAccess? = null,
        verificationMethodAccess: VerificationMethodAccess? = null
    ) : this(
        CredentialDidResolver { did -> resolveDid(did).asCredentialDidResolution() },
        documentAccess,
        verificationMethodAccess
    )

    /**
     * Verifies a delegation chain between a delegator and delegate.
     * 
     * @param delegatorDid The DID that grants the delegation
     * @param delegateDid The DID that receives the delegation
     * @param capability Optional capability name (for future use)
     * @return DelegationChainResult with validity and path information
     */
    suspend fun verifyDelegationChain(
        delegatorDid: String,
        delegateDid: String,
        capability: String? = null
    ): DelegationChainResult = withContext(Dispatchers.IO) {
        val errors = mutableListOf<String>()
        val path = mutableListOf<String>()
        
        // Get required service instances
        val docAccess = documentAccess ?: AdapterLoader.didDocumentAccess()
        val vmAccess = verificationMethodAccess ?: AdapterLoader.verificationMethodAccess()
        
        // If services not available, throw error with helpful message
        if (docAccess == null || vmAccess == null) {
            throw IllegalStateException(
                "DidDocumentAccess or VerificationMethodAccess not available. " +
                "Ensure vericore-did module is on classpath by importing a class from it " +
                "(e.g., com.geoknoesis.vericore.did.DidDocument) to trigger service registration."
            )
        }
        
        // Resolve delegator DID document
        val delegatorResult = didResolver.resolve(delegatorDid)
        if (delegatorResult == null || !delegatorResult.isResolvable) {
            return@withContext DelegationChainResult(
                valid = false,
                path = emptyList(),
                errors = listOf("Failed to resolve delegator DID: $delegatorDid")
            )
        }
        
        val delegatorDoc = delegatorResult.document
            ?: delegatorResult.raw?.let { runCatching { docAccess.getDocument(it) }.getOrNull() }
            ?: docAccess.getDocument(delegatorResult)
        if (delegatorDoc == null) {
            return@withContext DelegationChainResult(
                valid = false,
                path = emptyList(),
                errors = listOf("Delegator DID document not available: $delegatorDid")
            )
        }
        
        path.add(delegatorDid)
        
        // Check if delegator has capabilityDelegation relationships
        val capabilityDelegation = docAccess.getCapabilityDelegation(delegatorDoc)
        
        if (capabilityDelegation.isEmpty()) {
            return@withContext DelegationChainResult(
                valid = false,
                path = path,
                errors = listOf("Delegator '$delegatorDid' has no capabilityDelegation relationships")
            )
        }
        
        // Resolve delegate DID document
        val delegateResult = didResolver.resolve(delegateDid)
        if (delegateResult == null || !delegateResult.isResolvable) {
            return@withContext DelegationChainResult(
                valid = false,
                path = path,
                errors = listOf("Failed to resolve delegate DID: $delegateDid")
            )
        }
        
        val delegateDoc = delegateResult.document
            ?: delegateResult.raw?.let { runCatching { docAccess.getDocument(it) }.getOrNull() }
            ?: docAccess.getDocument(delegateResult)
        if (delegateDoc == null) {
            return@withContext DelegationChainResult(
                valid = false,
                path = path,
                errors = listOf("Delegate DID document not available: $delegateDid")
            )
        }
        
        path.add(delegateDid)
        
        // Check if delegate is in delegator's capabilityDelegation list
        val isDelegated = capabilityDelegation.any { ref ->
            val refStr = ref.toString()
            refStr == delegateDid || 
            refStr.startsWith("$delegateDid#") ||
            try {
                // Use services to check verification methods
                val verificationMethods = docAccess.getVerificationMethod(delegateDoc)
                verificationMethods.any { vm ->
                    try {
                        val vmId = vmAccess.getId(vm)
                        val vmController = vmAccess.getController(vm)
                        vmId == refStr && vmController == delegateDid
                    } catch (e: Exception) {
                        false
                    }
                }
            } catch (e: Exception) {
                false
            }
        }
        
        if (!isDelegated) {
            return@withContext DelegationChainResult(
                valid = false,
                path = path,
                errors = listOf("Delegate '$delegateDid' is not in delegator '$delegatorDid' capabilityDelegation list")
            )
        }
        
        // TODO: Verify delegation credential/proof if present
        // This would involve checking for a VerifiableCredential that proves the delegation
        
        DelegationChainResult(
            valid = true,
            path = path,
            errors = emptyList()
        )
    }
    
    /**
     * Verifies a multi-hop delegation chain.
     * 
     * @param chain List of DIDs forming the delegation chain (first is delegator, last is delegate)
     * @param capability Optional capability name
     * @return DelegationChainResult with validity and path information
     */
    suspend fun verifyDelegationChainMultiHop(
        chain: List<String>,
        capability: String? = null
    ): DelegationChainResult = withContext(Dispatchers.IO) {
        if (chain.size < 2) {
            return@withContext DelegationChainResult(
                valid = false,
                path = chain,
                errors = listOf("Delegation chain must have at least 2 DIDs")
            )
        }
        
        val errors = mutableListOf<String>()
        val path = mutableListOf<String>()
        
        // Verify each link in the chain
        for (i in 0 until chain.size - 1) {
            val delegator = chain[i]
            val delegate = chain[i + 1]
            
            val linkResult = verifyDelegationChain(delegator, delegate, capability)
            if (!linkResult.valid) {
                errors.addAll(linkResult.errors)
                errors.add("Failed delegation link: $delegator -> $delegate")
                return@withContext DelegationChainResult(
                    valid = false,
                    path = chain,
                    errors = errors
                )
            }
            
            if (i == 0) {
                path.addAll(linkResult.path)
            } else {
                path.add(delegate)
            }
        }
        
        DelegationChainResult(
            valid = true,
            path = path,
            errors = emptyList()
        )
    }
}

/**
 * Result of delegation chain verification.
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
