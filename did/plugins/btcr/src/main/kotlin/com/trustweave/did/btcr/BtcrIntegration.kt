package com.trustweave.did.btcr

import com.trustweave.did.DidCreationOptions
import com.trustweave.did.DidMethod
import com.trustweave.did.spi.DidMethodProvider
import com.trustweave.kms.KeyManagementService

/**
 * SPI provider for did:btcr (Bitcoin Reference) method.
 * 
 * Automatically discovered via Java ServiceLoader when the module is on the classpath.
 * 
 * Note: This provider requires a KeyManagementService to be provided via options.
 */
class BtcrIntegration : DidMethodProvider {
    
    override val name: String = "btcr"
    
    override val supportedMethods: List<String> = listOf("btcr")

    override fun create(methodName: String, options: DidCreationOptions): DidMethod? {
        if (methodName != "btcr") {
            return null
        }
        
        // Get KMS from options
        val kms = options.additionalProperties["kms"] as? KeyManagementService
            ?: throw IllegalArgumentException("KeyManagementService must be provided in options for did:btcr")
        
        return BtcrDidMethod(kms)
    }
}

