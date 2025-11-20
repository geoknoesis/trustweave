package com.geoknoesis.vericore.kms.thalesluna

import com.geoknoesis.vericore.kms.Algorithm
import com.geoknoesis.vericore.kms.KeyManagementService
import com.geoknoesis.vericore.kms.spi.KeyManagementServiceProvider

class ThalesLunaKeyManagementServiceProvider : KeyManagementServiceProvider {
    override val name: String = "thales-luna"
    
    override val supportedAlgorithms: Set<Algorithm> = ThalesLunaKeyManagementService.SUPPORTED_ALGORITHMS

    override fun create(options: Map<String, Any?>): KeyManagementService {
        val config = ThalesLunaKmsConfig.fromMap(options)
        return ThalesLunaKeyManagementService(config)
    }
}

