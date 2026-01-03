package org.trustweave.testkit.spi

import org.trustweave.kms.Algorithm
import org.trustweave.kms.KeyManagementService
import org.trustweave.kms.spi.KeyManagementServiceProvider
import org.trustweave.testkit.kms.InMemoryKeyManagementService

/**
 * SPI provider for testkit's InMemoryKeyManagementService.
 * 
 * This provider is automatically discovered when testkit is on the classpath.
 * It provides the "inMemory" provider name for testing scenarios.
 * 
 * **Note:** This provider uses the testkit implementation, which is separate
 * from the production inmemory plugin. When testkit is on the classpath,
 * this provider will be available for auto-discovery.
 */
class TestkitKeyManagementServiceProvider : KeyManagementServiceProvider {
    override val name: String = "inMemory"
    
    override val supportedAlgorithms: Set<Algorithm> = 
        InMemoryKeyManagementService.SUPPORTED_ALGORITHMS
    
    override val requiredEnvironmentVariables: List<String> = emptyList()
    
    override fun create(options: Map<String, Any?>): KeyManagementService {
        return InMemoryKeyManagementService()
    }
}


