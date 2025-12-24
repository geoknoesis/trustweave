package org.trustweave.kms.inmemory

import org.trustweave.kms.Algorithm
import org.trustweave.kms.KeyManagementService
import org.trustweave.kms.spi.KeyManagementServiceProvider

/**
 * SPI provider for TrustWeave's native in-memory Key Management Service.
 *
 * This provider is automatically discovered via Java ServiceLoader when the module is on the classpath.
 * The in-memory KMS requires no configuration or credentials, making it ideal for development and testing.
 *
 * **Example:**
 * ```kotlin
 * import org.trustweave.kms.*
 * 
 * // Simple factory API
 * val kms = KeyManagementServices.create("inmemory")
 *
 * // Or directly
 * val kms = InMemoryKeyManagementService()
 * ```
 *
 * **Use Cases:**
 * - Development and testing
 * - Single-process applications
 * - Ephemeral key management
 * - Prototyping and demos
 *
 * **Not Recommended For:**
 * - Production environments requiring key persistence
 * - Multi-process or distributed systems
 * - High-security scenarios requiring hardware-backed keys
 */
class InMemoryKeyManagementServiceProvider : KeyManagementServiceProvider {
    override val name: String = "inmemory"

    override val supportedAlgorithms: Set<Algorithm> = InMemoryKeyManagementService.SUPPORTED_ALGORITHMS

    /**
     * In-memory KMS requires no environment variables or credentials.
     */
    override val requiredEnvironmentVariables: List<String> = emptyList()

    override fun create(options: Map<String, Any?>): KeyManagementService {
        // In-memory KMS doesn't need any configuration
        // Options are ignored, but we could extend this in the future to support
        // things like custom key stores, persistence options, etc.
        return InMemoryKeyManagementService()
    }
}

