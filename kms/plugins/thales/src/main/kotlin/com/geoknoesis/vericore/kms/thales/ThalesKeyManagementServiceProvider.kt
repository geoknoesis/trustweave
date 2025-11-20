package com.geoknoesis.vericore.kms.thales

import com.geoknoesis.vericore.kms.Algorithm
import com.geoknoesis.vericore.kms.KeyManagementService
import com.geoknoesis.vericore.kms.spi.KeyManagementServiceProvider

class ThalesKeyManagementServiceProvider : KeyManagementServiceProvider {
    override val name: String = "thales"
    
    override val supportedAlgorithms: Set<Algorithm> = ThalesKeyManagementService.SUPPORTED_ALGORITHMS

    override fun create(options: Map<String, Any?>): KeyManagementService {
        val config = ThalesKmsConfig.fromMap(options)
        return ThalesKeyManagementService(config)
    }
}

