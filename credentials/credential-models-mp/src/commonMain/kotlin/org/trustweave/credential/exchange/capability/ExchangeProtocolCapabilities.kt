package org.trustweave.credential.exchange.capability

import org.trustweave.credential.exchange.ExchangeOperation
import kotlinx.serialization.json.JsonElement

/**
 * Protocol capabilities for credential exchange.
 * 
 * Describes what operations and features a protocol supports.
 * Similar to ProofEngineCapabilities in the credential API.
 */
data class ExchangeProtocolCapabilities(
    /**
     * Operations supported by this protocol.
     */
    val supportedOperations: Set<ExchangeOperation>,
    
    /**
     * Whether the protocol supports asynchronous message handling.
     */
    val supportsAsync: Boolean = false,
    
    /**
     * Whether the protocol can handle multiple credentials in a single exchange.
     */
    val supportsMultipleCredentials: Boolean = false,
    
    /**
     * Whether the protocol supports selective disclosure in proof requests.
     */
    val supportsSelectiveDisclosure: Boolean = false,
    
    /**
     * Whether the protocol requires transport-level security (TLS/mTLS).
     */
    val requiresTransportSecurity: Boolean = true,
    
    /**
     * Protocol-specific metadata and capabilities.
     */
    val metadata: Map<String, JsonElement> = emptyMap()
) {
    /**
     * Check if protocol supports a specific operation.
     */
    fun supports(operation: ExchangeOperation): Boolean = 
        operation in supportedOperations
    
    /**
     * Check if protocol supports all given operations.
     */
    fun supportsAll(operations: Set<ExchangeOperation>): Boolean =
        operations.all { it in supportedOperations }
    
    /**
     * Check if protocol supports any of the given operations.
     */
    fun supportsAny(operations: Set<ExchangeOperation>): Boolean =
        operations.any { it in supportedOperations }
}

