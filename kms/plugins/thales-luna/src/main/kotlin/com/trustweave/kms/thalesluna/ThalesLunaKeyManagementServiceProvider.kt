package com.trustweave.kms.thalesluna

import com.trustweave.kms.Algorithm
import com.trustweave.kms.KeyManagementService
import com.trustweave.kms.spi.KeyManagementServiceProvider

class ThalesLunaKeyManagementServiceProvider : KeyManagementServiceProvider {
    override val name: String = "thales-luna"
    
    override val supportedAlgorithms: Set<Algorithm> = ThalesLunaKeyManagementService.SUPPORTED_ALGORITHMS

    override fun create(options: Map<String, Any?>): KeyManagementService {
        val config = ThalesLunaKmsConfig.fromMap(options)
        return ThalesLunaKeyManagementService(config)
    }
}

