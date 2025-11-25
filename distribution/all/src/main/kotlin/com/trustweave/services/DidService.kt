package com.trustweave.services

import com.trustweave.TrustWeaveContext
import com.trustweave.core.*
import com.trustweave.did.DidCreationOptions
import com.trustweave.did.exception.DidException
import com.trustweave.did.validation.DidValidator
import com.trustweave.did.DidCreationOptionsBuilder
import com.trustweave.did.DidDocument
import com.trustweave.did.DidMethod
import com.trustweave.did.resolver.DidResolutionResult
import com.trustweave.did.didCreationOptions

/**
 * Focused service for DID operations.
 * 
 * Provides a clean, focused API for creating, resolving, updating, and deactivating DIDs.
 * 
 * **Example:**
 * ```kotlin
 * val TrustWeave = TrustWeave.create()
 * val did = trustweave.dids.create()
 * val resolved = trustweave.dids.resolve("did:key:...")
 * ```
 */
class DidService(
    private val context: TrustWeaveContext
) {
    /**
     * Creates a new DID using the specified method.
     * 
     * **Example:**
     * ```kotlin
     * // Simple usage with default method
     * val did = trustweave.dids.create()
     * 
     * // With specific method
     * val did = trustweave.dids.create(method = "key")
     * 
     * // With custom configuration
     * val did = trustweave.dids.create {
     *     algorithm = KeyAlgorithm.ED25519
     *     purpose(KeyPurpose.AUTHENTICATION)
     * }
     * ```
     * 
     * @param method DID method name (default: "key")
     * @param options DID creation options
     * @return The created DID document
     * @throws DidException.DidMethodNotRegistered if method is not registered
     */
    suspend fun create(
        method: String = "key",
        options: DidCreationOptions = DidCreationOptions()
    ): DidDocument {
        val availableMethods = context.getAvailableDidMethods()
        if (method !in availableMethods) {
            throw DidException.DidMethodNotRegistered(
                method = method,
                availableMethods = availableMethods
            )
        }
        
        val didMethod = context.getDidMethod(method)
            ?: throw DidException.DidMethodNotRegistered(
                method = method,
                availableMethods = availableMethods
            )
        
        return didMethod.createDid(options)
    }
    
    /**
     * Creates a DID using a fluent builder.
     */
    suspend fun create(
        method: String = "key",
        configure: DidCreationOptionsBuilder.() -> Unit
    ): DidDocument = create(method, didCreationOptions(configure))
    
    /**
     * Resolves a DID to its document.
     * 
     * **Example:**
     * ```kotlin
     * val result = trustweave.dids.resolve("did:key:z6Mkfriq...")
     * if (result.document != null) {
     *     println("DID resolved: ${result.document.id}")
     * } else {
     *     println("DID not found")
     * }
     * ```
     * 
     * @param did The DID string to resolve
     * @return Resolution result containing the document and metadata
     * @throws DidException.InvalidDidFormat if DID format is invalid
     * @throws DidException.DidMethodNotRegistered if method is not registered
     */
    suspend fun resolve(did: String): DidResolutionResult {
        // Validate DID format
        DidValidator.validateFormat(did).let {
            if (!it.isValid()) {
                throw DidException.InvalidDidFormat(
                    did = did,
                    reason = it.errorMessage() ?: "Invalid DID format"
                )
            }
        }
        
        // Validate method is registered
        val availableMethods = context.getAvailableDidMethods()
        DidValidator.validateMethod(did, availableMethods).let {
            if (!it.isValid()) {
                throw DidException.DidMethodNotRegistered(
                    method = DidValidator.extractMethod(did) ?: "unknown",
                    availableMethods = availableMethods
                )
            }
        }
        
        return context.resolveDid(did)
    }
    
    /**
     * Updates a DID document.
     * 
     * **Example:**
     * ```kotlin
     * val updated = trustweave.dids.update("did:key:example") { document ->
     *     document.copy(
     *         service = document.service + Service(
     *             id = "${document.id}#service-1",
     *             type = "LinkedDomains",
     *             serviceEndpoint = "https://example.com/service"
     *         )
     *     )
     * }
     * ```
     * 
     * @param did The DID to update
     * @param updater Function that transforms the current document to the new document
     * @return The updated DID document
     * @throws DidException.InvalidDidFormat if DID format is invalid
     * @throws DidException.DidMethodNotRegistered if method is not registered
     */
    suspend fun update(
        did: String,
        updater: (DidDocument) -> DidDocument
    ): DidDocument {
        require(did.isNotBlank()) { "DID is required" }
        
        DidValidator.validateFormat(did).let {
            if (!it.isValid()) {
                throw DidException.InvalidDidFormat(
                    did = did,
                    reason = it.errorMessage() ?: "Invalid DID format"
                )
            }
        }
        
        val methodName = DidValidator.extractMethod(did)
            ?: throw DidException.InvalidDidFormat(
                did = did,
                reason = "Failed to extract method from DID"
            )
        
        val method = context.didRegistry.get(methodName)
            ?: throw DidException.DidMethodNotRegistered(
                method = methodName,
                availableMethods = context.didRegistry.getAllMethodNames()
            )
        
        return method.updateDid(did, updater)
    }
    
    /**
     * Deactivates a DID.
     * 
     * **Example:**
     * ```kotlin
     * val deactivated = trustweave.dids.deactivate("did:key:example")
     * if (deactivated) {
     *     println("DID deactivated successfully")
     * }
     * ```
     * 
     * @param did The DID to deactivate
     * @return true if deactivated, false otherwise
     * @throws DidException.InvalidDidFormat if DID format is invalid
     * @throws DidException.DidMethodNotRegistered if method is not registered
     */
    suspend fun deactivate(did: String): Boolean {
        require(did.isNotBlank()) { "DID is required" }
        
        DidValidator.validateFormat(did).let {
            if (!it.isValid()) {
                throw DidException.InvalidDidFormat(
                    did = did,
                    reason = it.errorMessage() ?: "Invalid DID format"
                )
            }
        }
        
        val methodName = DidValidator.extractMethod(did)
            ?: throw DidException.InvalidDidFormat(
                did = did,
                reason = "Failed to extract method from DID"
            )
        
        val method = context.didRegistry.get(methodName)
            ?: throw DidException.DidMethodNotRegistered(
                method = methodName,
                availableMethods = context.didRegistry.getAllMethodNames()
            )
        
        return method.deactivateDid(did)
    }
    
    /**
     * Gets available DID methods.
     * 
     * @return List of registered DID method names
     */
    fun availableMethods(): List<String> = context.getAvailableDidMethods()
}

