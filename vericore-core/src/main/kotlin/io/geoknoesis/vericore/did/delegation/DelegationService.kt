package io.geoknoesis.vericore.did.delegation

import io.geoknoesis.vericore.core.VeriCoreException
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
 * val delegationService = DelegationService(
 *     resolveDid = { did -> didRegistry.resolve(did) }
 * )
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
    private val resolveDid: suspend (String) -> Any? // DidResolutionResult? - using Any to avoid dependency
) {
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
        
        // Resolve delegator DID document using reflection
        val delegatorResult = resolveDid(delegatorDid) as? Any
        if (delegatorResult == null) {
            return@withContext DelegationChainResult(
                valid = false,
                path = emptyList(),
                errors = listOf("Failed to resolve delegator DID: $delegatorDid")
            )
        }
        
        val delegatorDoc = try {
            val getDocumentMethod = delegatorResult.javaClass.getMethod("getDocument")
            getDocumentMethod.invoke(delegatorResult) as? Any
        } catch (e: Exception) {
            null
        }
        
        if (delegatorDoc == null) {
            return@withContext DelegationChainResult(
                valid = false,
                path = emptyList(),
                errors = listOf("Delegator DID document not found: $delegatorDid")
            )
        }
        
        path.add(delegatorDid)
        
        // Check if delegator has capabilityDelegation relationships using reflection
        val capabilityDelegation = try {
            val getCapabilityDelegationMethod = delegatorDoc.javaClass.getMethod("getCapabilityDelegation")
            val result = getCapabilityDelegationMethod.invoke(delegatorDoc) as? List<*>
            result?.mapNotNull { it?.toString() } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
        
        if (capabilityDelegation.isEmpty()) {
            return@withContext DelegationChainResult(
                valid = false,
                path = path,
                errors = listOf("Delegator '$delegatorDid' has no capabilityDelegation relationships")
            )
        }
        
        // Resolve delegate DID document
        val delegateResult = resolveDid(delegateDid) as? Any
        if (delegateResult == null) {
            return@withContext DelegationChainResult(
                valid = false,
                path = path,
                errors = listOf("Failed to resolve delegate DID: $delegateDid")
            )
        }
        
        val delegateDoc = try {
            val getDocumentMethod = delegateResult.javaClass.getMethod("getDocument")
            getDocumentMethod.invoke(delegateResult) as? Any
        } catch (e: Exception) {
            null
        }
        
        if (delegateDoc == null) {
            return@withContext DelegationChainResult(
                valid = false,
                path = path,
                errors = listOf("Delegate DID document not found: $delegateDid")
            )
        }
        
        path.add(delegateDid)
        
        // Check if delegate is in delegator's capabilityDelegation list
        val isDelegated = capabilityDelegation.any { ref ->
            val refStr = ref.toString()
            refStr == delegateDid || 
            refStr.startsWith("$delegateDid#") ||
            try {
                val getVerificationMethodMethod = delegateDoc.javaClass.getMethod("getVerificationMethod")
                val verificationMethods = getVerificationMethodMethod.invoke(delegateDoc) as? List<*>
                verificationMethods?.any { vm ->
                    try {
                        val getIdMethod = vm?.javaClass?.getMethod("getId")
                        val getControllerMethod = vm?.javaClass?.getMethod("getController")
                        val vmId = getIdMethod?.invoke(vm)?.toString()
                        val vmController = getControllerMethod?.invoke(vm)?.toString()
                        vmId == refStr && vmController == delegateDid
                    } catch (e: Exception) {
                        false
                    }
                } ?: false
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
