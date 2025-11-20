package com.geoknoesis.vericore.did.tezos

import com.geoknoesis.vericore.did.DidCreationOptions
import com.geoknoesis.vericore.did.DidMethod
import com.geoknoesis.vericore.did.spi.DidMethodProvider
import com.geoknoesis.vericore.kms.KeyManagementService

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

