package com.trustweave.did.tezos

import com.trustweave.did.DidCreationOptions
import com.trustweave.did.DidMethod
import com.trustweave.did.spi.DidMethodProvider
import com.trustweave.kms.KeyManagementService

/**
 * SPI provider for did:tz (Tezos) method.
 * 
 * Automatically discovered via Java ServiceLoader when the module is on the classpath.
 */
class TezosIntegration : DidMethodProvider {
    
    override val name: String = "tezos"
    
    override val supportedMethods: List<String> = listOf("tz")

    override fun create(methodName: String, options: DidCreationOptions): DidMethod? {
        if (methodName != "tz") {
            return null
        }
        
        val kms = options.additionalProperties["kms"] as? KeyManagementService
            ?: throw IllegalArgumentException("KeyManagementService must be provided in options for did:tz")
        
        return TezosDidMethod(kms)
    }
}

