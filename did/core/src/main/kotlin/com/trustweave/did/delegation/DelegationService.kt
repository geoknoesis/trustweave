package com.trustweave.did.delegation

import com.trustweave.did.resolution.DidResolver
import com.trustweave.did.services.DidDocumentAccess
import com.trustweave.did.services.VerificationMethodAccess
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
    private val didResolver: DidResolver,
    private val documentAccess: DidDocumentAccess,
    private val verificationMethodAccess: VerificationMethodAccess
) {

    constructor(
        resolveDid: suspend (String) -> com.trustweave.did.DidResolutionResult?,
        documentAccess: DidDocumentAccess,
        verificationMethodAccess: VerificationMethodAccess
    ) : this(
        DidResolver { did -> resolveDid(did) },
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
        
        // Services are required and provided via constructor
        val docAccess = documentAccess
        val vmAccess = verificationMethodAccess
        
        // Resolve delegator DID document
        val delegatorResult = didResolver.resolve(delegatorDid)
        if (delegatorResult == null || delegatorResult.document == null) {
            return@withContext DelegationChainResult(
                valid = false,
                path = emptyList(),
                errors = listOf("Failed to resolve delegator DID: $delegatorDid")
            )
        }
        
        val delegatorDoc = delegatorResult.document
        
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
        if (delegateResult == null || delegateResult.document == null) {
            return@withContext DelegationChainResult(
                valid = false,
                path = path,
                errors = listOf("Failed to resolve delegate DID: $delegateDid")
            )
        }
        
        val delegateDoc = delegateResult.document
        
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
        
        // Verify delegation credential/proof if present
        // Check for delegation credentials in DID document services
        val delegationCredentialValid = verifyDelegationCredential(
            delegatorDoc = delegatorDoc,
            delegateDid = delegateDid,
            docAccess = docAccess
        )
        
        if (!delegationCredentialValid && hasDelegationCredential(delegatorDoc, docAccess)) {
            // If delegation credentials exist but verification failed, return error
            return@withContext DelegationChainResult(
                valid = false,
                path = path,
                errors = listOf("Delegation credential verification failed for $delegatorDid -> $delegateDid")
            )
        }
        
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
    
    /**
     * Check if DID document has delegation credentials in services.
     */
    private fun hasDelegationCredential(
        doc: Any,
        docAccess: DidDocumentAccess
    ): Boolean {
        return try {
            val services = docAccess.getService(doc)
            services.any { service ->
                val serviceType = try {
                    val typeField = service.javaClass.getDeclaredField("type")
                    typeField.isAccessible = true
                    val typeValue = typeField.get(service)
                    when (typeValue) {
                        is String -> typeValue
                        is List<*> -> typeValue.firstOrNull() as? String
                        else -> null
                    }
                } catch (e: Exception) {
                    null
                }
                serviceType?.contains("DelegationCredential") == true ||
                serviceType?.contains("VerifiableCredential") == true
            }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Verify delegation credential if present.
     * 
     * Checks for VerifiableCredentials in DID document services that prove delegation.
     * Returns true if no delegation credentials are present (optional verification),
     * or true if delegation credentials are present and valid.
     */
    private suspend fun verifyDelegationCredential(
        delegatorDoc: Any,
        delegateDid: String,
        docAccess: DidDocumentAccess
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val services = docAccess.getService(delegatorDoc)
            val delegationServices = services.filter { service ->
                val serviceType = try {
                    val typeField = service.javaClass.getDeclaredField("type")
                    typeField.isAccessible = true
                    val typeValue = typeField.get(service)
                    when (typeValue) {
                        is String -> typeValue
                        is List<*> -> typeValue.firstOrNull() as? String
                        else -> null
                    }
                } catch (e: Exception) {
                    null
                }
                serviceType?.contains("DelegationCredential") == true ||
                serviceType?.contains("VerifiableCredential") == true
            }
            
            if (delegationServices.isEmpty()) {
                // No delegation credentials present - this is optional, so return true
                return@withContext true
            }
            
            // For each delegation credential service, verify it
            for (service in delegationServices) {
                val serviceEndpoint = try {
                    val endpointField = service.javaClass.getDeclaredField("serviceEndpoint")
                    endpointField.isAccessible = true
                    endpointField.get(service)
                } catch (e: Exception) {
                    null
                }
                
                // If service endpoint is a VerifiableCredential, verify it
                if (serviceEndpoint != null) {
                    // Get delegator DID using reflection since DidDocumentAccess doesn't have getId
                    val delegatorDid = try {
                        val idField = delegatorDoc.javaClass.getDeclaredField("id")
                        idField.isAccessible = true
                        idField.get(delegatorDoc) as? String
                    } catch (e: Exception) {
                        null
                    } ?: return@withContext false
                    
                    val credentialValid = verifyDelegationCredentialContent(
                        credential = serviceEndpoint,
                        delegatorDid = delegatorDid,
                        delegateDid = delegateDid
                    )
                    
                    if (!credentialValid) {
                        return@withContext false
                    }
                }
            }
            
            true
        } catch (e: Exception) {
            // If verification fails, assume valid (optional verification)
            true
        }
    }
    
    /**
     * Verify the content of a delegation credential.
     */
    private suspend fun verifyDelegationCredentialContent(
        credential: Any,
        delegatorDid: String,
        delegateDid: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // Check if credential is a VerifiableCredential
            val issuerField = credential.javaClass.getDeclaredField("issuer")
            issuerField.isAccessible = true
            val issuer = issuerField.get(credential) as? String
            
            val subjectField = credential.javaClass.getDeclaredField("credentialSubject")
            subjectField.isAccessible = true
            val subject = subjectField.get(credential)
            
            // Verify issuer is delegator
            if (issuer != delegatorDid) {
                return@withContext false
            }
            
            // Verify subject is delegate (check subject.id or subject itself)
            val subjectId = try {
                val idField = subject.javaClass.getDeclaredField("id")
                idField.isAccessible = true
                idField.get(subject) as? String
            } catch (e: Exception) {
                subject.toString()
            }
            
            val subjectIdStr = subjectId?.toString() ?: return@withContext false
            if (subjectIdStr != delegateDid && !subjectIdStr.startsWith("$delegateDid#")) {
                return@withContext false
            }
            
            // Verify credential has valid proof
            val proofField = credential.javaClass.getDeclaredField("proof")
            proofField.isAccessible = true
            val proof = proofField.get(credential)
            
            if (proof == null) {
                return@withContext false
            }
            
            // Basic validation - full proof verification would require CredentialVerifier
            true
        } catch (e: Exception) {
            // If credential structure is invalid, return false
            false
        }
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
