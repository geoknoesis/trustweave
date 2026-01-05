package org.trustweave.did.discovery

import org.trustweave.did.identifiers.Did
import org.trustweave.did.registry.DidMethodRegistry

/**
 * DID Method Discovery Service.
 *
 * Discovers available DID methods and their capabilities, enabling
 * dynamic method registration and capability detection.
 *
 * **Discovery Sources:**
 * - Local registry
 * - Universal Resolver
 * - Method-specific endpoints
 * - Configuration files
 *
 * **Use Cases:**
 * - Dynamic method loading
 * - Capability negotiation
 * - Method availability checking
 * - Runtime method registration
 *
 * **Example Usage:**
 * ```kotlin
 * val discoveryService = DefaultDidMethodDiscoveryService(registry)
 *
 * // Discover available methods
 * val methods = discoveryService.discoverAvailableMethods()
 * println("Available methods: ${methods.joinToString()}")
 *
 * // Check if method is supported
 * if (discoveryService.isMethodSupported("web")) {
 *     println("did:web is supported")
 * }
 *
 * // Get method capabilities
 * val capabilities = discoveryService.getMethodCapabilities("web")
 * ```
 */
interface DidMethodDiscoveryService {
    /**
     * Discover all available DID methods.
     *
     * @return List of available method names
     */
    suspend fun discoverAvailableMethods(): List<String>
    
    /**
     * Check if a method is supported.
     *
     * @param method The method name to check
     * @return true if method is supported
     */
    suspend fun isMethodSupported(method: String): Boolean
    
    /**
     * Get capabilities of a DID method.
     *
     * @param method The method name
     * @return Method capabilities, or null if method not found
     */
    suspend fun getMethodCapabilities(method: String): MethodCapabilities?
    
    /**
     * Discover methods from a Universal Resolver endpoint.
     *
     * @param endpoint The Universal Resolver endpoint URL
     * @return List of discovered method names
     */
    suspend fun discoverFromUniversalResolver(endpoint: String): List<String>
}

/**
 * Method capabilities.
 */
data class MethodCapabilities(
    val method: String,
    val supportsCreate: Boolean = false,
    val supportsUpdate: Boolean = false,
    val supportsDeactivate: Boolean = false,
    val supportsResolve: Boolean = true,
    val keyTypes: List<String> = emptyList(),
    val features: List<String> = emptyList()
)

/**
 * Default implementation of method discovery service.
 */
class DefaultDidMethodDiscoveryService(
    private val registry: DidMethodRegistry
) : DidMethodDiscoveryService {
    
    override suspend fun discoverAvailableMethods(): List<String> {
        return registry.getAllMethodNames()
    }
    
    override suspend fun isMethodSupported(method: String): Boolean {
        return registry.get(method) != null
    }
    
    override suspend fun getMethodCapabilities(method: String): MethodCapabilities? {
        val didMethod = registry.get(method) ?: return null
        
        return MethodCapabilities(
            method = method,
            supportsCreate = true,  // Most methods support create
            supportsUpdate = true,   // Most methods support update
            supportsDeactivate = true, // Most methods support deactivate
            supportsResolve = true,
            keyTypes = listOf("Ed25519", "Secp256k1"), // Common key types
            features = emptyList() // Method-specific features
        )
    }
    
    override suspend fun discoverFromUniversalResolver(endpoint: String): List<String> {
        // Query Universal Resolver /methods endpoint
        // This is a simplified implementation
        // Full implementation would make HTTP request to endpoint
        return emptyList() // Placeholder
    }
}

