package org.trustweave.testkit.spi

import org.trustweave.did.DidCreationOptions
import org.trustweave.did.DidMethod
import org.trustweave.did.spi.DidMethodProvider
import org.trustweave.kms.KeyManagementService
import org.trustweave.testkit.did.DidKeyMockMethod

/**
 * SPI provider for testkit's DidKeyMockMethod.
 * 
 * This provider is automatically discovered when testkit is on the classpath.
 * It provides the "key" DID method for testing scenarios.
 * 
 * **Note:** This provider uses the testkit implementation, which is separate
 * from the production key DID plugin. When testkit is on the classpath,
 * this provider will be available for auto-discovery.
 */
class TestkitDidMethodProvider : DidMethodProvider {
    override val name: String = "key"
    
    override val supportedMethods: List<String> = listOf("key")
    
    override val requiredEnvironmentVariables: List<String> = emptyList()
    
    override fun create(methodName: String, options: DidCreationOptions): DidMethod? {
        if (methodName.lowercase() != "key") {
            return null
        }
        
        // Get KMS from options
        val kms = (options.additionalProperties["kms"] as? KeyManagementService)
            ?: throw IllegalStateException(
                "KMS is required for did:key method. " +
                "Ensure KMS is provided in DidCreationOptions.additionalProperties['kms']"
            )
        
        return DidKeyMockMethod(kms)
    }
}


