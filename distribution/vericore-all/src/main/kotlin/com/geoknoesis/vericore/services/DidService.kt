package com.geoknoesis.vericore.services

import com.geoknoesis.vericore.core.*
import com.geoknoesis.vericore.core.types.ProofType
import com.geoknoesis.vericore.did.DidCreationOptions
import com.geoknoesis.vericore.did.DidCreationOptionsBuilder
import com.geoknoesis.vericore.did.DidDocument
import com.geoknoesis.vericore.did.DidMethod
import com.geoknoesis.vericore.did.DidResolutionResult
import com.geoknoesis.vericore.did.didCreationOptions

/**
 * Focused service for DID operations.
 * 
 * Provides a clean, focused API for creating, resolving, updating, and deactivating DIDs.
 * 
 * **Example:**
 * ```kotlin
 * val vericore = VeriCore.create()
 * val did = vericore.dids.create()
 * val resolved = vericore.dids.resolve("did:key:...")
 * ```
 */
class DidService(
    private val context: VeriCoreContext
) {
    /**
     * Creates a new DID using the specified method.
     * 
     * **Example:**
     * ```kotlin
     * // Simple usage with default method
     * val did = vericore.dids.create()
     * 
     * // With specific method
     * val did = vericore.dids.create(method = "key")
     * 
     * // With custom configuration
     * val did = vericore.dids.create {
     *     algorithm = KeyAlgorithm.ED25519
     *     purpose(KeyPurpose.AUTHENTICATION)
     * }
     * ```
     * 
     * @param method DID method name (default: "key")
     * @param options DID creation options
     * @return The created DID document
     * @throws VeriCoreError.DidMethodNotRegistered if method is not registered
     */
    suspend fun create(
        method: String = "key",
        options: DidCreationOptions = DidCreationOptions()
    ): DidDocument {
        val availableMethods = context.getAvailableDidMethods()
        if (method !in availableMethods) {
            throw VeriCoreError.DidMethodNotRegistered(
                method = method,
                availableMethods = availableMethods
            )
        }
        
        val didMethod = context.getDidMethod(method)
            ?: throw VeriCoreError.DidMethodNotRegistered(
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
     * val result = vericore.dids.resolve("did:key:z6Mkfriq...")
     * if (result.document != null) {
     *     println("DID resolved: ${result.document.id}")
     * } else {
     *     println("DID not found")
     * }
     * ```
     * 
     * @param did The DID string to resolve
     * @return Resolution result containing the document and metadata
     * @throws VeriCoreError.InvalidDidFormat if DID format is invalid
     * @throws VeriCoreError.DidMethodNotRegistered if method is not registered
     */
    suspend fun resolve(did: String): DidResolutionResult {
        // Validate DID format
        DidValidator.validateFormat(did).let {
            if (!it.isValid()) {
                throw VeriCoreError.InvalidDidFormat(
                    did = did,
                    reason = it.errorMessage() ?: "Invalid DID format"
                )
            }
        }
        
        // Validate method is registered
        val availableMethods = context.getAvailableDidMethods()
        DidValidator.validateMethod(did, availableMethods).let {
            if (!it.isValid()) {
                throw VeriCoreError.DidMethodNotRegistered(
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
     * val updated = vericore.dids.update("did:key:example") { document ->
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
     * @throws VeriCoreError.InvalidDidFormat if DID format is invalid
     * @throws VeriCoreError.DidMethodNotRegistered if method is not registered
     */
    suspend fun update(
        did: String,
        updater: (DidDocument) -> DidDocument
    ): DidDocument {
        require(did.isNotBlank()) { "DID is required" }
        
        DidValidator.validateFormat(did).let {
            if (!it.isValid()) {
                throw VeriCoreError.InvalidDidFormat(
                    did = did,
                    reason = it.errorMessage() ?: "Invalid DID format"
                )
            }
        }
        
        val methodName = DidValidator.extractMethod(did)
            ?: throw VeriCoreError.InvalidDidFormat(
                did = did,
                reason = "Failed to extract method from DID"
            )
        
        val method = context.didRegistry.get(methodName)
            ?: throw VeriCoreError.DidMethodNotRegistered(
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
     * val deactivated = vericore.dids.deactivate("did:key:example")
     * if (deactivated) {
     *     println("DID deactivated successfully")
     * }
     * ```
     * 
     * @param did The DID to deactivate
     * @return true if deactivated, false otherwise
     * @throws VeriCoreError.InvalidDidFormat if DID format is invalid
     * @throws VeriCoreError.DidMethodNotRegistered if method is not registered
     */
    suspend fun deactivate(did: String): Boolean {
        require(did.isNotBlank()) { "DID is required" }
        
        DidValidator.validateFormat(did).let {
            if (!it.isValid()) {
                throw VeriCoreError.InvalidDidFormat(
                    did = did,
                    reason = it.errorMessage() ?: "Invalid DID format"
                )
            }
        }
        
        val methodName = DidValidator.extractMethod(did)
            ?: throw VeriCoreError.InvalidDidFormat(
                did = did,
                reason = "Failed to extract method from DID"
            )
        
        val method = context.didRegistry.get(methodName)
            ?: throw VeriCoreError.DidMethodNotRegistered(
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

